package com.devtime.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Código sequencial do contrato, {@code CT-0001} por tenant (INV-CTR-01).
 *
 * <p>O próximo código deriva do maior já emitido no tenant. <b>Alternativas rejeitadas:</b> uma
 * {@code SEQUENCE} do PostgreSQL é global e não se particiona por tenant, o que faria o primeiro
 * contrato de um tenant novo nascer com um número arbitrário; uma tabela de contadores
 * acrescentaria schema não previsto em database.md §7 e um ponto de contenção por tenant.
 *
 * <p>A corrida entre duas criações simultâneas é resolvida pelo índice único parcial {@code
 * uq_contracts_tenant_code}: a segunda transação falha e o chamador reemite o código. É a mesma
 * barreira que protegeria qualquer das alternativas.
 */
@Component
@RequiredArgsConstructor
public class ContractCodeGenerator {

    private static final String PREFIX = "CT-";
    private static final int PADDING = 4;

    private final ContractRepository repository;

    /**
     * @return o próximo código livre no tenant da sessão
     */
    public String next() {
        int highest = repository.findHighestCode().map(this::sequenceOf).orElse(0);
        return format(highest + 1);
    }

    private int sequenceOf(String code) {
        try {
            return Integer.parseInt(code.substring(PREFIX.length()));
        } catch (NumberFormatException notSequential) {
            // Código informado manualmente fora do padrão não participa da sequência; reiniciar do
            // zero é preferível a propagar erro, porque o índice único continua garantindo a
            // unicidade e o usuário pode ter escolhido um código próprio deliberadamente.
            return 0;
        }
    }

    private String format(int sequence) {
        return PREFIX + String.format("%0" + PADDING + "d", sequence);
    }
}
