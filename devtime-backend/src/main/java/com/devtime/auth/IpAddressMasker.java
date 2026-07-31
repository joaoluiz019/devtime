package com.devtime.auth;

/**
 * Máscara parcial de endereço IP para exibição (§9.2 de security.md, §5.11 de authentication.md).
 *
 * <p>O formato de referência do documento é {@code 200.***.***.42}: primeiro e último octetos
 * visíveis. O suficiente para o titular reconhecer "esta sessão é do meu escritório" sem que a
 * listagem se torne um mapa de endereços utilizável por quem obtenha acesso à tela.
 *
 * <p>Em IPv6 o critério é o mesmo aplicado a grupos: primeiro e último preservados.
 */
final class IpAddressMasker {

    private static final String REDACTED_GROUP = "***";

    private IpAddressMasker() {}

    static String mask(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        String separator = ipAddress.contains(":") ? ":" : ".";
        String[] parts = ipAddress.split(java.util.regex.Pattern.quote(separator));
        if (parts.length < 3) {
            // Valor fora do formato esperado é totalmente ocultado: mascarar parcialmente algo cuja
            // estrutura não se conhece pode preservar justamente a parte identificadora.
            return REDACTED_GROUP;
        }
        StringBuilder masked = new StringBuilder(parts[0]);
        for (int index = 1; index < parts.length - 1; index++) {
            masked.append(separator).append(REDACTED_GROUP);
        }
        return masked.append(separator).append(parts[parts.length - 1]).toString();
    }
}
