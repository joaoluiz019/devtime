-- V030 — cancelamento de organização (specs/002-users §11, RN-008).
--
-- Lacuna de documentação reportada: `database.md` §7.1 e `entities.md` §6.1 não preveem coluna
-- alguma para o instante do cancelamento, mas `users.md` §6.3 exige devolver `dataRetainedUntil`
-- e `exportAvailableUntil`, e o `TenantPurgeJob` (spec §22.4) precisa selecionar os tenants
-- cancelados há mais de 30 dias. Derivar o instante de `updated_at` seria incorreto: qualquer
-- alteração posterior — inclusive a própria exportação — reiniciaria a retenção.
--
-- Aditiva e nullable (MG-03): a coluna só é preenchida na transição para CANCELLED, e V002 não é
-- alterada (ART-053, BR-035).

ALTER TABLE tenants ADD COLUMN cancelled_at        TIMESTAMPTZ  NULL;
ALTER TABLE tenants ADD COLUMN purge_scheduled_at  TIMESTAMPTZ  NULL;
ALTER TABLE tenants ADD COLUMN cancellation_reason VARCHAR(500) NULL;

-- Os três campos são preenchidos juntos, na mesma transição. `cancellation_reason` é opcional
-- (users.md §6.3), por isso fica fora da constraint.
ALTER TABLE tenants ADD CONSTRAINT ck_tenants_cancellation_pair
    CHECK ((cancelled_at IS NULL) = (purge_scheduled_at IS NULL));

-- RN-008: sustenta a varredura do TenantPurgeJob, que percorre apenas tenants cancelados cuja
-- retenção venceu. Parcial porque é a única leitura desta coluna em todo o sistema.
CREATE INDEX idx_tenants_purge_scheduled
    ON tenants (purge_scheduled_at)
    WHERE deleted_at IS NULL AND status = 'CANCELLED';
