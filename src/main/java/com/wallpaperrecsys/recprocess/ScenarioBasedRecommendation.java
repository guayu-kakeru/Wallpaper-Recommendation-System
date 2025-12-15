package com.wallpaperrecsys.recprocess;

import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.datamanager.User;
import com.wallpaperrecsys.datamanager.Wallpaper;
import com.wallpaperrecsys.datamanager.Rating;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scenario-based Recommendation Service
 * 基于场景的推荐服务
 */
public class ScenarioBasedRecommendation {
    
    // 场景配置映射
    private static final Map<String, ScenarioConfig> SCENARIO_CONFIGS = new HashMap<>();
    
    static {
        // 工作场景
        SCENARIO_CONFIGS.put("work", new ScenarioConfig(
            Arrays.asList("minimalist"),                 // 目前数据里可命中的 style 较少，主要靠 tag/category
            Arrays.asList("calm", "cool"),
            Arrays.asList("#ffffff", "#cccccc", "#999999"), // 用颜色近似“干净/中性”
            Arrays.asList("city", "abstract", "nature"),
            Arrays.asList("极简", "简约", "宁静", "城市", "写实"),
            0.3, 0.7  // 更注重用户偏好
        ));
        
        // 游戏场景
        SCENARIO_CONFIGS.put("gaming", new ScenarioConfig(
            Arrays.asList("anime"),
            Arrays.asList("energetic", "dark"),
            Arrays.asList("#000000", "#111111", "#222222"),
            Arrays.asList("anime", "abstract", "space", "city"),
            Arrays.asList("动漫", "二次元", "暗黑", "霓虹", "科幻", "太空", "抽象"),
            0.5, 0.5  // 平衡多样性和用户偏好
        ));
        
        // 阅读场景
        SCENARIO_CONFIGS.put("reading", new ScenarioConfig(
            Arrays.asList("minimalist"),
            Arrays.asList("calm", "warm"),
            Arrays.asList("#ffffff", "#abbcda", "#999999"),
            Arrays.asList("nature", "abstract", "city"),
            Arrays.asList("宁静", "温暖", "风景", "自然", "简约"),
            0.4, 0.6
        ));
        
        // 睡眠场景
        SCENARIO_CONFIGS.put("sleep", new ScenarioConfig(
            Arrays.asList("minimalist"),
            Arrays.asList("calm", "dark"),
            Arrays.asList("#000000", "#111111", "#222222"),
            Arrays.asList("space", "abstract", "nature"),
            Arrays.asList("暗黑", "深夜", "夜景", "星空", "太空", "宁静"),
            0.2, 0.8  // 更注重用户偏好
        ));
        
        // 创意场景
        SCENARIO_CONFIGS.put("creative", new ScenarioConfig(
            Arrays.asList("anime"),
            Arrays.asList("energetic"),
            Arrays.asList("#ff", "#f7", "#a8"), // 颜色包含即可（见匹配逻辑）
            Arrays.asList("abstract", "city"),
            Arrays.asList("抽象", "艺术", "设计", "霓虹", "色彩"),
            0.6, 0.4  // 更注重多样性
        ));
        
        // 早晨场景
        SCENARIO_CONFIGS.put("morning", new ScenarioConfig(
            Arrays.asList("minimalist"),
            Arrays.asList("energetic", "warm"),
            Arrays.asList("#ffffff", "#abbcda", "#cccccc"),
            Arrays.asList("nature", "city"),
            Arrays.asList("清新", "明亮", "风景", "城市"),
            0.4, 0.6
        ));
        
        // 下午场景
        SCENARIO_CONFIGS.put("afternoon", new ScenarioConfig(
            Arrays.asList("minimalist"),
            Arrays.asList("energetic", "warm"),
            Arrays.asList("#ffffff", "#999999", "#abbcda"),
            Arrays.asList("city", "nature"),
            Arrays.asList("温暖", "城市", "风景"),
            0.5, 0.5
        ));
        
        // 晚上场景
        SCENARIO_CONFIGS.put("evening", new ScenarioConfig(
            Arrays.asList("minimalist"),
            Arrays.asList("calm", "warm"),
            Arrays.asList("#111111", "#222222", "#999999"),
            Arrays.asList("city", "nature"),
            Arrays.asList("夜景", "城市", "宁静", "温暖"),
            0.3, 0.7
        ));
        
        // 深夜场景
        SCENARIO_CONFIGS.put("night", new ScenarioConfig(
            Arrays.asList("minimalist"),
            Arrays.asList("calm", "dark", "cool"),
            Arrays.asList("#000000", "#111111", "#222222"),
            Arrays.asList("space", "abstract", "city"),
            Arrays.asList("夜景", "暗黑", "星空", "太空", "城市"),
            0.2, 0.8
        ));
    }
    
