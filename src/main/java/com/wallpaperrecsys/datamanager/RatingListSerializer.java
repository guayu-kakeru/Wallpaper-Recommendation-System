package com.wallpaperrecsys.datamanager;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;

/**
 * RatingListSerializer, custom serializer for Rating list
 * 评分列表序列化器
 */
public class RatingListSerializer extends JsonSerializer<List<Rating>> {
    @Override
    public void serialize(List<Rating> ratings, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();
        if (ratings != null) {
            for (Rating rating : ratings) {
                gen.writeStartObject();
                gen.writeNumberField("userId", rating.getUserId());
                gen.writeNumberField("wallpaperId", rating.getWallpaperId());
                gen.writeNumberField("score", rating.getScore());
                gen.writeNumberField("timestamp", rating.getTimestamp());
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
    }
}

