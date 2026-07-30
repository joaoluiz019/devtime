package com.devtime.category;

import com.devtime.category.domain.CategoryExceptions;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RN-502: nome único por tenant, sem diferenciar caixa (spec 005 §27).
 *
 * <p>A verificação na aplicação existe para produzir {@code DEVTIME-2601} com o campo {@code name}
 * identificado, o que o índice único sozinho não faria — a violação de constraint chega como {@code
 * DEVTIME-2001} genérico. O índice permanece como segunda barreira contra corrida entre duas
 * requisições simultâneas (CX-01).
 *
 * <p>Acentos <b>não</b> são normalizados: "Análise" e "Analise" são categorias distintas (CX-02),
 * coerente com RN-404 em {@code 003-clients}.
 */
@Component
@RequiredArgsConstructor
public class CategoryNameUniquenessValidator {

    private final CategoryRepository repository;

    /**
     * @param excludedId identificador ignorado na verificação; nulo na criação
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2601} / {@code 409}
     */
    public void assertUnique(String name, UUID excludedId) {
        if (repository.existsByNameIgnoreCase(name, excludedId)) {
            throw CategoryExceptions.duplicateName(name); // RN-502
        }
    }
}
