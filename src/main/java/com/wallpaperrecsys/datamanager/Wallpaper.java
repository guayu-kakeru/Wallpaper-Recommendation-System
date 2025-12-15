package com.wallpaperrecsys.datamanager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wallpaperrecsys.model.Embedding;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Wallpaper Class, contains attributes for wallpaper data
 * 壁纸类，包含壁纸的所有属性
 */
public class Wallpaper {
    // 内部使用的数值型ID，从1开始递增，便于与ratings.csv对齐
    int wallpaperId;
    // 外部源ID，例如 Wallhaven 的字符串ID（dpqdxj 等）
    String externalId;
    String title;
    String imageUrl;
    String thumbnailUrl;
    int resolutionWidth;
    int resolutionHeight;
    List<String> tags;
    List<String> categories;
    String style;
    String mood;
    String colorPalette;
    // 下载次数
    int downloadCount;
    // 平均评分
    double averageRating;
    // 评分数量
    int ratingNumber;
    // 文件大小（KB）
    int fileSize;
    // 文件格式
    String format;
    // 上传时间
    String uploadTime;

    // embedding向量
    @JsonIgnore
    Embedding emb;

    // 所有评分列表
    @JsonIgnore
    List<Rating> ratings;

    // 壁纸特征
    @JsonIgnore
    Map<String, String> wallpaperFeatures;

    final int TOP_RATING_SIZE = 10;

    @JsonSerialize(using = RatingListSerializer.class)
    List<Rating> topRatings;

    public Wallpaper() {
        ratingNumber = 0;
        averageRating = 0;
        downloadCount = 0;
        this.tags = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.ratings = new ArrayList<>();
        this.topRatings = new LinkedList<>();
        this.emb = null;
        this.wallpaperFeatures = null;
    }

    public int getWallpaperId() {
        return wallpaperId;
    }

    public void setWallpaperId(int wallpaperId) {
        this.wallpaperId = wallpaperId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public int getResolutionWidth() {
        return resolutionWidth;
    }

    public void setResolutionWidth(int resolutionWidth) {
        this.resolutionWidth = resolutionWidth;
    }

    public int getResolutionHeight() {
        return resolutionHeight;
    }

    public void setResolutionHeight(int resolutionHeight) {
        this.resolutionHeight = resolutionHeight;
    }

    public List<String> getTags() {
        return tags;
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void addCategory(String category) {
        this.categories.add(category);
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getColorPalette() {
        return colorPalette;
    }

    public void setColorPalette(String colorPalette) {
        this.colorPalette = colorPalette;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public int getRatingNumber() {
        return ratingNumber;
    }

    public void setRatingNumber(int ratingNumber) {
        this.ratingNumber = ratingNumber;
    }

    public int getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(String uploadTime) {
        this.uploadTime = uploadTime;
    }

    public List<Rating> getRatings() {
        return ratings;
    }

    public void addRating(Rating rating) {
        averageRating = (averageRating * ratingNumber + rating.getScore()) / (ratingNumber + 1);
        ratingNumber++;
        this.ratings.add(rating);
        addTopRating(rating);
    }

    public void addTopRating(Rating rating) {
        if (this.topRatings.isEmpty()) {
            this.topRatings.add(rating);
        } else {
            int index = 0;
            for (Rating topRating : this.topRatings) {
                if (topRating.getScore() >= rating.getScore()) {
                    break;
                }
                index++;
            }
            topRatings.add(index, rating);
            if (topRatings.size() > TOP_RATING_SIZE) {
                topRatings.remove(0);
            }
        }
    }

    public Embedding getEmb() {
        return emb;
    }

    public void setEmb(Embedding emb) {
        this.emb = emb;
    }

    public Map<String, String> getWallpaperFeatures() {
        return wallpaperFeatures;
    }

    public void setWallpaperFeatures(Map<String, String> wallpaperFeatures) {
        this.wallpaperFeatures = wallpaperFeatures;
    }

    public List<Rating> getTopRatings() {
        return topRatings;
    }
}

