package com.devtime.contract.domain;

import java.time.LocalDate;

/**
 * Entrada do gerador de períodos — apenas o que define o ciclo (spec 004 §6.2).
 *
 * <p>É um objeto de valor sem identidade e sem acesso a banco: a geração é cálculo puro, o que
 * permite que a prévia ({@code POST /contracts/preview-periods}) e a geração real compartilhem
 * exatamente o mesmo código. CA-01 de contracts.md exige que a prévia reflita o que será gerado —
 * garantia que só é estrutural se houver um único algoritmo.
 *
 * @param type modelo comercial; {@code HOURLY_OPEN} zera {@code contractedMinutes} (RN-210)
 * @param monthlyMinutes pacote mensal; nulo em {@code HOURLY_OPEN}
 * @param startDate início da vigência
 * @param endDate fim da vigência; quando presente, trunca e interrompe a geração (RN-214)
 * @param billingDay dia de faturamento, 1 a 28 (RN-203)
 * @param prorateFirstPeriod rateio de período parcial (RN-217, CX-07)
 */
public record PeriodSpec(
        ContractType type,
        Integer monthlyMinutes,
        LocalDate startDate,
        LocalDate endDate,
        int billingDay,
        boolean prorateFirstPeriod) {}
