package com.wallpaperrecsys.eval;

import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.util.Config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Offline evaluation entry for comparing multiple recommenders.
 * 离线评测入口：对比多种推荐模型（popularity / itemcf / emb），输出 Precision@K / Recall@K / NDCG@K 等指标。
 *
 * 使用示例：
 * - mvn -q -DskipTests package
 * - java -cp target/wallpaper-recommendation-system-1.0-SNAPSHOT-jar-with-dependencies.jar com.wallpaperrecsys.eval.OfflineEvalMain --k=10 --like=4.0
 */
public class OfflineEvalMain {

    public static void main(String[] args) throws Exception {
        System.out.println("[OfflineEvalMain] version=2025-12-15 emb_eval_fixed itemcf_backfill");
        Args a = Args.parse(args);

        // 1) 加载壁纸与 embedding（emb 模型需要）
        WallpaperDataManager.getInstance().loadData(
                Config.DEFAULT_WALLPAPER_DATA_PATH,
                Config.DEFAULT_RATING_DATA_PATH,
                Config.DEFAULT_WALLPAPER_EMB_PATH,
                Config.DEFAULT_USER_EMB_PATH
        );

        // 2) 读取 ratings.csv（为了严格的 train/test 切分）
        List<RatingRecord> records = RatingRecord.readCsv(Config.DEFAULT_RATING_DATA_PATH);
        Map<Integer, List<RatingRecord>> byUser = groupByUser(records);
        Split split = timeSplit(byUser, a.leaveOut, a.minTrain);

        if (split.evalUserIds.isEmpty()) {
            System.out.println("没有可评测用户：请检查 ratings.csv 或降低 --minTrain / --leaveOut 参数。");
            return;
        }

        // 3) 构建离线模型（仅用训练集）
        PopularityModel popularity = PopularityModel.build(split.trainByUser, a.likeThreshold);
        ItemCFModel itemcf = ItemCFModel.build(split.trainByUser, a.likeThreshold, a.maxNeighbors);

        // 4) 评测对比
        List<ModelResult> results = new ArrayList<>();
        results.add(evaluatePopularity(split, popularity, a));
        results.add(evaluateItemCF(split, itemcf, popularity, a));
        results.add(evaluateEmbedding(split, popularity, a));

        printTable(results, a);
        writeCsv(results, a);
    }

    // --------------------------
    // Args / IO
    // --------------------------

    static class Args {
        int k = 10;
        double likeThreshold = 4.0;
        int leaveOut = 1;
        int minTrain = 3;
        int maxUsers = 0; // 0 表示不限
        int maxNeighbors = 80;
        String reportDir = "reports";

        static Args parse(String[] args) {
            Args a = new Args();
            if (args == null) return a;
            for (String s : args) {
                if (s == null) continue;
                if (s.startsWith("--k=")) a.k = Integer.parseInt(s.substring("--k=".length()));
                else if (s.startsWith("--like=")) a.likeThreshold = Double.parseDouble(s.substring("--like=".length()));
                else if (s.startsWith("--leaveOut=")) a.leaveOut = Integer.parseInt(s.substring("--leaveOut=".length()));
                else if (s.startsWith("--minTrain=")) a.minTrain = Integer.parseInt(s.substring("--minTrain=".length()));
                else if (s.startsWith("--maxUsers=")) a.maxUsers = Integer.parseInt(s.substring("--maxUsers=".length()));
                else if (s.startsWith("--maxNeighbors=")) a.maxNeighbors = Integer.parseInt(s.substring("--maxNeighbors=".length()));
                else if (s.startsWith("--reportDir=")) a.reportDir = s.substring("--reportDir=".length());
            }
            return a;
        }
    }

    static class RatingRecord {
        final int userId;
        final int wallpaperId;
        final double rating;
        final long timestamp;

        RatingRecord(int userId, int wallpaperId, double rating, long timestamp) {
            this.userId = userId;
            this.wallpaperId = wallpaperId;
            this.rating = rating;
            this.timestamp = timestamp;
        }

