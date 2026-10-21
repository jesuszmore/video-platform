package com.example.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
public class ProcessingQueuePublisher {

    private static final Logger log = LoggerFactory.getLogger(ProcessingQueuePublisher.class);

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public ProcessingQueuePublisher(SqsClient sqs, ObjectMapper objectMapper,
                                    @Value("${app.sqs.transcode-queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    public void enqueue(ProcessingMessage message) {
        try {
            String body = objectMapper.writeValueAsString(message);
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(body)
                    .build());
            log.info("Mensaje encolado videoId={}", message.videoId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el mensaje de procesamiento", e);
        }
    }
}
