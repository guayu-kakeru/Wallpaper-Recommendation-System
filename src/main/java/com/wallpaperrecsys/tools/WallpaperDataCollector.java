package com.wallpaperrecsys.tools;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Wallpaper Data Collector
 * 壁纸数据采集工具
 * 支持从多个来源获取壁纸资源
 */
public class WallpaperDataCollector {
    
    // 免费壁纸API配置
    private static final String UNSPLASH_API_KEY = "YOUR_UNSPLASH_ACCESS_KEY"; // 需要注册获取
    private static final String PEXELS_API_KEY = "YOUR_PEXELS_API_KEY"; // 需要注册获取
    
    /**
     * 主方法 - 演示如何使用采集工具
     */
    public static void main(String[] args) {
        System.out.println("=== 壁纸数据采集工具 ===\n");
        System.out.println("请选择数据源：");
        System.out.println("1. Unsplash API (需要API Key)");
        System.out.println("2. Pexels API (需要API Key)");
        System.out.println("3. 手动添加壁纸数据");
        System.out.println("4. 从CSV文件批量导入");
        System.out.println("5. 查看当前数据");
        
        Scanner scanner = new Scanner(System.in);
        int choice = scanner.nextInt();
        
        try {
            switch (choice) {
                case 1:
                    // 使用默认查询词和数量进行示例采集
                    collectFromUnsplash("wallpaper", 20);
                    break;
                case 2:
                    // 使用默认查询词和数量进行示例采集
                    collectFromPexels("wallpaper", 20);
                    break;
                case 3:
                    addWallpaperManually();
                    break;
                case 4:
                    importFromCSV();
                    break;
                case 5:
                    viewCurrentData();
                    break;
                default:
                    System.out.println("无效选择");
            }
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从Unsplash API获取壁纸
     * Unsplash是一个免费高质量图片网站，提供API
     * 注册地址: https://unsplash.com/developers
     */
    public static List<WallpaperInfo> collectFromUnsplash(String query, int count) throws Exception {
        if (UNSPLASH_API_KEY.equals("YOUR_UNSPLASH_ACCESS_KEY")) {
            System.out.println("请先在代码中配置 Unsplash API Key");
            System.out.println("注册地址: https://unsplash.com/developers");
            return new ArrayList<>();
        }
        
        List<WallpaperInfo> wallpapers = new ArrayList<>();
        String apiUrl = String.format(
            "https://api.unsplash.com/search/photos?query=%s&per_page=%d&client_id=%s",
            query, count, UNSPLASH_API_KEY
        );
        
        // TODO: 实现HTTP请求和JSON解析
        // 使用Jackson或Gson解析响应
        // 提取图片URL、描述、标签等信息
        
        return wallpapers;
    }
    
    /**
     * 从Pexels API获取壁纸
     * Pexels是另一个免费高质量图片网站
     * 注册地址: https://www.pexels.com/api/
     */
    public static List<WallpaperInfo> collectFromPexels(String query, int count) throws Exception {
        if (PEXELS_API_KEY.equals("YOUR_PEXELS_API_KEY")) {
            System.out.println("请先在代码中配置 Pexels API Key");
            System.out.println("注册地址: https://www.pexels.com/api/");
            return new ArrayList<>();
        }
        
        List<WallpaperInfo> wallpapers = new ArrayList<>();
        String apiUrl = String.format(
            "https://api.pexels.com/v1/search?query=%s&per_page=%d",
            query, count
        );
        
        // TODO: 实现HTTP请求和JSON解析
        
        return wallpapers;
    }
    
    /**
     * 手动添加壁纸数据
     */
    public static void addWallpaperManually() throws Exception {
        Scanner scanner = new Scanner(System.in);
        List<WallpaperInfo> wallpapers = new ArrayList<>();
        
        System.out.println("请输入壁纸信息（输入 'done' 完成）：");
        
        while (true) {
            System.out.print("标题: ");
            String title = scanner.nextLine();
            if (title.equals("done")) break;
            
            System.out.print("图片URL: ");
            String imageUrl = scanner.nextLine();
            
            System.out.print("分辨率宽度: ");
            int width = Integer.parseInt(scanner.nextLine());
            
            System.out.print("分辨率高度: ");
            int height = Integer.parseInt(scanner.nextLine());
            
            System.out.print("标签（用|分隔）: ");
            String tags = scanner.nextLine();
            
            System.out.print("分类（用|分隔）: ");
            String categories = scanner.nextLine();
            
            System.out.print("风格: ");
            String style = scanner.nextLine();
            
            System.out.print("情绪: ");
            String mood = scanner.nextLine();
            
            System.out.print("色调: ");
            String colorPalette = scanner.nextLine();
            
            WallpaperInfo info = new WallpaperInfo();
            info.title = title;
            info.imageUrl = imageUrl;
            info.resolutionWidth = width;
            info.resolutionHeight = height;
            info.tags = tags;
            info.categories = categories;
            info.style = style;
            info.mood = mood;
            info.colorPalette = colorPalette;
            
            wallpapers.add(info);
            System.out.println("✓ 已添加: " + title + "\n");
        }
        
        // 保存到CSV
        saveToCSV(wallpapers, "data/wallpapers.csv", true);
        System.out.println("✓ 数据已保存到 data/wallpapers.csv");
    }
    
    /**
     * 从CSV文件导入
     */
    public static void importFromCSV() throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入CSV文件路径: ");
        String csvPath = scanner.nextLine();
        
        List<WallpaperInfo> wallpapers = readFromCSV(csvPath);
        saveToCSV(wallpapers, "data/wallpapers.csv", true);
        System.out.println("✓ 已导入 " + wallpapers.size() + " 条壁纸数据");
    }
    
    /**
     * 查看当前数据
     */
    public static void viewCurrentData() throws Exception {
        List<WallpaperInfo> wallpapers = readFromCSV("data/wallpapers.csv");
        System.out.println("当前共有 " + wallpapers.size() + " 条壁纸数据：\n");
        
        for (int i = 0; i < Math.min(10, wallpapers.size()); i++) {
            WallpaperInfo w = wallpapers.get(i);
            System.out.println(String.format("%d. %s (%dx%d) - %s", 
                i + 1, w.title, w.resolutionWidth, w.resolutionHeight, w.tags));
        }
        
        if (wallpapers.size() > 10) {
            System.out.println("... 还有 " + (wallpapers.size() - 10) + " 条数据");
        }
    }
    
    /**
     * 保存到CSV文件
     */
    private static void saveToCSV(List<WallpaperInfo> wallpapers, String filePath, boolean append) 
            throws IOException {
        File file = new File(filePath);
        boolean fileExists = file.exists();
        
        try (FileWriter writer = new FileWriter(file, append)) {
            // 如果是新文件，写入表头
            if (!fileExists || !append) {
                writer.write("wallpaperId,title,imageUrl,thumbnailUrl,resolutionWidth,resolutionHeight," +
                    "tags,categories,style,mood,colorPalette,fileSize,format,uploadTime\n");
            }
            
            // 读取现有数据以确定下一个ID
            int nextId = 1;
            if (fileExists && append) {
                List<WallpaperInfo> existing = readFromCSV(filePath);
                if (!existing.isEmpty()) {
                    nextId = existing.size() + 1;
                }
            }
            
            // 写入新数据
            for (WallpaperInfo w : wallpapers) {
                writer.write(String.format("%d,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s,%d,%s,%s\n",
                    nextId++,
                    escapeCSV(w.title),
                    w.imageUrl != null ? w.imageUrl : "",
                    w.thumbnailUrl != null ? w.thumbnailUrl : "",
                    w.resolutionWidth,
                    w.resolutionHeight,
                    w.tags != null ? w.tags : "",
                    w.categories != null ? w.categories : "",
                    w.style != null ? w.style : "",
                    w.mood != null ? w.mood : "",
                    w.colorPalette != null ? w.colorPalette : "",
                    w.fileSize > 0 ? w.fileSize : 1024,
                    w.format != null ? w.format : "jpg",
                    w.uploadTime != null ? w.uploadTime : java.time.LocalDate.now().toString()
                ));
            }
        }
    }
    
    /**
     * 从CSV文件读取
     */
    private static List<WallpaperInfo> readFromCSV(String filePath) throws IOException {
        List<WallpaperInfo> wallpapers = new ArrayList<>();
        File file = new File(filePath);
        
        if (!file.exists()) {
            return wallpapers;
        }
        
        try (Scanner scanner = new Scanner(file)) {
            boolean skipFirstLine = true;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (skipFirstLine) {
                    skipFirstLine = false;
                    continue;
                }
                
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length >= 8) {
                    WallpaperInfo info = new WallpaperInfo();
                    try {
                        info.wallpaperId = Integer.parseInt(parts[0]);
                        info.title = parts[1];
                        info.imageUrl = parts[2];
                        info.thumbnailUrl = parts.length > 3 ? parts[3] : "";
                        info.resolutionWidth = parts.length > 4 ? Integer.parseInt(parts[4]) : 1920;
                        info.resolutionHeight = parts.length > 5 ? Integer.parseInt(parts[5]) : 1080;
                        info.tags = parts.length > 6 ? parts[6] : "";
                        info.categories = parts.length > 7 ? parts[7] : "";
                        info.style = parts.length > 8 ? parts[8] : "";
                        info.mood = parts.length > 9 ? parts[9] : "";
                        info.colorPalette = parts.length > 10 ? parts[10] : "";
                        info.fileSize = parts.length > 11 ? Integer.parseInt(parts[11]) : 1024;
                        info.format = parts.length > 12 ? parts[12] : "jpg";
                        info.uploadTime = parts.length > 13 ? parts[13] : "";
                        
                        wallpapers.add(info);
                    } catch (Exception e) {
                        System.err.println("解析行失败: " + line);
                    }
                }
            }
        }
        
        return wallpapers;
    }
    
    /**
     * CSV转义
     */
    private static String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * 壁纸信息类
     */
    public static class WallpaperInfo {
        int wallpaperId;
        String title;
        String imageUrl;
        String thumbnailUrl;
        int resolutionWidth;
        int resolutionHeight;
        String tags;
        String categories;
        String style;
        String mood;
        String colorPalette;
        int fileSize;
        String format;
        String uploadTime;
    }
}

