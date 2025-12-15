package com.wallpaperrecsys;

import com.wallpaperrecsys.datamanager.WallpaperDataManager;
import com.wallpaperrecsys.datamanager.Wallpaper;
import com.wallpaperrecsys.recprocess.RecForYouProcess;
import com.wallpaperrecsys.recprocess.SimilarWallpaperProcess;
import com.wallpaperrecsys.recprocess.ScenarioBasedRecommendation;
import com.wallpaperrecsys.recprocess.TimeAwareRecommendation;
import com.wallpaperrecsys.service.AISearchService;

import java.util.List;

/**
 * Main class - Demo of Wallpaper Recommendation System
 * 主类 - 壁纸推荐系统演示
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("=== 智能壁纸推荐系统演示 ===\n");
        
        try {
            // 1. 加载数据
            System.out.println("1. 正在加载数据...");
            WallpaperDataManager manager = WallpaperDataManager.getInstance();
            manager.loadData(
                "data/wallpapers.csv",
                "data/ratings.csv",
                "data/wallpaper_embeddings.csv",
                "data/user_embeddings.csv"
            );
            System.out.println("✓ 数据加载完成\n");
            
            // 2. 个性化推荐
            System.out.println("2. 个性化推荐（用户ID=1）:");
            List<Wallpaper> personalizedRecs = RecForYouProcess.getRecList(1, 5, "emb");
            printWallpapers(personalizedRecs);
            
            // 3. 相似壁纸推荐
            System.out.println("\n3. 相似壁纸推荐（壁纸ID=1）:");
            List<Wallpaper> similarRecs = SimilarWallpaperProcess.getRecList(1, 5, "emb");
            printWallpapers(similarRecs);
            
            // 4. AI关键词搜索
            System.out.println("\n4. AI关键词搜索（关键词='nature'）:");
            List<Wallpaper> searchResults = AISearchService.intelligentSearch("nature", 5);
            printWallpapers(searchResults);
            
            // 5. 场景推荐
            System.out.println("\n5. 场景推荐（场景='work'）:");
            List<Wallpaper> scenarioRecs = ScenarioBasedRecommendation.recommendByScenario("work", 1, 5);
            printWallpapers(scenarioRecs);
            
            // 6. 时间感知推荐
            System.out.println("\n6. 时间感知推荐:");
            List<Wallpaper> timeRecs = TimeAwareRecommendation.recommendByTime(1, 5);
            printWallpapers(timeRecs);
            
            System.out.println("\n=== 演示完成 ===");
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 打印壁纸列表
     */
    private static void printWallpapers(List<Wallpaper> wallpapers) {
        if (wallpapers == null || wallpapers.isEmpty()) {
            System.out.println("  没有找到壁纸");
            return;
        }
        
        for (int i = 0; i < wallpapers.size(); i++) {
            Wallpaper w = wallpapers.get(i);
            System.out.println(String.format("  %d. [ID:%d] %s - %s (%s, %s)", 
                i + 1,
                w.getWallpaperId(),
                w.getTitle(),
                w.getStyle() != null ? w.getStyle() : "N/A",
                w.getMood() != null ? w.getMood() : "N/A",
                w.getTags().toString()
            ));
        }
    }
}

