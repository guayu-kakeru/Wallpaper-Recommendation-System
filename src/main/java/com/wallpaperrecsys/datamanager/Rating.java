package com.wallpaperrecsys.datamanager;

/**
 * Rating Class, represents a user's rating for a wallpaper
 * 评分类，表示用户对壁纸的评分
 */
public class Rating {
    int userId;
    int wallpaperId;
    double score;
    long timestamp;

    public Rating() {
    }

    public Rating(int userId, int wallpaperId, double score, long timestamp) {
        this.userId = userId;
        this.wallpaperId = wallpaperId;
        this.score = score;
        this.timestamp = timestamp;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getWallpaperId() {
        return wallpaperId;
    }

    public void setWallpaperId(int wallpaperId) {
        this.wallpaperId = wallpaperId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}

