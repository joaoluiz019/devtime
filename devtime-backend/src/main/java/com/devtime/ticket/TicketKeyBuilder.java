package com.devtime.ticket;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Chave legível do ticket (RN-302, §6.2 da spec 007).
 *
 * <p>{@code key = {contract.code}-{number}} — {@code CT-0001} + {@code 42} → {@code CT-0001-42}. É
 * o identificador que sobrevive a e-mail, reunião e nota fiscal, enquanto um UUID não.
 *
 * <p>A chave é <b>derivada, não persistida</b>: entities.md §6.12 a marca 📐 e database.md §7.7 não
 * declara a coluna. Ambos precedem specs/007 §13.2 pela hierarquia IA-11. A consequência prática é
 * que a busca por chave decompõe o valor em (código do contrato, número) e resolve pelo índice
 * {@code uq_tickets_contract_number}, em vez de comparar uma string armazenada.
 *
 * <p>A decomposição corta no <b>último</b> hífen porque o código do contrato contém hífen por
 * construção ({@code CT-0001}); cortar no primeiro devolveria {@code CT} e {@code 0001-42}.
 */
@Component
public class TicketKeyBuilder {

    private static final char SEPARATOR = '-';

    /**
     * @param contractCode código do contrato, ex.: {@code CT-0001}
     * @param number sequencial do ticket dentro do contrato
     */
    public String build(String contractCode, int number) {
        return contractCode + SEPARATOR + number;
    }

    /**
     * Decompõe uma chave legível.
     *
     * @return vazio quando a chave não segue o formato — chave malformada e chave de outro tenant
     *     produzem a mesma resposta {@code 404} (ART-024, CX-19)
     */
    public Optional<TicketKey> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }
        int separator = key.lastIndexOf(SEPARATOR);
        if (separator <= 0 || separator == key.length() - 1) {
            return Optional.empty();
        }
        try {
            int number = Integer.parseInt(key.substring(separator + 1));
            if (number < 1) {
                return Optional.empty();
            }
            return Optional.of(new TicketKey(key.substring(0, separator), number));
        } catch (NumberFormatException notAKey) {
            return Optional.empty();
        }
    }

    /**
     * Chave decomposta.
     *
     * @param contractCode código do contrato
     * @param number sequencial dentro do contrato
     */
    public record TicketKey(String contractCode, int number) {}
}
