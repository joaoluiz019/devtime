package com.devtime.report;

import com.devtime.report.domain.ReportExceptions;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Limite do intervalo de datas (RN-705, §17.1 e §17.2).
 *
 * <p>O teto de 366 dias existe por dois motivos que se reforçam: proteção de desempenho (SG-08,
 * §20.1) e sentido de produto — nenhum relatório de cobrança cobre mais de um ano. 366 e não 365
 * para que um ano bissexto inteiro caiba (CX-14).
 *
 * <p>O intervalo é <b>fechado</b> nas duas pontas (BR-149): de 1º de janeiro a 31 de dezembro são
 * 365 dias, não 364. É por isso que a contagem soma um.
 */
@Component
public class ReportPeriodValidator {

    /**
     * Valida obrigatoriedade, ordem e tamanho do intervalo.
     *
     * @throws BusinessRuleException {@code DEVTIME-2000} quando falta uma das pontas ou {@code to}
     *     é anterior a {@code from} (§17.1, camada 1); {@code DEVTIME-3001} quando o intervalo
     *     excede o teto (§17.2, camada 2)
     */
    public void assertValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw incompleteRange();
        }
        if (to.isBefore(from)) {
            throw invertedRange();
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > ReportExceptions.MAX_RANGE_DAYS) {
            // CX-14: 366 passa, 367 não.
            throw ReportExceptions.dateRangeTooLarge(days);
        }
    }

    private BusinessRuleException incompleteRange() {
        return new RangeValidationException(
                Map.of("required", "from,to"), "Informe um intervalo válido");
    }

    private BusinessRuleException invertedRange() {
        return new RangeValidationException(
                Map.of("constraint", "to >= from"), "Data final anterior à inicial");
    }

    /**
     * §17.1: intervalo malformado é validação de <b>formato</b>, e {@code DEVTIME-2000} já está
     * registrado como {@code 400} no catálogo compartilhado. Mesma resolução adotada em {@code
     * 010-dashboard}.
     */
    static class RangeValidationException extends BusinessRuleException {

        RangeValidationException(Map<String, Object> details, String message) {
            super(ErrorCode.VALIDATION_FAILED, details, message);
        }
    }
}
