package com.jdec.platform.shared.config;

import com.jdec.platform.shared.config.properties.OssProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class OssConfig {

    @Bean
    public S3Client s3Client(OssProperties ossProperties) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(
                        ossProperties.getAccessKey(), ossProperties.getSecretKey());

        return S3Client.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .endpointOverride(URI.create(ossProperties.getEndpoint()))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(ossProperties.isPathStyleAccess())
                                .build())
                .build();
    }
}
