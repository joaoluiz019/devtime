package com.devtime.auth.domain;

/**
 * Finalidade de um {@link VerificationToken} (T-001-05).
 *
 * <p>O tipo participa da busca junto com o hash: um token de redefinição de senha apresentado ao
 * endpoint de verificação de e-mail não pode ser aceito, ainda que o valor esteja correto e não
 * consumido. Sem o tipo na consulta, um token de menor privilégio serviria de chave para o fluxo de
 * maior privilégio.
 */
public enum VerificationTokenType {

    /** Ativação de conta. Validade de 7 dias (§4.2 de state-machines.md). */
    EMAIL_VERIFICATION,

    /** Redefinição de senha. Validade de 1 hora, uso único (RN-461 / PW-06). */
    PASSWORD_RESET,

    /** Convite para um tenant. Validade de 7 dias (RN-457). */
    INVITATION
}
