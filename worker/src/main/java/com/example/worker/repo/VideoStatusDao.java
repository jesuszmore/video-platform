package com.example.worker.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class VideoStatusDao {

    private final JdbcTemplate jdbc;

    public VideoStatusDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(UUID videoId) {
        int updated = jdbc.update(
                "update videos set status = 'PROCESSING', updated_at = now() where id = ? and status = 'QUEUED'",
                videoId);
        return updated > 0;
    }

    public String statusOf(UUID videoId) {
        var statuses = jdbc.queryForList("select status from videos where id = ?", String.class, videoId);
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    public void resetToQueued(UUID videoId) {
        jdbc.update("update videos set status = 'QUEUED', updated_at = now() where id = ?", videoId);
    }

    public void markReady(UUID videoId, String hlsManifestUrl, String thumbnailUrl, int durationSeconds) {
        jdbc.update("update videos set status = 'READY', hls_manifest_url = ?, thumbnail_url = ?, "
                        + "duration_seconds = ?, error_message = null, updated_at = now() where id = ?",
                hlsManifestUrl, thumbnailUrl, durationSeconds, videoId);
    }

    public void markFailed(UUID videoId, String errorMessage) {
        String error = errorMessage == null ? "error desconocido" : errorMessage;
        if (error.length() > 2000) {
            error = error.substring(0, 2000);
        }
        jdbc.update("update videos set status = 'FAILED', error_message = ?, updated_at = now() where id = ?",
                error, videoId);
    }
}