    /**
     * Recommend wallpapers based on scenario
     * 基于场景推荐壁纸
     */
    public static List<Wallpaper> recommendByScenario(
        String scenario, 
        int userId, 
        int size
    ) {
        ScenarioConfig config = SCENARIO_CONFIGS.get(scenario.toLowerCase());
        if (config == null) {
            return getDefaultRecommendations(userId, size);
        }
        
        User user = WallpaperDataManager.getInstance().getUserById(userId);
        HashMap<Integer, Wallpaper> candidateMap = new HashMap<>();
        
        // 1. Recall based on scenario config
        for (String style : config.preferredStyles) {
            List<Wallpaper> candidates = WallpaperDataManager.getInstance()
                .getWallpapersByStyle(style, 50, "rating");
            for (Wallpaper w : candidates) {
                candidateMap.put(w.getWallpaperId(), w);
            }
        }
        
        for (String mood : config.preferredMoods) {
            List<Wallpaper> candidates = WallpaperDataManager.getInstance()
                .getWallpapersByMood(mood, 50, "rating");
            for (Wallpaper w : candidates) {
                candidateMap.put(w.getWallpaperId(), w);
            }
        }
        
        for (String category : config.preferredCategories) {
            List<Wallpaper> candidates = WallpaperDataManager.getInstance()
                .getWallpapersByCategory(category, 50, "rating");
            for (Wallpaper w : candidates) {
                candidateMap.put(w.getWallpaperId(), w);
            }
        }

        // 额外：按“标签”直接召回（与 wallpapers.csv 的 tags 字段一致，命中率更高）
        if (config.preferredTags != null) {
            for (String tag : config.preferredTags) {
                List<Wallpaper> tagCandidates = WallpaperDataManager.getInstance()
                        .getWallpapersByTag(tag, 80, "rating");
                for (Wallpaper w : tagCandidates) {
                    candidateMap.put(w.getWallpaperId(), w);
                }
            }
        }
        
        // 2. If candidate set is too small, supplement with popular wallpapers
        if (candidateMap.size() < size * 2) {
            List<Wallpaper> popularWallpapers = WallpaperDataManager.getInstance()
                .getWallpapers(size * 2, "download");
            for (Wallpaper w : popularWallpapers) {
                candidateMap.put(w.getWallpaperId(), w);
            }
        }
        
        // 3. Rank
        List<Wallpaper> rankedList = rankByScenario(
            new ArrayList<>(candidateMap.values()),
            config,
            user
        );
        
        return rankedList.subList(0, Math.min(size, rankedList.size()));
    }
    
