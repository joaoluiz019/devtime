package com.devtime.shared.tenancy;

/**
 * Sinaliza tentativa de gravar entidade em tenant diferente do da sessão.
 *
 * <p>{@code security.md} §14 classifica tentativa de acesso cross-tenant como evento de log {@code
 * ERROR} com alerta crítico. A exceção é intencionalmente não-recuperável: nenhuma camada acima
 * deve tratá-la e prosseguir.
 */
public class CrossTenantWriteException extends IllegalStateException {

    public CrossTenantWriteException(String entityType) {
        super("Tentativa de escrita cross-tenant em " + entityType);
    }
}
