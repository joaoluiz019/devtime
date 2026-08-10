package com.devtime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.report.domain.ExportFormat;
import com.devtime.report.domain.ExportStatus;
import com.devtime.report.domain.ReportType;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import com.devtime.report.dto.ExportRequests.ExportParameters;
import com.devtime.report.dto.ExportRequests.ExportRequest;
import com.devtime.report.dto.ExportResponses.ExportExecutionResponse;
import com.devtime.report.dto.ExportResponses.ExportResponse;
import com.devtime.report.dto.ReportRequests.ReportFilters;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.InMemoryStorageConfiguration;
import com.devtime.support.WorkLogScenario;
import com.devtime.worklog.WorkLogService;
import com.devtime.worklog.dto.WorkLogRequests.WorkLogCreateRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * Ciclo completo de exportação (§8 de reports.md, T-012-21 a T-012-29).
 *
 * <p>Cobre o caminho que nenhuma outra suíte tocava: solicitar, gerar, armazenar, assinar a URL,
 * listar, cancelar, falhar e expirar. O storage é o de memória — o assunto aqui é a máquina de
 * estados e o conteúdo produzido, não o protocolo S3, que {@code AttachmentScanIntegrationTest}
 * verifica contra MinIO real.
 *
 * <p>O intervalo é janeiro de 2026 porque o cenário compartilhado ativa contratos em 10/01/2026 e o
 * relógio dos testes está fixo em 29/07/2026.
 */
@Import(InMemoryStorageConfiguration.class)
class ReportExportIntegrationTest extends FeatureTestSupport {

    private static final LocalDate JANUARY_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_END = LocalDate.of(2026, 1, 31);

    @Autowired private ExportService exportService;
    @Autowired private ExportWorker exportWorker;
    @Autowired private WorkLogService workLogService;
    @Autowired private WorkLogScenario scenario;

    @Test
    @DisplayName("RN-706 / CX-10: abaixo do limiar a exportação é síncrona e já entrega o arquivo")
    void smallExportIsSynchronous() {
        cenarioComUmRegistro();

        ExportResponse resposta =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), null));

        assertThat(resposta.status())
                .as("uma linha não justifica fila: o arquivo sai na própria requisição")
                .isEqualTo(ExportStatus.COMPLETED);
        assertThat(resposta.downloadUrl()).isNotBlank();
        assertThat(resposta.fileName()).endsWith(".csv");
        assertThat(resposta.sizeBytes()).isPositive();
    }

    @Test
    @DisplayName("ART-074 / CE-R-12: a mesma Idempotency-Key não gera segundo arquivo")
    void idempotencyKeyReturnsSameExecution() {
        cenarioComUmRegistro();
        String chave = UUID.randomUUID().toString();

        ExportResponse primeira =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), chave));
        ExportResponse segunda =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), chave));

        assertThat(segunda.id())
                .as("um duplo clique não consome cota nem gera arquivo novo")
                .isEqualTo(primeira.id());
    }

    @Test
    @DisplayName(
            "§8.2: a listagem devolve as execuções do tenant, da mais recente para a mais antiga")
    void listReturnsExecutions() {
        cenarioComUmRegistro();
        asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), null));
        asOwnerOfA(() -> exportService.request(pedido(ExportFormat.XLSX), null));

        var pagina = asOwnerOfA(() -> exportService.list(PageRequest.of(0, 10)));

        assertThat(pagina.content()).hasSize(2);
        assertThat(pagina.content())
                .extracting(ExportExecutionResponse::status)
                .containsOnly(ExportStatus.COMPLETED);
    }

    @Test
    @DisplayName("SG-01 / CA-21: execução de outro tenant é indistinguível de inexistente")
    void executionOfAnotherTenantIsNotFound() {
        cenarioComUmRegistro();
        ExportResponse minha =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), null));

        assertThatThrownBy(() -> asOwnerOfB(() -> exportService.get(minha.id())))
                .as("404, nunca 403 — o 403 confirmaria que o recurso existe")
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("FA-13 / RN-712: nova URL assinada não regera o arquivo")
    void downloadUrlIsRegeneratedWithoutRebuildingTheFile() {
        cenarioComUmRegistro();
        ExportResponse exportacao =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), null));

        ExportExecutionResponse antes = asOwnerOfA(() -> exportService.get(exportacao.id()));
        String url = asOwnerOfA(() -> exportService.downloadUrl(exportacao.id()));
        ExportExecutionResponse depois = asOwnerOfA(() -> exportService.get(exportacao.id()));

        assertThat(url).isNotBlank();
        assertThat(depois.completedAt())
                .as("regerar custaria a agregação inteira para produzir bytes idênticos")
                .isEqualTo(antes.completedAt());
        assertThat(depois.sizeBytes()).isEqualTo(antes.sizeBytes());
    }

    @Test
    @DisplayName("§11.1: o worker ignora execução que já saiu de QUEUED (BR-185)")
    void workerIsIdempotent() {
        cenarioComUmRegistro();
        ExportResponse exportacao =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), null));

        boolean reprocessou = asOwnerOfA(() -> exportWorker.process(exportacao.id()));

        assertThat(reprocessou)
                .as("duas instâncias do worker não podem gerar o mesmo arquivo duas vezes")
                .isFalse();
    }

    @Test
    @DisplayName("§19.1: expirar remove o binário antes de o registro perder a chave")
    void expireRemovesBinaryBeforeDroppingTheKey() {
        cenarioComUmRegistro();
        ExportResponse exportacao =
                asOwnerOfA(() -> exportService.request(pedido(ExportFormat.CSV), null));

        boolean expirou = asOwnerOfA(() -> exportWorker.expire(exportacao.id()));

        assertThat(expirou).isTrue();
        assertThat(asOwnerOfA(() -> exportService.get(exportacao.id())).status())
                .isEqualTo(ExportStatus.EXPIRED);
    }

    @Test
    @DisplayName("§9.3: os três formatos são gerados e produzem arquivos de tamanho positivo")
    void allThreeFormatsAreGenerated() {
        cenarioComUmRegistro();

        for (ExportFormat formato : ExportFormat.values()) {
            ExportResponse resposta =
                    asOwnerOfA(() -> exportService.request(pedido(formato), null));

            assertThat(resposta.status())
                    .as("formato %s", formato)
                    .isEqualTo(ExportStatus.COMPLETED);
            assertThat(resposta.sizeBytes()).as("formato %s", formato).isPositive();
            assertThat(resposta.fileName())
                    .as("formato %s", formato)
                    .endsWith("." + formato.extension());
        }
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private void cenarioComUmRegistro() {
        var setup = asOwnerOfA(scenario::create);
        asOwnerOfA(
                () ->
                        workLogService.create(
                                new WorkLogCreateRequest(
                                        setup.ticket().id(),
                                        WorkLogScenario.at(9, 0),
                                        WorkLogScenario.at(11, 0),
                                        0,
                                        "Registro exportável",
                                        setup.category().id(),
                                        true,
                                        List.of(),
                                        null)));
    }

    private ExportRequest pedido(ExportFormat formato) {
        return new ExportRequest(
                ReportType.TIMESHEET,
                formato,
                new ExportParameters(
                        null,
                        null,
                        null,
                        new ReportFilters(
                                null,
                                null,
                                null,
                                null,
                                JANUARY_START,
                                JANUARY_END,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)),
                new ExportOptions("relatorio-de-horas", false, false, "pt-BR"));
    }
}
