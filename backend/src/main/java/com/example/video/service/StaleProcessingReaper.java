package com.example.video.service;

import com.example.video.domain.Video;
import com.example.video.domain.VideoStatus;
import com.example.video.repo.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Red de seguridad para los videos que se quedan en PROCESSING: si el worker
 * murio (OOM, reinicio, contenedor recreado) o su mensaje de SQS se perdio, la
 * fila queda atascada para siempre porque el consumidor solo reclama QUEUED.
 * Este reaper los devuelve a QUEUED y vuelve a publicar el job.
 */
@Component
public class StaleProcessingReaper {

    private static final Logger log = LoggerFactory.getLogger(StaleProcessingReaper.class);

    private final VideoRepository repository;
    private final MultipartUploadService multipart;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final long staleMinutes;

    public StaleProcessingReaper(VideoRepository repository,
                                 MultipartUploadService multipart,
                                 ApplicationEventPublisher events,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${app.reaper.enabled:true}") boolean enabled,
                                 @Value("${app.reaper.stale-minutes:30}") long staleMinutes) {
        this.repository = repository;
        this.multipart = multipart;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.staleMinutes = staleMinutes;
    }

    @Scheduled(fixedDelayString = "${app.reaper.interval-ms:120000}")
    public void requeueStaleProcessing() {
        if (!enabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(staleMinutes, ChronoUnit.MINUTES);
        List<Video> stale = repository.findByStatusAndUpdatedAtBefore(VideoStatus.PROCESSING, cutoff);
        for (Video video : stale) {
            requeue(video.getId(), video.getS3Key(), cutoff);
        }
    }

    private void requeue(UUID videoId, String s3Key, Instant cutoff) {
        try {
            // Una transaccion por video: si publicar uno falla no bloquea al resto.
            transaction.executeWithoutResult(status -> {
                // Update condicional: si el worker termino entre la consulta y este
                // punto, no machacamos su READY/FAILED.
                if (repository.requeueIfStale(videoId, cutoff) == 0) {
                    return;
                }
                events.publishEvent(new VideoQueuedEvent(videoId, multipart.rawBucket(), s3Key));
                log.warn("Video {} llevaba mas de {} min en PROCESSING; se reencola", videoId, staleMinutes);
            });
        } catch (RuntimeException ex) {
            log.error("No se pudo reencolar el video {}: {}", videoId, ex.getMessage());
        }
    }
}
