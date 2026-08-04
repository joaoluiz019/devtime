package com.devtime.tenant;

import com.devtime.tenant.dto.TenantCommands.NewTenant;
import com.devtime.tenant.dto.TenantRequests.TenantCancelRequest;
import com.devtime.tenant.dto.TenantRequests.TenantSettingsRequest;
import com.devtime.tenant.dto.TenantRequests.TenantUpdateRequest;
import com.devtime.tenant.dto.TenantResponses.TenantCancelResponse;
import com.devtime.tenant.dto.TenantResponses.TenantResponse;
import com.devtime.tenant.dto.TenantViews.SessionSnapshot;
import com.devtime.tenant.dto.TenantViews.TenantOption;
import com.devtime.tenant.dto.TenantViews.TenantView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura e provisionamento de organização, consumidos por {@code 001-authentication}.
 *
 * <p>As quatro primeiras operações servem à sessão e são consumidas por {@code 001-authentication}.
 * As demais são a gestão da organização de {@code 002-users} (§22.2) e não possuem consumidor fora
 * da própria feature — {@code auth} nunca as chama.
 *
 * <p>A exportação completa (E-06) <b>não</b> está aqui: ela depende do mecanismo assíncrono de
 * {@code report_executions}, que pertence a {@code 012-reports} e ainda não existe. A lacuna foi
 * reportada em vez de contornada com um mecanismo paralelo.
 */
public interface TenantService {

    /**
     * Cria a organização no cadastro (spec 001 §7, passo 3).
     *
     * <p>O identificador é fornecido por quem chama, já gerado como UUIDv7 (ART-010). A inversão é
     * necessária: o filtro de tenant é ativado na abertura da transação, portanto o {@code
     * tenantId} precisa existir <b>antes</b> dela para que o {@code Membership} e as categorias
     * criados na mesma transação sejam corretamente escopados (CE-01).
     *
     * @return o {@code slug} atribuído, já resolvido contra colisões (CX-03)
     */
    String provision(UUID tenantId, NewTenant command);

    Optional<TenantView> find(UUID tenantId);

    /**
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} quando não
     *     existe
     */
    TenantView require(UUID tenantId);

    /**
     * Organizações disponíveis para o usuário (spec 001 E-08, {@code GET /auth/tenants}).
     *
     * <p>Retorna apenas vínculos {@code ACTIVE}, incluindo os de organizações suspensas — CX-08
     * exige que apareçam marcadas, e não omitidas.
     */
    List<TenantOption> optionsFor(UUID userId);

    /**
     * Estado do par organização/vínculo para os passos 3 e 4 de {@code permissions.md} §4.1.
     *
     * @return vazio quando não existe vínculo entre o usuário e a organização
     */
    Optional<SessionSnapshot> sessionSnapshot(UUID tenantId, UUID userId);

    /** users.md §6.1: dados completos da organização da sessão, com {@code settings} tipado. */
    TenantResponse currentDetail();

    /**
     * Organização da sessão como emissora de relatório (RN-703).
     *
     * <p>Interface pública para {@code 011-bank-hours}, que a congela no snapshot do fechamento, e
     * para {@code 012-reports}, que monta o cabeçalho ao vivo em período aberto.
     *
     * <p>Sem {@code @PreAuthorize} próprio, diferentemente de {@link #currentDetail()}: quem chega
     * aqui já teve {@code PERIOD_CLOSE} ou {@code REPORT_VIEW_*} verificado, e exigir também {@code
     * TENANT_VIEW} faria o fechamento de um período depender de uma permissão de administração que
     * o papel encarregado do fechamento não precisa ter.
     */
    com.devtime.tenant.dto.TenantViews.TenantIssuer issuer();

    /**
     * users.md §6.1: atualização parcial dos dados da organização.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2004} em conflito de
     *     versão (RN-004), {@code DEVTIME-2000} para fuso inválido
     */
    TenantResponse update(TenantUpdateRequest request);

    /**
     * users.md §6.2: atualização parcial das 10 chaves operacionais.
     *
     * <p>CE-03/CP-03: <b>nada é recalculado</b>. Alterar {@code roundingMinutes} ou {@code
     * timezone} vale apenas para registros futuros — recalcular mudaria relatórios já entregues ao
     * cliente, o que ART-005 proíbe.
     */
    TenantResponse updateSettings(TenantSettingsRequest request);

    /**
     * users.md §6.3: cancela a organização, com senha e confirmação digitada (SG-04).
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-1011} para senha
     *     incorreta, {@code DEVTIME-2000} para confirmação divergente, {@code DEVTIME-2010} quando
     *     há período em {@code CLOSING} (CX-12)
     */
    TenantCancelResponse cancel(TenantCancelRequest request);

    /** RN-008: tenants cuja retenção de 30 dias venceu, para o {@code TenantPurgeJob}. */
    int purgeExpiredCancellations();
}
