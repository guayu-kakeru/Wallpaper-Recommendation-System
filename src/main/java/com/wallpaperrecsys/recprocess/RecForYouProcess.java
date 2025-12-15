package com.wallpaperrecsys.recprocess;

import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.datamanager.User;
import com.wallpaperrecsys.datamanager.Wallpaper;

import java.util.*;

/**
 * Recommendation process for personalized wallpaper recommendation
 * 个性化壁纸推荐处理类
 */
public class RecForYouProcess {

    /**
     * Get recommendation wallpaper list for a user
     * 获取用户的推荐壁纸列表
     * @param userId input user id
     * @param size size of recommended items
     * @param model model used for ranking
     * @return list of recommended wallpapers
     */
    public static List<Wallpaper> getRecList(int userId, int size, String model) {
        User user = WallpaperDataManager.getInstance().getUserById(userId);
        if (null == user) {
            return new ArrayList<>();
        }

        // 过滤：不要把用户已经评分/交互过的壁纸再次推荐给他
        Set<Integer> seenWallpaperIds = new HashSet<>();
        if (user.getRatings() != null) {
            for (com.wallpaperrecsys.datamanager.Rating r : user.getRatings()) {
                seenWallpaperIds.add(r.getWallpaperId());
            }
        }

        final int CANDIDATE_SIZE = 800;
        // 生成候选集：基于评分或下载量
        List<Wallpaper> candidates = WallpaperDataManager.getInstance()
            .getWallpapers(CANDIDATE_SIZE, "rating");

        // 过滤掉已看/已评分
        if (!seenWallpaperIds.isEmpty()) {
            List<Wallpaper> filtered = new ArrayList<>(candidates.size());
            for (Wallpaper w : candidates) {
                if (!seenWallpaperIds.contains(w.getWallpaperId())) {
                    filtered.add(w);
                }
            }
            candidates = filtered;
        }

        // 排序候选集
        List<Wallpaper> rankedList = ranker(user, candidates, model);

        if (rankedList.size() > size) {
            return rankedList.subList(0, size);
        }
        return rankedList;
    }

    /**
     * Rank candidates
     * 对候选集进行排序
     * @param user input user
     * @param candidates wallpaper candidates
     * @param model model name used for ranking
     * @return ranked wallpaper list
     */
    public static List<Wallpaper> ranker(User user, List<Wallpaper> candidates, String model) {
        HashMap<Wallpaper, Double> candidateScoreMap = new HashMap<>();

        switch (model.toLowerCase()) {
            case "emb":
                // 基于embedding相似度排序
                for (Wallpaper candidate : candidates) {
                    double similarity = calculateEmbSimilarScore(user, candidate);
                    // embedding 不可用时降级到流行度，避免大量 -1 导致推荐质量不稳定
                    if (similarity < 0) {
                        similarity = calculatePopularityScore(candidate) * 0.1;
                    }
                    candidateScoreMap.put(candidate, similarity);
                }
                break;
            case "popularity":
                // 基于流行度排序
                for (Wallpaper candidate : candidates) {
                    double popularityScore = calculatePopularityScore(candidate);
                    candidateScoreMap.put(candidate, popularityScore);
                }
                break;
            case "itemcf":
                // ItemCF 模型：由 ItemCFRecommendation 负责打分
                for (Wallpaper candidate : candidates) {
                    double cfScore = ItemCFRecommendation.getInstance()
                            .score(user.getUserId(), candidate.getWallpaperId());
                    candidateScoreMap.put(candidate, cfScore);
                }
                break;
            default:
                // 默认排序：按候选集顺序
                for (int i = 0; i < candidates.size(); i++) {
                    candidateScoreMap.put(candidates.get(i), (double) (candidates.size() - i));
                }
        }

        // 按得分降序排序
        List<Wallpaper> rankedList = new ArrayList<>();
        candidateScoreMap.entrySet().stream()
            .sorted(Map.Entry.<Wallpaper, Double>comparingByValue(Comparator.reverseOrder()))
            .forEach(m -> rankedList.add(m.getKey()));
        
        return rankedList;
    }

    /**
     * Calculate similarity score based on embedding
     * 基于embedding计算相似度分数
     */
    public static double calculateEmbSimilarScore(User user, Wallpaper candidate) {
        if (null == user || null == candidate || null == user.getEmb() || null == candidate.getEmb()) {
            return -1;
        }
        return user.getEmb().calculateSimilarity(candidate.getEmb());
    }

    /**
     * Calculate popularity score
     * 计算流行度分数
     */
    public static double calculatePopularityScore(Wallpaper wallpaper) {
        if (wallpaper == null) {
            return 0.0;
        }
        
        // 组合评分和下载量
        double ratingScore = wallpaper.getAverageRating() / 5.0;
        double downloadScore = Math.min(wallpaper.getDownloadCount() / 10000.0, 1.0);
        
        return ratingScore * 0.6 + downloadScore * 0.4;
    }
}