        static List<RatingRecord> readCsv(String path) throws Exception {
            List<String> lines = Files.readAllLines(Paths.get(path));
            List<RatingRecord> out = new ArrayList<>();
            boolean skip = true;
            for (String line : lines) {
                if (skip) { skip = false; continue; }
                if (line == null || line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                int userId = Integer.parseInt(parts[0].trim());
                int wallpaperId = Integer.parseInt(parts[1].trim());
                double rating = Double.parseDouble(parts[2].trim());
                long ts = Long.parseLong(parts[3].trim());
                out.add(new RatingRecord(userId, wallpaperId, rating, ts));
            }
            return out;
        }
    }

    static Map<Integer, List<RatingRecord>> groupByUser(List<RatingRecord> records) {
        Map<Integer, List<RatingRecord>> m = new HashMap<>();
        for (RatingRecord r : records) {
            m.computeIfAbsent(r.userId, k -> new ArrayList<>()).add(r);
        }
        return m;
    }

    // --------------------------
    // Split
    // --------------------------

    static class Split {
        final Map<Integer, List<RatingRecord>> trainByUser;
        final Map<Integer, List<RatingRecord>> testByUser;
        final List<Integer> evalUserIds;

        Split(Map<Integer, List<RatingRecord>> trainByUser,
              Map<Integer, List<RatingRecord>> testByUser,
              List<Integer> evalUserIds) {
            this.trainByUser = trainByUser;
            this.testByUser = testByUser;
            this.evalUserIds = evalUserIds;
        }
    }

    static Split timeSplit(Map<Integer, List<RatingRecord>> byUser, int leaveOut, int minTrain) {
        Map<Integer, List<RatingRecord>> train = new HashMap<>();
        Map<Integer, List<RatingRecord>> test = new HashMap<>();
        List<Integer> evalUsers = new ArrayList<>();

        for (Map.Entry<Integer, List<RatingRecord>> e : byUser.entrySet()) {
            int userId = e.getKey();
            List<RatingRecord> rs = new ArrayList<>(e.getValue());
            rs.sort(Comparator.comparingLong(o -> o.timestamp));
            if (rs.size() <= leaveOut) continue;

            List<RatingRecord> tr = rs.subList(0, rs.size() - leaveOut);
            List<RatingRecord> te = rs.subList(rs.size() - leaveOut, rs.size());
            if (tr.size() < minTrain) continue;

            train.put(userId, new ArrayList<>(tr));
            test.put(userId, new ArrayList<>(te));
            evalUsers.add(userId);
        }
        return new Split(train, test, evalUsers);
    }

    static Set<Integer> likedItems(List<RatingRecord> rs, double likeThreshold) {
        Set<Integer> s = new HashSet<>();
        if (rs == null) return s;
        for (RatingRecord r : rs) {
            if (r.rating >= likeThreshold) s.add(r.wallpaperId);
        }
        return s;
    }

    static Set<Integer> seenItems(List<RatingRecord> rs) {
        Set<Integer> s = new HashSet<>();
        if (rs == null) return s;
        for (RatingRecord r : rs) s.add(r.wallpaperId);
        return s;
    }

    // --------------------------
    // Models
    // --------------------------

    static class PopularityModel {
        final List<Integer> rankedItems; // 全局从高到低

        PopularityModel(List<Integer> rankedItems) {
            this.rankedItems = rankedItems;
        }

        static PopularityModel build(Map<Integer, List<RatingRecord>> trainByUser, double likeThreshold) {
            Map<Integer, Integer> likeCount = new HashMap<>();
            Map<Integer, Double> ratingSum = new HashMap<>();
            Map<Integer, Integer> ratingCnt = new HashMap<>();

            for (List<RatingRecord> rs : trainByUser.values()) {
                for (RatingRecord r : rs) {
                    ratingSum.put(r.wallpaperId, ratingSum.getOrDefault(r.wallpaperId, 0.0) + r.rating);
                    ratingCnt.put(r.wallpaperId, ratingCnt.getOrDefault(r.wallpaperId, 0) + 1);
                    if (r.rating >= likeThreshold) {
                        likeCount.put(r.wallpaperId, likeCount.getOrDefault(r.wallpaperId, 0) + 1);
                    }
                }
            }

            List<Map.Entry<Integer, Double>> scored = new ArrayList<>();
            for (Map.Entry<Integer, Integer> e : ratingCnt.entrySet()) {
                int item = e.getKey();
                double avg = ratingSum.get(item) / Math.max(1, e.getValue());
                double lc = likeCount.getOrDefault(item, 0);
                // 轻量融合：喜欢数为主，均分为辅
                double score = lc + 0.1 * avg;
                scored.add(new AbstractMap.SimpleEntry<>(item, score));
            }
            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            List<Integer> ranked = new ArrayList<>();
            for (Map.Entry<Integer, Double> e : scored) ranked.add(e.getKey());
            return new PopularityModel(ranked);
        }

