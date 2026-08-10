package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-015-29: uploads concorrentes contra MinIO e ClamAV reais.
 *
 * <p>Fica em {@code com.devtime.attachment} porque depende de {@code AttachmentTestSupport}, que
 * traz a infraestrutura de storage e antivírus. A marcação {@code carga} o mantém fora do ciclo
 * padrão pelo mesmo motivo dos demais: sobe dois contêineres e envia dezenas de arquivos.
 *
 * <p>O ponto sob teste é a <b>deduplicação por checksum</b> (CP-06) sob concorrência. O conteúdo
 * idêntico enviado ao mesmo tempo por várias pessoas é o caso comum — o mesmo anexo encaminhado à
 * equipe. Se a contagem de referências divergir aqui, a exclusão de um anexo apagaria o binário que
 * outro ainda usa, e o segundo download devolveria erro para um arquivo que a interface mostra como
 * disponível.
 */
@Tag("carga")
class AttachmentConcurrencyLoadTest extends AttachmentTestSupport {

    private static final int UPLOADS_SIMULTANEOS = 12;

    @Test
    @DisplayName("T-015-29: 12 uploads simultâneos do mesmo conteúdo mantêm o binário consistente")
    void concurrentUploadsMustKeepStorageConsistent() throws Exception {
        UUID ticketId = newTicket();
        CountDownLatch largada = new CountDownLatch(1);

        List<AttachmentResponse> enviados;
        try (ExecutorService pool = Executors.newFixedThreadPool(UPLOADS_SIMULTANEOS)) {
            List<Future<AttachmentResponse>> tarefas =
                    java.util.stream.IntStream.range(0, UPLOADS_SIMULTANEOS)
                            .mapToObj(
                                    indice ->
                                            pool.submit(
                                                    () -> {
                                                        largada.await();
                                                        return uploadPng(
                                                                ticketId, "concorrente.png");
                                                    }))
                            .toList();

            largada.countDown();
            enviados = new java.util.ArrayList<>();
            for (Future<AttachmentResponse> tarefa : tarefas) {
                enviados.add(tarefa.get(120, TimeUnit.SECONDS));
            }
        }

        assertThat(enviados)
                .as("todo upload aceito produz um registro próprio (RN-806 não foi atingido)")
                .hasSize(UPLOADS_SIMULTANEOS)
                .extracting(AttachmentResponse::id)
                .doesNotHaveDuplicates();

        // CP-06: o conteúdo é o mesmo, então a chave de storage também — deduplicação por checksum
        // dentro do tenant. O binário precisa existir uma vez e continuar legível.
        List<String> chaves =
                enviados.stream().map(anexo -> storageKeyOf(anexo.id())).distinct().toList();

        System.out.println(
                "T-015-29 — "
                        + UPLOADS_SIMULTANEOS
                        + " uploads simultâneos produziram "
                        + chaves.size()
                        + " chave(s) de storage");

        assertThat(chaves)
                .as("mesmo checksum no mesmo tenant é um único binário (CP-06)")
                .hasSize(1);
        assertThat(storage.exists(chaves.get(0)))
                .as("o binário compartilhado por todos os registros continua no storage")
                .isTrue();
    }
}
