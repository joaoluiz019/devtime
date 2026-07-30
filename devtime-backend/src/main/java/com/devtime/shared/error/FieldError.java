package com.devtime.shared.error;

/**
 * Erro de um campo específico, exposto em {@code errors[]} (ART-072, ADR-017 EX-02).
 *
 * <p>O frontend mapeia cada entrada para o campo correspondente do formulário (FR-070, FM-06), em
 * vez de exibir a mensagem em toast — daí a necessidade do nome do campo separado da mensagem.
 *
 * @param field caminho do campo no corpo da requisição, em camelCase
 * @param message mensagem já apresentável, sem dado sensível
 */
public record FieldError(String field, String message) {}
