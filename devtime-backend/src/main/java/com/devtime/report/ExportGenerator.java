package com.devtime.report;

import com.devtime.report.domain.ExportFormat;
import com.devtime.report.domain.ReportExceptions;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ExportRequests.ExportParameters;
import com.devtime.report.render.RenderableReport;
import com.devtime.report.render.ReportRenderer;
import com.devtime.shared.storage.StoragePort;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gera o arquivo de uma exportação e o grava no storage.
 *
 * <p><b>O relatório é recomposto, nunca reaproveitado de uma consulta anterior.</b> É o que torna a
 * exportação reproduzível (RN-707, OB-08): os mesmos parâmetros produzem o mesmo arquivo, inclusive
 * meses depois, e um período fechado produz bytes idênticos (RN-708) porque a fonte é o snapshot.
 *
 * <p><b>O arquivo passa por disco, não por memória</b> (CP-13, OB-06). O renderer escreve em um
 * arquivo temporário e o storage o lê em fluxo. O desvio existe porque a porta de storage exige o
 * tamanho exato antes de enviar — e descobrir o tamanho bufferizando o conteúdo em memória é
 * exatamente o que a escrita em fluxo existe para evitar.
 */
@Component
@RequiredArgsConstructor
public class ExportGenerator {

    /** PDF-04 e CA-10: nada de identificador técnico no nome do arquivo entregue. */
    private static final Pattern UNSAFE_FILE_NAME = Pattern.compile("[^A-Za-z0-9._-]+");

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final ReportService reportService;
    private final ReportHeaderBuilder headerBuilder;
    private final List<ReportRenderer> renderers;
    private final StoragePort storage;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    /** Resultado da geração, para que o serviço complete a execução (§11). */
    public record GeneratedFile(String storageKey, String fileName, long sizeBytes, int rowCount) {}

    public GeneratedFile generate(
            UUID executionId,
            ReportType reportType,
            ExportFormat format,
            ExportParameters parameters,
            ExportOptions options) {
        RenderableReport report = compose(reportType, parameters);
        String fileName = fileNameOf(reportType, format, options);
        String storageKey = storageKeyOf(executionId, format);

        Path temporary = temporaryFile(format);
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                rendererFor(format).render(report, options, output);
            }
            long sizeBytes = Files.size(temporary);
            try (InputStream content = Files.newInputStream(temporary)) {
                storage.store(storageKey, content, sizeBytes, format.contentType());
            }
            return new GeneratedFile(
                    storageKey,
                    fileName,
                    sizeBytes,
                    report.totals() == null ? 0 : report.totals().entriesCount());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao materializar a exportação", e);
        } finally {
            deleteQuietly(temporary);
        }
    }

    /** §8.1: os mesmos parâmetros do endpoint de consulta correspondente. */
    private RenderableReport compose(ReportType reportType, ExportParameters parameters) {
        Locale locale = headerBuilder.locale();
        return switch (reportType) {
            case CONTRACT_PERIOD ->
                    RenderableReport.of(
                            reportService.contractPeriod(
                                    required(parameters.contractPeriodId(), "contractPeriodId"),
                                    parameters.filtersOrEmpty()),
                            locale);
            case CLIENT_SUMMARY ->
                    RenderableReport.of(
                            reportService.clientSummary(
                                    required(parameters.clientId(), "clientId"),
                                    parameters.filtersOrEmpty()),
                            locale);
            case TIMESHEET ->
                    RenderableReport.of(
                            reportService.timesheet(parameters.filtersOrEmpty()), locale);
            case TICKET_DETAIL ->
                    RenderableReport.of(
                            reportService.ticketDetail(
                                    required(parameters.ticketId(), "ticketId"),
                                    parameters.filtersOrEmpty()),
                            locale);
            case PRODUCTIVITY ->
                    RenderableReport.of(
                            reportService.productivity(parameters.filtersOrEmpty()), locale);
        };
    }

    /** §17.2 / {@code DEVTIME-3003}: o tipo exige um recorte que o pedido não trouxe. */
    private UUID required(UUID value, String parameterName) {
        if (value == null) {
            throw ReportExceptions.parametersIncompatible(parameterName);
        }
        return value;
    }

    private ReportRenderer rendererFor(ExportFormat format) {
        return renderers.stream()
                .filter(renderer -> renderer.format() == format)
                .findFirst()
                .orElseThrow(
                        // CG-06: um formato sem renderer é defeito de configuração, não caso de
                        // negócio — o enum e os beans são declarados no mesmo módulo.
                        () -> new IllegalStateException("Sem renderer para o formato " + format));
    }

    /**
     * Nome apresentado no download.
     *
     * <p>{@code options.fileName} é entrada externa (BR-170): passa por uma allowlist de caracteres
     * antes de virar {@code Content-Disposition}. Sem isso, um nome com {@code "} ou quebra de
     * linha permitiria injetar cabeçalho de resposta.
     */
    private String fileNameOf(ReportType reportType, ExportFormat format, ExportOptions options) {
        String requested = options == null ? null : options.fileName();
        String base =
                requested == null || requested.isBlank()
                        ? "DevTime_%s_%s"
                                .formatted(
                                        reportType.name(),
                                        clock.now().atZone(clock.tenantZone()).format(FILE_STAMP))
                        : UNSAFE_FILE_NAME.matcher(requested).replaceAll("_");
        return base + "." + format.extension();
    }

    /**
     * Chave opaca, prefixada pelo tenant (integrations.md §6.2).
     *
     * <p>Deriva do identificador da execução e não do nome do arquivo: é o que permite rastrear o
     * objeto até o registro de {@code report_executions} e o que impede que um nome escolhido pelo
     * usuário influencie o caminho no storage — mesma decisão de {@code StorageKeyGenerator} em
     * {@code 015}.
     */
    private String storageKeyOf(UUID executionId, ExportFormat format) {
        return "%s/exports/%s.%s"
                .formatted(tenantContext.requireTenantId(), executionId, format.extension());
    }

    private Path temporaryFile(ExportFormat format) {
        try {
            return Files.createTempFile("devtime-export-", "." + format.extension());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar o arquivo temporário da exportação", e);
        }
    }

    /**
     * §19.1: o temporário some sempre, inclusive quando a geração falha.
     *
     * <p>Um temporário órfão é conteúdo de relatório — dado pessoal — fora do storage controlado e
     * fora de qualquer política de expiração.
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Falhar aqui mascararia a exceção original da geração, que é a informação útil.
        }
    }
}
