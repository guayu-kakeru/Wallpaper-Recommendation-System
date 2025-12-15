package com.wallpaperrecsys.recprocess;

import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.datamanager.Wallpaper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommendation process for similar wallpapers
 * 相似壁纸推荐处理类
 */
public class SimilarWallpaperProcess {

    /**
     * Get similar wallpaper list
     * 获取相似壁纸列表
     * @param wallpaperId input wallpaper id
     * @param size size of similar items
     * @param model model used for calculating similarity
     * @return list of similar wallpapers
     */
    public static List<Wallpaper> getRecList(int wallpaperId, int size, String model) {
        Wallpaper wallpaper = WallpaperDataManager.getInstance().getWallpaperById(wallpaperId);
        if (null == wallpaper) {
            return new ArrayList<>();
        }
        
        List<Wallpaper> candidates = candidateGenerator(wallpaper);
        List<Wallpaper> rankedList = ranker(wallpaper, candidates, model);

        if (rankedList.size() > size) {
            return rankedList.subList(0, size);
        }
        return rankedList;
    }

    /**
     * Generate candidates for similar wallpapers recommendation
     * 生成相似壁纸推荐的候选集
     * @param wallpaper input wallpaper object
     * @return wallpaper candidates
     */
    public static List<Wallpaper> candidateGenerator(Wallpaper wallpaper) {
        HashMap<Integer, Wallpaper> candidateMap = new HashMap<>();
        
        // 1. 基于标签召回
        for (String tag : wallpaper.getTags()) {
            List<Wallpaper> tagCandidates = WallpaperDataManager.getInstance()
                .getWallpapersByTag(tag, 100, "rating");
            for (Wallpaper candidate : tagCandidates) {
                candidateMap.put(candidate.getWallpaperId(), candidate);
            }
        }
        
        // 2. 基于分类召回
        for (String category : wallpaper.getCategories()) {
            List<Wallpaper> categoryCandidates = WallpaperDataManager.getInstance()
                .getWallpapersByCategory(category, 100, "rating");
            for (Wallpaper candidate : categoryCandidates) {
                candidateMap.put(candidate.getWallpaperId(), candidate);
            }
        }
        
        // 3. 基于风格召回
        if (wallpaper.getStyle() != null && !wallpaper.getStyle().isEmpty()) {
            List<Wallpaper> styleCandidates = WallpaperDataManager.getInstance()
                .getWallpapersByStyle(wallpaper.getStyle(), 50, "rating");
            for (Wallpaper candidate : styleCandidates) {
                candidateMap.put(candidate.getWallpaperId(), candidate);
            }
        }
        
        // 移除自身
        candidateMap.remove(wallpaper.getWallpaperId());
        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Multiple-retrieval candidate generation method
     * 多路召回候选生成方法
     */
    public static List<Wallpaper> multipleRetrievalCandidates(Wallpaper wallpaper) {
        if (null == wallpaper) {
            return null;
        }

        HashSet<String> tags = new HashSet<>(wallpaper.getTags());
        HashSet<String> categories = new HashSet<>(wallpaper.getCategories());

        HashMap<Integer, Wallpaper> candidateMap = new HashMap<>();
        
        // 1. 标签召回
        for (String tag : tags) {
            List<Wallpaper> candidates = WallpaperDataManager.getInstance()
                .getWallpapersByTag(tag, 20, "rating");
            for (Wallpaper candidate : candidates) {
                candidateMap.put(candidate.getWallpaperId(), candidate);
            }
        }
        
        // 2. 分类召回
        for (String category : categories) {
            List<Wallpaper> candidates = WallpaperDataManager.getInstance()
                .getWallpapersByCategory(category, 20, "rating");
            for (Wallpaper candidate : candidates) {
                candidateMap.put(candidate.getWallpaperId(), candidate);
            }
        }
        
        // 3. 热门召回
        List<Wallpaper> popularCandidates = WallpaperDataManager.getInstance()
            .getWallpapers(100, "download");
        for (Wallpaper candidate : popularCandidates) {
            candidateMap.put(candidate.getWallpaperId(), candidate);
        }
        
        // 4. 最新上传
        List<Wallpaper> latestCandidates = WallpaperDataManager.getInstance()
            .getWallpapers(100, "uploadtime");
        for (Wallpaper candidate : latestCandidates) {
            candidateMap.put(candidate.getWallpaperId(), candidate);
        }

        candidateMap.remove(wallpaper.getWallpaperId());
        return new ArrayList<>(candidateMap.values());
    }

    /**
     * Embedding based candidate generation method
     * 基于embedding的候选生成方法
     */
    public static List<Wallpaper> retrievalCandidatesByEmbedding(Wallpaper wallpaper, int size) {
        if (null == wallpaper || null == wallpaper.getEmb()) {
            return null;
        }

        List<Wallpaper> allCandidates = WallpaperDataManager.getInstance()
            .getAllWallpapersWithEmbedding();
        
        HashMap<Wallpaper, Double> wallpaperScoreMap = new HashMap<>();
        for (Wallpaper candidate : allCandidates) {
            if (candidate.getWallpaperId() == wallpaper.getWallpaperId()) {
                continue; // 跳过自身
            }
            double similarity = calculateEmbSimilarScore(wallpaper, candidate);
            wallpaperScoreMap.put(candidate, similarity);
        }

        List<Map.Entry<Wallpaper, Double>> wallpaperScoreList = 
            new ArrayList<>(wallpaperScoreMap.entrySet());
        wallpaperScoreList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        List<Wallpaper> candidates = new ArrayList<>();
        for (Map.Entry<Wallpaper, Double> entry : wallpaperScoreList) {
            candidates.add(entry.getKey());
        }

        return candidates.subList(0, Math.min(candidates.size(), size));
    }

    /**
     * Rank candidates
     * 对候选集排序
     */
    public static List<Wallpaper> ranker(Wallpaper wallpaper, List<Wallpaper> candidates, String model) {
        HashMap<Wallpaper, Double> candidateScoreMap = new HashMap<>();
        
        for (Wallpaper candidate : candidates) {
            double similarity;
            switch (model.toLowerCase()) {
                case "emb":
                    similarity = calculateEmbSimilarScore(wallpaper, candidate);
                    break;
                default:
                    similarity = calculateSimilarScore(wallpaper, candidate);
            }
            candidateScoreMap.put(candidate, similarity);
        }
        
        List<Wallpaper> rankedList = new ArrayList<>();
        candidateScoreMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Double>comparingByValue(Comparator.reverseOrder()))
            .forEach(m -> rankedList.add(m.getKey()));
        
        return rankedList;
    }

