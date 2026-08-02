package com.devtime.attachment;

import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Geração da {@code storageKey} (SG-05, CP-05).
 *
 * <p>A chave é <b>opaca</b> e composta apenas de valores gerados ou derivados pelo sistema:
 *
 * <pre>{@code {tenantId}/attachments/{yyyy}/{MM}/{checksumSha256}}</pre>
 *
 * conforme a organização de chaves de integrations.md §6.2.
 *
 * <p><b>Nenhuma parte do nome do arquivo participa</b> (CP-05, CA-13). Derivar a chave do nome
 * permitiria a um nome malicioso influenciar o caminho no storage — o mesmo vetor que o {@link
 * FileNameSanitizer} fecha, reintroduzido pela porta dos fundos. O checksum é seguro nessa posição
 * porque é hexadecimal de 64 caracteres, saída de uma função, e não entrada do usuário.
 *
 * <p>O prefixo por {@code tenantId} vem do mesmo documento: permite aplicar política de ciclo de
 * vida, quota e exclusão por tenant diretamente no bucket. Ele <b>não</b> é o que garante
 * isolamento — isso é o filtro de tenant no banco (ART-022), e a deduplicação restrita ao tenant
 * (§6.4) é o que impede que dois tenants convirjam para a mesma chave.
 */
@Component
@RequiredArgsConstructor
public class StorageKeyGenerator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final TenantContext tenantContext;
    private final TenantClock clock;

    /**
     * @param checksumSha256 hexadecimal minúsculo de 64 caracteres, calculado pelo {@link
     *     ChecksumCalculator}
     */
    public String generate(String checksumSha256) {
        var today = clock.today();
        return tenantContext.requireTenantId()
                + "/attachments/"
                + today.format(YEAR)
                + "/"
                + today.format(MONTH)
                + "/"
                + checksumSha256;
    }
}
