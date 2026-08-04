package com.devtime.report;

import com.devtime.report.dto.ReportResponses.ReportBalance;
import com.devtime.report.dto.ReportResponses.ReportFinancial;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bloco {@code financial} do relatório (§6, RN-709, ART-043).
 *
 * <p>ART-043: o MVP não processa pagamentos. Estes valores são <b>informativos</b> — servem à
 * conversa com o cliente sobre o período, não a uma fatura emitida pelo sistema.
 *
 * <p><b>Lacuna reportada.</b> §6 de {@code reports.md} traz um único exemplo de {@code financial},
 * e nele o consumo <b>excede</b> o saldo: {@code regularMinutes} coincide com {@code
 * availableMinutes}. Nenhum documento define o que {@code regularMinutes} vale quando o período
 * consome menos do que tem disponível. Implementado como {@code min(consumido, disponível)} — as
 * horas efetivamente trabalhadas dentro do saldo —, que é a única leitura compatível com o exemplo
 * e com ART-043: fixar {@code availableMinutes} cobraria por horas não trabalhadas, o que o sistema
 * não tem base documental para afirmar. Isolado em um método para poder ser corrigido em um lugar
 * só quando a definição chegar.
 */
@Component
@RequiredArgsConstructor
public class ReportFinancialCalculator {

    private final MoneyFormatter moneyFormatter;

    /**
     * Compõe o bloco, ou devolve nulo quando ele não deve existir.
     *
     * <p>CP-03 e CE-R-05: sem {@code hourlyRate} o bloco inteiro é omitido, <b>sem erro</b>. As
     * duas causas — falta de {@code CONTRACT_VIEW_FINANCIAL} e contrato sem valor hora — chegam
     * aqui da mesma forma, um {@code hourlyRate} nulo, e é assim que devem chegar: distinguir as
     * duas na resposta diria a quem não tem permissão que o contrato <b>tem</b> valor hora.
     *
     * @param overageRate nulo recai sobre {@code hourlyRate}: um contrato que cobra excedente sem
     *     taxa própria o cobra pela taxa normal
     */
    public ReportFinancial compose(
            ReportBalance balance, BigDecimal hourlyRate, BigDecimal overageRate, String currency) {
        if (hourlyRate == null) {
            return null;
        }

        int regularMinutes = Math.min(balance.consumedMinutes(), balance.availableMinutes());
        int overageMinutes = balance.overageMinutes();
        BigDecimal effectiveOverageRate = overageRate == null ? hourlyRate : overageRate;

        BigDecimal regularValue = moneyFormatter.valueOf(regularMinutes, hourlyRate);
        BigDecimal overageValue = moneyFormatter.valueOf(overageMinutes, effectiveOverageRate);

        return new ReportFinancial(
                currency,
                hourlyRate,
                overageRate,
                regularMinutes,
                regularValue,
                overageMinutes,
                overageValue,
                regularValue.add(overageValue));
    }
}
