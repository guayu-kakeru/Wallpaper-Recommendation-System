package com.wallpaperrecsys.recprocess;

import java.util.List;

/**
 * Scenario Configuration Class
 * 场景配置类
 */
public class ScenarioConfig {
    public List<String> preferredStyles;      // 偏好风格
    public List<String> preferredMoods;       // 偏好情绪
    public List<String> preferredColorPalettes;  // 偏好色调
    public List<String> preferredCategories; // 偏好分类
    public List<String> preferredTags;       // 偏好标签（直接匹配 wallpapers.csv 的 tags 字段）
    public double diversityWeight;           // 多样性权重
    public double userPreferenceWeight;      // 用户偏好权重
    
    public ScenarioConfig(
        List<String> preferredStyles,
        List<String> preferredMoods,
        List<String> preferredColorPalettes,
        List<String> preferredCategories,
        List<String> preferredTags,
        double diversityWeight,
        double userPreferenceWeight
    ) {
        this.preferredStyles = preferredStyles;
        this.preferredMoods = preferredMoods;
        this.preferredColorPalettes = preferredColorPalettes;
        this.preferredCategories = preferredCategories;
        this.preferredTags = preferredTags;
        this.diversityWeight = diversityWeight;
        this.userPreferenceWeight = userPreferenceWeight;
    }
}

