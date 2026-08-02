package com.devtime.attachment;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Endereço de origem da requisição, para as trilhas de §18.
 *
 * <p>{@code getRemoteAddr()} e <b>não</b> {@code X-Forwarded-For}, pelo mesmo motivo já registrado
 * em {@code AuthController}: headers informados pelo cliente não podem escolher o próprio
 * identificador. O valor confiável atrás de proxy reverso é responsabilidade da borda, que o
 * repassa por {@code RemoteIpValve} — mecanismo que substitui o próprio {@code getRemoteAddr()}.
 *
 * <p>Aqui a consequência é mais séria que um limite de taxa: o IP registrado em {@code
 * ATTACHMENT_SCAN_INFECTED} é a base de uma investigação de segurança, e um valor escolhido por
 * quem enviou o arquivo apontaria a investigação para outra pessoa.
 */
final class RequestIpResolver {

    private RequestIpResolver() {}

    static String resolve(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }
}