        List<Integer> recommend(Set<Integer> seen, int k) {
            List<Integer> out = new ArrayList<>(k);
            for (Integer id : rankedItems) {
                if (seen.contains(id)) continue;
                out.add(id);
                if (out.size() >= k) break;
            }
            return out;
        }
    }

    static class ItemCFModel {
        // i -> (j -> sim)
        final Map<Integer, Map<Integer, Double>> topSim;
        final int maxNeighbors;
        final double likeThreshold;

        ItemCFModel(Map<Integer, Map<Integer, Double>> topSim, int maxNeighbors, double likeThreshold) {
            this.topSim = topSim;
            this.maxNeighbors = maxNeighbors;
            this.likeThreshold = likeThreshold;
        }

        static ItemCFModel build(Map<Integer, List<RatingRecord>> trainByUser,
                                 double likeThreshold,
                                 int maxNeighbors) {
            Map<Integer, Integer> itemCnt = new HashMap<>();
            Map<Integer, Map<Integer, Integer>> coCount = new HashMap<>();

            for (List<RatingRecord> rs : trainByUser.values()) {
                List<Integer> liked = new ArrayList<>();
                for (RatingRecord r : rs) {
                    if (r.rating >= likeThreshold) liked.add(r.wallpaperId);
                }
                if (liked.isEmpty()) continue;
                Collections.sort(liked);

                // 去重
                List<Integer> uniq = new ArrayList<>();
                Integer prev = null;
                for (Integer x : liked) {
                    if (prev == null || !prev.equals(x)) uniq.add(x);
                    prev = x;
                }
                for (Integer i : uniq) itemCnt.put(i, itemCnt.getOrDefault(i, 0) + 1);

                int n = uniq.size();
                if (n < 2) continue;
                for (int a = 0; a < n; a++) {
                    int i = uniq.get(a);
                    Map<Integer, Integer> row = coCount.computeIfAbsent(i, k -> new HashMap<>());
                    for (int b = a + 1; b < n; b++) {
                        int j = uniq.get(b);
                        row.put(j, row.getOrDefault(j, 0) + 1);
                        Map<Integer, Integer> row2 = coCount.computeIfAbsent(j, k -> new HashMap<>());
                        row2.put(i, row2.getOrDefault(i, 0) + 1);
                    }
                }
            }

            Map<Integer, Map<Integer, Double>> topSim = new HashMap<>();
            for (Map.Entry<Integer, Map<Integer, Integer>> e : coCount.entrySet()) {
                int i = e.getKey();
                int cntI = itemCnt.getOrDefault(i, 0);
                if (cntI <= 0) continue;

                List<Map.Entry<Integer, Double>> sims = new ArrayList<>();
                for (Map.Entry<Integer, Integer> e2 : e.getValue().entrySet()) {
                    int j = e2.getKey();
                    int cntJ = itemCnt.getOrDefault(j, 0);
                    if (cntJ <= 0) continue;
                    int co = e2.getValue();
                    double sim = co / Math.sqrt((double) cntI * (double) cntJ);
                    sims.add(new AbstractMap.SimpleEntry<>(j, sim));
                }
                sims.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                int limit = Math.min(maxNeighbors, sims.size());
                Map<Integer, Double> row = new HashMap<>();
                for (int idx = 0; idx < limit; idx++) {
                    row.put(sims.get(idx).getKey(), sims.get(idx).getValue());
                }
                topSim.put(i, row);
            }
            return new ItemCFModel(topSim, maxNeighbors, likeThreshold);
        }

