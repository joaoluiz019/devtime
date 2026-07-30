-- V001 — Extensões do PostgreSQL (database.md §8.1, fase F0).

-- Funções de hash e geração de bytes aleatórios.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Necessária para as constraints EXCLUDE que combinam igualdade e sobreposição de range
-- (ex.: ex_periods_no_overlap em contract_periods, §7.6).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Necessária para os índices GIN de busca por similaridade de texto
-- (ex.: idx_clients_tenant_name_trgm, §7.4).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
