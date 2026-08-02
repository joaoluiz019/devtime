package com.devtime.shared.storage;

import com.devtime.shared.config.DevTimeProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoncurrentVersionExpiration;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.VersioningConfiguration;

/**
 * Garante o bucket com a configuração exigida por integrations.md §6.2 (T-015-01).
 *
 * <p>Três propriedades, nesta ordem de importância:
 *
 * <ol>
 *   <li><b>SG-01 — bucket privado.</b> R-08 classifica um bucket acidentalmente público como risco
 *       de impacto <b>crítico</b>, e é a falha que nenhum código da aplicação corrige: uma vez que
 *       o objeto responde sem assinatura, toda a cadeia de RN-803 é contornável por quem tiver a
 *       URL. Aplicar o bloqueio na inicialização o torna consequência do deploy, não de um passo
 *       manual que alguém pode esquecer.
 *   <li><b>SG-03 — versionamento habilitado, com retenção de 30 dias.</b> É versionamento <b>de
 *       objeto no storage</b>, para recuperação operacional. Não é versionamento de anexo: RS-09 e
 *       §4 da spec 015 mantêm isso fora do roadmap, e CP-13 proíbe qualquer rota de atualização. As
 *       duas coisas coexistem sem contradição porque atuam em camadas distintas — o storage guarda
 *       versões do binário; o domínio expõe um anexo imutável.
 *   <li><b>SG-02 — criptografia em repouso</b>, herdada da configuração do bucket no provedor.
 * </ol>
 *
 * <p>Executa em {@code ApplicationReadyEvent} e não em {@code @PostConstruct}: uma falha de storage
 * é degradação prevista (integrations.md §4) e não pode impedir a aplicação de subir — o registro
 * de horas continua funcionando sem anexos (SG-05).
 */
@Component
@Slf4j
public class StorageBucketInitializer {

    /** SG-03: retenção de versões anteriores. */
    private static final int NONCURRENT_VERSION_RETENTION_DAYS = 30;

    private static final String VERSION_RETENTION_RULE_ID = "devtime-noncurrent-version-retention";

    private final S3Client client;
    private final String bucket;

    public StorageBucketInitializer(S3Client client, DevTimeProperties properties) {
        this.client = client;
        this.bucket = properties.storage().bucket();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            createIfAbsent();
            blockPublicAccess();
            enableVersioning();
            applyVersionRetention();
            log.info(
                    "bucket de anexos pronto bucket={} versionamento=habilitado acesso=privado",
                    bucket);
        } catch (RuntimeException failure) {
            // ER-02 admite a captura ampla aqui, e ela é deliberada: o SDK sinaliza
            // indisponibilidade por `SdkClientException`, que não é `S3Exception`, e capturar
            // apenas os tipos previstos deixaria a conexão recusada derrubar a inicialização.
            //
            // SG-05 / IN-04 / ER-08: falha de storage é degradação prevista (integrations.md §4) e
            // **não pode impedir a aplicação de subir** — o registro de horas continua funcionando
            // sem anexos. O alerta é operacional; a consequência é que o upload falhará até o
            // storage voltar, e nenhum download é liberado sem CLEAN.
            log.error(
                    "não foi possível preparar o bucket de anexos bucket={} — uploads falharão até"
                            + " a normalização",
                    bucket,
                    failure);
        }
    }

    private void createIfAbsent() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException absent) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("bucket de anexos criado bucket={}", bucket);
        }
    }

    /** SG-01 / CP-16 / DoD-09: nenhum objeto acessível sem assinatura. */
    private void blockPublicAccess() {
        client.putPublicAccessBlock(
                PutPublicAccessBlockRequest.builder()
                        .bucket(bucket)
                        .publicAccessBlockConfiguration(
                                PublicAccessBlockConfiguration.builder()
                                        .blockPublicAcls(true)
                                        .ignorePublicAcls(true)
                                        .blockPublicPolicy(true)
                                        .restrictPublicBuckets(true)
                                        .build())
                        .build());
    }

    /** SG-03: versionamento de objeto no storage. */
    private void enableVersioning() {
        client.putBucketVersioning(
                PutBucketVersioningRequest.builder()
                        .bucket(bucket)
                        .versioningConfiguration(
                                VersioningConfiguration.builder()
                                        .status(BucketVersioningStatus.ENABLED)
                                        .build())
                        .build());
    }

    /**
     * SG-03: versões anteriores expiram em 30 dias.
     *
     * <p>Sem a regra, o versionamento cresceria indefinidamente e a remoção do binário exigida por
     * RN-805 e INV-ATT-06 seria apenas aparente — a versão anterior continuaria recuperável. A
     * janela de 30 dias é o que integrations.md define como suficiente para recuperação
     * operacional.
     */
    private void applyVersionRetention() {
        client.putBucketLifecycleConfiguration(
                PutBucketLifecycleConfigurationRequest.builder()
                        .bucket(bucket)
                        .lifecycleConfiguration(
                                config ->
                                        config.rules(
                                                List.of(
                                                        LifecycleRule.builder()
                                                                .id(VERSION_RETENTION_RULE_ID)
                                                                .status(ExpirationStatus.ENABLED)
                                                                .filter(
                                                                        LifecycleRuleFilter
                                                                                .builder()
                                                                                .prefix("")
                                                                                .build())
                                                                .noncurrentVersionExpiration(
                                                                        NoncurrentVersionExpiration
                                                                                .builder()
                                                                                .noncurrentDays(
                                                                                        NONCURRENT_VERSION_RETENTION_DAYS)
                                                                                .build())
                                                                .build())))
                        .build());
    }
}
