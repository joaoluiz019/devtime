package com.devtime.audit;

import com.devtime.audit.dto.AuditEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registro da trilha de auditoria (RN-006).
 *
 * <p>Interface pública consumida pelas features que mantêm entidades auditáveis — {@code Client},
 * {@code Contract} e {@code ContractPeriod} nesta sprint (entities.md §6.20).
 *
 * <p>O registro ocorre na <b>mesma transação</b> da alteração: uma trilha que pode divergir do dado
 * não tem valor em disputa contratual, que é a razão de ART-003 exigi-la.
 */
public interface AuditService {

    /**
     * Registra uma alteração.
     *
     * @param action verbo no passado, ex.: {@code CONTRACT_CREATED} (entities.md §6.20)
     * @param entityType nome da entidade auditada
     * @param entityId identificador da entidade
     * @param beforeState apenas os campos alterados; vazio em criação
     * @param afterState apenas os campos alterados; vazio em exclusão
     */
    void record(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState);

    /**
     * Registra uma alteração acrescentando contexto ao {@code metadata}.
     *
     * <p>Existe porque §18 de {@code specs/007-tickets} exige, na trilha, dados que não são campos
     * alterados da entidade: o {@code blockReason} de uma transição e o {@code workLogId} que
     * disparou a reabertura automática (RN-312). Sem eles, o responsável veria o ticket concluído
     * voltar a "em andamento" sem explicação — e a linha do tempo é justamente onde ele buscaria a
     * resposta.
     *
     * @param extraMetadata acrescentado ao contexto padrão ({@code traceId}); nunca dado sensível
     *     (ART-084)
     */
    void record(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> extraMetadata);

    /**
     * Registra uma alteração executada pelo sistema, sem usuário (CE-S-06).
     *
     * <p>Usado por geração automática de períodos, em que o ator é um job e não uma pessoa.
     */
    void recordSystemAction(
            String action, String entityType, UUID entityId, Map<String, Object> afterState);

    /**
     * Registra uma alteração de sistema com contexto adicional.
     *
     * <p>RN-312 é auditada com {@code actorType = SYSTEM} e o {@code workLogId} disparador.
     */
    void recordSystemAction(
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> extraMetadata);

    /**
     * Trilha de uma entidade, do evento mais recente para o mais antigo.
     *
     * <p>Interface pública consumida pela linha do tempo do ticket (§9.1 de {@code tickets.md}).
     * Devolve DTO, nunca a entidade (AR-02, ART-061).
     */
    List<AuditEntry> findByEntity(String entityType, UUID entityId);
}
