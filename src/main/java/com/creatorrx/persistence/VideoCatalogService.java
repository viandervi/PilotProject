package com.creatorrx.persistence;

import com.creatorrx.persistence.entity.VideoEntity;
import com.creatorrx.persistence.repository.VideoRepository;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent metadata persistence for a video already associated with a verified channel.
 * Channel ownership and YouTube API access must be checked by the calling sync workflow.
 */
@Singleton
public class VideoCatalogService {
    private final VideoRepository videos;

    public VideoCatalogService(VideoRepository videos) {
        this.videos = videos;
    }

    public VideoEntity upsert(UUID channelId, String youtubeVideoId, String title,
                              Instant publishedAt, Integer durationSeconds) {
        if (channelId == null || youtubeVideoId == null || !youtubeVideoId.matches("[A-Za-z0-9_-]{11}")) {
            throw new IllegalArgumentException("Invalid channel or YouTube video ID");
        }
        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("Video duration must not be negative");
        }
        var existing = videos.findByYoutubeVideoId(youtubeVideoId);
        var now = Instant.now();
        if (existing.isPresent()) {
            var previous = existing.get();
            if (!previous.channelId().equals(channelId)) {
                throw new IllegalStateException("Video already belongs to a different channel");
            }
            return videos.update(new VideoEntity(previous.id(), channelId, youtubeVideoId,
                    title, publishedAt, durationSeconds, previous.createdAt(), now));
        }
        return videos.save(new VideoEntity(UUID.randomUUID(), channelId, youtubeVideoId,
                title, publishedAt, durationSeconds, now, now));
    }
}
