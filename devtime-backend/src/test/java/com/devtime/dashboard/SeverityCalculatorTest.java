package com.devtime.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.dashboard.domain.ContractSeverity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Escala de severidade do painel (§6.2 de specs/010, RN-602).
 *
 * <p>R-03 identifica "severidade divergindo dos alertas por e-mail" como risco da feature: o
 * cliente receber um alerta em 70% enquanto a tela mostra "OK" destrói a confiança nos dois. Esta
 * suíte é o que prova que os dois números saem dos mesmos limiares.
 */
class SeverityCalculatorTest {

    private final SeverityCalculator calculator = new SeverityCalculator();

    @ParameterizedTest(name = "consumo de {0}% com limiares padrão produz {1}")
    @CsvSource({
        "0,     OK",
        "49.99, OK",
        "50,    INFO",
        "79.99, INFO",
        "80,    WARNING",
        "99.99, WARNING",
        "100,   CRITICAL",
        "105.07, CRITICAL"
    })
    @DisplayName("§6.2: a escala padrão é 50 / 80 / 100")
    void defaultScale(String rate, ContractSeverity expected) {
        assertThat(calculator.calculate(new BigDecimal(rate), List.of())).isEqualTo(expected);
    }

    @ParameterizedTest(name = "consumo de {0}% com limiares [70, 90] produz {1}")
    @CsvSource({"69.99, OK", "70, INFO", "89.99, INFO", "90, WARNING", "100, CRITICAL"})
    @DisplayName("CP-04 / CA-03: a escala usa os limiares do CONTRATO, nunca 50/80/100 fixos")
    void contractThresholdsWin(String rate, ContractSeverity expected) {
        assertThat(calculator.calculate(new BigDecimal(rate), List.of(70, 90))).isEqualTo(expected);
    }

    @Test
    @DisplayName("CX-05 / CE-10: contrato HOURLY_OPEN tem rate 0 e severidade OK")
    void hourlyOpenIsAlwaysOk() {
        assertThat(calculator.calculate(BigDecimal.ZERO, List.of())).isEqualTo(ContractSeverity.OK);
    }

    @Test
    @DisplayName("RN-222 / CX-06: saldo zerado com consumo produz rate 100 e severidade CRITICAL")
    void overageIsCriticalRegardlessOfThresholds() {
        // Mesmo com um limiar configurado acima de 100, excedente é sempre crítico: RN-604 trata
        // impacto financeiro direto, não preferência de alerta.
        assertThat(calculator.calculate(new BigDecimal("100"), List.of(120)))
                .isEqualTo(ContractSeverity.CRITICAL);
    }

    @Test
    @DisplayName("§6.2: limiares nulos ou vazios recorrem ao padrão em vez de degradar para OK")
    void nullThresholdsFallBackToDefault() {
        assertThat(calculator.calculate(new BigDecimal("85"), null))
                .isEqualTo(ContractSeverity.WARNING);
    }

    @Test
    @DisplayName("§6.2: um único limiar configurado nunca produz WARNING abaixo de 100%")
    void singleThreshold() {
        assertThat(calculator.calculate(new BigDecimal("95"), List.of(60)))
                .isEqualTo(ContractSeverity.INFO);
    }

    @Test
    @DisplayName("RN-603: o limiar atingido identifica o alerta, e é o do contrato")
    void highestReachedThresholdComesFromContract() {
        assertThat(calculator.highestReachedThreshold(new BigDecimal("92"), List.of(70, 90)))
                .contains(90);
        assertThat(calculator.highestReachedThreshold(new BigDecimal("69"), List.of(70, 90)))
                .isEmpty();
    }

    @Test
    @DisplayName("§6.2: limiares fora de ordem são normalizados antes de aplicados")
    void thresholdsAreSorted() {
        assertThat(calculator.calculate(new BigDecimal("75"), List.of(90, 70)))
                .isEqualTo(ContractSeverity.INFO);
    }
}
