package com.wallpaperrecsys.datamanager;

import com.wallpaperrecsys.model.Embedding;
import com.wallpaperrecsys.util.Config;
import com.wallpaperrecsys.util.Utility;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WallpaperDataManager - manages all wallpaper data loading and access
 * 壁纸数据管理器 - 管理所有壁纸数据的加载和访问
 */
public class WallpaperDataManager {
    // 单例实例
    private static volatile WallpaperDataManager instance;
    
    HashMap<Integer, Wallpaper> wallpaperMap;
    // 按外部字符串ID（如 wallhaven 代码）索引壁纸，便于和 embedding 文件对齐
    HashMap<String, Wallpaper> wallpaperExternalIdMap;
    HashMap<Integer, User> userMap;
    // 标签反向索引，用于快速查询
    HashMap<String, List<Wallpaper>> tagReverseIndexMap;
    // 分类反向索引
    HashMap<String, List<Wallpaper>> categoryReverseIndexMap;
    // 风格反向索引
    HashMap<String, List<Wallpaper>> styleReverseIndexMap;
    // 情绪反向索引
    HashMap<String, List<Wallpaper>> moodReverseIndexMap;

    private WallpaperDataManager() {
        this.wallpaperMap = new HashMap<>();
        this.wallpaperExternalIdMap = new HashMap<>();
        this.userMap = new HashMap<>();
        this.tagReverseIndexMap = new HashMap<>();
        this.categoryReverseIndexMap = new HashMap<>();
        this.styleReverseIndexMap = new HashMap<>();
        this.moodReverseIndexMap = new HashMap<>();
        instance = this;
    }

    public static WallpaperDataManager getInstance() {
        if (null == instance) {
            synchronized (WallpaperDataManager.class) {
                if (null == instance) {
                    instance = new WallpaperDataManager();
                }
            }
        }
        return instance;
    }

    /**
     * Load data from file system
     * 从文件系统加载数据
     */
    public void loadData(String wallpaperDataPath, String ratingDataPath, 
                        String wallpaperEmbPath, String userEmbPath) throws Exception {
        loadWallpaperData(wallpaperDataPath);
        loadRatingData(ratingDataPath);
        loadWallpaperEmb(wallpaperEmbPath);
        loadUserEmb(userEmbPath);
    }

