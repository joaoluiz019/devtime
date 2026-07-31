package com.devtime.user;

import com.devtime.user.dto.UserSummary;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface pública mínima da feature {@code 002-users}, publicada por {@code 007-tickets}.
 *
 * <p>{@code 002} ainda não foi implementada (ver "Pendências" no {@code CHANGELOG.md}). Esta
 * interface existe porque RN-304 (responsável do ticket) e RN-813 (menções em comentários) precisam
 * exibir e resolver pessoas, e AR-02 proíbe que {@code ticket}, {@code comment} ou {@code tenant}
 * alcancem a entidade {@code User} diretamente. O escopo é deliberadamente estreito: apenas leitura
 * de resumo, sem cadastro, sem papel e sem status — o ciclo de vida do usuário pertence a {@code
 * 002}.
 *
 * <p>Não é tenant-scoped: {@code users} é tabela global (ART-013). O recorte por tenant é aplicado
 * por quem chama, cruzando com {@link com.devtime.tenant.MembershipService}.
 */
public interface UserService {

    /**
     * Resumos de exibição, em uma única consulta em lote.
     *
     * @return mapa por identificador; identificadores inexistentes ficam ausentes do mapa
     */
    Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds);

    /**
     * Resumo individual, com nome de exibição substituto quando o usuário não existe mais.
     *
     * <p>RN-458: registros de um membro removido são preservados; a anonimização substitui o nome
     * exibido, não o vínculo.
     */
    UserSummary summaryOf(UUID userId);

    /**
     * Usuários cujo identificador de exibição consta em {@code handles} (§6.2 de {@code
     * specs/014-comments}).
     *
     * <p>A comparação é feita sobre {@code displayName} em minúsculas. Usuários sem {@code
     * displayName} não são alcançáveis por menção — não possuem identificador de exibição.
     */
    List<UserSummary> findByHandles(Collection<String> handles);
}
