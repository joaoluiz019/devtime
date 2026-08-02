package com.devtime.shared.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * Porta de saída do object storage (integrations.md §6.2).
 *
 * <p>SG-01: o bucket <b>nunca</b> é público; todo acesso de leitura passa por URL assinada com
 * expiração curta. CP-15/OB-06: o binário nunca é servido pela aplicação — o download é
 * redirecionamento, e por isso não existe aqui nenhum método que devolva o conteúdo ao chamador em
 * caminho de requisição.
 *
 * <p>{@link #openStream(String)} existe apenas para a verificação antivírus, que é servidor a
 * servidor e assíncrona. É a única leitura de conteúdo do sistema depois do upload.
 */
public interface StoragePort {

    /**
     * Grava o objeto.
     *
     * <p>O chamador é responsável por só invocar este método <b>depois</b> de validar tamanho, tipo
     * e assinatura binária (CP-04): gravar antes colocaria conteúdo não verificado no storage.
     *
     * @param key identificador opaco gerado por {@code StorageKeyGenerator}; nunca derivado do nome
     *     do arquivo (CP-05)
     * @param content fluxo do conteúdo; não é carregado integralmente em memória (CP-14)
     * @param contentLength tamanho exato em bytes, já validado contra RN-801
     * @param contentType tipo já verificado contra a assinatura binária (RN-802)
     */
    void store(String key, InputStream content, long contentLength, String contentType);

    /**
     * URL de download assinada, com expiração.
     *
     * <p>SG-06: o objeto é servido com {@code Content-Disposition: attachment}, nunca renderizado
     * pelo navegador — renderizar conteúdo externo é a superfície de ataque que §4 da spec 015
     * recusa explicitamente.
     *
     * @param ttl validade curta (SG-08); uma URL longeva é um link público com data marcada
     * @param downloadFileName nome apresentado ao usuário no download
     */
    String presignedDownloadUrl(String key, Duration ttl, String downloadFileName);

    /**
     * Remove o binário.
     *
     * <p>Chamado em dois momentos: exclusão do último registro que referencia a chave (RN-805) e
     * entrada em {@code INFECTED} (INV-ATT-06). Idempotente: remover chave inexistente não é erro.
     */
    void delete(String key);

    boolean exists(String key);

    /**
     * Abre o conteúdo para verificação antivírus.
     *
     * <p>Devolve {@link Optional#empty()} quando o objeto não existe — o binário pode ter sido
     * removido entre o enfileiramento e o processamento, por exclusão do último referenciador.
     * Tratar a ausência como falha faria o item consumir as três tentativas de §4.9 por um motivo
     * que não é falha alguma.
     */
    Optional<InputStream> openStream(String key);

    /**
     * Chaves presentes no bucket, para reconciliação.
     *
     * <p>Consumida apenas pelo {@code OrphanBinaryJob} (§22.4), que compara este inventário com os
     * registros que declaram binário presente. O job <b>alerta sem remover</b> (CP-10): por isso a
     * porta não precisa — e deliberadamente não oferece — nenhuma operação de remoção em lote.
     *
     * @param limit teto de chaves por execução; a varredura é semanal e não precisa ser exaustiva
     *     numa única passagem
     */
    java.util.List<String> listKeys(int limit);
}
