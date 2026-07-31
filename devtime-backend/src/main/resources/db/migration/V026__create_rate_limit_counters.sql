-- V026 — rate_limit_counters (security.md §8.1, ART-073, T-001-38).
--
-- "Implementação: contador em banco no MVP; migração para Redis em F6" (security.md §8.1). Não é
-- entidade de domínio: não possui exclusão lógica, auditoria nem versão, e por isso é manipulada
-- por JdbcTemplate e não por JPA — o mesmo tratamento dado a `shedlock` em V007.
--
-- A janela é fixa (não deslizante): `window_started_at` marca o início do intervalo corrente e o
-- contador é reiniciado quando a janela expira. Uma janela deslizante exigiria guardar cada
-- tentativa individualmente, multiplicando escritas em endpoints que são justamente os mais
-- atacados.

CREATE TABLE rate_limit_counters (
    -- Escopo do limite: "<endpoint>:<dimensão>:<valor>". O valor de e-mail entra como hash
    -- (ART-084) para que a tabela não se torne uma lista de endereços cadastrados.
    bucket_key        VARCHAR(200) NOT NULL,
    window_started_at TIMESTAMPTZ  NOT NULL,
    hit_count         INTEGER      NOT NULL,
    CONSTRAINT pk_rate_limit_counters PRIMARY KEY (bucket_key),
    CONSTRAINT ck_rate_limit_counters_hit_count CHECK (hit_count >= 0)
);

-- Sustenta a limpeza periódica das janelas encerradas.
CREATE INDEX idx_rate_limit_counters_window ON rate_limit_counters (window_started_at);
