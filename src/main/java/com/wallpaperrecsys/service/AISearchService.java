package com.wallpaperrecsys.service;

import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.datamanager.Wallpaper;
import com.wallpaperrecsys.model.Embedding;
import com.wallpaperrecsys.service.TextEmbeddingService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Search Service - Intelligent keyword search for wallpapers
 * AI搜索服务 - 智能关键词搜索壁纸
 */
public class AISearchService {
    
    /**
     * Search wallpapers by keyword
     * 基于关键词搜索壁纸
     * @param keyword user input keyword
     * @param size number of results to return
     * @return list of relevant wallpapers
     */
    public static List<Wallpaper> searchByKeyword(String keyword, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. Get keyword embedding
        Embedding keywordEmb = TextEmbeddingService.getEmbedding(keyword.trim());
        if (keywordEmb == null) {
            // Fallback to text matching if embedding service unavailable
            return fallbackSearch(keyword, size);
        }
        
        // 2. Get all wallpapers with embedding
        List<Wallpaper> allWallpapers = WallpaperDataManager.getInstance()
            .getAllWallpapersWithEmbedding();
        
        // 3. Calculate similarity
        HashMap<Wallpaper, Double> similarityMap = new HashMap<>();
        for (Wallpaper wallpaper : allWallpapers) {
            if (wallpaper.getEmb() != null) {
                double similarity = keywordEmb.calculateSimilarity(wallpaper.getEmb());
                similarityMap.put(wallpaper, similarity);
            }
        }
        
        // 4. Sort and return Top N
        return similarityMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Double>comparingByValue().reversed())
            .limit(size)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Multi-keyword search with AND logic
     * 多关键词搜索（AND逻辑）
     */
    public static List<Wallpaper> searchByKeywordsAND(List<String> keywords, int size) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Get embeddings for each keyword
        List<Embedding> keywordEmbs = new ArrayList<>();
        for (String keyword : keywords) {
            Embedding emb = TextEmbeddingService.getEmbedding(keyword.trim());
            if (emb != null) {
                keywordEmbs.add(emb);
            }
        }
        
        if (keywordEmbs.isEmpty()) {
            return fallbackSearch(keywords.get(0), size);
        }
        
        List<Wallpaper> allWallpapers = WallpaperDataManager.getInstance()
            .getAllWallpapersWithEmbedding();
        
        HashMap<Wallpaper, Double> scoreMap = new HashMap<>();
        
        for (Wallpaper wallpaper : allWallpapers) {
            if (wallpaper.getEmb() == null) continue;
            
            // Calculate average similarity with all keywords
            double totalSimilarity = 0.0;
            int validKeywords = 0;
            
            for (Embedding keywordEmb : keywordEmbs) {
                double sim = keywordEmb.calculateSimilarity(wallpaper.getEmb());
                totalSimilarity += sim;
                validKeywords++;
            }
            
            if (validKeywords > 0) {
                double avgSimilarity = totalSimilarity / validKeywords;
                scoreMap.put(wallpaper, avgSimilarity);
            }
        }
        
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Double>comparingByValue().reversed())
            .limit(size)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Multi-keyword search with OR logic
     * 多关键词搜索（OR逻辑）
     */
    public static List<Wallpaper> searchByKeywordsOR(List<String> keywords, int size) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }
        
        HashMap<Wallpaper, Double> scoreMap = new HashMap<>();
        
        for (String keyword : keywords) {
            List<Wallpaper> results = searchByKeyword(keyword, size * 2);
            for (Wallpaper wallpaper : results) {
                // Take maximum similarity
                double currentScore = scoreMap.getOrDefault(wallpaper, 0.0);
                Embedding keywordEmb = TextEmbeddingService.getEmbedding(keyword);
                if (keywordEmb != null && wallpaper.getEmb() != null) {
                    double similarity = keywordEmb.calculateSimilarity(wallpaper.getEmb());
                    scoreMap.put(wallpaper, Math.max(currentScore, similarity));
                }
            }
        }
        
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Double>comparingByValue().reversed())
            .limit(size)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Fallback search using text matching
     * 降级搜索（文本匹配）
     */
    private static List<Wallpaper> fallbackSearch(String keyword, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Wallpaper> allWallpapers = WallpaperDataManager.getInstance().getAllWallpapers();
        HashMap<Wallpaper, Integer> matchScoreMap = new HashMap<>();
        
        String keywordLower = keyword.toLowerCase();

        // 简单的中英文同义词映射，方便英文关键词命中中文标签/类别
        if (keywordLower.contains("anime") || keywordLower.contains("cartoon")) {
            keywordLower = "动漫";
        } else if (keywordLower.contains("nature") || keywordLower.contains("landscape")) {
            keywordLower = "风景";
        } else if (keywordLower.contains("city") || keywordLower.contains("urban")) {
            keywordLower = "城市";
        } else if (keywordLower.contains("space") || keywordLower.contains("galaxy") || keywordLower.contains("cosmos")) {
            keywordLower = "太空";
        } else if (keywordLower.contains("abstract")) {
            keywordLower = "抽象";
        } else if (keywordLower.contains("night")) {
            keywordLower = "夜景";
        }
        
        for (Wallpaper wallpaper : allWallpapers) {
            int score = 0;
            
            // Title match
            if (wallpaper.getTitle() != null && 
                wallpaper.getTitle().toLowerCase().contains(keywordLower)) {
                score += 10;
            }
            
            // Tag match
            for (String tag : wallpaper.getTags()) {
                if (tag.toLowerCase().contains(keywordLower)) {
                    score += 5;
                }
            }
            
            // Category match
            for (String category : wallpaper.getCategories()) {
                if (category.toLowerCase().contains(keywordLower)) {
                    score += 3;
                }
            }
            
            if (score > 0) {
                matchScoreMap.put(wallpaper, score);
            }
        }
        
        return matchScoreMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Integer>comparingByValue().reversed())
            .limit(size)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Intelligent search combining multiple strategies
     * 智能搜索（结合多种策略）
     */
    public static List<Wallpaper> intelligentSearch(String query, int size) {
        // 1. Try embedding search
        List<Wallpaper> embeddingResults = searchByKeyword(query, size);
        
        // 2. If results are too few, supplement with text matching
        if (embeddingResults.size() < size / 2) {
            List<Wallpaper> textResults = fallbackSearch(query, size);
            
            // Merge results (deduplicate)
            HashMap<Integer, Wallpaper> mergedMap = new HashMap<>();
            for (Wallpaper w : embeddingResults) {
                mergedMap.put(w.getWallpaperId(), w);
            }
            for (Wallpaper w : textResults) {
                if (!mergedMap.containsKey(w.getWallpaperId())) {
                    mergedMap.put(w.getWallpaperId(), w);
                }
            }
            
            return new ArrayList<>(mergedMap.values()).subList(0, 
                Math.min(size, mergedMap.size()));
        }
        
        return embeddingResults;
    }
}

