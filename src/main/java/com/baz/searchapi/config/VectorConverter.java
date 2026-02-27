package com.baz.searchapi.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.StringJoiner;

@Converter
public class VectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] v) {
        if (v == null) return null;
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float f : v) sj.add(String.valueOf(f));
        return sj.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String s) {
        if (s == null) return null;
        String[] parts = s.substring(1, s.length() - 1).split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Float.parseFloat(parts[i]);
        return v;
    }
}
