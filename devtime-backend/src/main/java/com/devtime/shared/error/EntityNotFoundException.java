package com.devtime.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * Recurso inexistente ou pertencente a outro tenant.
 *
 * <p>ART-024 / RN-002 / TI-04: as duas situações produzem exatamente a mesma resposta ({@code 404
 * DEVTIME-2002}). Retornar {@code 403} para recurso de outro tenant confirmaria sua existência,
 * permitindo enumerar dados entre tenants.
 *
 * <p>Os detalhes carregam apenas o <b>tipo</b> da entidade, nunca o identificador solicitado: TI-05
 * exige que a resposta de tenant errado seja indistinguível da de recurso inexistente, e ecoar o id
 * não agrega informação ao cliente (que o enviou) enquanto amplia o que aparece em logs de proxy.
 */
public class EntityNotFoundException extends BusinessRuleException {

    private EntityNotFoundException(String entityType, UUID id) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                Map.of("entityType", entityType),
                "Recurso não encontrado: " + entityType + " " + id);
    }

    public static EntityNotFoundException of(Class<?> entityType, UUID id) {
        return new EntityNotFoundException(entityType.getSimpleName(), id);
    }
}
