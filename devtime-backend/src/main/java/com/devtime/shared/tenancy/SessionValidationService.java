package com.devtime.shared.tenancy;

import java.time.Instant;
import java.util.UUID;

/**
 * Verificação de estado da sessão a cada requisição (passos 3 e 4 de {@code permissions.md} §4.1).
 *
 * <p>A interface vive em {@code shared} e a implementação na feature {@code tenant}: é inversão de
 * dependência, não exceção a AR-01. O filtro precisa consultar organização e vínculo, mas {@code
 * shared} não pode depender de uma feature (BR-004) — então declara o contrato e recebe a
 * implementação por injeção.
 *
 * <p><b>Custo.</b> É uma consulta por requisição autenticada. §20 do spec proíbe consultar o banco
 * para <b>resolver permissão</b> (CP-07), o que continua valendo: o papel vem do token e as
 * permissões são derivadas dele em memória (TK-03). O que esta consulta responde é outra coisa — se
 * a organização foi suspensa ou o vínculo revogado desde a emissão do token —, e sem ela CE-P-09 e
 * CE-AU-07 seriam impossíveis: um membro removido continuaria operando por até 15 minutos.
 */
public interface SessionValidationService {

    /** Resultado da verificação, na ordem em que {@code permissions.md} §4.1 as aplica. */
    enum Decision {
        /** Prossegue. */
        ALLOWED,
        /** RN-007 / {@code DEVTIME-1201}: organização suspensa; apenas leitura. */
        TENANT_READ_ONLY,
        /** RN-008 / {@code DEVTIME-1202}: organização cancelada. */
        TENANT_CANCELLED,
        /** RN-459 / {@code DEVTIME-1102}: vínculo inexistente, suspenso ou removido. */
        MEMBERSHIP_INACTIVE,
        /** TK-05 / IMP-04: token emitido antes da última alteração de papel. */
        TOKEN_STALE
    }

    /**
     * @param tokenIssuedAt claim {@code iat}; comparada a {@code membership.roleChangedAt} (TK-05)
     */
    Decision validate(UUID tenantId, UUID userId, Instant tokenIssuedAt);
}
