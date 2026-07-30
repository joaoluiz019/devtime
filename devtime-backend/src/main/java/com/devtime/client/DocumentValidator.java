package com.devtime.client;

import com.devtime.client.domain.ClientExceptions;
import com.devtime.client.domain.DocumentType;
import org.springframework.stereotype.Component;

/**
 * Validação de CPF e CNPJ por dígitos verificadores (RN-402).
 *
 * <p>A validação existe porque o erro de digitação em documento fiscal só costuma ser descoberto na
 * emissão da nota — quando corrigi-lo já custa retrabalho ao cliente e ao prestador.
 *
 * <p>Documentos com todos os dígitos iguais são rejeitados explicitamente (CX-04): {@code
 * 111.111.111-11} <b>passa</b> na fórmula dos dígitos verificadores, mas é sequência inválida
 * conhecida e nunca corresponde a uma pessoa real.
 *
 * <p>{@link DocumentType#OTHER} não é validado: documentos estrangeiros não possuem o mesmo
 * algoritmo, e reprová-los impediria cadastrar clientes fora do Brasil.
 */
@Component
public class DocumentValidator {

    private static final int CPF_LENGTH = 11;
    private static final int CNPJ_LENGTH = 14;
    private static final int[] CNPJ_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /**
     * @param normalizedDocument documento já sem máscara ({@link DocumentNormalizer})
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2402} / {@code 422}
     */
    public void assertValid(DocumentType type, String normalizedDocument) {
        if (normalizedDocument == null || type == null || type == DocumentType.OTHER) {
            return;
        }
        boolean valid =
                switch (type) {
                    case CPF -> isValidCpf(normalizedDocument);
                    case CNPJ -> isValidCnpj(normalizedDocument);
                    case OTHER -> true;
                };
        if (!valid) {
            throw ClientExceptions.invalidDocument(type); // RN-402
        }
    }

    public boolean isValidCpf(String document) {
        if (document.length() != CPF_LENGTH || hasAllDigitsEqual(document)) {
            return false;
        }
        int firstCheck = cpfCheckDigit(document, 9, 10);
        int secondCheck = cpfCheckDigit(document, 10, 11);
        return firstCheck == digitAt(document, 9) && secondCheck == digitAt(document, 10);
    }

    public boolean isValidCnpj(String document) {
        if (document.length() != CNPJ_LENGTH || hasAllDigitsEqual(document)) {
            return false;
        }
        int firstCheck = cnpjCheckDigit(document, 12);
        int secondCheck = cnpjCheckDigit(document, 13);
        return firstCheck == digitAt(document, 12) && secondCheck == digitAt(document, 13);
    }

    /** Peso decrescente a partir de {@code startWeight}, módulo 11 com resto < 2 valendo zero. */
    private int cpfCheckDigit(String document, int length, int startWeight) {
        int sum = 0;
        for (int position = 0; position < length; position++) {
            sum += digitAt(document, position) * (startWeight - position);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    /** Pesos cíclicos 2..9 aplicados da direita para a esquerda (tabela {@link #CNPJ_WEIGHTS}). */
    private int cnpjCheckDigit(String document, int length) {
        int offset = CNPJ_WEIGHTS.length - length;
        int sum = 0;
        for (int position = 0; position < length; position++) {
            sum += digitAt(document, position) * CNPJ_WEIGHTS[offset + position];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private boolean hasAllDigitsEqual(String document) {
        return document.chars().distinct().count() == 1;
    }

    private int digitAt(String document, int position) {
        return Character.digit(document.charAt(position), 10);
    }
}
