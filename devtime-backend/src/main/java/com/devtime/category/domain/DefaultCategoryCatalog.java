package com.devtime.category.domain;

import java.util.List;

/**
 * Catálogo das 9 categorias criadas com o tenant (RN-501, entities.md §6.10).
 *
 * <p>Os valores são normativos: nome, cor, ícone e {@code billableByDefault} reproduzem exatamente
 * a tabela de entities.md §6.10. A ordem da lista define o {@code sortOrder} inicial.
 *
 * <p>O seed existe porque exigir que o usuário modele uma taxonomia antes de registrar a primeira
 * hora é barreira de adoção desnecessária — ele quer registrar horas, não classificar trabalho.
 */
public final class DefaultCategoryCatalog {

    /**
     * Definição de uma categoria de sistema.
     *
     * @param name nome exibido
     * @param color cor hexadecimal usada em gráficos
     * @param icon ícone PrimeIcons
     * @param billableByDefault valor inicial de {@code billable} no work log (RN-112)
     */
    public record DefaultCategory(
            String name, String color, String icon, boolean billableByDefault) {}

    private static final List<DefaultCategory> ENTRIES =
            List.of(
                    new DefaultCategory("Desenvolvimento", "#6366F1", "pi-code", true),
                    new DefaultCategory("Correção de Bug", "#EF4444", "pi-wrench", true),
                    new DefaultCategory("Reunião", "#F59E0B", "pi-users", true),
                    new DefaultCategory("Suporte", "#10B981", "pi-headphones", true),
                    new DefaultCategory("Análise / Planejamento", "#8B5CF6", "pi-compass", true),
                    new DefaultCategory("Code Review", "#06B6D4", "pi-eye", true),
                    new DefaultCategory("Documentação", "#64748B", "pi-file", true),
                    new DefaultCategory("Infraestrutura / Deploy", "#0EA5E9", "pi-server", true),
                    new DefaultCategory(
                            "Interno (não faturável)", "#94A3B8", "pi-briefcase", false));

    private DefaultCategoryCatalog() {}

    public static List<DefaultCategory> entries() {
        return ENTRIES;
    }
}
