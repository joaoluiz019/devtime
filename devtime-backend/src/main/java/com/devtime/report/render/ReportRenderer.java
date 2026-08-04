package com.devtime.report.render;

import com.devtime.report.domain.ExportFormat;
import com.devtime.report.dto.ExportRequests.ExportOptions;
import java.io.OutputStream;

/**
 * Materialização de um relatório em arquivo (§22.3 de specs/012).
 *
 * <p><b>Escrever no {@link OutputStream} é obrigatório, não conveniência</b> (CP-13, OB-06). Um
 * XLSX de 50.000 linhas construído em memória consome centenas de megabytes, e duas exportações
 * simultâneas derrubariam a instância. Isso é diferente das otimizações de outras features, em que
 * a alternativa é lenta — aqui a alternativa <b>falha</b>. Por isso a assinatura não devolve {@code
 * byte[]}: um renderer que quisesse acumular tudo em memória teria de fazê-lo explicitamente,
 * contra a interface.
 */
public interface ReportRenderer {

    ExportFormat format();

    /**
     * Escreve o relatório no fluxo.
     *
     * <p>Não fecha o {@code output}: quem o abriu decide quando fechá-lo, porque o mesmo fluxo pode
     * estar sendo contado ou espelhado pelo chamador para preencher {@code sizeBytes}.
     */
    void render(RenderableReport report, ExportOptions options, OutputStream output);
}
