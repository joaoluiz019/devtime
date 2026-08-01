package com.devtime.worklog;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Cálculo de duração do registro de horas (RN-110 a RN-112, §6.3 de specs/008).
 *
 * <p>BR-066: cálculo puro, sem efeito colateral e sem acesso a banco. Toda aritmética é inteira
 * (BR-143, ART-034) — nenhum {@code double} entra aqui, porque uma duração representada em ponto
 * flutuante produz valores como {@code 149.99999} que, exibidos, viram o número que o cliente
 * contesta.
 *
 * <p>O arredondamento configurável é de {@link RoundingPolicy}, não desta classe: BR-067 separa
 * cálculo de estratégia configurável, e manter os dois juntos tornaria impossível testar a direção
 * do arredondamento isoladamente — que é o ponto de R-04.
 */
@Component
public class WorkLogCalculator {

    /**
     * RN-110: {@code floor((endedAt − startedAt) em segundos / 60)}.
     *
     * <p>RN-010 / BR-144: os segundos são <b>truncados por divisão inteira</b>, nunca arredondados.
     * Arredondar 11:30:59 para 151 minutos cobraria do cliente um minuto que não foi trabalhado
     * (PR-03); a divisão inteira de Java já trunca em direção a zero, e a entrada é sempre positiva
     * porque RN-114 é verificada antes.
     */
    public int grossMinutes(Instant startedAt, Instant endedAt) {
        return (int) (Duration.between(startedAt, endedAt).toSeconds() / 60);
    }

    /** RN-111: {@code grossMinutes − pausedMinutes}, antes do arredondamento. */
    public int netMinutes(int grossMinutes, int pausedMinutes) {
        return grossMinutes - pausedMinutes;
    }

    /**
     * RN-112 / RN-223: apenas horas faturáveis consomem o saldo do contrato.
     *
     * <p>As não faturáveis continuam aparecendo em relatórios como {@code nonBillableMinutes} — o
     * trabalho existiu e é visível; ele apenas não é cobrado.
     */
    public int billableMinutes(int netMinutes, boolean billable) {
        return billable ? netMinutes : 0;
    }
}
