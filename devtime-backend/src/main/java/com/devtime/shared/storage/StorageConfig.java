package com.devtime.shared.storage;

import com.devtime.shared.config.DevTimeProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cliente do object storage (integrations.md §6.2).
 *
 * <p>{@code pathStyleAccessEnabled} é habilitado porque o MinIO de desenvolvimento não resolve
 * subdomínio por bucket. Em S3 real o modo de caminho continua funcionando, então manter um único
 * arranjo evita que o ambiente local exercite um caminho de código diferente do de produção — que é
 * a origem clássica de falha que só aparece em produção.
 */
@Configuration
public class StorageConfig {

    @Bean
    public S3Client s3Client(DevTimeProperties properties) {
        DevTimeProperties.StorageProps storage = properties.storage();
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(DevTimeProperties properties) {
        DevTimeProperties.StorageProps storage = properties.storage();
        return S3Presigner.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private StaticCredentialsProvider credentials(DevTimeProperties.StorageProps storage) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }
}
