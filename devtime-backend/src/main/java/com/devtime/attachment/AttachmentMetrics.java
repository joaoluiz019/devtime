package com.devtime.attachment;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Métricas da feature 015 (spec §29).
 *
 * <p>Reunidas em um componente para que a lista de §29 seja verificável por leitura. Quatro têm
 * alerta declarado, e todas quatro são de segurança:
 *
 * <ul>
 *   <li>{@code attachment.rejected.signature} acima de 5/dia — padrão de tentativa de burla;
 *   <li>{@code attachment.infected} acima de 0 — <b>alerta crítico</b>;
 *   <li>{@code attachment.scan.exhausted} acima de 0 — arquivo inacessível para sempre;
 *   <li>{@code attachment.orphan.detected} acima de 0 — contagem de referências com defeito.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AttachmentMetrics {

    private final MeterRegistry registry;

    /** {@code attachment.uploaded} com {@code contentType} e {@code deduplicated}. */
    public void uploaded(String contentType, boolean deduplicated) {
        registry.counter(
                        "attachment.uploaded",
                        "contentType",
                        contentType,
                        "deduplicated",
                        String.valueOf(deduplicated))
                .increment();
    }

    /** {@code attachment.rejected.size} (RN-801). */
    public void rejectedBySize() {
        registry.counter("attachment.rejected.size").increment();
    }

    /** {@code attachment.rejected.type}: o tipo declarado não está na allowlist (passo 7). */
    public void rejectedByType(String declaredContentType) {
        registry.counter("attachment.rejected.type", "contentType", declaredContentType)
                .increment();
    }

    /**
     * {@code attachment.rejected.signature}: a assinatura não corresponde ao declarado (passo 8).
     *
     * <p>Acima de 5 por dia é alerta. Um usuário erra o tipo ocasionalmente; uma sequência de erros
     * é alguém procurando qual extensão passa.
     */
    public void rejectedBySignature(String declaredContentType) {
        registry.counter("attachment.rejected.signature", "declaredType", declaredContentType)
                .increment();
    }

    /** {@code attachment.infected}: <b>qualquer</b> ocorrência é alerta crítico (R-01). */
    public void infected() {
        registry.counter("attachment.infected").increment();
    }

    /** {@code attachment.scan.duration}, com bucket de tamanho. */
    public void scanDuration(Duration duration, long sizeBytes) {
        Timer.builder("attachment.scan.duration")
                .tag("sizeBucket", sizeBucket(sizeBytes))
                .register(registry)
                .record(duration);
    }

    /** {@code attachment.scan.failed} com a tentativa; acima de 5% das verificações é alerta. */
    public void scanFailed(int attempt) {
        registry.counter("attachment.scan.failed", "attempt", String.valueOf(attempt)).increment();
    }

    /**
     * {@code attachment.scan.exhausted}: três tentativas esgotadas.
     *
     * <p>Alerta em qualquer ocorrência porque a consequência é definitiva — o arquivo fica
     * inacessível para sempre (§6.3) e o usuário precisa ser orientado a reenviar.
     */
    public void scanExhausted() {
        registry.counter("attachment.scan.exhausted").increment();
    }

    /** {@code attachment.download}. */
    public void downloaded() {
        registry.counter("attachment.download").increment();
    }

    /** {@code attachment.download.blocked}: alto em {@code PENDING} indica verificador lento. */
    public void downloadBlocked(String scanStatus) {
        registry.counter("attachment.download.blocked", "scanStatus", scanStatus).increment();
    }

    /** {@code attachment.orphan.detected}: binário no storage sem registro que o referencie. */
    public void orphanDetected(int count) {
        registry.counter("attachment.orphan.detected").increment(count);
    }

    /**
     * Faixas de tamanho para o histograma de duração.
     *
     * <p>Faixas fixas em vez do valor exato como tag: cardinalidade ilimitada de rótulo é o modo
     * clássico de derrubar um sistema de métricas.
     */
    private String sizeBucket(long sizeBytes) {
        if (sizeBytes <= 102_400) {
            return "ate100KB";
        }
        if (sizeBytes <= 1_048_576) {
            return "ate1MB";
        }
        if (sizeBytes <= 5_242_880) {
            return "ate5MB";
        }
        return "ate10MB";
    }
}
