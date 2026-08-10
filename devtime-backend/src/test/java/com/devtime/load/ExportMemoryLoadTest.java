package com.devtime.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.report.domain.ReportSource;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ReportResponses.ReportEntry;
import com.devtime.report.dto.ReportResponses.ReportGroup;
import com.devtime.report.dto.ReportResponses.ReportTotals;
import com.devtime.report.render.CsvRenderer;
import com.devtime.report.render.RenderableReport;
import com.devtime.report.render.XlsxRenderer;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T-012-35: exportação de 50.000 linhas sem carregar o arquivo em memória.
 *
 * <p>OB-06 aponta o XLSX como o caso crítico: a implementação ingênua monta a planilha inteira em
 * objetos antes de escrever, e 50.000 linhas de relatório derrubam o processo — que atende todos os
 * tenants, não apenas quem pediu o arquivo.
 *
 * <p>A saída vai para um {@link OutputStream} que <b>conta e descarta</b>. Escrever em arquivo
 * mediria o disco; guardar em memória mediria justamente o que o teste quer proibir.
 */
class ExportMemoryLoadTest extends LoadTestSupport {

    private static final int LINHAS = 50_000;

    /** Duas ordens de grandeza abaixo do que custaria reter as 50.000 linhas formatadas. */
    private static final long TETO_DE_HEAP = 16L * 1024 * 1024;

    @Autowired private CsvRenderer csvRenderer;
    @Autowired private XlsxRenderer xlsxRenderer;

    @Test
    @DisplayName("T-012-35: CSV de 50.000 linhas escreve em fluxo, sem reter o arquivo em memória")
    void csvMustStreamFiftyThousandRows() {
        verificarFluxo("CSV", (relatorio, saida) -> csvRenderer.render(relatorio, opcoes(), saida));
    }

    @Test
    @DisplayName("T-012-35 / OB-06: XLSX de 50.000 linhas escreve em fluxo")
    void xlsxMustStreamFiftyThousandRows() {
        verificarFluxo(
                "XLSX", (relatorio, saida) -> xlsxRenderer.render(relatorio, opcoes(), saida));
    }

    /**
     * O critério é um <b>teto absoluto de heap</b>, e não uma razão entre medições.
     *
     * <p>Duas alternativas foram descartadas por não medirem o que prometem. Comparar o heap ao
     * tamanho do arquivo falha para o XLSX, que é um ZIP: 50.000 linhas cabem em 2,7 MB
     * comprimidos, e um renderer que retivesse tudo passaria pela comparação por acidente da
     * compressão. Comparar o crescimento entre 10.000 e 50.000 linhas soa mais rigoroso, mas
     * amostragem de heap em JVM sob teste tem ruído da ordem de megabytes — o CSV media 3,3× para
     * 5× de linhas sem estar acumulando nada.
     *
     * <p>O que OB-06 quer evitar é concreto: uma exportação grande derrubar o processo que atende
     * todos os tenants. A implementação ingênua de XLSX retém as 50.000 linhas como objetos e custa
     * centenas de megabytes; um renderer em fluxo mantém uma janela de poucas linhas. Entre esses
     * dois mundos há duas ordens de grandeza, e um teto de 16 MB separa os dois sem depender de
     * ruído de medição.
     */
    private void verificarFluxo(String formato, Renderizacao renderizacao) {
        Medicao medicao = renderizar(renderizacao, LINHAS);

        System.out.printf(
                "T-012-35 — %s: %d linhas → %d bytes escritos, heap +%d kB (teto %d kB)%n",
                formato, LINHAS, medicao.bytes(), medicao.heap() / 1024, TETO_DE_HEAP / 1024);

        assertThat(medicao.bytes()).as("o arquivo foi realmente produzido").isPositive();
        assertThat(medicao.heap())
                .as(
                        "RN-706 / OB-06: renderizar %d linhas não pode reter mais que %d MB — acima"
                                + " disso o renderer está acumulando o arquivo em memória",
                        LINHAS, TETO_DE_HEAP / (1024 * 1024))
                .isLessThan(TETO_DE_HEAP);
    }

    private record Medicao(long bytes, long heap) {}

    private Medicao renderizar(Renderizacao renderizacao, int linhas) {
        RenderableReport relatorio = relatorioCom(linhas);
        AtomicLong bytesEscritos = new AtomicLong();

        OutputStream contador =
                new OutputStream() {
                    @Override
                    public void write(int umByte) {
                        bytesEscritos.incrementAndGet();
                    }

                    @Override
                    public void write(byte[] bloco, int inicio, int tamanho) {
                        bytesEscritos.addAndGet(tamanho);
                    }
                };

        // A massa de entrada é construída antes de medir: ela é responsabilidade de quem chama o
        // renderer, e contá-la mediria o teste em vez do código sob teste.
        long antes = heapUsado();
        renderizacao.render(relatorio, contador);
        long crescimento = Math.max(heapUsado() - antes, 0);
        return new Medicao(bytesEscritos.get(), crescimento);
    }

    /**
     * Coleta antes de medir: sem isso a medição inclui lixo de qualquer teste anterior.
     *
     * <p>Três chamadas porque {@code System.gc()} é um pedido, não uma ordem — uma passagem só
     * costuma deixar para trás o que ficou pendente de finalização.
     */
    private long heapUsado() {
        for (int passagem = 0; passagem < 3; passagem++) {
            System.gc();
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private ExportOptions opcoes() {
        return new ExportOptions("carga", false, false, "pt-BR");
    }

    private RenderableReport relatorioCom(int linhas) {
        List<ReportEntry> entradas = new ArrayList<>(linhas);
        for (int indice = 0; indice < linhas; indice++) {
            entradas.add(
                    new ReportEntry(
                            LocalDate.of(2026, 1, 1 + indice % 28),
                            Instant.parse("2026-01-15T12:00:00Z"),
                            Instant.parse("2026-01-15T13:00:00Z"),
                            "SUS-" + indice,
                            "Ticket de carga " + indice,
                            "Desenvolvimento",
                            "Pessoa " + (indice % 20),
                            "Descrição do registro número " + indice,
                            60,
                            "1h 00min",
                            new BigDecimal("1.00"),
                            indice % 5 != 0,
                            List.of("carga"),
                            null));
        }

        return new RenderableReport(
                ReportType.TIMESHEET,
                "CARGA-" + LINHAS,
                Instant.parse("2026-07-29T14:32:10Z"),
                "Teste de carga",
                ReportSource.LIVE,
                false,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(new ReportGroup(null, null, 60 * linhas, 60 * linhas, "—", entradas)),
                null,
                new ReportTotals(
                        linhas,
                        28,
                        linhas,
                        60 * linhas,
                        60 * linhas,
                        0,
                        "—",
                        BigDecimal.valueOf(linhas),
                        null),
                Locale.forLanguageTag("pt-BR"));
    }

    @FunctionalInterface
    private interface Renderizacao {
        void render(RenderableReport relatorio, OutputStream saida);
    }
}
