package com.devtime.attachment.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Exceções de regra da feature 015 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra que a origina.
 *
 * <p>§19.1 e CP-19: <b>nenhum detalhe carrega o nome do arquivo</b>. O nome é texto livre e pode
 * conter dado pessoal — {@code contrato-joao-silva.pdf} é um exemplo trivial —, e os detalhes da
 * resposta de erro são registrados junto com ela.
 */
public final class AttachmentExceptions {

    private AttachmentExceptions() {}

    /** RN-801: arquivo acima do limite por arquivo (FA-08, CX-01). */
    public static BusinessRuleException fileTooLarge(long sizeBytes, long maxBytes) {
        return new FileTooLargeException(sizeBytes, maxBytes);
    }

    /**
     * RN-801: quota do tenant excedida (FA-09, CX-17).
     *
     * <p>Os detalhes informam o consumo atual porque FA-09 o exige: "quota excedida" sem o número
     * não indica ao usuário quanto precisa liberar.
     */
    public static BusinessRuleException quotaExceeded(
            long usedBytes, long limitBytes, long attemptedBytes) {
        return new QuotaExceededException(usedBytes, limitBytes, attemptedBytes);
    }

    /** RN-802, passo 7 de §6.1: {@code contentType} declarado fora da allowlist. */
    public static BusinessRuleException unsupportedFileType(String declaredContentType) {
        return new UnsupportedFileTypeException(declaredContentType);
    }

    /**
     * RN-802, passo 8 de §6.1: a assinatura binária não corresponde ao tipo declarado.
     *
     * <p>FA-06, CX-03 a CX-05, CX-07. A resposta é indistinguível de {@link
     * #unsupportedFileType(String)}: informar qual das duas camadas barrou diria ao atacante o que
     * ajustar na próxima tentativa.
     */
    public static BusinessRuleException contentTypeMismatch(String declaredContentType) {
        return new ContentTypeMismatchException(declaredContentType);
    }

    /**
     * RN-803: download em {@code PENDING} ou {@code FAILED} (FA-03, FA-05).
     *
     * <p>{@code 409}. Os dois estados compartilham status porque ambos são "ainda não verificado" —
     * mas a UI os distingue por {@code scanStatus}, que vai nos detalhes: em {@code PENDING} a
     * orientação é aguardar; em {@code FAILED}, reenviar (CP-20).
     */
    public static BusinessRuleException notScanned(ScanStatus scanStatus) {
        return new AttachmentNotScannedException(scanStatus);
    }

    /** RN-803: download de arquivo infectado (FA-04). {@code 403}; o binário já foi removido. */
    public static BusinessRuleException infected() {
        return new AttachmentInfectedException();
    }

    /** RN-806: limite de anexos no alvo (FA-10, CX-19, CX-21). */
    public static BusinessRuleException limitExceeded(String target, int limit) {
        return new AttachmentLimitExceededException(target, limit);
    }

    /**
     * INV-ATT-01: nenhum alvo ou dois alvos (FA-17).
     *
     * <p>{@code DEVTIME-2000} / {@code 422}, conforme §12 da spec.
     */
    public static BusinessRuleException targetExclusivityViolated() {
        return new TargetExclusivityViolatedException();
    }

    /** RN-801. */
    public static final class FileTooLargeException extends BusinessRuleException {
        private FileTooLargeException(long sizeBytes, long maxBytes) {
            super(
                    ErrorCode.ATTACHMENT_TOO_LARGE,
                    Map.of("sizeBytes", sizeBytes, "maxBytes", maxBytes),
                    "Arquivo excede o tamanho máximo permitido");
        }
    }

    /** RN-801. */
    public static final class QuotaExceededException extends BusinessRuleException {
        private QuotaExceededException(long usedBytes, long limitBytes, long attemptedBytes) {
            super(
                    ErrorCode.ATTACHMENT_TOO_LARGE,
                    Map.of(
                            "usedBytes", usedBytes,
                            "limitBytes", limitBytes,
                            "attemptedBytes", attemptedBytes),
                    "Quota de armazenamento da organização excedida");
        }
    }

    /** RN-802, passo 7. */
    public static final class UnsupportedFileTypeException extends BusinessRuleException {
        private UnsupportedFileTypeException(String declaredContentType) {
            super(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED,
                    Map.of("contentType", describe(declaredContentType)),
                    "Tipo de arquivo não permitido");
        }
    }

    /**
     * O tipo declarado pode ser nulo — o cliente simplesmente não o informou (§17.1).
     *
     * <p>{@code Map.of} recusa valor nulo, e sem esta normalização a ausência do cabeçalho
     * produziria {@code NullPointerException} e, portanto, {@code 500 DEVTIME-9001} em vez do
     * {@code 415 DEVTIME-2702} documentado — trocando um erro de entrada por um erro do servidor
     * exatamente no caminho que mais interessa a quem sonda a allowlist.
     */
    private static String describe(String declaredContentType) {
        return declaredContentType == null ? "" : declaredContentType;
    }

    /** RN-802, passo 8. */
    public static final class ContentTypeMismatchException extends BusinessRuleException {
        private ContentTypeMismatchException(String declaredContentType) {
            super(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED,
                    Map.of("contentType", describe(declaredContentType)),
                    "Tipo de arquivo não permitido");
        }
    }

    /** RN-803, {@code PENDING} e {@code FAILED}. */
    public static final class AttachmentNotScannedException extends BusinessRuleException {
        private AttachmentNotScannedException(ScanStatus scanStatus) {
            super(
                    ErrorCode.ATTACHMENT_NOT_SCANNED,
                    Map.of("scanStatus", scanStatus.name()),
                    "Arquivo em verificação de segurança");
        }
    }

    /** RN-803, {@code INFECTED}. */
    public static final class AttachmentInfectedException extends BusinessRuleException {
        private AttachmentInfectedException() {
            super(
                    ErrorCode.ATTACHMENT_NOT_SCANNED,
                    HttpStatus.FORBIDDEN,
                    Map.of("scanStatus", ScanStatus.INFECTED.name()),
                    "Arquivo bloqueado por segurança");
        }
    }

    /** RN-806. */
    public static final class AttachmentLimitExceededException extends BusinessRuleException {
        private AttachmentLimitExceededException(String target, int limit) {
            super(
                    ErrorCode.ATTACHMENT_LIMIT_EXCEEDED,
                    Map.of("target", target, "limit", limit),
                    "Máximo de anexos atingido");
        }
    }

    /** INV-ATT-01. */
    public static final class TargetExclusivityViolatedException extends BusinessRuleException {
        private TargetExclusivityViolatedException() {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of(),
                    "Informe exatamente um destino para o anexo");
        }
    }
}
