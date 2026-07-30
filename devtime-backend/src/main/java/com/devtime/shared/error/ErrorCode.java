package com.devtime.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Códigos de erro estáveis {@code DEVTIME-XXXX} (ART-113, ADR-017 EX-03).
 *
 * <p>Um código aposentado <b>nunca</b> é reutilizado: clientes tratam o código programaticamente, e
 * a reutilização silenciosamente mudaria o significado de uma condição já integrada.
 *
 * <p>Este enum contém apenas os códigos transversais que a fundação técnica (F0) precisa: faixas de
 * autenticação, autorização, tenancy, validação genérica e infraestrutura, conforme §6 da
 * constituição. Os códigos específicos de cada domínio são registrados pela feature que os introduz
 * (EX-13) — por exemplo, os códigos de autenticação {@code DEVTIME-1003} a {@code DEVTIME-1012} são
 * responsabilidade da tarefa T-001-34.
 *
 * <p>A chave de mensagem existe porque EX-10 exige que a apresentação passe por i18n: o código é o
 * identificador estável, o texto é apresentação e pode mudar sem quebrar o contrato.
 */
@Getter
public enum ErrorCode {

    // ── Autenticação e sessão · DEVTIME-1000–1099 ────────────────────────────────────────────
    /**
     * Token ausente, malformado, expirado ou com assinatura inválida.
     *
     * <p>Deliberadamente único para todas essas causas: distinguir revelaria detalhe de
     * implementação e permitiria sondar a validade de tokens (security.md §14).
     */
    AUTHENTICATION_REQUIRED(
            "DEVTIME-1001", HttpStatus.UNAUTHORIZED, "error.authentication.required"),
    TENANT_NOT_SELECTED("DEVTIME-1002", HttpStatus.UNAUTHORIZED, "error.tenant.notSelected"),

    // ── Autorização e permissões · DEVTIME-1100–1199 ─────────────────────────────────────────
    PERMISSION_DENIED("DEVTIME-1101", HttpStatus.FORBIDDEN, "error.permission.denied"),
    MEMBERSHIP_INACTIVE("DEVTIME-1102", HttpStatus.FORBIDDEN, "error.membership.inactive"),
    OWNERSHIP_VIOLATION("DEVTIME-1103", HttpStatus.FORBIDDEN, "error.ownership.violation"),
    ADMIN_OVER_OWNER("DEVTIME-1104", HttpStatus.FORBIDDEN, "error.admin.overOwner"),

    // ── Tenancy · DEVTIME-1200–1299 ──────────────────────────────────────────────────────────
    /** RN-001: operação fora do tenant do usuário autenticado. */
    CROSS_TENANT_OPERATION("DEVTIME-1200", HttpStatus.FORBIDDEN, "error.tenant.crossTenant"),
    TENANT_SUSPENDED("DEVTIME-1201", HttpStatus.FORBIDDEN, "error.tenant.suspended"),
    TENANT_CANCELLED("DEVTIME-1202", HttpStatus.FORBIDDEN, "error.tenant.cancelled"),

    // ── Validação genérica · DEVTIME-2000–2099 ───────────────────────────────────────────────
    VALIDATION_FAILED("DEVTIME-2000", HttpStatus.BAD_REQUEST, "error.validation.failed"),
    /** Violação de unicidade, mapeada por nome de constraint (EX-08). */
    UNIQUENESS_VIOLATION("DEVTIME-2001", HttpStatus.CONFLICT, "error.uniqueness.violation"),
    /**
     * RN-002 / ART-024: recurso inexistente <b>ou</b> de outro tenant.
     *
     * <p>O mesmo código para ambos é o ponto central do isolamento: um código distinto para
     * "existe, mas é de outro tenant" confirmaria a existência do recurso.
     */
    RESOURCE_NOT_FOUND("DEVTIME-2002", HttpStatus.NOT_FOUND, "error.resource.notFound"),
    /** RN-011: alteração de campo imutável (🔒 em entities.md). */
    IMMUTABLE_FIELD("DEVTIME-2003", HttpStatus.UNPROCESSABLE_ENTITY, "error.field.immutable"),
    /** RN-004 / ART-052: conflito de concorrência otimista. */
    VERSION_CONFLICT("DEVTIME-2004", HttpStatus.CONFLICT, "error.version.conflict"),
    DELETE_RESTRICTED("DEVTIME-2005", HttpStatus.CONFLICT, "error.delete.restricted"),
    /** RN-012 / ART-073: {@code size} acima do máximo de 100. */
    PAGE_SIZE_EXCEEDED("DEVTIME-2006", HttpStatus.BAD_REQUEST, "error.page.sizeExceeded"),
    /** ART-074: repetição de {@code Idempotency-Key} com corpo diferente. */
    IDEMPOTENCY_CONFLICT("DEVTIME-2007", HttpStatus.CONFLICT, "error.idempotency.conflict"),
    /** Transição de estado inválida; a resposta inclui {@code availableTransitions} (EX-09). */
    INVALID_STATE_TRANSITION("DEVTIME-2010", HttpStatus.CONFLICT, "error.state.invalidTransition"),
    TERMINAL_STATE("DEVTIME-2011", HttpStatus.CONFLICT, "error.state.terminal"),

    // ── Infraestrutura e erros inesperados · DEVTIME-9000–9099 ───────────────────────────────
    /**
     * Qualquer exceção não prevista (EX-04).
     *
     * <p>O comportamento padrão precisa ser fechar, não abrir: uma exceção nova produz erro
     * genérico e log completo, nunca uma resposta com detalhe vazado.
     */
    UNEXPECTED("DEVTIME-9001", HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected"),
    RATE_LIMIT_EXCEEDED("DEVTIME-9002", HttpStatus.TOO_MANY_REQUESTS, "error.rateLimit.exceeded");

    private final String code;
    private final HttpStatus defaultStatus;
    private final String messageKey;

    ErrorCode(String code, HttpStatus defaultStatus, String messageKey) {
        this.code = code;
        this.defaultStatus = defaultStatus;
        this.messageKey = messageKey;
    }
}
