package com.devtime.auth;

/**
 * Recuperação de senha (RN-461, PW-06, PW-07, T-001-27).
 *
 * <p>SG-02 / AC-001-32: a solicitação responde sempre da mesma forma, com ou sem conta
 * correspondente. É o que impede que o endpoint funcione como verificador de cadastro.
 */
public interface PasswordResetService {

    /**
     * Solicita a redefinição. Sempre bem-sucedida do ponto de vista do chamador.
     *
     * <p>Não lança para e-mail inexistente, conta bloqueada ou não verificada: qualquer diferença
     * de comportamento — inclusive uma exceção transformada em resposta distinta — reabriria o
     * canal de enumeração.
     */
    void requestReset(String email);

    /**
     * Redefine a senha com o token recebido por e-mail.
     *
     * <p>Efeitos (§5.8): {@code passwordChangedAt} atualizado (TK-04), <b>todas</b> as sessões
     * revogadas — inclusive a que solicitou (CE-AU-05) — e conta desbloqueada (CX-07).
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-1007} para token
     *     expirado ou já usado; {@code DEVTIME-2451} para senha fora da política
     */
    void resetPassword(String rawToken, String newPassword);
}
