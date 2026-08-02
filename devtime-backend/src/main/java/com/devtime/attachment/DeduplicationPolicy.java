package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.UploadContent;
import com.devtime.shared.storage.StorageException;
import com.devtime.shared.storage.StoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Passos 11 e 12 de §6.1 e passos 5 e 6 de §6.4 — deduplicação e contagem de referências (RN-805).
 *
 * <p><b>A deduplicação é restrita ao tenant</b> (CP-06, CA-15). Compartilhar binário entre tenants
 * criaria um canal de inferência: o tempo de upload revelaria que outro tenant possui o mesmo
 * arquivo. O ganho de storage não compensa o vazamento (§6.4). A restrição não é escrita aqui — é o
 * filtro de tenant sobre {@code findByChecksum}, o que a torna consequência da arquitetura e não de
 * uma cláusula que alguém pode remover.
 *
 * @see AttachmentRepository#findByChecksum
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeduplicationPolicy {

    private final AttachmentRepository repository;
    private final StoragePort storage;
    private final StorageKeyGenerator keyGenerator;

    /**
     * Resolve a chave do conteúdo, gravando o binário apenas quando ele ainda não existe.
     *
     * <p>CP-04: chamado <b>depois</b> da validação de assinatura. Gravar antes colocaria conteúdo
     * não verificado no storage.
     *
     * <p>OB-03 / CX-11: o registro novo nasce em {@code PENDING} mesmo quando o binário é reusado
     * de um anexo já {@code CLEAN}. É redundante de propósito — herdar o {@code scanStatus}
     * exigiria confiar que o checksum garante identidade <b>e</b> que o veredito anterior continua
     * válido, e assinaturas de antivírus são atualizadas: um arquivo limpo ontem pode ser detectado
     * hoje.
     *
     * @return a chave a persistir e se houve reuso, para a trilha de auditoria (§18)
     */
    public Deduplication resolve(String checksum, UploadContent content, String contentType) {
        Optional<Attachment> existing =
                repository.findByChecksum(checksum, PageRequest.of(0, 1)).stream().findFirst();

        if (existing.isPresent()) {
            // Passo 11 — RN-805: reusa a chave; o binário não é gravado de novo.
            return new Deduplication(existing.get().getStorageKey(), true);
        }

        // Passo 12 — grava o binário.
        String key = keyGenerator.generate(checksum);
        try (InputStream in = content.openStream()) {
            storage.store(key, in, content.sizeBytes(), contentType);
        } catch (IOException unreadable) {
            throw new StorageException("falha ao ler o conteúdo para gravação", unreadable);
        }
        return new Deduplication(key, false);
    }

    /**
     * Passos 5 e 6 de §6.4: remove o binário apenas se este for o último referenciador.
     *
     * <p>CX-12: excluir um de dois registros que compartilham binário o <b>preserva</b>, e o outro
     * continua baixável. CX-13: excluir o último o remove (INV-ATT-05).
     *
     * @return {@code true} se o binário foi removido do storage
     */
    public boolean removeBinaryIfLastReference(Attachment attachment) {
        long others =
                repository.countOtherReferencesToStorageKey(
                        attachment.getStorageKey(), attachment.getId());
        if (others > 0) {
            log.debug(
                    "binário preservado attachmentId={} referenciasRestantes={}",
                    attachment.getId(),
                    others);
            return false;
        }
        storage.delete(attachment.getStorageKey());
        return true;
    }

    /**
     * @param storageKey chave a persistir no registro
     * @param deduplicated se o binário foi reusado; alimenta a trilha (§18) e a métrica {@code
     *     attachment.dedup.ratio} (§29)
     */
    public record Deduplication(String storageKey, boolean deduplicated) {}
}
