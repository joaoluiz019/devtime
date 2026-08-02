package com.devtime.shared.antivirus;

import com.devtime.shared.config.DevTimeProperties;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter de {@link AntivirusPort} sobre o daemon do ClamAV (integrations.md §6.3).
 *
 * <p>Fala {@code INSTREAM} diretamente no socket, sem biblioteca intermediária. O protocolo tem
 * três elementos — comando, blocos precedidos do tamanho em 4 bytes <i>big-endian</i>, e bloco zero
 * de terminação — e nenhuma das bibliotecas disponíveis acrescenta algo além disso, enquanto todas
 * acrescentariam uma dependência a justificar em DP-01 e a manter atualizada em DP-03. É a mesma
 * decisão de fronteira de CE-G-07.
 *
 * <p><b>Nunca lança</b> (AV-02): qualquer falha vira {@link AntivirusPort.ScanVerdict#FAILED}, que
 * mantém o download bloqueado e devolve o anexo à fila de §4.9. Propagar a exceção faria a
 * indisponibilidade do verificador virar erro de requisição de quem apenas consultou um anexo.
 */
@Component
@Slf4j
public class ClamAvAdapter implements AntivirusPort {

    /**
     * 8 KB por bloco. O daemon aceita até {@code StreamMaxLength}; blocos menores apenas aumentam o
     * número de idas e voltas, e blocos maiores não reduzem o tempo de verificação de forma
     * observável em arquivos de até 10 MB (RN-801).
     */
    private static final int CHUNK_SIZE = 8192;

    private static final byte[] INSTREAM_COMMAND =
            "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public ClamAvAdapter(DevTimeProperties properties) {
        DevTimeProperties.AntivirusProps antivirus = properties.antivirus();
        this.host = antivirus.host();
        this.port = antivirus.port();
        this.connectTimeoutMillis = (int) antivirus.connectTimeout().toMillis();
        this.readTimeoutMillis = (int) antivirus.readTimeout().toMillis();
    }

    @Override
    public ScanResult scan(InputStream content) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            return exchange(socket, content);
        } catch (IOException failure) {
            // §28: o nome do arquivo e o conteúdo nunca entram em log.
            log.warn("verificação antivírus indisponível causa={}", failure.getMessage());
            return ScanResult.failed("verificador indisponível: " + failure.getMessage());
        }
    }

    private ScanResult exchange(Socket socket, InputStream content) throws IOException {
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                InputStream in = socket.getInputStream()) {
            out.write(INSTREAM_COMMAND);

            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            // CP-14: o arquivo atravessa em blocos; nunca existe uma cópia inteira em memória.
            while ((read = content.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                out.writeInt(read);
                out.write(buffer, 0, read);
            }
            out.writeInt(0); // bloco vazio encerra o fluxo
            out.flush();

            return interpret(readResponse(in));
        }
    }

    private String readResponse(InputStream in) throws IOException {
        byte[] buffer = new byte[512];
        int read = in.read(buffer);
        if (read <= 0) {
            return "";
        }
        return new String(buffer, 0, read, StandardCharsets.US_ASCII).trim();
    }

    /**
     * Interpreta a resposta do daemon.
     *
     * <p>{@code stream: OK}, {@code stream: <ameaça> FOUND} ou {@code ... ERROR}. Qualquer resposta
     * fora dessas três formas é tratada como {@code FAILED}: AV-02 não admite que o desconhecido
     * seja lido como ausência de ameaça.
     */
    private ScanResult interpret(String response) {
        if (response.endsWith("OK") && !response.contains("FOUND")) {
            return ScanResult.clean();
        }
        if (response.contains("FOUND")) {
            return ScanResult.infected(extractThreat(response));
        }
        log.warn("resposta inesperada do verificador antivírus resposta={}", response);
        return ScanResult.failed("resposta inesperada do verificador: " + response);
    }

    /** Extrai a ameaça de {@code stream: Eicar-Signature FOUND}. */
    private String extractThreat(String response) {
        int start = response.indexOf(':');
        int end = response.lastIndexOf("FOUND");
        if (start < 0 || end <= start) {
            return "desconhecida";
        }
        String threat = response.substring(start + 1, end).trim();
        return threat.isEmpty() ? "desconhecida" : threat;
    }
}
