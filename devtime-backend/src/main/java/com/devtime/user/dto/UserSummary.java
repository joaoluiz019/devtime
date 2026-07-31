package com.devtime.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Resumo de exibição de uma pessoa (tickets.md §5 e §10.1).
 *
 * <p>BR-100/BR-102: {@code record} imutável, sem entidade JPA. Nenhum campo sensível: {@code
 * email}, {@code passwordHash} e {@code status} estão deliberadamente ausentes — este DTO existe
 * para <b>exibir</b> um nome ao lado de um ticket ou comentário, e ampliá-lo transformaria toda
 * listagem de tickets em um diretório de pessoas.
 *
 * @param id identificador do usuário
 * @param name nome exibido; {@code Usuário Removido} quando o cadastro não existe mais (RN-458)
 * @param handle identificador de exibição usado em menções, em minúsculas; nulo quando ausente
 * @param avatarUrl imagem de perfil, quando houver
 */
@Schema(name = "UserSummary")
public record UserSummary(UUID id, String name, String handle, String avatarUrl) {

    /** RN-458: o vínculo histórico é preservado; apenas o nome exibido é substituído. */
    public static final String REMOVED_USER_NAME = "Usuário Removido";

    public static UserSummary removed(UUID id) {
        return new UserSummary(id, REMOVED_USER_NAME, null, null);
    }
}
