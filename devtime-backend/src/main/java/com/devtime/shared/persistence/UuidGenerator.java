package com.devtime.shared.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/**
 * Geração de identificadores de entidade.
 *
 * <p>ART-010: toda chave primária é UUIDv7 (time-ordered) gerada na camada de aplicação. O banco
 * nunca gera identificador (ART-011).
 *
 * <p>A escolha de UUIDv7 sobre UUIDv4 preserva a localidade de índice B-Tree: chaves crescentes no
 * tempo concentram as inserções nas páginas mais recentes, evitando a fragmentação que o UUIDv4
 * provoca ao espalhar escritas por toda a árvore.
 */
public final class UuidGenerator {

    private UuidGenerator() {}

    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
