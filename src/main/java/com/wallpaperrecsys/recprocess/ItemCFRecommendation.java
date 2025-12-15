package com.wallpaperrecsys.recprocess;

import com.wallpaperrecsys.datamanager.Rating;
import com.wallpaperrecsys.datamanager.User;
import com.wallpaperrecsys.datamanager.Wallpaper;
import com.wallpaperrecsys.datamanager.WallpaperDataManager;

import java.util.*;

/**
 * Item-based Collaborative Filtering (ItemCF) recommender for personalization.
 * 基于物品的协同过滤（ItemCF）个性化推荐：用用户共同喜欢的壁纸构建物品相似度。
 *
 * 说明：
 * - 在线推荐场景下，这里直接用当前加载到内存的 ratings 构建相似度（一次构建，多次复用）。
 * - 离线评测会在 eval 包里用训练集单独构建，避免数据泄漏。
 */
public class ItemCFRecommendation {

    private static volatile ItemCFRecommendation instance;

    // 认为“喜欢”的评分阈值（可按数据分布调整）
    private static final double LIKE_THRESHOLD = 4.0;
    // 每个物品保留的近邻数量（控制内存与速度）
    private static final int MAX_NEIGHBORS = 80;

    private volatile boolean built = false;

    // i -> (j -> sim(i,j))，只保存 Top-N 近邻
    private final Map<Integer, Map<Integer, Double>> itemTopSim = new HashMap<>();
    // i -> countLikedUsers
    private final Map<Integer, Integer> itemLikedUserCount = new HashMap<>();

    private ItemCFRecommendation() {
    }

    public static ItemCFRecommendation getInstance() {
        if (instance == null) {
            synchronized (ItemCFRecommendation.class) {
                if (instance == null) {
                    instance = new ItemCFRecommendation();
                }
            }
        }
        return instance;
    }

    /**
     * Score a candidate item for a given user.
     * 给定 userId 与候选 wallpaperId，输出 ItemCF 分数（越大越推荐）。
     */
    public double score(int userId, int candidateWallpaperId) {
        ensureBuilt();

        User user = WallpaperDataManager.getInstance().getUserById(userId);
        if (user == null || user.getRatings() == null || user.getRatings().isEmpty()) {
            return fallbackPopularity(candidateWallpaperId);
        }

        double sum = 0.0;
        int used = 0;

        for (Rating r : user.getRatings()) {
            if (r.getScore() < LIKE_THRESHOLD) {
                continue;
            }
            int likedItemId = r.getWallpaperId();
            Map<Integer, Double> neighbors = itemTopSim.get(likedItemId);
            if (neighbors == null) {
                continue;
            }
            Double sim = neighbors.get(candidateWallpaperId);
            if (sim == null) {
                continue;
            }

            // 用评分强度做轻量加权（归一化到 0~1）
            double pref = Math.min(Math.max(r.getScore() / 5.0, 0.0), 1.0);
            sum += sim * pref;
            used++;
        }

        if (used == 0) {
            return fallbackPopularity(candidateWallpaperId);
        }
        return sum;
    }

    /**
     * Ensure the similarity model is built once.
     */
    private void ensureBuilt() {
        if (built) {
            return;
        }
        synchronized (this) {
            if (built) {
                return;
            }
            buildFromLoadedRatings();
            built = true;
        }
    }

    /**
     * Build item-item similarity using co-occurrence on "liked" interactions.
     * 用“喜欢”行为的共现构建物品相似度：sim(i,j)=co(i,j)/sqrt(cnt(i)*cnt(j))
     */
    private void buildFromLoadedRatings() {
        List<User> users = WallpaperDataManager.getInstance().getAllUsers();
        if (users == null || users.isEmpty()) {
            return;
        }

        // i -> (j -> coCount)
        Map<Integer, Map<Integer, Integer>> coCount = new HashMap<>();

        for (User u : users) {
            List<Rating> ratings = u.getRatings();
            if (ratings == null || ratings.isEmpty()) {
                continue;
            }

            // 该用户喜欢的 item 集合
            List<Integer> liked = new ArrayList<>();
            for (Rating r : ratings) {
                if (r.getScore() >= LIKE_THRESHOLD) {
                    liked.add(r.getWallpaperId());
                }
            }
            if (liked.size() < 2) {
                // size==1 仍需要更新 itemLikedUserCount
                for (Integer itemId : liked) {
                    itemLikedUserCount.put(itemId, itemLikedUserCount.getOrDefault(itemId, 0) + 1);
                }
                continue;
            }

            // 去重避免一个用户对同一物品多次计数
            Collections.sort(liked);
            List<Integer> uniqueLiked = new ArrayList<>();
            Integer prev = null;
            for (Integer x : liked) {
                if (prev == null || !prev.equals(x)) {
                    uniqueLiked.add(x);
                }
                prev = x;
            }

            for (Integer itemId : uniqueLiked) {
                itemLikedUserCount.put(itemId, itemLikedUserCount.getOrDefault(itemId, 0) + 1);
            }

            int n = uniqueLiked.size();
            for (int a = 0; a < n; a++) {
                int i = uniqueLiked.get(a);
                Map<Integer, Integer> row = coCount.computeIfAbsent(i, k -> new HashMap<>());
                for (int b = a + 1; b < n; b++) {
                    int j = uniqueLiked.get(b);
                    row.put(j, row.getOrDefault(j, 0) + 1);
                    // 对称
                    Map<Integer, Integer> row2 = coCount.computeIfAbsent(j, k -> new HashMap<>());
                    row2.put(i, row2.getOrDefault(i, 0) + 1);
                }
            }
        }

        // 计算相似度并保留 Top-N
        for (Map.Entry<Integer, Map<Integer, Integer>> e : coCount.entrySet()) {
            int i = e.getKey();
            int cntI = itemLikedUserCount.getOrDefault(i, 0);
            if (cntI <= 0) {
                continue;
            }

            List<Map.Entry<Integer, Double>> sims = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e2 : e.getValue().entrySet()) {
                int j = e2.getKey();
                int cntJ = itemLikedUserCount.getOrDefault(j, 0);
                if (cntJ <= 0) {
                    continue;
                }
                int co = e2.getValue();
                double sim = co / Math.sqrt((double) cntI * (double) cntJ);
                sims.add(new AbstractMap.SimpleEntry<>(j, sim));
            }

            sims.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            Map<Integer, Double> top = new HashMap<>();
            int limit = Math.min(MAX_NEIGHBORS, sims.size());
            for (int k = 0; k < limit; k++) {
                top.put(sims.get(k).getKey(), sims.get(k).getValue());
            }
            itemTopSim.put(i, top);
        }
    }

    private double fallbackPopularity(int wallpaperId) {
        Wallpaper w = WallpaperDataManager.getInstance().getWallpaperById(wallpaperId);
        if (w == null) {
            return 0.0;
        }
        // 用现成的平均评分做一个简易兜底（0~1）
        return (w.getAverageRating() / 5.0) * 0.05;
    }
}


