package com.wallpaperrecsys.util;

/**
 * Configuration class
 * 配置类
 */
public class Config {
    
    // Embedding数据源：FILE 或 REDIS
    public static final String DATA_SOURCE_FILE = "FILE";
    public static final String DATA_SOURCE_REDIS = "REDIS";
    
    public static String EMB_DATA_SOURCE = DATA_SOURCE_FILE;
    
    // 是否从Redis加载特征
    public static boolean IS_LOAD_ITEM_FEATURE_FROM_REDIS = false;
    public static boolean IS_LOAD_USER_FEATURE_FROM_REDIS = false;
    
    // Redis配置
    public static String REDIS_ENDPOINT = "localhost";
    public static int REDIS_PORT = 6379;
    
    // Embedding服务配置
    public static String EMBEDDING_SERVICE_URL = "http://localhost:5000/api/embedding";
    public static String EMBEDDING_SOURCE = "python_service"; // python_service 或 local_model
    
    // 默认数据路径
    public static String DEFAULT_WALLPAPER_DATA_PATH = "data/wallpapers.csv";
    public static String DEFAULT_RATING_DATA_PATH = "data/ratings.csv";
    public static String DEFAULT_WALLPAPER_EMB_PATH = "data/wallpaper_embeddings.csv";
    public static String DEFAULT_USER_EMB_PATH = "data/user_embeddings.csv";
}

