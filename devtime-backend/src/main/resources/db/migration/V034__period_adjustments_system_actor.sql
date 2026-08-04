-- V034 — ajuste automático de sistema em period_adjustments (RN-230, FA-14).
--
-- V018 declarou applied_by NOT NULL sob o pressuposto de que todo ajuste tem autor humano
-- ("sempre o usuário autenticado"). RN-230 tem um ajuste sem autor: a expiração de saldo
-- transportado é aplicada por RolloverExpiryJob, que roda sem sessão de usuário (CE-S-06), e a
-- trilha o registra como `actorType = SYSTEM`. O javadoc de PeriodAdjustment.appliedBy já previa
-- "nulo em ajuste automático de sistema"; a coluna é que não permitia.
--
-- Correção aditiva (BR-035): V018 não é alterada. A FK para users permanece — quando há autor, ele
-- continua tendo de existir; o que muda é que a ausência de autor passa a ser representável.
--
-- Inventar um usuário de sistema seria a alternativa, e foi rejeitada: uma linha em `users` que
-- ninguém pode autenticar apareceria em toda listagem de membros e em todo relatório de autoria,
-- e a pergunta "quem aplicou este ajuste?" passaria a ter uma resposta falsa em vez de nenhuma.

ALTER TABLE period_adjustments ALTER COLUMN applied_by DROP NOT NULL;

COMMENT ON COLUMN period_adjustments.applied_by IS
    'Usuário que aplicou o ajuste; NULL apenas em ajuste automático de sistema (RN-230).';
