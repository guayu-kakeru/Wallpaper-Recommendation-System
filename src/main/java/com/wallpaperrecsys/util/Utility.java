package com.wallpaperrecsys.util;

import com.wallpaperrecsys.model.Embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for common operations
 * 工具类，提供通用操作
 */
public class Utility {
    
    /**
     * Parse embedding string to Embedding object
     * 解析embedding字符串为Embedding对象
     * @param embStr embedding string with space-separated values
     * @return Embedding object
     */
    public static Embedding parseEmbStr(String embStr) {
        if (embStr == null || embStr.trim().isEmpty()) {
            return null;
        }
        
        String[] embStrings = embStr.trim().split("\\s+");
        List<Double> vector = new ArrayList<>();
        
        for (String element : embStrings) {
            try {
                vector.add(Double.parseDouble(element));
            } catch (NumberFormatException e) {
                // 忽略无效的数字
                continue;
            }
        }
        
        return new Embedding(vector);
    }
    
    /**
     * Convert Embedding to string
     * 将Embedding转换为字符串
     */
    public static String embToStr(Embedding emb) {
        if (emb == null || emb.getVector() == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emb.getVector().size(); i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(emb.getVector().get(i));
        }
        return sb.toString();
    }
}

