package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.storage.StoragePort;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jobs da feature 015 (spec §22.4).
 *
 * <p>Reunidos em uma classe pelo critério já usado em {@code NotificationJobs} e {@code
 * AuthCleanupJobs}: mesma natureza — varredura por predicado sobre o estado atual — e mesmo arranjo
 * de contexto por tenant.
 *
 * <p>BR-049: os jobs percorrem todos os tenants e <b>definem o contexto a cada item</b>. Sem isso,
 * a atualização do anexo escreveria fora do tenant a que ele pertence.
 */
@Component
@Profile("scheduler")
@RequiredArgsConstructor
@Slf4j
public class AttachmentJobs {

    /** BR-186: lote por execução, para que uma fila acumulada não monopolize o job. */
    private static final int SCAN_BATCH = 50;

    /** Teto por varredura semanal; evita que um bucket grande prenda o job por horas (BR-186). */
    private static final int ORPHAN_SCAN_LIMIT = 10_000;

    private final AttachmentRepository repository;
    private final ScanService scanService;
    private final StoragePort storage;
    private final AttachmentMetrics metrics;
    private final TenantContext tenantContext;

    /**
     * {@code ScanWorkerJob} — processa {@code PENDING} e reprocessa {@code FAILED} (§22.4).
     *
     * <p>A cada 20 segundos. O intervalo curto é o que torna a espera do usuário tolerável: §20
     * aceita que o arquivo fique indisponível "por alguns segundos após o envio", e a UI comunica
     * isso explicitamente.
     *
     * <p>CX-20: com o verificador indisponível por horas, os anexos apenas se acumulam em {@code
     * PENDING}; nenhum download é liberado e a fila é processada ao restabelecer.
     */
    @Scheduled(cron = "*/20 * * * * *")
    @SchedulerLock(name = "scanWorker", lockAtMostFor = "PT10M")
    public void processScanQueue() {
        List<Attachment> pending = queue();
        int clean = 0;
        int infected = 0;
        int failed = 0;
        int exhausted = 0;

        for (Attachment attachment : pending) {
            // BR-049: o contexto do tenant do próprio anexo, a cada item.
            switch (inTenant(attachment)) {
                case CLEAN -> clean++;
                case INFECTED -> infected++;
                case FAILED -> failed++;
                case EXHAUSTED -> exhausted++;
                case SKIPPED -> {
                    // Já resolvido por outra execução; convergente por construção (BR-185).
                }
            }
        }
        if (!pending.isEmpty()) {
            log.info(
                    "fila de verificação processada itens={} limpos={} infectados={} falhas={}"
                            + " esgotados={}",
                    pending.size(),
                    clean,
                    infected,
                    failed,
                    exhausted);
        }
    }

    /**
     * Fila: {@code PENDING} primeiro, {@code FAILED} depois.
     *
     * <p>A ordem importa. Um anexo recém-enviado tem alguém esperando por ele; um que já falhou
     * está sendo reprocessado e a espera acabou há mais tempo. Atender {@code FAILED} antes faria
     * uma fila de reprocessamento adiar indefinidamente todo upload novo.
     *
     * <p>Executada <b>sem</b> tenant no contexto — é o que permite ao filtro de Hibernate
     * permanecer inativo e a consulta {@code @CrossTenant} enxergar todos os tenants.
     */
    private List<Attachment> queue() {
        tenantContext.clear();
        List<Attachment> queue =
                new ArrayList<>(
                        repository.findByScanStatus(
                                ScanStatus.PENDING, PageRequest.of(0, SCAN_BATCH)));
        int remaining = SCAN_BATCH - queue.size();
        if (remaining > 0) {
            repository
                    .findByScanStatus(ScanStatus.FAILED, PageRequest.of(0, remaining))
                    // CP-11: quem esgotou as três tentativas não volta para a fila.
                    .stream()
                    .filter(Attachment::hasAttemptsLeft)
                    .forEach(queue::add);
        }
        return queue;
    }

    /**
     * BR-187: falha em um tenant não interrompe o processamento dos demais.
     *
     * <p>É a única captura ampla desta feature, e existe porque a alternativa — deixar a exceção
     * subir — pararia a fila inteira por causa de um anexo. Sem ela, um único registro corrompido
     * bloquearia todos os downloads do sistema até alguém intervir.
     */
    private ScanService.ScanOutcome inTenant(Attachment attachment) {
        try {
            tenantContext.set(systemSession(attachment));
            return scanService.scan(attachment.getId());
        } catch (RuntimeException failure) {
            log.error(
                    "falha ao processar verificação attachmentId={} tenantId={}",
                    attachment.getId(),
                    attachment.getTenantId(),
                    failure);
            return ScanService.ScanOutcome.FAILED;
        } finally {
            // Contrato de limpeza do TenantContext: sem isto, a thread do agendador carregaria o
            // tenant do item anterior — vazamento entre tenants.
            tenantContext.clear();
        }
    }

    /**
     * Sessão de sistema para o item.
     *
     * <p>CE-P-08: a atualização de {@code scanStatus} é do sistema e ignora RBAC. O papel {@code
     * OWNER} é usado porque o job precisa alcançar o registro independentemente de quem o enviou —
     * não há usuário nesta operação, e {@code userId} nulo não é aceito por {@code TenantSession}.
     */
    private TenantSession systemSession(Attachment attachment) {
        return new TenantSession(
                attachment.getUploadedBy(),
                attachment.getTenantId(),
                null,
                Role.OWNER,
                RolePermissions.of(Role.OWNER),
                "UTC");
    }

    /**
     * {@code OrphanBinaryJob} — detecta binário no storage sem registro que o referencie (§22.4).
     *
     * <p><b>Alerta sem remover</b> (CP-10, OB-05). Remover automaticamente apagaria dado do cliente
     * com base numa inferência sobre a contagem de referências; se essa contagem tiver defeito, a
     * remoção é irreversível. É o mesmo princípio de {@code WorkLogConsistencyJob} em {@code 008} e
     * de {@code SnapshotIntegrityJob} em {@code 011}: detectar é do sistema, corrigir é humano.
     *
     * <p>A comparação é feita em memória com um {@link java.util.HashSet} das chaves referenciadas.
     * A tabela é pequena por construção (§20.1: "dezenas por ticket, no máximo"; o volume está no
     * storage, não no banco), e um {@code EXISTS} por chave do bucket seria uma consulta por
     * objeto.
     */
    @Scheduled(cron = "0 0 5 * * 0")
    @SchedulerLock(name = "orphanBinary", lockAtMostFor = "PT60M")
    @Transactional(readOnly = true)
    public void detectOrphanBinaries() {
        tenantContext.clear();
        Set<String> referenced = new HashSet<>(repository.findReferencedStorageKeys());
        List<String> orphans =
                storage.listKeys(ORPHAN_SCAN_LIMIT).stream()
                        .filter(key -> !referenced.contains(key))
                        .toList();

        metrics.orphanDetected(orphans.size());
        if (orphans.isEmpty()) {
            log.info("nenhum binário órfão detectado chavesReferenciadas={}", referenced.size());
            return;
        }
        // §28: WARN por chave. A chave é opaca e pode entrar em log; o nome do arquivo, não.
        orphans.forEach(key -> log.warn("binário órfão detectado storageKey={}", key));
        log.warn(
                "detecção de órfãos concluída orfaos={} — remoção é operação humana (CP-10)",
                orphans.size());
    }
}
