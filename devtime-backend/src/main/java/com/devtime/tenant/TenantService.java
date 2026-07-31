package com.devtime.tenant;

import com.devtime.tenant.dto.TenantCommands.NewTenant;
import com.devtime.tenant.dto.TenantViews.SessionSnapshot;
import com.devtime.tenant.dto.TenantViews.TenantOption;
import com.devtime.tenant.dto.TenantViews.TenantView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura e provisionamento de organização, consumidos por {@code 001-authentication}.
 *
 * <p>Escopo deliberadamente restrito ao que a sessão exige. Edição de dados e configurações,
 * cancelamento e exportação pertencem a {@code 002-users} e não são expostos aqui.
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
}
