package com.example.video.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica el mensaje de SQS <b>despues</b> de que la transaccion confirme el
 * estado QUEUED. Si se publicase dentro de la transaccion, el worker podria
 * recibirlo antes del commit, no ver el video como QUEUED y descartar el
 * mensaje, dejando el video atascado.
 */
@Component
public class VideoQueuedListener {

    private static final Logger log = LoggerFactory.getLogger(VideoQueuedListener.class);

    private final ProcessingQueuePublisher queue;

    public VideoQueuedListener(ProcessingQueuePublisher queue) {
        this.queue = queue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVideoQueued(VideoQueuedEvent event) {
        log.debug("Transaccion confirmada, encolando videoId={}", event.videoId());
        queue.enqueue(new ProcessingMessage(event.videoId(), event.bucket(), event.key()));
    }
}