    /**
     * Calculate similarity score
     * 计算相似度分数
     */
    public static double calculateSimilarScore(Wallpaper wallpaper, Wallpaper candidate) {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // 1. 标签相似度 (30%)
        int sameTagCount = 0;
        for (String tag : wallpaper.getTags()) {
            if (candidate.getTags().contains(tag)) {
                sameTagCount++;
            }
        }
        double tagSimilarity = wallpaper.getTags().isEmpty() ? 0.0 :
            (double) sameTagCount / Math.max(wallpaper.getTags().size(), candidate.getTags().size());
        score += tagSimilarity * 0.3;
        totalWeight += 0.3;
        
        // 2. 分类相似度 (20%)
        int sameCategoryCount = 0;
        for (String category : wallpaper.getCategories()) {
            if (candidate.getCategories().contains(category)) {
                sameCategoryCount++;
            }
        }
        double categorySimilarity = wallpaper.getCategories().isEmpty() ? 0.0 :
            (double) sameCategoryCount / Math.max(wallpaper.getCategories().size(), candidate.getCategories().size());
        score += categorySimilarity * 0.2;
        totalWeight += 0.2;
        
        // 3. 风格相似度 (20%)
        double styleSimilarity = 0.0;
        if (wallpaper.getStyle() != null && candidate.getStyle() != null) {
            styleSimilarity = wallpaper.getStyle().equals(candidate.getStyle()) ? 1.0 : 0.0;
        }
        score += styleSimilarity * 0.2;
        totalWeight += 0.2;
        
        // 4. 情绪相似度 (10%)
        double moodSimilarity = 0.0;
        if (wallpaper.getMood() != null && candidate.getMood() != null) {
            moodSimilarity = wallpaper.getMood().equals(candidate.getMood()) ? 1.0 : 0.0;
        }
        score += moodSimilarity * 0.1;
        totalWeight += 0.1;
        
        // 5. 评分分数 (20%)
        double ratingScore = candidate.getAverageRating() / 5.0;
        score += ratingScore * 0.2;
        totalWeight += 0.2;
        
        return totalWeight > 0 ? score / totalWeight : 0.0;
    }

    /**
     * Calculate similarity score based on embedding
     * 基于embedding计算相似度
     */
    public static double calculateEmbSimilarScore(Wallpaper wallpaper, Wallpaper candidate) {
        if (null == wallpaper || null == candidate || 
            null == wallpaper.getEmb() || null == candidate.getEmb()) {
            return -1;
        }
        return wallpaper.getEmb().calculateSimilarity(candidate.getEmb());
    }
}

