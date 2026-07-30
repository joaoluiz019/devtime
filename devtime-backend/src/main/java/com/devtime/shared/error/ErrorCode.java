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

    // ── Contratos e períodos · DEVTIME-2200–2299 (contracts.md §14) ──────────────────────────
    /** RN-201 / RN-405: cliente inexistente, de outro tenant ou inativo. */
    CONTRACT_CLIENT_INVALID(
            "DEVTIME-2201", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.clientInvalid"),
    /** RN-202: {@code monthlyMinutes} fora de 1–44.640. */
    CONTRACT_MONTHLY_MINUTES_INVALID(
            "DEVTIME-2202",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "error.contract.monthlyMinutesInvalid"),
    /** RN-203: {@code billingDay} fora de 1–28. */
    CONTRACT_BILLING_DAY_INVALID(
            "DEVTIME-2203", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.billingDayInvalid"),
    /** RN-204: {@code endDate} anterior a {@code startDate}. */
    CONTRACT_DATE_RANGE_INVALID(
            "DEVTIME-2204", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.dateRangeInvalid"),
    /** RN-205: exclusão permitida apenas em {@code DRAFT}. */
    CONTRACT_DELETE_RESTRICTED(
            "DEVTIME-2205", HttpStatus.CONFLICT, "error.contract.deleteRestricted"),
    /** INV-CTR-01: código já usado no tenant. */
    CONTRACT_CODE_DUPLICATED("DEVTIME-2206", HttpStatus.CONFLICT, "error.contract.codeDuplicated"),
    /** RN-207: alteração de {@code monthlyMinutes} atingiria período fechado. */
    CONTRACT_CHANGE_AFFECTS_CLOSED_PERIOD(
            "DEVTIME-2207", HttpStatus.CONFLICT, "error.contract.changeAffectsClosedPeriod"),
    /** RN-208: alteração de {@code billingDay} com horas lançadas no período aberto. */
    CONTRACT_BILLING_DAY_LOCKED(
            "DEVTIME-2208", HttpStatus.CONFLICT, "error.contract.billingDayLocked"),
    /** INV-CTR-04: política {@code CAPPED} sem teto de transporte. */
    CONTRACT_ROLLOVER_CAP_REQUIRED(
            "DEVTIME-2209", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.rolloverCapRequired"),
    /** INV-CTR-03: campos incompatíveis com {@code HOURLY_OPEN}. */
    CONTRACT_TYPE_INCOHERENT(
            "DEVTIME-2210", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.typeIncoherent"),
    /** Ativação com campos obrigatórios do tipo ausentes. */
    CONTRACT_ACTIVATION_INCOMPLETE(
            "DEVTIME-2211", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.activationIncomplete"),
    /** RN-213 de contracts.md §8.4: data de término inválida. */
    CONTRACT_END_DATE_INVALID(
            "DEVTIME-2213", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.endDateInvalid"),
    /** RN-215: justificativa obrigatória com no mínimo 10 caracteres. */
    JUSTIFICATION_REQUIRED(
            "DEVTIME-2215", HttpStatus.UNPROCESSABLE_ENTITY, "error.justification.required"),

    // ── Clientes · DEVTIME-2400–2449 (clients.md §13) ────────────────────────────────────────
    /** RN-401: cliente com contrato {@code ACTIVE} ou {@code SUSPENDED}. */
    CLIENT_DELETE_RESTRICTED("DEVTIME-2401", HttpStatus.CONFLICT, "error.client.deleteRestricted"),
    /** RN-402: CPF/CNPJ reprovado nos dígitos verificadores. */
    CLIENT_DOCUMENT_INVALID(
            "DEVTIME-2402", HttpStatus.UNPROCESSABLE_ENTITY, "error.client.documentInvalid"),
    /** RN-403: documento já cadastrado no tenant. */
    CLIENT_DOCUMENT_DUPLICATED(
            "DEVTIME-2403", HttpStatus.CONFLICT, "error.client.documentDuplicated"),
    /** RN-404: nome já cadastrado no tenant. */
    CLIENT_NAME_DUPLICATED("DEVTIME-2404", HttpStatus.CONFLICT, "error.client.nameDuplicated"),
    /** RN-405: cliente inativo não aceita novos contratos. */
    CLIENT_INACTIVE("DEVTIME-2405", HttpStatus.UNPROCESSABLE_ENTITY, "error.client.inactive"),
    /** RN-406: mais de um contato marcado como principal. */
    CONTACT_PRIMARY_CONFLICT(
            "DEVTIME-2406", HttpStatus.UNPROCESSABLE_ENTITY, "error.contact.primaryConflict"),
    /** RN-407: inativação com contratos ativos exige confirmação explícita. */
    CLIENT_DEACTIVATION_CONFIRMATION_REQUIRED(
            "DEVTIME-2407",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "error.client.deactivationConfirmationRequired"),
    /** clients.md §10.1: limite de 20 contatos por cliente. */
    CONTACT_LIMIT_REACHED(
            "DEVTIME-2408", HttpStatus.UNPROCESSABLE_ENTITY, "error.contact.limitReached"),

    // ── Categorias · DEVTIME-2600–2649 (users.md §8) ─────────────────────────────────────────
    /** RN-502: nome já usado no tenant, sem diferenciar caixa. */
    CATEGORY_NAME_DUPLICATED("DEVTIME-2601", HttpStatus.CONFLICT, "error.category.nameDuplicated"),
    /** RN-503: categoria de sistema não é excluível, apenas inativável e renomeável. */
    CATEGORY_SYSTEM_PROTECTED(
            "DEVTIME-2602", HttpStatus.CONFLICT, "error.category.systemProtected"),
    /** RN-505: exclusão com registros vinculados exige categoria substituta. */
    CATEGORY_REPLACEMENT_REQUIRED(
            "DEVTIME-2603", HttpStatus.CONFLICT, "error.category.replacementRequired"),
    /** users.md §8.3: substituta inexistente, inativa ou igual à excluída. */
    CATEGORY_REPLACEMENT_INVALID(
            "DEVTIME-2605", HttpStatus.UNPROCESSABLE_ENTITY, "error.category.replacementInvalid"),

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
