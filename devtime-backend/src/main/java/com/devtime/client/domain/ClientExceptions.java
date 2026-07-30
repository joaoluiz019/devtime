package com.devtime.client.domain;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * Exceções de regra da feature 003 (spec §27).
 *
 * <p>BR-063: toda instância nasce de um método fábrica nomeado pela regra.
 */
public final class ClientExceptions {

    private ClientExceptions() {}

    /** RN-402: dígitos verificadores reprovados ou sequência repetida (CX-04). */
    public static BusinessRuleException invalidDocument(DocumentType type) {
        return new InvalidDocumentException(type);
    }

    /** RN-403. */
    public static BusinessRuleException duplicateDocument() {
        return new DuplicateClientException(
                ErrorCode.CLIENT_DOCUMENT_DUPLICATED, "documentNumber", "documento");
    }

    /** RN-404. */
    public static BusinessRuleException duplicateName() {
        return new DuplicateClientException(ErrorCode.CLIENT_NAME_DUPLICATED, "name", "nome");
    }

    /** RN-401: contratos {@code ACTIVE} ou {@code SUSPENDED} impedem a exclusão. */
    public static BusinessRuleException hasActiveContracts(int activeContractsCount) {
        return new ClientHasActiveContractsException(activeContractsCount);
    }

    /** RN-405: cliente inativo não aceita novos contratos. */
    public static BusinessRuleException inactive(UUID clientId) {
        return new InactiveClientException(clientId);
    }

    /** RN-407: inativação com contratos ativos exige confirmação explícita. */
    public static BusinessRuleException deactivationConfirmationRequired(int activeContracts) {
        return new DeactivationConfirmationRequiredException(activeContracts);
    }

    /** RN-406: mais de um contato principal na mesma requisição (CX-07). */
    public static BusinessRuleException multiplePrimaryContacts() {
        return new PrimaryContactConflictException();
    }

    /** clients.md §10.1: limite de 20 contatos por cliente. */
    public static BusinessRuleException contactLimitReached(int limit) {
        return new ContactLimitReachedException(limit);
    }

    /** RN-402. */
    public static final class InvalidDocumentException extends BusinessRuleException {
        private InvalidDocumentException(DocumentType type) {
            super(
                    ErrorCode.CLIENT_DOCUMENT_INVALID,
                    Map.of("field", "documentNumber", "documentType", type.name()),
                    "Documento inválido para o tipo " + type);
        }
    }

    /** RN-403 e RN-404. */
    public static final class DuplicateClientException extends BusinessRuleException {
        private DuplicateClientException(ErrorCode code, String field, String label) {
            super(code, Map.of("field", field), "Já existe um cliente com este " + label);
        }
    }

    /** RN-401. */
    public static final class ClientHasActiveContractsException extends BusinessRuleException {
        private ClientHasActiveContractsException(int activeContractsCount) {
            super(
                    ErrorCode.CLIENT_DELETE_RESTRICTED,
                    Map.of("activeContracts", activeContractsCount),
                    "Cliente com contrato ativo não pode ser excluído");
        }
    }

    /** RN-407. */
    public static final class DeactivationConfirmationRequiredException
            extends BusinessRuleException {
        private DeactivationConfirmationRequiredException(int activeContracts) {
            super(
                    ErrorCode.CLIENT_DEACTIVATION_CONFIRMATION_REQUIRED,
                    Map.of("activeContracts", activeContracts, "field", "confirmed"),
                    "Confirmação obrigatória: o cliente possui contratos ativos");
        }
    }

    /** RN-406. */
    public static final class PrimaryContactConflictException extends BusinessRuleException {
        private PrimaryContactConflictException() {
            super(
                    ErrorCode.CONTACT_PRIMARY_CONFLICT,
                    Map.of("field", "contacts"),
                    "Apenas um contato pode ser principal");
        }
    }

    /** clients.md §10.1. */
    public static final class ContactLimitReachedException extends BusinessRuleException {
        private ContactLimitReachedException(int limit) {
            super(
                    ErrorCode.CONTACT_LIMIT_REACHED,
                    Map.of("limit", limit),
                    "Limite de contatos atingido");
        }
    }

    /** RN-405. */
    public static final class InactiveClientException extends BusinessRuleException {
        private InactiveClientException(UUID clientId) {
            super(
                    ErrorCode.CLIENT_INACTIVE,
                    Map.of("clientId", clientId),
                    "Cliente inativo não aceita novos contratos");
        }
    }
}
