-- V029 — corrige o tipo de period_snapshots.checksum (defeito de V020).
--
-- `V020` declarou a coluna como CHAR(64). O PostgreSQL reporta CHAR como `bpchar`, e a validação de
-- schema do Hibernate — obrigatória por ART-054 / BR-034 (`ddl-auto = validate`) — recusa a
-- divergência contra o `String(64)` da entidade:
--
--   Schema-validation: wrong column type encountered in column [checksum] in table
--   [period_snapshots]; found [bpchar (Types#CHAR)], but expecting [varchar(64) (Types#VARCHAR)]
--
-- O defeito não aparecia porque nenhum banco havia sido migrado do zero desde `V020`; ele se
-- manifesta no critério de saída de F0 ("migration do zero em banco limpo") e impede a aplicação de
-- iniciar. Foi encontrado pela suíte de `015-attachments`, que subiu um banco limpo.
--
-- ART-053 / BR-035 / IA-03: `V020` está mesclada e **não** é alterada. A correção é aditiva.
--
-- CHAR(n) preenche com espaços à direita até n; SHA-256 em hexadecimal ocupa exatamente 64
-- caracteres, então nenhum valor existente tem preenchimento a remover e a conversão preserva o
-- conteúdo bit a bit. O `btrim` é defensivo e não altera dado íntegro.

ALTER TABLE period_snapshots
    ALTER COLUMN checksum TYPE VARCHAR(64) USING btrim(checksum);
