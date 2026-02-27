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

import java.nio.file.Path;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

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
        Path modelPath = dir.resolve("model.onnx");
        Path tokenizerPath = dir.resolve("tokenizer.json");

        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath.toString());
        log.info("ONNX session loaded");

        tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        log.info("Tokenizer loaded from {}", tokenizerPath);
    }

    @PreDestroy
    public void destroy() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
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
}
