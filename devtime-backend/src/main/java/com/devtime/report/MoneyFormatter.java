package com.devtime.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Valor monetário derivado de uma duração (RN-709, ART-040 a ART-042).
 *
 * <p><b>{@code HALF_UP}, não para baixo</b> (CP-07). É o arredondamento que o cliente espera em
 * valores financeiros. Diferentemente de RN-113, que arredonda <b>duração</b> para baixo por
 * princípio (PR-03), o valor aqui deriva de uma duração <b>já truncada</b>: aplicar o truncamento
 * outra vez cobraria menos do que o tempo efetivamente registrado.
 *
 * <p>ART-042: o armazenamento mantém 4 casas; a apresentação e a exportação usam 2. Este formatador
 * é a fronteira de apresentação, então devolve sempre 2.
 */
@Component
public class MoneyFormatter {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /** ART-042: 2 casas na apresentação, {@code HALF_UP}. */
    public static final int SCALE = 2;

    /**
     * Valor de uma duração à taxa informada.
     *
     * <p>A divisão por 60 usa escala interna de 10 casas <b>antes</b> do arredondamento final: com
     * a escala de apresentação já aplicada na divisão, 1 minuto a R$ 150,00/hora daria R$ 2,50
     * arredondado duas vezes, e o erro reapareceria multiplicado pela quantidade de linhas.
     *
     * @param hourlyRate nulo devolve nulo, e não zero: contrato sem valor hora <b>omite</b> as
     *     colunas monetárias (CE-R-05, FA-18), e zero seria a afirmação de que o trabalho não vale
     *     nada
     */
    public BigDecimal valueOf(int minutes, BigDecimal hourlyRate) {
        if (hourlyRate == null) {
            return null;
        }
        return hourlyRate
                .multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, 10, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Soma apresentável de valores já calculados; nulo é tratado como ausência, não como zero. */
    public BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
