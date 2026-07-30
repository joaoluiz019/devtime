package com.devtime.shared.pagination;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope de listagem paginada (ART-073).
 *
 * <p>Substitui a serialização direta de {@code Page} do Spring Data, cujo formato JSON não é
 * estável entre versões e expõe estrutura interna do framework no contrato da API.
 *
 * @param content itens da página
 * @param page número da página, base zero
 * @param size tamanho solicitado
 * @param totalElements total de itens que satisfazem o filtro
 * @param totalPages total de páginas
 * @param last se esta é a última página
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    /** Converte o conteúdo preservando os metadados de paginação. */
    public static <S, T> PageResponse<T> of(
            Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
