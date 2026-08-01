package com.devtime.tenant;

import com.devtime.tenant.dto.TenantSettings;
import java.util.Map;
import java.util.UUID;

/**
 * Leitura tipada de {@code tenant.settings} (entities.md §6.1.1).
 *
 * <p>Interface pública consumida por {@code 008-worklogs} (RN-113, RN-119, RN-120) e {@code
 * 009-timer} (RN-163, RN-164). BR-003: é o único caminho para essas features alcançarem a
 * configuração do tenant — nenhuma delas conhece {@code Tenant} nem {@code TenantRepository}.
 *
 * <p>Existe separada de {@link TenantService} porque resolve um problema distinto: {@code
 * TenantService} devolve o tenant como recurso, com o JSON tal como persistido; aqui a configuração
 * já chega convertida e com os padrões aplicados, que é o que uma regra de negócio consegue usar.
 */
public interface TenantSettingsService {

    /**
     * Configuração efetiva do tenant, com os padrões de entities.md §6.1.1 aplicados às chaves
     * ausentes.
     *
     * @throws com.devtime.shared.error.EntityNotFoundException {@code DEVTIME-2002} quando o tenant
     *     não existe
     */
    TenantSettings settingsOf(UUID tenantId);

    /** Configuração do tenant da sessão corrente. */
    TenantSettings current();

    /**
     * Mescla o JSON persistido sobre os padrões, preservando chaves desconhecidas.
     *
     * <p>Serve à resposta de {@code GET /auth/me}, que devolve a configuração como mapa aberto: uma
     * chave introduzida por um tenant específico deve chegar ao cliente, ainda que não exista em
     * {@link TenantSettings}.
     */
    Map<String, Object> merged(String settingsJson);
}
