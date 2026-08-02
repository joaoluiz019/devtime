package com.devtime.user;

import com.devtime.user.dto.UserProfileRequests.UserPreferencesRequest;
import com.devtime.user.dto.UserProfileRequests.UserProfileUpdateRequest;
import com.devtime.user.dto.UserProfileResponses.UserProfileResponse;
import java.io.InputStream;

/**
 * Perfil, preferências e avatar do usuário autenticado (spec 002 §22.2).
 *
 * <p>Não recebe identificador de usuário em nenhuma operação: o alvo é <b>sempre</b> o titular da
 * sessão. É a forma mais forte de garantir o ownership de §16 — não existe assinatura que permita
 * editar o perfil de outra pessoa, então não existe verificação a esquecer.
 */
public interface UserProfileService {

    /** Perfil do usuário autenticado, com preferências já normalizadas pelos padrões §6.2.1. */
    UserProfileResponse current();

    /** users.md §5.1: atualização parcial; campos nulos preservam o valor atual. */
    UserProfileResponse updateProfile(UserProfileUpdateRequest request);

    /** users.md §5.2: atualização parcial das preferências, mesclada sobre o JSON existente. */
    UserProfileResponse updatePreferences(UserPreferencesRequest request);

    /**
     * users.md §5.3: envia o avatar.
     *
     * <p>O fluxo é aberto duas vezes por quem chama — uma para a validação de assinatura, outra
     * para a gravação. Ler uma vez e reter o conteúdo em memória obrigaria a carregar o arquivo
     * inteiro (CP-14 de 015).
     *
     * @param contentSupplier abre um novo fluxo do conteúdo a cada invocação
     */
    UserProfileResponse uploadAvatar(
            long sizeBytes,
            String contentType,
            java.util.function.Supplier<InputStream> contentSupplier);

    /** users.md §5.3: remove o avatar e o binário correspondente. */
    void removeAvatar();
}
