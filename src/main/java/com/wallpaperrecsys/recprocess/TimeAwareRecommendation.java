package com.wallpaperrecsys.recprocess;

import com.wallpaperrecsys.datamanager.Wallpaper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Time-aware Recommendation
 * 时间感知推荐
 */
public class TimeAwareRecommendation {

    /**
     * Recommend wallpapers based on current time
     * 根据当前时间推荐壁纸
     */
    public static List<Wallpaper> recommendByTime(int userId, int size) {
        int hour = LocalDateTime.now().getHour();
        String timeSlot;

        if (hour >= 6 && hour < 12) {
            timeSlot = "morning";  // 早晨：明亮、清新
        } else if (hour >= 12 && hour < 18) {
            timeSlot = "afternoon"; // 下午：活力、温暖
        } else if (hour >= 18 && hour < 22) {
            timeSlot = "evening";   // 晚上：柔和、暗色
        } else {
            timeSlot = "night";     // 深夜：暗色、柔和
        }

        // Reuse scenario recommendation logic
        return ScenarioBasedRecommendation.recommendByScenario(
            timeSlot, userId, size
        );
    }
}

