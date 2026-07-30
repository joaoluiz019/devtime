package com.devtime.shared.tenancy;

/**
 * Sinaliza uso do {@link TenantContext} sem sessão inicializada.
 *
 * <p>Conforme §10 da constituição, esta condição resulta em {@code 500} com log {@code ERROR} e
 * alerta — <b>nunca</b> em degradação para "todos os tenants".
 */
public class TenantContextNotInitializedException extends IllegalStateException {

    public TenantContextNotInitializedException() {
        super("TenantContext não inicializado");
    }
}
