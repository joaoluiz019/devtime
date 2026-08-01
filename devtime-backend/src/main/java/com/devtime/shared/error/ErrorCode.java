package com.devtime.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Códigos de erro estáveis {@code DEVTIME-XXXX} (ART-113, ADR-017 EX-03).
 *
 * <p>Um código aposentado <b>nunca</b> é reutilizado: clientes tratam o código programaticamente, e
 * a reutilização silenciosamente mudaria o significado de uma condição já integrada.
 *
 * <p>Os códigos específicos de cada domínio são registrados pela feature que os introduz (EX-13).
 * Os códigos de autenticação {@code DEVTIME-1003} a {@code DEVTIME-1012} e {@code DEVTIME-2451} a
 * {@code DEVTIME-2459} foram registrados pela feature 001 (T-001-34) e seguem a tabela consolidada
 * de {@code docs/04-api/authentication.md} §8, que é normativa sobre o contrato de erro da API.
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
    /** INV-USR-04: autenticou, mas não possui membership ativo em nenhum tenant. */
    NO_ACTIVE_MEMBERSHIP("DEVTIME-1003", HttpStatus.FORBIDDEN, "error.membership.noneActive"),
    /** Cookie de refresh ausente, desconhecido, revogado ou expirado (CX-06). */
    REFRESH_TOKEN_INVALID("DEVTIME-1004", HttpStatus.UNAUTHORIZED, "error.refreshToken.invalid"),
    /** RN-005 / RT-04: token rotacionado reapresentado; toda a cadeia é revogada. */
    REFRESH_TOKEN_REUSE_DETECTED(
            "DEVTIME-1005", HttpStatus.UNAUTHORIZED, "error.refreshToken.reuseDetected"),
    /** RN-453: 5 falhas em 15 minutos bloqueiam a conta por 30 minutos. */
    ACCOUNT_LOCKED("DEVTIME-1006", HttpStatus.LOCKED, "error.account.locked"),
    /** RN-461: token de redefinição expirado (1 hora) ou já consumido. */
    PASSWORD_RESET_TOKEN_INVALID(
            "DEVTIME-1007", HttpStatus.GONE, "error.passwordReset.tokenInvalid"),
    /** §4.2 de state-machines.md: login com conta em {@code PENDING_ACTIVATION}. */
    EMAIL_NOT_VERIFIED("DEVTIME-1008", HttpStatus.FORBIDDEN, "error.email.notVerified"),
    /** Token de verificação emitido há mais de 7 dias. */
    VERIFICATION_TOKEN_EXPIRED("DEVTIME-1009", HttpStatus.GONE, "error.verification.tokenExpired"),
    /** Token de verificação desconhecido — distinto de expirado, por exigência de §5.6. */
    VERIFICATION_TOKEN_INVALID(
            "DEVTIME-1010", HttpStatus.NOT_FOUND, "error.verification.tokenInvalid"),
    /** PW-05: alteração de senha com senha atual incorreta. */
    CURRENT_PASSWORD_INCORRECT(
            "DEVTIME-1011", HttpStatus.UNPROCESSABLE_ENTITY, "error.password.currentIncorrect"),
    /** {@code authentication.md} §5.9: nova senha igual à atual. */
    PASSWORD_UNCHANGED("DEVTIME-1012", HttpStatus.UNPROCESSABLE_ENTITY, "error.password.unchanged"),

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

    // ── Conta e organização · DEVTIME-2450–2499 (authentication.md §8) ───────────────────────
    /** RN-451 / PW-02: senha fora da política mínima. */
    PASSWORD_POLICY_VIOLATION(
            "DEVTIME-2451", HttpStatus.UNPROCESSABLE_ENTITY, "error.password.policyViolation"),
    /** RN-452 / INV-USR-01: e-mail já pertence a um usuário não excluído. */
    EMAIL_ALREADY_REGISTERED("DEVTIME-2452", HttpStatus.CONFLICT, "error.email.alreadyRegistered"),
    /** RN-457: convite expirado (7 dias) ou invalidado por reenvio. */
    INVITATION_EXPIRED("DEVTIME-2457", HttpStatus.GONE, "error.invitation.expired"),
    /** Convite desconhecido ou revogado. */
    INVITATION_INVALID("DEVTIME-2458", HttpStatus.NOT_FOUND, "error.invitation.invalid"),
    /** {@code authentication.md} §5.12: já existe membership do usuário neste tenant. */
    ALREADY_MEMBER("DEVTIME-2459", HttpStatus.CONFLICT, "error.membership.alreadyMember"),

    // ── Work logs e classificação · DEVTIME-2100–2199 (worklogs.md) ─────────────────────────
    /** RN-101: registro de horas sem ticket. Não existe hora sem explicação. */
    WORKLOG_TICKET_REQUIRED(
            "DEVTIME-2100", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.ticketRequired"),
    /**
     * RN-102: sessões do mesmo usuário se sobrepõem.
     *
     * <p>Os detalhes carregam o registro conflitante: "já existe um registro neste intervalo" só é
     * acionável se o usuário puder ir até ele.
     */
    WORKLOG_OVERLAP("DEVTIME-2102", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.overlap"),
    /** RN-103: {@code grossMinutes} acima de 1.440. */
    WORKLOG_SESSION_TOO_LONG(
            "DEVTIME-2103", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.sessionTooLong"),
    /**
     * RN-104: categoria inexistente, de outro tenant ou inativa.
     *
     * <p>Registrado aqui por 007-tickets, que é a primeira feature a referenciar uma categoria em
     * campo de escrita ({@code defaultCategoryId}). O work log em si chega com 008.
     */
    CATEGORY_INVALID_OR_INACTIVE(
            "DEVTIME-2104", HttpStatus.UNPROCESSABLE_ENTITY, "error.category.invalidOrInactive"),
    /** RN-105 / RN-158: descrição fora de 3–2.000 caracteres após aparar. */
    WORKLOG_DESCRIPTION_INVALID(
            "DEVTIME-2105", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.descriptionInvalid"),
    /** RN-107: nenhum período de contrato contém a data de trabalho. */
    WORKLOG_NO_PERIOD_FOR_DATE(
            "DEVTIME-2107", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.noPeriodForDate"),
    /** RN-114: {@code endedAt} menor ou igual a {@code startedAt}. */
    WORKLOG_RANGE_INVALID(
            "DEVTIME-2114", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.rangeInvalid"),
    /** RN-115: tempo líquido zero ou negativo. Registro vazio polui o relatório. */
    WORKLOG_NET_MINUTES_INVALID(
            "DEVTIME-2115", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.netMinutesInvalid"),
    /** RN-116 / RN-157: {@code pausedMinutes} fora de {@code [0, grossMinutes)}. */
    WORKLOG_PAUSED_MINUTES_INVALID(
            "DEVTIME-2116", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.pausedMinutesInvalid"),
    /** RN-117: {@code startedAt} fora da vigência do contrato. */
    WORKLOG_OUTSIDE_CONTRACT_VALIDITY(
            "DEVTIME-2117",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "error.workLog.outsideContractValidity"),
    /** RN-118: {@code endedAt} no futuro além da tolerância de 2 minutos. */
    WORKLOG_ENDED_IN_FUTURE(
            "DEVTIME-2118", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.endedInFuture"),
    /** RN-119: data de trabalho futura sem {@code allowFutureWorkLogs}. */
    WORKLOG_FUTURE_DATE_NOT_ALLOWED(
            "DEVTIME-2119", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.futureDateNotAllowed"),
    /** RN-120: fora da janela retroativa e sem papel {@code ADMIN}/{@code OWNER}. */
    WORKLOG_RETROACTIVE_LIMIT(
            "DEVTIME-2120", HttpStatus.UNPROCESSABLE_ENTITY, "error.workLog.retroactiveLimit"),
    /** RN-121 / INV-WKL-07: registro de período fechado é imutável até a reabertura formal. */
    WORKLOG_LOCKED("DEVTIME-2121", HttpStatus.CONFLICT, "error.workLog.locked"),
    /** RN-124: mover a data para um período fechado alteraria um relatório já emitido. */
    WORKLOG_PERIOD_TRANSFER_BLOCKED(
            "DEVTIME-2124", HttpStatus.CONFLICT, "error.workLog.periodTransferBlocked"),

    // ── Cronômetro · DEVTIME-2150–2199 (worklogs.md §9 a §12) ────────────────────────────────
    /** RN-150 / INV-TMR-01: já existe cronômetro ativo do usuário, em qualquer tenant. */
    TIMER_ALREADY_ACTIVE("DEVTIME-2150", HttpStatus.CONFLICT, "error.timer.alreadyActive"),
    /** RN-153: pausar exige {@code RUNNING}. */
    TIMER_NOT_RUNNING("DEVTIME-2153", HttpStatus.CONFLICT, "error.timer.notRunning"),
    /** RN-155: retomar exige {@code PAUSED}. */
    TIMER_NOT_PAUSED("DEVTIME-2155", HttpStatus.CONFLICT, "error.timer.notPaused"),
    /** RN-165: janela de 7 dias para recuperar um cronômetro abandonado. */
    TIMER_NOT_RECOVERABLE("DEVTIME-2165", HttpStatus.CONFLICT, "error.timer.notRecoverable"),

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
    /** RN-231: {@code OveragePolicy = BLOCK} e o registro ultrapassaria o saldo disponível. */
    PERIOD_BALANCE_INSUFFICIENT(
            "DEVTIME-2220", HttpStatus.UNPROCESSABLE_ENTITY, "error.period.balanceInsufficient"),
    /**
     * RN-232: <b>aviso</b>, não erro.
     *
     * <p>Acompanha um {@code 201 Created} em {@code warnings[]}. O status declarado aqui só existe
     * porque o enum o exige; este código nunca é o status de uma resposta.
     */
    PERIOD_OVERAGE_WARNING(
            "DEVTIME-2221", HttpStatus.UNPROCESSABLE_ENTITY, "error.period.overageWarning"),
    /** RN-235: ajuste só em período {@code OPEN} ou {@code REOPENED}. */
    PERIOD_NOT_ADJUSTABLE("DEVTIME-2235", HttpStatus.CONFLICT, "error.period.notAdjustable"),
    /** RN-236 / INV-ADJ-01: ajuste é imutável; correção se faz por estorno. */
    ADJUSTMENT_IMMUTABLE("DEVTIME-2236", HttpStatus.CONFLICT, "error.adjustment.immutable"),
    /** RN-237: o ajuste deixaria {@code availableMinutes} negativo. */
    ADJUSTMENT_WOULD_MAKE_BALANCE_NEGATIVE(
            "DEVTIME-2237",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "error.adjustment.wouldMakeBalanceNegative"),
    /** RN-239: fechamento antes do {@code endDate} sem confirmação explícita. */
    PERIOD_CLOSE_TOO_EARLY("DEVTIME-2239", HttpStatus.CONFLICT, "error.period.closeTooEarly"),
    /** RN-240: existe cronômetro ativo — inclusive {@code PAUSED} — no período. */
    PERIOD_HAS_ACTIVE_TIMER("DEVTIME-2240", HttpStatus.CONFLICT, "error.period.hasActiveTimer"),
    /** RN-244: reabertura exige que nenhum período posterior esteja fechado. */
    PERIOD_LATER_ALREADY_CLOSED(
            "DEVTIME-2244", HttpStatus.CONFLICT, "error.period.laterAlreadyClosed"),

    // ── Tickets · DEVTIME-2300–2399 (tickets.md §13) ─────────────────────────────────────────
    /** RN-301: contrato ausente, inexistente no tenant ou inválido para o ticket. */
    TICKET_CONTRACT_REQUIRED(
            "DEVTIME-2301", HttpStatus.UNPROCESSABLE_ENTITY, "error.ticket.contractRequired"),
    /** RN-303: título fora de 3–200 caracteres. */
    TICKET_TITLE_INVALID(
            "DEVTIME-2303", HttpStatus.UNPROCESSABLE_ENTITY, "error.ticket.titleInvalid"),
    /** RN-304: responsável inexistente ou sem membership {@code ACTIVE} no tenant. */
    TICKET_ASSIGNEE_INVALID(
            "DEVTIME-2304", HttpStatus.UNPROCESSABLE_ENTITY, "error.ticket.assigneeInvalid"),
    /** RN-305 / INV-TCK-02: movimentação de contrato com work logs registrados. */
    TICKET_CONTRACT_MOVE_RESTRICTED(
            "DEVTIME-2305", HttpStatus.CONFLICT, "error.ticket.contractMoveRestricted"),
    /** RN-306: contrato {@code ENDED}/{@code CANCELLED} não aceita registro de horas. */
    CONTRACT_NOT_ACCEPTING_WORK(
            "DEVTIME-2306", HttpStatus.UNPROCESSABLE_ENTITY, "error.contract.notAcceptingWork"),
    /** RN-307 / INV-TCK-03: ticket com work logs é cancelável, nunca excluível. */
    TICKET_DELETE_RESTRICTED("DEVTIME-2307", HttpStatus.CONFLICT, "error.ticket.deleteRestricted"),
    /** RN-311: conclusão bloqueada por cronômetro ativo — {@code RUNNING} ou {@code PAUSED}. */
    TICKET_ACTIVE_TIMER("DEVTIME-2311", HttpStatus.CONFLICT, "error.ticket.activeTimer"),
    /** RN-313 / INV-TAG-01: limite de 10 etiquetas por alvo. */
    TAG_LIMIT_EXCEEDED("DEVTIME-2313", HttpStatus.UNPROCESSABLE_ENTITY, "error.tag.limitExceeded"),
    /** state-machines.md §4.7: {@code blockReason} ausente ou com menos de 5 caracteres. */
    TICKET_BLOCK_REASON_REQUIRED(
            "DEVTIME-2314", HttpStatus.UNPROCESSABLE_ENTITY, "error.ticket.blockReasonRequired"),
    /** RN-305: contrato de destino pertence a outro cliente (tickets.md §8.3). */
    TICKET_TARGET_CONTRACT_CLIENT_MISMATCH(
            "DEVTIME-2315",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "error.ticket.targetContractClientMismatch"),

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

    // ── Tags · DEVTIME-2600–2699 (users.md §9) ───────────────────────────────────────────────
    /** RN-507: nome normalizado já existente no tenant. */
    TAG_NAME_DUPLICATED("DEVTIME-2604", HttpStatus.CONFLICT, "error.tag.nameDuplicated"),

    // ── Comentários · DEVTIME-2700–2799 (tickets.md §13) ─────────────────────────────────────
    /** RN-811: corpo fora de 1–10.000 caracteres após aparar. */
    COMMENT_BODY_INVALID(
            "DEVTIME-2705", HttpStatus.UNPROCESSABLE_ENTITY, "error.comment.bodyInvalid"),
    /** RN-812: janela de 24 horas encerrada (tickets.md §10.2). */
    COMMENT_EDIT_WINDOW_EXPIRED(
            "DEVTIME-2706", HttpStatus.CONFLICT, "error.comment.editWindowExpired"),
    /** RN-815 / INV-CMT-03: comentário de sistema é imutável (tickets.md §10.2). */
    COMMENT_SYSTEM_IMMUTABLE("DEVTIME-2707", HttpStatus.CONFLICT, "error.comment.systemImmutable"),

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
