package com.example.worker.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class VideoStatusDao {

    private final JdbcTemplate jdbc;

    public VideoStatusDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reclama un job QUEUED o uno PROCESSING que ya no esta vivo (su worker
     * desaparecio sin actualizar {@code updated_at}). Asi un mensaje reentregado
     * por SQS puede recuperar un video que quedaria atascado para siempre.
     */
    public boolean claim(UUID videoId, Instant staleBefore) {
        int updated = jdbc.update(
                "update videos set status = 'PROCESSING', updated_at = now() "
                        + "where id = ? and (status = 'QUEUED' "
                        + "or (status = 'PROCESSING' and updated_at < ?))",
                videoId, staleBefore);
        return updated > 0;
    }

    /**
     * Marca el job como vivo. Lo llama el heartbeat para que otros workers no
     * consideren caducado un trabajo largo.
     */
    public void touch(UUID videoId) {
        jdbc.update("update videos set updated_at = now() where id = ? and status = 'PROCESSING'", videoId);
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
