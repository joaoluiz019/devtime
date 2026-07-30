package com.devtime.contract;

import org.springframework.stereotype.Component;

/**
 * Rateio proporcional de período parcial (RN-217).
 *
 * <p>Cobrar o mês cheio por 10 dias de serviço gera disputa; é essa a razão de a proporção existir.
 *
 * <p>BR-066: cálculo puro, sem efeito colateral e sem acesso a dados. CX-09 e ART-034: a operação
 * ocorre inteiramente em inteiros — passar por {@code double} introduziria erro de representação
 * exatamente no número que vai para a fatura do cliente.
 */
@Component
public class ProrationCalculator {

    /**
     * {@code round(monthlyMinutes × periodDays / fullCycleDays)}, com arredondamento meio para cima
     * feito por aritmética inteira.
     *
     * @param monthlyMinutes pacote mensal do contrato
     * @param periodDays dias corridos do período
     * @param fullCycleDays dias corridos do ciclo cheio correspondente
     */
    public int prorate(int monthlyMinutes, int periodDays, int fullCycleDays) {
        if (fullCycleDays <= 0) {
            // Ciclo de duração não positiva é impossível pela construção do gerador; falhar alto
            // (CG-06) evita divisão por zero silenciosa caso a pré-condição seja quebrada.
            throw new IllegalArgumentException("fullCycleDays deve ser positivo");
        }
        long product = (long) monthlyMinutes * periodDays;
        // O termo (fullCycleDays / 2) é o arredondamento meio para cima sem ponto flutuante.
        return (int) ((product + fullCycleDays / 2L) / fullCycleDays);
    }
}
