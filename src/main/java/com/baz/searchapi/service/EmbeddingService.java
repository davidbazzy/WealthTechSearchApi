package com.baz.searchapi.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private static final String HF_BASE =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main";
    private static final String MODEL_URL = HF_BASE + "/onnx/model.onnx";
    private static final String TOKENIZER_URL = HF_BASE + "/tokenizer.json";

    private final String modelDir;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    public EmbeddingService(@Value("${embedding.model.dir:models/all-MiniLM-L6-v2}") String modelDir) {
        this.modelDir = modelDir;
    }

    @PostConstruct
    public void init() throws Exception {
        Path dir = Path.of(modelDir);
        Files.createDirectories(dir);

        Path modelPath = dir.resolve("model.onnx");
        Path tokenizerPath = dir.resolve("tokenizer.json");

        downloadIfMissing(modelPath, MODEL_URL, "ONNX model");
        downloadIfMissing(tokenizerPath, TOKENIZER_URL, "tokenizer");

        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath.toString());
        log.info("ONNX session loaded");

        tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        log.info("Tokenizer loaded from {}", tokenizerPath);
    }

    private void downloadIfMissing(Path path, String url, String label) throws IOException {
        if (!Files.exists(path)) {
            log.info("Downloading {} from HuggingFace...", label);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream in = response.body()) {
                    Files.copy(in, path);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted: " + label, e);
            }
            log.info("{} downloaded to {}", label, path);
        }
    }

    @PreDestroy
    public void destroy() throws OrtException {
        if (session != null) session.close();
    }

    /**
     * Generate a 384-dimensional embedding for the given text.
     */
    public float[] embed(String text) {
        try {
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();

            try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, new long[][]{inputIds});
                 OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, new long[][]{attentionMask});
                 OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, new long[][]{tokenTypeIds})) {

                Map<String, OnnxTensor> inputs = Map.of(
                        "input_ids", inputIdsTensor,
                        "attention_mask", attentionMaskTensor,
                        "token_type_ids", tokenTypeIdsTensor
                );

                try (OrtSession.Result result = session.run(inputs)) {
                    // Output shape: [1, seq_len, 384] — last_hidden_state
                    float[][][] output = (float[][][]) result.get(0).getValue();
                    float[] pooled = meanPooling(output[0], attentionMask);
                    return normalize(pooled);
                }
            }
        } catch (OrtException e) {
            throw new RuntimeException("Embedding inference failed", e);
        }
    }

    private float[] meanPooling(float[][] tokenEmbeddings, long[] attentionMask) {
        int dim = tokenEmbeddings[0].length;
        float[] sum = new float[dim];
        float maskSum = 0;

        for (int i = 0; i < tokenEmbeddings.length; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < dim; j++) {
                    sum[j] += tokenEmbeddings[i][j];
                }
                maskSum += 1;
            }
        }

        if (maskSum > 0) {
            for (int j = 0; j < dim; j++) {
                sum[j] /= maskSum;
            }
        }
        return sum;
    }

    private float[] normalize(float[] v) {
        float norm = 0;
        for (float f : v) norm += f * f;
        norm = (float) Math.sqrt(norm);
        if (norm == 0) return v;

        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            result[i] = v[i] / norm;
        }
        return result;
    }

    // --- Serialization utilities for storing float[] as byte[] in the DB ---

    public static byte[] toBytes(float[] embedding) {
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(embedding);
        return buffer.array();
    }

    public static float[] toFloats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] embedding = new float[bytes.length / 4];
        buffer.asFloatBuffer().get(embedding);
        return embedding;
    }

    /**
     * Cosine similarity between two vectors. Returns value in [-1, 1].
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