        List<Integer> recommend(List<RatingRecord> trainRatings, Set<Integer> seen, int k) {
            Map<Integer, Double> score = new HashMap<>();
            if (trainRatings != null) {
                for (RatingRecord r : trainRatings) {
                    if (r.rating < likeThreshold) continue;
                    Map<Integer, Double> neigh = topSim.get(r.wallpaperId);
                    if (neigh == null) continue;
                    double pref = Math.min(Math.max(r.rating / 5.0, 0.0), 1.0);
                    for (Map.Entry<Integer, Double> e : neigh.entrySet()) {
                        int cand = e.getKey();
                        if (seen.contains(cand)) continue;
                        score.put(cand, score.getOrDefault(cand, 0.0) + e.getValue() * pref);
                    }
                }
            }

            List<Map.Entry<Integer, Double>> list = new ArrayList<>(score.entrySet());
            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<Integer> out = new ArrayList<>(k);
            for (Map.Entry<Integer, Double> e : list) {
                out.add(e.getKey());
                if (out.size() >= k) break;
            }
            return out;
        }
    }

    // --------------------------
    // Metrics
    // --------------------------

    static class Metrics {
        static double precisionAtK(List<Integer> recs, Set<Integer> gt, int k) {
            if (k <= 0) return 0.0;
            int hit = 0;
            int limit = Math.min(k, recs.size());
            for (int i = 0; i < limit; i++) if (gt.contains(recs.get(i))) hit++;
            return (double) hit / (double) k;
        }

        static double recallAtK(List<Integer> recs, Set<Integer> gt, int k) {
            if (gt == null || gt.isEmpty()) return 0.0;
            int hit = 0;
            int limit = Math.min(k, recs.size());
            for (int i = 0; i < limit; i++) if (gt.contains(recs.get(i))) hit++;
            return (double) hit / (double) gt.size();
        }

        static double hitRateAtK(List<Integer> recs, Set<Integer> gt, int k) {
            int limit = Math.min(k, recs.size());
            for (int i = 0; i < limit; i++) if (gt.contains(recs.get(i))) return 1.0;
            return 0.0;
        }

        static double ndcgAtK(List<Integer> recs, Set<Integer> gt, int k) {
            int limit = Math.min(k, recs.size());
            double dcg = 0.0;
            for (int i = 0; i < limit; i++) {
                int rank = i + 1;
                if (gt.contains(recs.get(i))) {
                    dcg += 1.0 / log2(rank + 1);
                }
            }
            // ideal DCG：全部命中时
            int idealHits = Math.min(k, gt.size());
            double idcg = 0.0;
            for (int i = 0; i < idealHits; i++) {
                int rank = i + 1;
                idcg += 1.0 / log2(rank + 1);
            }
            if (idcg == 0.0) return 0.0;
            return dcg / idcg;
        }

        private static double log2(double x) {
            return Math.log(x) / Math.log(2.0);
        }
    }

    // --------------------------
    // Results / evaluation
    // --------------------------

    static class ModelResult {
        final String model;
        final int users;
        final double precision;
        final double recall;
        final double ndcg;
        final double hitRate;

        ModelResult(String model, int users, double precision, double recall, double ndcg, double hitRate) {
            this.model = model;
            this.users = users;
            this.precision = precision;
            this.recall = recall;
            this.ndcg = ndcg;
            this.hitRate = hitRate;
        }
    }

    static ModelResult evaluatePopularity(Split split, PopularityModel model, Args a) {
        return evaluate(split, a, "popularity", (userId, train, seen) -> model.recommend(seen, a.k));
    }

    static ModelResult evaluateItemCF(Split split, ItemCFModel model, PopularityModel fallback, Args a) {
        return evaluate(split, a, "itemcf", (userId, train, seen) -> {
            List<Integer> recs = model.recommend(train, seen, a.k);
            // 如果 ItemCF 太稀疏（候选不足），用热门回填到 K，避免“全空列表”导致指标失真
            if (recs.size() < a.k) {
                Set<Integer> already = new HashSet<>(recs);
                List<Integer> backfill = fallback.recommend(seen, a.k);
                for (Integer x : backfill) {
                    if (already.contains(x)) continue;
                    recs.add(x);
                    already.add(x);
                    if (recs.size() >= a.k) break;
                }
            }
            return recs;
        });
    }

