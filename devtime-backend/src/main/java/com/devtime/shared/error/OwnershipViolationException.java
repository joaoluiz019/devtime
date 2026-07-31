package com.devtime.shared.error;

import java.util.Map;

/**
 * Violação de propriedade sobre um recurso do próprio tenant (permissions.md §8, {@code
 * DEVTIME-1103}).
 *
 * <p>Distinta de {@link EntityNotFoundException} por decisão explícita: o recurso <b>é</b> do
 * tenant do requisitante e sua existência já é conhecida por ele — negar com {@code 403} não revela
 * nada novo. É apenas para recurso de <b>outro tenant</b> que ART-024 exige {@code 404}, e essa
 * verificação ocorre antes (ordem 6 e 7 da §4.1 de permissions.md).
 *
 * <p>Os detalhes carregam apenas o tipo da entidade: nome, autor e conteúdo não acrescentam nada ao
 * cliente e ampliariam o que aparece em log de proxy.
 */
public class OwnershipViolationException extends BusinessRuleException {

    public OwnershipViolationException(String entityType) {
        super(
                ErrorCode.OWNERSHIP_VIOLATION,
                Map.of("entityType", entityType),
                "Você só pode alterar seus próprios registros: " + entityType);
    }
}
