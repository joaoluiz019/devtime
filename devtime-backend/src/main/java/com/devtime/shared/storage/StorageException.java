package com.devtime.shared.storage;

/**
 * Falha de infraestrutura do object storage.
 *
 * <p>Não é exceção de negócio: não possui {@code DEVTIME-XXXX} próprio e chega ao cliente como
 * {@code DEVTIME-9001} pelo {@code GlobalExceptionHandler} (EX-04). Uma falha de storage no upload
 * é indisponibilidade, não entrada inválida — informar ao usuário um código de regra o levaria a
 * corrigir um arquivo que não tem defeito.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
