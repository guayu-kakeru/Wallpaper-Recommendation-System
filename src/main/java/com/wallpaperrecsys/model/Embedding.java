package com.wallpaperrecsys.model;

import java.util.List;

/**
 * Embedding Class, represents a vector embedding
 * Embedding类，表示向量嵌入
 */
public class Embedding {
    List<Double> vector;

    public Embedding() {
    }

    public Embedding(List<Double> vector) {
        this.vector = vector;
    }

    public List<Double> getVector() {
        return vector;
    }

    public void setVector(List<Double> vector) {
        this.vector = vector;
    }

    /**
     * Calculate cosine similarity with another embedding
     * 计算与另一个embedding的余弦相似度
     */
    public double calculateSimilarity(Embedding other) {
        if (this.vector == null || other == null || other.vector == null) {
            return -1;
        }
        if (this.vector.size() != other.vector.size()) {
            return -1;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < this.vector.size(); i++) {
            dotProduct += this.vector.get(i) * other.vector.get(i);
            normA += Math.pow(this.vector.get(i), 2);
            normB += Math.pow(other.vector.get(i), 2);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

