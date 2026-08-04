package com.devtime.report;

import com.devtime.shared.storage.StoragePort;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * URL de download assinada, com expiração curta (RN-712, RP-12, INV-RPT-06).
 *
 * <p><b>Quinze minutos, e a expiração não é negociável</b> (CP-11, SG-03). Uma URL permanente seria
 * um link compartilhável sem autenticação — o modo mais fácil de vazar um relatório consolidado
 * (§19.1). O binário nunca é servido pela aplicação: o download é redirecionamento para o storage,
 * e é a assinatura que autoriza o acesso.
 *
 * <p>FA-13: expirada a URL, uma nova solicitação gera <b>outra assinatura sobre o mesmo objeto</b>,
 * sem regerar o arquivo. Regerar custaria a agregação inteira para produzir bytes idênticos, e em
 * período fechado eles seriam idênticos por construção (RN-708).
 */
@Component
@RequiredArgsConstructor
public class SignedUrlProvider {

    /** RN-712 e RS-03. */
    public static final Duration TTL = Duration.ofMinutes(15);

    private final StoragePort storage;

    /**
     * @param downloadFileName nome apresentado ao usuário; o objeto é servido com {@code
     *     Content-Disposition: attachment} pelo próprio adapter (SG-06)
     */
    public String urlFor(String storageKey, String downloadFileName) {
        return storage.presignedDownloadUrl(storageKey, TTL, downloadFileName);
    }
}
