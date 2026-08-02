package com.devtime.shared.storage;

import com.devtime.shared.config.DevTimeProperties;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Adapter de {@link StoragePort} sobre S3 e compatíveis (integrations.md §6.2).
 *
 * <p>É a única classe do sistema que conhece o SDK. CE-G-07: a biblioteca vence apenas na fronteira
 * de integração; nenhuma feature importa {@code software.amazon.awssdk}.
 */
@Component
@Slf4j
public class S3StorageAdapter implements StoragePort {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3StorageAdapter(S3Client client, S3Presigner presigner, DevTimeProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = properties.storage().bucket();
    }

    @Override
    public void store(String key, InputStream content, long contentLength, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(contentLength)
                            // SG-06: o objeto nunca é renderizado pelo navegador.
                            .contentDisposition("attachment")
                            .build(),
                    // O tamanho é informado ao SDK para que ele transmita em fluxo, sem
                    // bufferizar o conteúdo inteiro para descobri-lo (CP-14).
                    RequestBody.fromInputStream(content, contentLength));
        } catch (S3Exception failure) {
            // §19.1: o nome do arquivo nunca entra em log; a chave é opaca e pode entrar.
            throw new StorageException("falha ao gravar objeto key=" + key, failure);
        }
    }

    @Override
    public String presignedDownloadUrl(String key, Duration ttl, String downloadFileName) {
        GetObjectRequest get =
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        // O nome chega assinado na URL: o objeto no storage tem chave opaca, e sem
                        // esta sobreposição o navegador salvaria o arquivo com o nome da chave.
                        .responseContentDisposition(contentDisposition(downloadFileName))
                        .build();
        try {
            return presigner
                    .presignGetObject(
                            GetObjectPresignRequest.builder()
                                    .signatureDuration(ttl)
                                    .getObjectRequest(get)
                                    .build())
                    .url()
                    .toString();
        } catch (S3Exception failure) {
            throw new StorageException("falha ao assinar URL key=" + key, failure);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException absent) {
            // Idempotente por contrato: remover o que já não existe é o resultado desejado.
            log.debug("objeto já ausente na remoção key={}", key);
        } catch (S3Exception failure) {
            throw new StorageException("falha ao remover objeto key=" + key, failure);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException absent) {
            return false;
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) {
                return false;
            }
            throw new StorageException("falha ao consultar objeto key=" + key, failure);
        }
    }

    @Override
    public Optional<InputStream> openStream(String key) {
        try {
            return Optional.of(
                    client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()));
        } catch (NoSuchKeyException absent) {
            return Optional.empty();
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageException("falha ao ler objeto key=" + key, failure);
        }
    }

    @Override
    public List<String> listKeys(int limit) {
        try {
            return client
                    .listObjectsV2Paginator(
                            ListObjectsV2Request.builder().bucket(bucket).maxKeys(1000).build())
                    .contents()
                    .stream()
                    .map(S3Object::key)
                    .limit(limit)
                    .toList();
        } catch (NoSuchBucketException absent) {
            return List.of();
        } catch (S3Exception failure) {
            throw new StorageException("falha ao listar objetos do bucket", failure);
        }
    }

    /**
     * Cabeçalho {@code Content-Disposition} com o nome já sanitizado.
     *
     * <p>Aspas duplas e barras invertidas são escapadas mesmo com o nome vindo do {@code
     * FileNameSanitizer}: o cabeçalho é um contexto de escape próprio, e depender da sanitização de
     * outra camada para a corretude desta é o acoplamento que produz injeção de cabeçalho quando
     * uma das duas muda.
     */
    private String contentDisposition(String fileName) {
        String escaped = fileName.replace("\\", "\\\\").replace("\"", "\\\"");
        return "attachment; filename=\"" + escaped + "\"";
    }
}
