package com.devtime.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * MinIO e ClamAV reais para os testes de {@code 015-attachments} (T-015-01, T-015-02).
 *
 * <p>integrations.md §11 usa contêineres reais "quando o comportamento real importa". Para esta
 * feature importa, e de forma não negociável:
 *
 * <ul>
 *   <li><b>DoD-02</b> exige o EICAR detectado. Um dublê programado para responder {@code INFECTED}
 *       provaria apenas que o dublê responde o que foi programado para responder — e §9 de {@code
 *       implementation-order.md} classifica "EICAR liberado para download" como o gatilho do risco
 *       crítico da feature.
 *   <li><b>DoD-06</b> exige provar a remoção do binário <b>por acesso direto ao storage</b>. Sem um
 *       storage de verdade, não há o que acessar.
 * </ul>
 *
 * <p>Os contêineres são {@code static} e reutilizados entre classes, pelo mesmo motivo do
 * PostgreSQL: a carga da base de assinaturas do ClamAV leva minutos, e pagá-la por classe tornaria
 * a suíte impraticável — o que na prática levaria a executá-la menos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AttachmentInfrastructureConfiguration {

    private static final String STORAGE_ACCESS_KEY = "devtime-test";
    private static final String STORAGE_SECRET_KEY = "devtime-test-secret";
    private static final String BUCKET = "devtime-attachments-test";

    private static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
                    .withUserName(STORAGE_ACCESS_KEY)
                    .withPassword(STORAGE_SECRET_KEY)
                    .withReuse(true);

    /**
     * ClamAV com o daemon exposto em 3310.
     *
     * <p>A espera é pela linha de prontidão no log, e não por porta aberta: o daemon aceita conexão
     * antes de terminar de carregar a base de assinaturas, e uma verificação nesse intervalo
     * responderia erro — que a máquina de §4.9 trataria como {@code FAILED}, consumindo tentativas
     * por um motivo que não é falha alguma.
     */
    private static final GenericContainer<?> CLAMAV =
            new GenericContainer<>(DockerImageName.parse("clamav/clamav:1.4"))
                    .withExposedPorts(3310)
                    .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
                    .waitingFor(
                            Wait.forLogMessage(".*socket found, clamd started.*\\n", 1)
                                    .withStartupTimeout(java.time.Duration.ofMinutes(5)))
                    .withReuse(true);

    static {
        MINIO.start();
        CLAMAV.start();
    }

    /**
     * Aponta a aplicação para os contêineres.
     *
     * <p>{@link DynamicPropertyRegistrar} em vez de {@code @DynamicPropertySource}: o segundo é
     * estático e por classe de teste, e obrigaria cada classe da feature a repeti-lo. Como bean, a
     * configuração acompanha o {@code @Import} e vale para todas.
     */
    @Bean
    DynamicPropertyRegistrar attachmentInfrastructureProperties() {
        return registry -> {
            registry.add("devtime.storage.endpoint", MINIO::getS3URL);
            registry.add("devtime.storage.bucket", () -> BUCKET);
            registry.add("devtime.storage.region", () -> "us-east-1");
            registry.add("devtime.storage.access-key", () -> STORAGE_ACCESS_KEY);
            registry.add("devtime.storage.secret-key", () -> STORAGE_SECRET_KEY);
            registry.add("devtime.antivirus.host", CLAMAV::getHost);
            registry.add("devtime.antivirus.port", () -> CLAMAV.getMappedPort(3310));
        };
    }
}
