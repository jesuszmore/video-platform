package com.example.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    private final String region;
    private final String endpoint;

    public AwsConfig(@Value("${app.aws.region:eu-west-1}") String region,
                     @Value("${app.aws.endpoint:}") String endpoint) {
        this.region = region;
        this.endpoint = endpoint;
    }

    private boolean hasEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    private StaticCredentialsProvider localCredentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }

    @Bean
    S3Client s3Client() {
        var builder = S3Client.builder().region(Region.of(region));
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(localCredentials())
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean
    SqsClient sqsClient() {
        var builder = SqsClient.builder().region(Region.of(region));
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(localCredentials());
        }
        return builder.build();
    }
}
