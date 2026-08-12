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

    /**
     * Token CSRF ausente ou inválido na requisição.
     *
     * <p>Código próprio, separado de {@link #PERMISSION_DENIED}: os dois nascem da mesma {@code
     * AccessDeniedException} da cadeia de filtros, mas pedem ações opostas de quem os recebe. "Você
     * não tem permissão" manda a pessoa procurar um administrador — e nenhum administrador resolve
     * um cookie que o navegador não devolveu. O sintoma clássico é <b>toda leitura funcionar e toda
     * alteração falhar</b>, exatamente o que a mensagem de permissão não sugere.
     */
    CSRF_TOKEN_INVALID("DEVTIME-1105", HttpStatus.FORBIDDEN, "error.csrf.invalid"),

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
    /**
     * RN-164 / {@code users.md} §6.2: {@code timerAutoAbandonMinutes} menor ou igual a {@code
     * timerLongRunningMinutes}.
     *
     * <p>Registrado por 002-users. O limiar de abandono precisa ser posterior ao de alerta; a
     * inversão faria o cronômetro ser abandonado antes de o usuário ser avisado.
     */
    TIMER_THRESHOLDS_INCONSISTENT(
            "DEVTIME-2020", HttpStatus.UNPROCESSABLE_ENTITY, "error.settings.timerThresholds"),
    /** RN-113 / {@code users.md} §6.2: {@code roundingMinutes} fora de {0, 5, 6, 10, 15, 30}. */
    ROUNDING_MINUTES_UNSUPPORTED(
            "DEVTIME-2021", HttpStatus.UNPROCESSABLE_ENTITY, "error.settings.roundingMinutes"),
    TERMINAL_STATE("DEVTIME-2011", HttpStatus.CONFLICT, "error.state.terminal"),

    // ── Conta e organização · DEVTIME-2450–2499 (authentication.md §8) ───────────────────────
    /** RN-451 / PW-02: senha fora da política mínima. */
    PASSWORD_POLICY_VIOLATION(
            "DEVTIME-2451", HttpStatus.UNPROCESSABLE_ENTITY, "error.password.policyViolation"),
    /** RN-452 / INV-USR-01: e-mail já pertence a um usuário não excluído. */
    EMAIL_ALREADY_REGISTERED("DEVTIME-2452", HttpStatus.CONFLICT, "error.email.alreadyRegistered"),
    /**
     * RN-455 / INV-TEN-02: a operação deixaria o tenant sem nenhum {@code OWNER} ativo.
     *
     * <p>É o erro mais consequente da feature 002: um tenant sem proprietário é irrecuperável pela
     * própria API — ninguém restante teria permissão para promover alguém.
     */
    LAST_OWNER_REQUIRED("DEVTIME-2455", HttpStatus.CONFLICT, "error.membership.lastOwner"),
    /** RN-456 / OWN-06: ninguém altera o próprio papel, nem sendo {@code OWNER}. */
    SELF_ROLE_CHANGE("DEVTIME-2456", HttpStatus.FORBIDDEN, "error.membership.selfRoleChange"),
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
    /** contracts.md §8.2/§8.4: suspender ou encerrar com cronômetro ativo no contrato. */
    CONTRACT_HAS_ACTIVE_TIMER("DEVTIME-2212", HttpStatus.CONFLICT, "error.contract.hasActiveTimer"),
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

    // ── Anexos · DEVTIME-2700–2704 (spec 015 §12, business-rules.md §17) ─────────────────────
    /**
     * RN-801: arquivo acima de 10 MB <b>ou</b> quota do tenant excedida.
     *
     * <p>Um único código para as duas condições porque é assim que business-rules.md §17 e a spec o
     * registram. Os detalhes distinguem o caso: o excesso de tamanho carrega {@code sizeBytes} e
     * {@code maxBytes}; a quota carrega {@code usedBytes} e {@code limitBytes}, exigido por FA-09
     * ("informando o consumo atual").
     *
     * <p>{@code 413} é o status de ambos, e não {@code 422}: o servidor está recusando uma carga
     * grande demais, que é exatamente a semântica de {@code Payload Too Large}.
     */
    ATTACHMENT_TOO_LARGE("DEVTIME-2701", HttpStatus.PAYLOAD_TOO_LARGE, "error.attachment.tooLarge"),
    /**
     * RN-802: tipo fora da allowlist <b>ou</b> assinatura binária divergente do tipo declarado.
     *
     * <p>OB-01: são defesas de naturezas diferentes — o passo 7 de §6.1 confia no que o cliente
     * declara, o passo 8 verifica o que o arquivo é. Compartilham o código porque a resposta ao
     * usuário é a mesma e distinguir informaria a um atacante qual das duas camadas o barrou.
     */
    ATTACHMENT_TYPE_NOT_ALLOWED(
            "DEVTIME-2702", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.attachment.typeNotAllowed"),
    /**
     * RN-803: download bloqueado por verificação.
     *
     * <p>Único código com <b>dois</b> status: {@code 409} em {@code PENDING} e {@code FAILED}
     * (§12), {@code 403} em {@code INFECTED}. A distinção é deliberada e visível ao usuário —
     * "aguarde" e "foi bloqueado" exigem reações diferentes, e CP-20 proíbe desabilitar o download
     * sem explicar. O status é definido por quem lança; este é o padrão.
     */
    ATTACHMENT_NOT_SCANNED("DEVTIME-2703", HttpStatus.CONFLICT, "error.attachment.notScanned"),
    /** RN-806: 20 anexos por ticket, 5 por comentário. Os limites são por alvo (CX-19). */
    ATTACHMENT_LIMIT_EXCEEDED(
            "DEVTIME-2704", HttpStatus.UNPROCESSABLE_ENTITY, "error.attachment.limitExceeded"),

    // ── Comentários · DEVTIME-2705–2799 (tickets.md §13) ─────────────────────────────────────
    /** RN-811: corpo fora de 1–10.000 caracteres após aparar. */
    COMMENT_BODY_INVALID(
            "DEVTIME-2705", HttpStatus.UNPROCESSABLE_ENTITY, "error.comment.bodyInvalid"),
    /** RN-812: janela de 24 horas encerrada (tickets.md §10.2). */
    COMMENT_EDIT_WINDOW_EXPIRED(
            "DEVTIME-2706", HttpStatus.CONFLICT, "error.comment.editWindowExpired"),
    /** RN-815 / INV-CMT-03: comentário de sistema é imutável (tickets.md §10.2). */
    COMMENT_SYSTEM_IMMUTABLE("DEVTIME-2707", HttpStatus.CONFLICT, "error.comment.systemImmutable"),

    // ── Relatórios e exportação · DEVTIME-3000–3099 (reports.md §13) ─────────────────────────
    /**
     * Intervalo de consulta acima do máximo permitido pelo endpoint.
     *
     * <p>Registrado por 002-users para a trilha de auditoria, cujo teto é de 90 dias ({@code
     * users.md} §10.1). {@code reports.md} §13 usa o mesmo código com teto de 366 dias: a condição
     * é a mesma — intervalo grande demais —, e o limite pertence ao endpoint, não ao código.
     */
    DATE_RANGE_EXCEEDED("DEVTIME-3001", HttpStatus.BAD_REQUEST, "error.dateRange.exceeded"),
    /**
     * Relatório de período {@code SCHEDULED} — o ciclo ainda não começou (reports.md §6).
     *
     * <p>A faixa 3002 a 3007 segue {@code reports.md} §12, e <b>não</b> §12 de {@code specs/012},
     * que atribui outros significados a 3002, 3003 e 3004. Vale a hierarquia IA-11: {@code 04-api/}
     * precede {@code specs/}. A divergência está tabelada no CHANGELOG desta sprint.
     */
    REPORT_PERIOD_NOT_STARTED("DEVTIME-3002", HttpStatus.CONFLICT, "error.report.periodNotStarted"),
    /** §8.1: parâmetros incompatíveis com o tipo de relatório solicitado. */
    REPORT_PARAMETERS_INCOMPATIBLE(
            "DEVTIME-3003", HttpStatus.UNPROCESSABLE_ENTITY, "error.report.parametersIncompatible"),
    /**
     * §8.3 / §4.10: a exportação não está no estado que a operação exige.
     *
     * <p>Cobre as duas condições: download antes de a geração concluir e cancelamento de uma
     * exportação já em {@code PROCESSING} (§11.1 de specs/012). {@code reports.md} tabela apenas a
     * primeira; a segunda é a mesma condição vista do outro lado, e ocupar um oitavo número para
     * ela criaria um contrato novo onde não há distinção que o cliente precise tratar (EX-03).
     */
    EXPORT_NOT_READY("DEVTIME-3004", HttpStatus.CONFLICT, "error.export.notReady"),
    /**
     * §8.3 / §4.10: o arquivo expirou; é preciso gerar novamente.
     *
     * <p>{@code 410 Gone} e não {@code 404}: o recurso <b>existiu</b> e a distinção importa ao
     * usuário, que precisa saber que o pedido dele foi atendido e que só o arquivo caducou.
     */
    EXPORT_EXPIRED("DEVTIME-3005", HttpStatus.GONE, "error.export.expired"),
    /** §8.3: a geração falhou; a resposta carrega o motivo. */
    EXPORT_FAILED("DEVTIME-3006", HttpStatus.CONFLICT, "error.export.failed"),
    /** §12: agrupamento fora dos suportados pelo tipo de relatório (§6.3 de specs/012). */
    REPORT_GROUPING_UNSUPPORTED(
            "DEVTIME-3007", HttpStatus.UNPROCESSABLE_ENTITY, "error.report.groupingUnsupported"),

    // ── Notificações · DEVTIME-4000–4099 (notifications.md §12) ──────────────────────────────
    /**
     * §9.1: tentativa de silenciar um tipo com {@code canMute = false}.
     *
     * <p>Reservado aos tipos {@code CRITICAL} — contrato excedido e anexo infectado. A faixa 4000 é
     * a definida por notifications.md §12, que é normativa sobre o contrato de erro da API.
     */
    NOTIFICATION_TYPE_NOT_MUTABLE(
            "DEVTIME-4001", HttpStatus.UNPROCESSABLE_ENTITY, "error.notification.typeNotMutable"),
    /**
     * §9.2: intervalo de horário silencioso inválido.
     *
     * <p>Registrado para preservar a faixa: {@code quietHours} não existe em {@code entities.md}
     * §6.2.1, que prevalece sobre notifications.md §9.1 por IA-11. Sem o campo, não há caminho que
     * produza este código — mas reaproveitá-lo depois mudaria o significado de uma condição já
     * publicada.
     */
    NOTIFICATION_QUIET_HOURS_INVALID(
            "DEVTIME-4002",
            HttpStatus.UNPROCESSABLE_ENTITY,
            "error.notification.quietHoursInvalid"),
    /** ST-03: mais de três conexões de fluxo simultâneas por usuário. */
    NOTIFICATION_STREAM_LIMIT(
            "DEVTIME-4003", HttpStatus.TOO_MANY_REQUESTS, "error.notification.streamLimit"),

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