    /**
     * Load wallpaper data from CSV file
     * 从CSV文件加载壁纸数据
     */
    private void loadWallpaperData(String wallpaperDataPath) throws Exception {
        System.out.println("Loading wallpaper data from " + wallpaperDataPath + " ...");
        boolean skipFirstLine = true;
        int count = 0;

        try (Scanner scanner = new Scanner(new File(wallpaperDataPath))) {
            while (scanner.hasNextLine()) {
                String wallpaperRawData = scanner.nextLine();
                if (skipFirstLine) {
                    skipFirstLine = false;
                    continue;
                }

                // 当前CSV格式: id,path,thumb,resolution,colors,tags
                String[] wallpaperData = wallpaperRawData.split(",");
                if (wallpaperData.length >= 3) {
                    try {
                        Wallpaper wallpaper = new Wallpaper();

                        // 外部ID（如 dpqdxj）
                        String externalId = wallpaperData[0].trim();
                        wallpaper.setExternalId(externalId);

                        // 内部自增ID，从1开始，保证与 ratings.csv 中的 wallpaperId 对齐
                        count++;
                        wallpaper.setWallpaperId(count);

                        // 标题：暂时使用外部ID作为标题，占位
                        wallpaper.setTitle(externalId);

                        // 原图和缩略图 URL
                        wallpaper.setImageUrl(wallpaperData[1].trim());
                        wallpaper.setThumbnailUrl(wallpaperData[2].trim());

                        // 分辨率解析（例如 3648x2736）
                        if (wallpaperData.length > 3 && !wallpaperData[3].trim().isEmpty()) {
                            String[] wh = wallpaperData[3].trim().split("x");
                            if (wh.length == 2) {
                                try {
                                    wallpaper.setResolutionWidth(Integer.parseInt(wh[0]));
                                    wallpaper.setResolutionHeight(Integer.parseInt(wh[1]));
                                } catch (NumberFormatException ignore) {
                                }
                            }
                        }

                        // 颜色列表，暂存为 colorPalette 字符串
                        if (wallpaperData.length > 4 && !wallpaperData[4].trim().isEmpty()) {
                            wallpaper.setColorPalette(wallpaperData[4].trim());
                        }

                        // 标签（用 | 分隔）
                        if (wallpaperData.length > 5 && !wallpaperData[5].trim().isEmpty()) {
                            String tags = wallpaperData[5];
                            String[] tagArray = tags.split("\\|");
                            for (String tag : tagArray) {
                                String trimmedTag = tag.trim();
                                if (!trimmedTag.isEmpty()) {
                                    wallpaper.addTag(trimmedTag);
                                    addWallpaper2TagIndex(trimmedTag, wallpaper);
                                    // 根据标签推断类别 / 风格 / 情绪
                                    inferCategoryStyleMoodFromTag(trimmedTag, wallpaper);
                                }
                            }
                        }

                        this.wallpaperMap.put(wallpaper.getWallpaperId(), wallpaper);
                        this.wallpaperExternalIdMap.put(externalId, wallpaper);
                    } catch (Exception e) {
                        System.err.println("Error parsing line: " + wallpaperRawData);
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Loading wallpaper data completed. " + count + " wallpapers in total.");
    }

    /**
     * Load rating data from CSV file
     * 从CSV文件加载评分数据
     */
    private void loadRatingData(String ratingDataPath) throws Exception {
        System.out.println("Loading rating data from " + ratingDataPath + " ...");
        boolean skipFirstLine = true;
        int count = 0;
        
        try (Scanner scanner = new Scanner(new File(ratingDataPath))) {
            while (scanner.hasNextLine()) {
                String ratingRawData = scanner.nextLine();
                if (skipFirstLine) {
                    skipFirstLine = false;
                    continue;
                }
                
                // CSV格式: userId,wallpaperId,rating,timestamp
                String[] ratingData = ratingRawData.split(",");
                if (ratingData.length >= 4) {
                    try {
                        int userId = Integer.parseInt(ratingData[0].trim());
                        int wallpaperId = Integer.parseInt(ratingData[1].trim());
                        double rating = Double.parseDouble(ratingData[2].trim());
                        long timestamp = Long.parseLong(ratingData[3].trim());
                        
                        Rating ratingObj = new Rating(userId, wallpaperId, rating, timestamp);
                        
                        // 添加到用户
                        User user = userMap.get(userId);
                        if (user == null) {
                            user = new User();
                            user.setUserId(userId);
                            userMap.put(userId, user);
                        }
                        user.addRating(ratingObj);
                        
                        // 添加到壁纸
                        Wallpaper wallpaper = wallpaperMap.get(wallpaperId);
                        if (wallpaper != null) {
                            wallpaper.addRating(ratingObj);
                        }
                        
                        count++;
                    } catch (Exception e) {
                        System.err.println("Error parsing rating line: " + ratingRawData);
                        e.printStackTrace();
                    }
                }
            }
        }
        System.out.println("Loading rating data completed. " + count + " ratings in total.");
    }

    /**
     * Load wallpaper embedding from file
     * 从文件加载壁纸embedding
     */
    private void loadWallpaperEmb(String wallpaperEmbPath) throws Exception {
        if (wallpaperEmbPath == null || wallpaperEmbPath.isEmpty()) {
            System.out.println("Wallpaper embedding path not provided, skipping...");
            return;
        }
        
        System.out.println("Loading wallpaper embedding from " + wallpaperEmbPath + " ...");
        int validEmbCount = 0;
        
        try (Scanner scanner = new Scanner(new File(wallpaperEmbPath))) {
            while (scanner.hasNextLine()) {
                String wallpaperRawEmbData = scanner.nextLine();
                String[] wallpaperEmbData = wallpaperRawEmbData.split(":");
                if (wallpaperEmbData.length == 2) {
                    try {
                        // embedding 文件中的ID是外部字符串ID（如 dpqdxj）
                        String externalId = wallpaperEmbData[0].trim();
                        Wallpaper w = wallpaperExternalIdMap.get(externalId);
                        if (w != null) {
                            Embedding emb = Utility.parseEmbStr(wallpaperEmbData[1]);
                            if (emb != null) {
                                w.setEmb(emb);
                                validEmbCount++;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing embedding line: " + wallpaperRawEmbData);
                    }
                }
            }
        }
        System.out.println("Loading wallpaper embedding completed. " + validEmbCount + " wallpaper embeddings in total.");
    }

    /**
     * Load user embedding from file
     * 从文件加载用户embedding
     */
    private void loadUserEmb(String userEmbPath) throws Exception {
        if (userEmbPath == null || userEmbPath.isEmpty()) {
            System.out.println("User embedding path not provided, skipping...");
            return;
        }
        
        System.out.println("Loading user embedding from " + userEmbPath + " ...");
        int validEmbCount = 0;
        
        try (Scanner scanner = new Scanner(new File(userEmbPath))) {
            while (scanner.hasNextLine()) {
                String userRawEmbData = scanner.nextLine();
                String[] userEmbData = userRawEmbData.split(":");
                if (userEmbData.length == 2) {
                    try {
                        int userId = Integer.parseInt(userEmbData[0].trim());
                        User u = getUserById(userId);
                        if (u != null) {
                            Embedding emb = Utility.parseEmbStr(userEmbData[1]);
                            if (emb != null) {
                                u.setEmb(emb);
                                validEmbCount++;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing user embedding line: " + userRawEmbData);
                    }
                }
            }
        }
        System.out.println("Loading user embedding completed. " + validEmbCount + " user embeddings in total.");
    }

    // 索引管理方法
    private void addWallpaper2TagIndex(String tag, Wallpaper wallpaper) {
        if (!this.tagReverseIndexMap.containsKey(tag)) {
            this.tagReverseIndexMap.put(tag, new ArrayList<>());
        }
        this.tagReverseIndexMap.get(tag).add(wallpaper);
    }

    private void addWallpaper2CategoryIndex(String category, Wallpaper wallpaper) {
        if (!this.categoryReverseIndexMap.containsKey(category)) {
            this.categoryReverseIndexMap.put(category, new ArrayList<>());
        }
        this.categoryReverseIndexMap.get(category).add(wallpaper);
    }

    private void addWallpaper2StyleIndex(String style, Wallpaper wallpaper) {
        if (!this.styleReverseIndexMap.containsKey(style)) {
            this.styleReverseIndexMap.put(style, new ArrayList<>());
        }
        this.styleReverseIndexMap.get(style).add(wallpaper);
    }

    private void addWallpaper2MoodIndex(String mood, Wallpaper wallpaper) {
        if (!this.moodReverseIndexMap.containsKey(mood)) {
            this.moodReverseIndexMap.put(mood, new ArrayList<>());
        }
        this.moodReverseIndexMap.get(mood).add(wallpaper);
    }

    /**
     * 根据单个标签粗略推断类别 / 风格 / 情绪
     */
    private void inferCategoryStyleMoodFromTag(String tag, Wallpaper wallpaper) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        String lower = tag.toLowerCase();

        // 动漫 / 二次元
        if (lower.contains("动漫") || lower.contains("anime") || lower.contains("二次元")) {
            wallpaper.addCategory("anime");
            addWallpaper2CategoryIndex("anime", wallpaper);
            wallpaper.setStyle("anime");
            addWallpaper2StyleIndex("anime", wallpaper);
        }

        // 自然风景
        if (lower.contains("风景") || lower.contains("nature") ||
                lower.contains("山") || lower.contains("forest") ||
                lower.contains("湖") || lower.contains("海")) {
            wallpaper.addCategory("nature");
            addWallpaper2CategoryIndex("nature", wallpaper);
        }

        // 城市 / 夜景
        if (lower.contains("城市") || lower.contains("city") ||
                lower.contains("街景") || lower.contains("夜景")) {
            wallpaper.addCategory("city");
            addWallpaper2CategoryIndex("city", wallpaper);
        }

        // 太空 / 星空
        if (lower.contains("太空") || lower.contains("space") ||
                lower.contains("星空") || lower.contains("galaxy")) {
            wallpaper.addCategory("space");
            addWallpaper2CategoryIndex("space", wallpaper);
        }

        // 抽象 / 极简
        if (lower.contains("抽象") || lower.contains("abstract")) {
            wallpaper.addCategory("abstract");
            addWallpaper2CategoryIndex("abstract", wallpaper);
        }
        if (lower.contains("极简")) {
            wallpaper.setStyle("minimalist");
            addWallpaper2StyleIndex("minimalist", wallpaper);
        }

        // 情绪 / 色调
        if (lower.contains("宁静") || lower.contains("平静")) {
            wallpaper.setMood("calm");
            addWallpaper2MoodIndex("calm", wallpaper);
        }
        if (lower.contains("活力") || lower.contains("热烈")) {
            wallpaper.setMood("energetic");
            addWallpaper2MoodIndex("energetic", wallpaper);
        }
        if (lower.contains("温暖")) {
            wallpaper.setMood("warm");
            addWallpaper2MoodIndex("warm", wallpaper);
        }
        if (lower.contains("冷色")) {
            wallpaper.setMood("cool");
            addWallpaper2MoodIndex("cool", wallpaper);
        }
        if (lower.contains("暗黑")) {
            wallpaper.setMood("dark");
            addWallpaper2MoodIndex("dark", wallpaper);
        }
    }

    // Getter方法
    public Wallpaper getWallpaperById(int wallpaperId) {
        return wallpaperMap.get(wallpaperId);
    }

    public User getUserById(int userId) {
        return userMap.get(userId);
    }

    /**
     * Get wallpapers by tag
     * 根据标签获取壁纸
     */
    public List<Wallpaper> getWallpapersByTag(String tag, int size, String sortBy) {
        List<Wallpaper> wallpapers = tagReverseIndexMap.getOrDefault(tag, new ArrayList<>());
        return sortWallpapers(wallpapers, sortBy, size);
    }

    /**
     * Get wallpapers by category
     * 根据分类获取壁纸
     */
    public List<Wallpaper> getWallpapersByCategory(String category, int size, String sortBy) {
        List<Wallpaper> wallpapers = categoryReverseIndexMap.getOrDefault(category, new ArrayList<>());
        return sortWallpapers(wallpapers, sortBy, size);
    }

    /**
     * Get wallpapers by style
     * 根据风格获取壁纸
     */
    public List<Wallpaper> getWallpapersByStyle(String style, int size, String sortBy) {
        List<Wallpaper> wallpapers = styleReverseIndexMap.getOrDefault(style, new ArrayList<>());
        return sortWallpapers(wallpapers, sortBy, size);
    }

    /**
     * Get wallpapers by mood
     * 根据情绪获取壁纸
     */
    public List<Wallpaper> getWallpapersByMood(String mood, int size, String sortBy) {
        List<Wallpaper> wallpapers = moodReverseIndexMap.getOrDefault(mood, new ArrayList<>());
        return sortWallpapers(wallpapers, sortBy, size);
    }

    /**
     * Get all wallpapers with sorting
     * 获取所有壁纸并排序
     */
    public List<Wallpaper> getWallpapers(int size, String sortBy) {
        List<Wallpaper> allWallpapers = new ArrayList<>(wallpaperMap.values());
        return sortWallpapers(allWallpapers, sortBy, size);
    }

    /**
     * Get all wallpapers
     * 获取所有壁纸
     */
    public List<Wallpaper> getAllWallpapers() {
        return new ArrayList<>(wallpaperMap.values());
    }

    /**
     * Get all wallpapers with embedding
     * 获取所有有embedding的壁纸
     */
    public List<Wallpaper> getAllWallpapersWithEmbedding() {
        return wallpaperMap.values().stream()
            .filter(w -> w.getEmb() != null)
            .collect(Collectors.toList());
    }

    /**
     * Sort wallpapers by different criteria
     * 根据不同标准排序壁纸
     */
    private List<Wallpaper> sortWallpapers(List<Wallpaper> wallpapers, String sortBy, int size) {
        List<Wallpaper> sorted;
        
        switch (sortBy.toLowerCase()) {
            case "rating":
                sorted = wallpapers.stream()
                    .sorted((w1, w2) -> Double.compare(w2.getAverageRating(), w1.getAverageRating()))
                    .collect(Collectors.toList());
                break;
            case "download":
                sorted = wallpapers.stream()
                    .sorted((w1, w2) -> Integer.compare(w2.getDownloadCount(), w1.getDownloadCount()))
                    .collect(Collectors.toList());
                break;
            case "uploadtime":
                sorted = wallpapers.stream()
                    .sorted((w1, w2) -> {
                        if (w1.getUploadTime() == null || w2.getUploadTime() == null) {
                            return 0;
                        }
                        return w2.getUploadTime().compareTo(w1.getUploadTime());
                    })
                    .collect(Collectors.toList());
                break;
            default:
                sorted = new ArrayList<>(wallpapers);
        }
        
        if (sorted.size() > size) {
            return sorted.subList(0, size);
        }
        return sorted;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }
}

