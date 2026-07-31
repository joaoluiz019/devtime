package com.devtime.tenant;

import java.util.Set;
import java.util.UUID;

/**
 * Interface pública mínima da feature {@code 002-users}, publicada por {@code 007-tickets}.
 *
 * <p>Cobre exclusivamente o que RN-304 (responsável do ticket) e RN-813 (menções) exigem: saber
 * quem é membro <b>ativo</b> do tenant da sessão. Convite, papel, suspensão e remoção pertencem a
 * {@code 002} e não são expostos aqui.
 *
 * <p>Todas as consultas são tenant-scoped pelo filtro automático (ART-022): um usuário ativo em
 * outro tenant é indistinguível de inexistente (ART-024).
 */
public interface MembershipService {

    /**
     * RN-304: o usuário possui membership {@code ACTIVE} no tenant da sessão.
     *
     * @return {@code false} para usuário inexistente, inativo, suspenso, removido ou de outro
     *     tenant — todas as causas produzem a mesma resposta, por ART-024
     */
    boolean isActiveMember(UUID userId);

    /** Identificadores dos membros ativos do tenant, em uma única consulta (RN-813). */
    Set<UUID> activeMemberIds();
}
