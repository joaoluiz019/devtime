package com.devtime.support;

import com.devtime.shared.storage.StoragePort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@link StoragePort} em memória, para os testes que <b>usam</b> o storage sem serem sobre ele.
 *
 * <p>O ciclo de exportação de {@code 012} grava um arquivo e devolve uma URL assinada; o que os
 * testes daquela feature verificam é a contagem antes da materialização, o limiar de RN-706, a
 * idempotência, a máquina de estados e o conteúdo renderizado. Subir MinIO para isso acrescentaria
 * um contêiner e dezenas de segundos a cada execução sem tornar nenhuma dessas asserções mais
 * verdadeira.
 *
 * <p>O contrato do storage real continua verificado onde ele é o assunto: {@code
 * AttachmentScanIntegrationTest} e {@code AttachmentJobsIntegrationTest} rodam contra MinIO de
 * verdade, e é lá que DoD-06 se prova.
 *
 * <p>A implementação guarda os bytes num mapa e devolve uma URL sintética. Ela <b>não</b> imita
 * expiração de assinatura: nenhum teste que a usa afirma sobre isso.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryStorageConfiguration {

    @Bean
    @Primary
    StoragePort inMemoryStorage() {
        return new InMemoryStorage();
    }

    /** Visível para os testes que precisam inspecionar o que foi gravado. */
    public static class InMemoryStorage implements StoragePort {

        private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

        @Override
        public void store(String key, InputStream content, long contentLength, String contentType) {
            try (content) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                content.transferTo(buffer);
                objetos.put(key, buffer.toByteArray());
            } catch (IOException falha) {
                throw new UncheckedIOException(falha);
            }
        }

        @Override
        public String presignedDownloadUrl(String key, Duration ttl, String downloadFileName) {
            return "https://storage.local/" + key + "?nome=" + downloadFileName;
        }

        @Override
        public void delete(String key) {
            objetos.remove(key);
        }

        @Override
        public boolean exists(String key) {
            return objetos.containsKey(key);
        }

        @Override
        public Optional<InputStream> openStream(String key) {
            return Optional.ofNullable(objetos.get(key)).map(ByteArrayInputStream::new);
        }

        @Override
        public List<String> listKeys(int limit) {
            return objetos.keySet().stream().limit(limit).toList();
        }

        /** Tamanho do objeto gravado, para as asserções de conteúdo. */
        public int sizeOf(String key) {
            byte[] conteudo = objetos.get(key);
            return conteudo == null ? -1 : conteudo.length;
        }

        public String textOf(String key) {
            byte[] conteudo = objetos.get(key);
            return conteudo == null
                    ? null
                    : new String(conteudo, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
