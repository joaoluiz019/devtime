package com.devtime.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Duração em {@code HH:MM} e em horas decimais (RN-710, ART-035).
 *
 * <p><b>As duas representações existem porque servem a leitores diferentes</b> (OB-03, CP-06).
 * {@code HH:MM} é o que o cliente confere visualmente; horas decimais é o que ele soma. Uma coluna
 * só forçaria a escolha, e o cliente resolveria manualmente — convertendo {@code 149:00} em {@code
 * 149} na calculadora, que é justamente o retrabalho que RN-710 elimina.
 *
 * <p>Não converte para {@code Duration} nem para {@code double} em passo algum: a entrada é minuto
 * inteiro (ART-034) e a saída é texto ou {@code BigDecimal}. Uma passagem por ponto flutuante
 * reintroduziria o erro de acumulação que ART-034 existe para evitar.
 */
@Component
public class DurationFormatter {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /**
     * {@code HH:MM}, com as horas sem teto de 24 — {@code 149:00} é uma leitura válida (§6).
     *
     * <p>Duração negativa aparece com o sinal antes das horas ({@code -02:30}) e nunca com o sinal
     * dentro dos minutos. Ela ocorre em saldo, não em registro de horas, mas o formatador é o mesmo
     * e devolver {@code -2:-30} seria ilegível.
     */
    public String toLabel(int minutes) {
        int absolute = Math.abs(minutes);
        return "%s%02d:%02d".formatted(minutes < 0 ? "-" : "", absolute / 60, absolute % 60);
    }

    /**
     * Horas decimais com 2 casas — a coluna somável do XLSX (XLS-02, coluna 11 de §9.2).
     *
     * <p>{@code HALF_UP} e não truncamento: aqui não se está cobrando tempo, apenas apresentando o
     * mesmo minuto em outra unidade. A regra de truncar (PR-03, RN-113) vale na <b>captura</b> da
     * duração, que já aconteceu — aplicá-la de novo na apresentação subtrairia tempo de novo.
     */
    public BigDecimal toDecimalHours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }
}
