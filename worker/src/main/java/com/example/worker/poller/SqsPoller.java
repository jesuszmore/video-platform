package com.example.worker.poller;

import com.example.worker.repo.VideoStatusDao;
import com.example.worker.service.FfmpegService;
import com.example.worker.service.ProcessingMessage;
import com.example.worker.service.StorageService;
import com.example.worker.service.VideoKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class SqsPoller {

    private static final Logger log = LoggerFactory.getLogger(SqsPoller.class);

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final StorageService storage;
    private final FfmpegService ffmpeg;
    private final VideoStatusDao dao;
    private final String queueUrl;
    private final int waitTimeSeconds;
    private final int visibilityTimeoutSeconds;
    private final int maxReceiveCount;
    private final long heartbeatMinutes;
    private final ScheduledExecutorService heartbeatExecutor;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public SqsPoller(SqsClient sqs, ObjectMapper objectMapper, StorageService storage, FfmpegService ffmpeg,
                     VideoStatusDao dao,
                     @Value("${app.sqs.transcode-queue-url}") String queueUrl,
                     @Value("${app.sqs.wait-time-seconds:20}") int waitTimeSeconds,
                     @Value("${app.sqs.visibility-timeout-seconds:3600}") int visibilityTimeoutSeconds,
                     @Value("${app.sqs.max-receive-count:3}") int maxReceiveCount,
                     @Value("${app.worker.heartbeat-minutes:5}") long heartbeatMinutes) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.ffmpeg = ffmpeg;
        this.dao = dao;
        this.queueUrl = queueUrl;
        this.waitTimeSeconds = waitTimeSeconds;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.maxReceiveCount = maxReceiveCount;
        this.heartbeatMinutes = heartbeatMinutes;
        ThreadFactory factory = r -> {
            Thread thread = new Thread(r, "sqs-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        Thread thread = new Thread(this::loop, "sqs-poller");
        // No-daemon: mantiene viva la JVM en una app sin servidor web.
        thread.setDaemon(false);
        thread.start();
        log.info("SqsPoller iniciado sobre {}", queueUrl);
    }

    @jakarta.annotation.PreDestroy
    public void stop() {
        running.set(false);
        heartbeatExecutor.shutdownNow();
    }

    private void loop() {
        while (running.get()) {
            try {
                var response = sqs.receiveMessage(b -> b
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(waitTimeSeconds)
                        .visibilityTimeout(visibilityTimeoutSeconds)
                        .attributeNames(QueueAttributeName.ALL));
                response.messages().forEach(this::process);
            } catch (SqsException ex) {
                log.warn("Error de SQS, reintentando en 5s: {}", ex.getMessage());
                sleep(5000);
            } catch (Exception ex) {
                log.error("Error inesperado en el poller, reintentando en 2s", ex);
                sleep(2000);
            }
        }
    }

    private void process(Message message) {
        ProcessingMessage job;
        try {
            job = objectMapper.readValue(message.body(), ProcessingMessage.class);
        } catch (Exception ex) {
            log.error("Mensaje ilegible, se descarta: {}", message.body(), ex);
            delete(message);
            return;
        }

        UUID videoId = job.videoId() != null ? job.videoId() : VideoKey.videoId(job.key());
        if (videoId == null) {
            log.error("Mensaje sin videoId parseable, se descarta: {}", message.body());
            delete(message);
            return;
        }

        if (!dao.claim(videoId)) {
            String status = dao.statusOf(videoId);
            if ("QUEUED".equals(status)) {
                // No deberia ocurrir tras publicar en SQS despues del commit, pero si
                // pasa (carrera transitoria) reintentamos pronto en vez de perder el job.
                log.warn("Video {} sigue QUEUED pero no se pudo reclamar; reintento en 30s", videoId);
                releaseSoon(message);
                return;
            }
            log.info("Video {} en estado {}; se descarta el mensaje", videoId, status);
            delete(message);
            return;
        }

        ScheduledFuture<?> heartbeat = scheduleHeartbeat(message.receiptHandle());
        Path workDir = null;
        try {
            StorageService.Download download = storage.downloadOriginal(videoId, job.bucket(), job.key());
            workDir = download.workDir();
            ffmpeg.transcodeToHls(download.input(), workDir);
            ffmpeg.generateThumbnail(download.input(), workDir.resolve("thumb.jpg"));
            int duration = ffmpeg.probeDurationSeconds(download.input());
            StorageService.Uploaded uploaded = storage.uploadOutputs(videoId, workDir);
            dao.markReady(videoId, uploaded.hlsManifestUrl(), uploaded.thumbnailUrl(), duration);
            delete(message);
            log.info("Video {} procesado correctamente", videoId);
        } catch (Exception ex) {
            int receiveCount = receiveCount(message);
            if (receiveCount >= maxReceiveCount) {
                dao.markFailed(videoId, ex.getMessage());
                log.error("Video {} fallido definitivamente (intento {}): {}", videoId, receiveCount, ex.getMessage());
            } else {
                dao.resetToQueued(videoId);
                log.warn("Video {} fallo (intento {}), se reintentara: {}", videoId, receiveCount, ex.getMessage());
            }
        } finally {
            heartbeat.cancel(false);
            deleteRecursively(workDir);
        }
    }

    private ScheduledFuture<?> scheduleHeartbeat(String receiptHandle) {
        if (heartbeatMinutes <= 0) {
            return heartbeatExecutor.schedule(() -> {
            }, 0, TimeUnit.SECONDS);
        }
        long period = heartbeatMinutes;
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(receiptHandle)
                        .visibilityTimeout(visibilityTimeoutSeconds)
                        .build());
                log.debug("Heartbeat de visibilidad extendido");
            } catch (Exception ex) {
                log.warn("No se pudo extender la visibilidad: {}", ex.getMessage());
            }
        }, period, period, TimeUnit.MINUTES);
    }

    private void delete(Message message) {
        sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
    }

    private void releaseSoon(Message message) {
        try {
            sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .visibilityTimeout(30)
                    .build());
        } catch (Exception ex) {
            log.warn("No se pudo reprogramar el mensaje: {}", ex.getMessage());
        }
    }

    private int receiveCount(Message message) {
        String value = message.attributesAsStrings().get("ApproximateReceiveCount");
        return value == null ? 1 : Integer.parseInt(value);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // limpieza best-effort
                }
            });
        } catch (IOException ex) {
            log.debug("No se pudo limpiar {}: {}", dir, ex.getMessage());
        }
    }
}
