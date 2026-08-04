package com.devtime.report.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Exceções de regra da feature 012 (§27 de specs/012).
 *
 * <p>BR-063: fábricas nomeadas pela regra, nunca construtor genérico.
 *
 * <p>CP-18 e §28: <b>nenhum detalhe carrega conteúdo de linha de relatório</b>. Descrição de work
 * log é texto livre com dado pessoal, e os detalhes da resposta de erro são registrados junto com
 * ela. O que sai aqui são identificadores de recorte e nomes de campo.
 */
public final class ReportExceptions {

    /** RN-705: teto do intervalo, em dias, contando as duas pontas (BR-149). */
    public static final int MAX_RANGE_DAYS = 366;

    private ReportExceptions() {}

    /** RN-705 / {@code DEVTIME-3001}: intervalo acima de 366 dias (CX-14). */
    public static BusinessRuleException dateRangeTooLarge(long requestedDays) {
        return new DateRangeTooLargeException(requestedDays);
    }

    /**
     * RN-711 e CE-P-10 / {@code DEVTIME-1101}: escopo além do permitido ao papel.
     *
     * <p>Os detalhes nomeiam o filtro recusado e <b>não</b> o recurso: o objetivo de verificar o
     * escopo antes da existência (§6.2) é não vazar, pelo erro, que o contrato existe — devolver o
     * identificador dele nos detalhes desfaria isso na mesma resposta.
     */
    public static BusinessRuleException scopeViolation(String requestedFilter) {
        return new ReportScopeViolationException(requestedFilter);
    }

    /** §6.1 e §12 de reports.md / {@code DEVTIME-3002}: período {@code SCHEDULED}. */
    public static BusinessRuleException periodNotStarted() {
        return new PeriodNotStartedException();
    }

    /** §8.1 / {@code DEVTIME-3003}: parâmetros incompatíveis com o tipo de relatório. */
    public static BusinessRuleException parametersIncompatible(String missingParameter) {
        return new ParametersIncompatibleException(missingParameter);
    }

    /** §6.3 / {@code DEVTIME-3007}: agrupamento fora dos suportados pelo tipo. */
    public static BusinessRuleException groupingUnsupported(
            ReportType reportType, ReportGrouping grouping) {
        return new GroupingUnsupportedException(reportType, grouping);
    }

    /**
     * §8.3 / {@code DEVTIME-3004}: a exportação não está no estado que a operação exige.
     *
     * <p>Cobre as duas condições: download antes de a geração concluir e cancelamento de uma
     * exportação já em {@code PROCESSING} (§11.1). {@code reports.md} §12 tabela apenas a primeira;
     * a segunda é a mesma condição vista do outro lado, e ocupar um oitavo número para ela criaria
     * um contrato novo onde não há distinção que o cliente precise tratar.
     */
    public static BusinessRuleException exportNotReady(ExportStatus status) {
        return new ExportNotReadyException(status);
    }

    /** §8.3 / {@code DEVTIME-3005}: o arquivo expirou; é preciso gerar novamente (CX-18). */
    public static BusinessRuleException exportExpired() {
        return new ExportExpiredException();
    }

    /** §8.3 / {@code DEVTIME-3006}: a geração falhou; a resposta carrega o motivo. */
    public static BusinessRuleException exportFailed(String failureReason) {
        return new ExportFailedException(failureReason);
    }

    /** RN-705 (§27). */
    public static class DateRangeTooLargeException extends BusinessRuleException {

        DateRangeTooLargeException(long requestedDays) {
            super(
                    ErrorCode.DATE_RANGE_EXCEEDED,
                    Map.of("requestedDays", requestedDays, "maxDays", MAX_RANGE_DAYS),
                    "Intervalo de datas excede o máximo permitido");
        }
    }

    /** RN-711, CE-P-10 (§27). */
    public static class ReportScopeViolationException extends BusinessRuleException {

        ReportScopeViolationException(String requestedFilter) {
            super(
                    ErrorCode.PERMISSION_DENIED,
                    Map.of("requestedFilter", requestedFilter, "allowedScope", "myWorkLogs"),
                    "Escopo não permitido para o papel");
        }
    }

    /** §6.1: um período que ainda não começou não tem o que relatar. */
    public static class PeriodNotStartedException extends BusinessRuleException {

        PeriodNotStartedException() {
            super(
                    ErrorCode.REPORT_PERIOD_NOT_STARTED,
                    Map.of("periodStatus", "SCHEDULED"),
                    "O período ainda não começou");
        }
    }

    /** §8.1 (§27). */
    public static class ParametersIncompatibleException extends BusinessRuleException {

        ParametersIncompatibleException(String missingParameter) {
            super(
                    ErrorCode.REPORT_PARAMETERS_INCOMPATIBLE,
                    Map.of("parameter", missingParameter),
                    "Parâmetros incompatíveis com o tipo de relatório");
        }
    }

    /** §6.3 (§27). */
    public static class GroupingUnsupportedException extends BusinessRuleException {

        GroupingUnsupportedException(ReportType reportType, ReportGrouping grouping) {
            super(
                    ErrorCode.REPORT_GROUPING_UNSUPPORTED,
                    Map.of(
                            "reportType",
                            reportType.name(),
                            "groupBy",
                            grouping.name(),
                            "supported",
                            ReportGrouping.supportedBy(reportType).stream()
                                    .map(Enum::name)
                                    .sorted()
                                    .toList()),
                    "Agrupamento inválido para este relatório");
        }
    }

    /** §4.10 (§27). */
    public static class ExportNotReadyException extends BusinessRuleException {

        ExportNotReadyException(ExportStatus status) {
            super(
                    ErrorCode.EXPORT_NOT_READY,
                    Map.of("status", status.name()),
                    "A exportação não está no estado exigido pela operação");
        }
    }

    /** §4.10 (§27). */
    public static class ExportExpiredException extends BusinessRuleException {

        ExportExpiredException() {
            super(
                    ErrorCode.EXPORT_EXPIRED,
                    Map.of("retentionDays", 7),
                    "O arquivo expirou. Gere novamente");
        }
    }

    /** §4.10 (§27). */
    public static class ExportFailedException extends BusinessRuleException {

        ExportFailedException(String failureReason) {
            super(
                    ErrorCode.EXPORT_FAILED,
                    Map.of("failureReason", String.valueOf(failureReason)),
                    "A geração da exportação falhou");
        }
    }
}
