package com.devtime.client.domain;

/**
 * Tipo do documento fiscal do cliente (entities.md §6.4).
 *
 * <p>{@code OTHER} existe para clientes estrangeiros ou documentos que não são CPF nem CNPJ: a
 * validação de dígitos verificadores (RN-402) só se aplica aos dois primeiros.
 */
public enum DocumentType {
    CPF,
    CNPJ,
    OTHER
}
