package com.devtime.shared.security;

/**
 * Sinaliza access token ausente, malformado, expirado ou com assinatura inválida.
 *
 * <p>{@code security.md} §14 exige que <b>todos</b> esses casos resultem na mesma resposta ao
 * cliente ({@code 401 DEVTIME-1001}), sem revelar qual verificação falhou. Por isso a mensagem
 * desta exceção nunca chega à resposta HTTP: serve apenas ao log do servidor.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String reason) {
        super(reason);
    }

    public InvalidAccessTokenException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
