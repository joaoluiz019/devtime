package com.devtime.user;

import com.devtime.shared.storage.StoragePort;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolução da URL de leitura do avatar.
 *
 * <p>{@code users.avatar_url} guarda a <b>chave</b> do objeto, não uma URL. SG-01 mantém o bucket
 * privado, então o endereço servido ao cliente é sempre assinado e de vida curta — persistir a URL
 * significaria persistir uma credencial vencida.
 *
 * <p>Valores que não são chave desta aplicação (uma URL externa gravada antes deste código) são
 * devolvidos como estão: reescrevê-los produziria um link quebrado.
 */
@Component
@RequiredArgsConstructor
public class AvatarUrlResolver {

    /** Prefixo das chaves geradas aqui; distingue chave de URL externa. */
    static final String KEY_PREFIX = "avatars/";

    /** RN-712: mesma validade das demais URLs assinadas do sistema. */
    static final Duration TTL = Duration.ofMinutes(15);

    private static final String DOWNLOAD_NAME = "avatar";

    private final StoragePort storagePort;

    public String resolve(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        if (!storedValue.startsWith(KEY_PREFIX)) {
            return storedValue;
        }
        return storagePort.presignedDownloadUrl(storedValue, TTL, DOWNLOAD_NAME);
    }

    public boolean isManagedKey(String storedValue) {
        return storedValue != null && storedValue.startsWith(KEY_PREFIX);
    }
}