    /**
     * Rank by scenario
     * 场景化排序
     */
    private static List<Wallpaper> rankByScenario(
        List<Wallpaper> candidates,
        ScenarioConfig config,
        User user
    ) {
        HashMap<Wallpaper, Double> scoreMap = new HashMap<>();
        
        for (Wallpaper w : candidates) {
            double score = 0.0;
            
            // 1. Style match (30%)
            if (w.getStyle() != null && config.preferredStyles.contains(w.getStyle())) {
                score += 0.3;
            }
            
            // 2. Mood match (20%)
            if (w.getMood() != null && config.preferredMoods.contains(w.getMood())) {
                score += 0.2;
            }
            
            // 3. Color palette match (15%)
            if (w.getColorPalette() != null && config.preferredColorPalettes != null) {
                for (String c : config.preferredColorPalettes) {
                    if (c != null && !c.isEmpty() && w.getColorPalette().contains(c)) {
                        score += 0.05; // 命中一个颜色加一点，避免过拟合
                    }
                }
            }
            
            // 4. Category match (15%)
            boolean categoryMatch = false;
            for (String category : config.preferredCategories) {
                if (w.getCategories().contains(category)) {
                    categoryMatch = true;
                    break;
                }
            }
            if (categoryMatch) {
                score += 0.15;
            }

            // 4.5 Tag match (20%)
            if (config.preferredTags != null && w.getTags() != null) {
                for (String t : w.getTags()) {
                    if (config.preferredTags.contains(t)) {
                        score += 0.05;
                    }
                }
            }
            
            // 5. User preference (20%)
            if (user != null) {
                double userPreferenceScore = calculateUserPreferenceScore(user, w);
                score += userPreferenceScore * config.userPreferenceWeight;
            }
            
            // 6. Diversity bonus
            double diversityScore = calculateDiversityScore(w, candidates);
            score += diversityScore * config.diversityWeight;
            
            scoreMap.put(w, score);
        }
        
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate user preference score
     * 计算用户偏好分数
     */
    private static double calculateUserPreferenceScore(User user, Wallpaper wallpaper) {
        if (user == null || user.getRatings().isEmpty()) {
            return 0.0;
        }
        
        double score = 0.0;
        
        // Analyze user history preferences
        Set<String> userPreferredTags = getUserPreferredTags(user);
        Set<String> userPreferredStyles = getUserPreferredStyles(user);
        Set<String> userPreferredMoods = getUserPreferredMoods(user);
        
        // Tag match
        for (String tag : wallpaper.getTags()) {
            if (userPreferredTags.contains(tag)) {
                score += 0.1;
            }
        }
        
        // Style match
        if (wallpaper.getStyle() != null && userPreferredStyles.contains(wallpaper.getStyle())) {
            score += 0.2;
        }
        
        // Mood match
        if (wallpaper.getMood() != null && userPreferredMoods.contains(wallpaper.getMood())) {
            score += 0.1;
        }
        
        // Normalize
        return Math.min(score, 1.0);
    }
    
    /**
     * Calculate diversity score
     * 计算多样性分数
     */
    private static double calculateDiversityScore(
        Wallpaper wallpaper,
        List<Wallpaper> candidates
    ) {
        // Simple implementation: reward diversity
        // In production, calculate based on tag/category/style differences
        return 0.1;
    }
    
    /**
     * Get user preferred tags
     * 获取用户偏好的标签
     */
    private static Set<String> getUserPreferredTags(User user) {
        Set<String> preferredTags = new HashSet<>();
        for (Rating rating : user.getRatings()) {
            if (rating.getScore() >= 4.0) {
                Wallpaper wallpaper = WallpaperDataManager.getInstance()
                    .getWallpaperById(rating.getWallpaperId());
                if (wallpaper != null) {
                    preferredTags.addAll(wallpaper.getTags());
                }
            }
        }
        return preferredTags;
    }
    
    /**
     * Get user preferred styles
     * 获取用户偏好的风格
     */
    private static Set<String> getUserPreferredStyles(User user) {
        Set<String> preferredStyles = new HashSet<>();
        for (Rating rating : user.getRatings()) {
            if (rating.getScore() >= 4.0) {
                Wallpaper wallpaper = WallpaperDataManager.getInstance()
                    .getWallpaperById(rating.getWallpaperId());
                if (wallpaper != null && wallpaper.getStyle() != null) {
                    preferredStyles.add(wallpaper.getStyle());
                }
            }
        }
        return preferredStyles;
    }
    
    /**
     * Get user preferred moods
     * 获取用户偏好的情绪
     */
    private static Set<String> getUserPreferredMoods(User user) {
        Set<String> preferredMoods = new HashSet<>();
        for (Rating rating : user.getRatings()) {
            if (rating.getScore() >= 4.0) {
                Wallpaper wallpaper = WallpaperDataManager.getInstance()
                    .getWallpaperById(rating.getWallpaperId());
                if (wallpaper != null && wallpaper.getMood() != null) {
                    preferredMoods.add(wallpaper.getMood());
                }
            }
        }
        return preferredMoods;
    }
    
    /**
     * Default recommendations
     * 默认推荐
     */
    private static List<Wallpaper> getDefaultRecommendations(int userId, int size) {
        return WallpaperDataManager.getInstance().getWallpapers(size, "rating");
    }
}

