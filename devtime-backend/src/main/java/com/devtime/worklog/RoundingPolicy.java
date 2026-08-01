package com.devtime.worklog;

import org.springframework.stereotype.Component;

/**
 * Arredondamento configurável do tempo líquido (RN-113).
 *
 * <p>BR-067: estratégia configurável isolada do cálculo. O valor vem de {@code
 * tenant.settings.roundingMinutes} (entities.md §6.1.1), cujos valores usuais são 5, 6, 10, 15 e
 * 30; {@code 0} desativa e é o padrão.
 *
 * <p><b>A direção é sempre para baixo, e isso não é configurável</b> (PR-03, BR-145, CP-06).
 * Arredondar para cima cobraria do cliente tempo que não foi trabalhado — a violação de confiança
 * mais direta que o produto poderia cometer. A classe existe separada justamente para que essa
 * direção seja verificável em um único ponto (R-04).
 */
@Component
public class RoundingPolicy {

    /**
     * {@code floor(netMinutes / roundingMinutes) × roundingMinutes}.
     *
     * <p>OB-05 / CX-14: uma sessão de 10 minutos com múltiplo 15 resulta em {@code 0} e é rejeitada
     * em seguida por RN-115. Parece defeito e é a consequência aritmética inevitável de arredondar
     * para baixo — a alternativa seria cobrar 15 minutos por 10 trabalhados.
     *
     * @param roundingMinutes múltiplo configurado; {@code 0} ou negativo desativa o arredondamento
     * @return o valor arredondado, nunca maior que a entrada
     */
    public int roundDown(int netMinutes, int roundingMinutes) {
        if (roundingMinutes <= 0 || netMinutes <= 0) {
            return netMinutes;
        }
        return (netMinutes / roundingMinutes) * roundingMinutes;
    }
}