    static ModelResult evaluateEmbedding(Split split, PopularityModel fallback, Args a) {
        // 注意：不能直接复用线上 RecForYouProcess.getRecList() 做离线评测。
        // 因为线上逻辑会过滤“用户已评分的所有物品”，在离线场景下会把测试集物品也过滤掉，导致永远命中不了。
        // 这里用离线口径：直接计算 user_emb 与 item_emb 的相似度，只过滤训练集 seen。
        return evaluate(split, a, "emb", (userId, train, seen) -> {
            com.wallpaperrecsys.datamanager.User user = WallpaperDataManager.getInstance().getUserById(userId);
            if (user == null || user.getEmb() == null) {
                return fallback.recommend(seen, a.k);
            }

            List<com.wallpaperrecsys.datamanager.Wallpaper> all = WallpaperDataManager.getInstance().getAllWallpapersWithEmbedding();
            Map<Integer, Double> score = new HashMap<>();
            for (com.wallpaperrecsys.datamanager.Wallpaper w : all) {
                int wid = w.getWallpaperId();
                if (seen.contains(wid)) continue;
                if (w.getEmb() == null) continue;
                double sim = user.getEmb().calculateSimilarity(w.getEmb());
                score.put(wid, sim);
            }

            if (score.isEmpty()) {
                return fallback.recommend(seen, a.k);
            }

            List<Map.Entry<Integer, Double>> list = new ArrayList<>(score.entrySet());
            list.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
            List<Integer> out = new ArrayList<>(a.k);
            for (Map.Entry<Integer, Double> e : list) {
                out.add(e.getKey());
                if (out.size() >= a.k) break;
            }
            // 不足 K 则回填
            if (out.size() < a.k) {
                Set<Integer> already = new HashSet<>(out);
                List<Integer> backfill = fallback.recommend(seen, a.k);
                for (Integer x : backfill) {
                    if (already.contains(x)) continue;
                    out.add(x);
                    already.add(x);
                    if (out.size() >= a.k) break;
                }
            }
            return out;
        });
    }

    interface RecFn {
        List<Integer> recommend(int userId, List<RatingRecord> train, Set<Integer> seen);
    }

    static ModelResult evaluate(Split split, Args a, String modelName, RecFn fn) {
        int users = 0;
        double p = 0, r = 0, n = 0, h = 0;

        for (Integer userId : split.evalUserIds) {
            if (a.maxUsers > 0 && users >= a.maxUsers) break;

            List<RatingRecord> train = split.trainByUser.get(userId);
            List<RatingRecord> test = split.testByUser.get(userId);
            Set<Integer> gt = likedItems(test, a.likeThreshold);
            if (gt.isEmpty()) {
                // 没有正例，不参与平均（否则会把指标稀释成 0）
                continue;
            }

            Set<Integer> seen = seenItems(train);
            List<Integer> recs = fn.recommend(userId, train, seen);

            p += Metrics.precisionAtK(recs, gt, a.k);
            r += Metrics.recallAtK(recs, gt, a.k);
            n += Metrics.ndcgAtK(recs, gt, a.k);
            h += Metrics.hitRateAtK(recs, gt, a.k);
            users++;
        }

        if (users == 0) {
            return new ModelResult(modelName, 0, 0, 0, 0, 0);
        }
        return new ModelResult(modelName, users, p / users, r / users, n / users, h / users);
    }

    static void printTable(List<ModelResult> results, Args a) {
        System.out.println();
        System.out.println("=== 离线评测结果（按用户时间切分，LeaveOut=" + a.leaveOut + ", Like>=" + a.likeThreshold + ", K=" + a.k + "）===");
        System.out.println(String.format("%-12s %-8s %-12s %-12s %-12s %-12s",
                "model", "users", "P@K", "R@K", "NDCG@K", "Hit@K"));
        for (ModelResult r : results) {
            System.out.println(String.format("%-12s %-8d %-12.4f %-12.4f %-12.4f %-12.4f",
                    r.model, r.users, r.precision, r.recall, r.ndcg, r.hitRate));
        }
        System.out.println();
    }

    static void writeCsv(List<ModelResult> results, Args a) throws Exception {
        if (a.reportDir == null || a.reportDir.trim().isEmpty()) {
            return;
        }
        File dir = new File(a.reportDir);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        String out = a.reportDir + File.separator + "offline_eval_k" + a.k + "_like" + a.likeThreshold + ".csv";
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out))) {
            bw.write("model,users,precision_at_k,recall_at_k,ndcg_at_k,hit_at_k\n");
            for (ModelResult r : results) {
                bw.write(r.model + "," + r.users + "," + r.precision + "," + r.recall + "," + r.ndcg + "," + r.hitRate + "\n");
            }
        }
        System.out.println("评测结果已写入: " + out);
    }
}


