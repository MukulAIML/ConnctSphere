package com.connectsphere.post.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

/**
 * AWS Rekognition client bean.
 * When AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY are provided via env-vars,
 * explicit credentials are used.  Otherwise, the SDK's default chain
 * (IAM role, ~/.aws/credentials, etc.) is used automatically.
 */
@Configuration
public class AwsRekognitionConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.access-key:}")
    private String accessKey;

    @Value("${aws.secret-key:}")
    private String secretKey;

    @Value("${aws.rekognition.enabled:false}")
    private boolean rekognitionEnabled;

    @Bean
    public RekognitionClient rekognitionClient() {
        Region region = Region.of(awsRegion);

        AwsCredentialsProvider credentialsProvider;
        if (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
        } else {
            // Fallback to IAM role / env / ~/.aws/credentials
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        return RekognitionClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
