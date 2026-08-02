package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.ScanStatus;
import com.devtime.shared.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/** RN-803 / INV-ATT-02 — a guarda de download (T-015-27). */
class DownloadGuardTest {

    private final DownloadGuard guard = new DownloadGuard();

    @Test
    @DisplayName("RN-803: CLEAN com binário presente é o único caso liberado")
    void cleanWithBinaryIsAllowed() {
        assertThatCode(() -> guard.assertDownloadable(attachment(ScanStatus.CLEAN, true)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(
            value = ScanStatus.class,
            names = {"PENDING", "FAILED"})
    @DisplayName("RN-803/FA-03/FA-05: PENDING e FAILED respondem 409 DEVTIME-2703")
    void unscannedStatesConflict(ScanStatus status) {
        assertThatThrownBy(() -> guard.assertDownloadable(attachment(status, true)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure -> {
                            var business = (BusinessRuleException) failure;
                            org.assertj.core.api.Assertions.assertThat(
                                            business.getErrorCode().getCode())
                                    .isEqualTo("DEVTIME-2703");
                            org.assertj.core.api.Assertions.assertThat(business.getStatus())
                                    .isEqualTo(HttpStatus.CONFLICT);
                        });
    }

    @Test
    @DisplayName("RN-803/FA-04: INFECTED responde 403 — o binário já foi removido")
    void infectedIsForbidden() {
        assertThatThrownBy(() -> guard.assertDownloadable(attachment(ScanStatus.INFECTED, false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure ->
                                org.assertj.core.api.Assertions.assertThat(
                                                ((BusinessRuleException) failure).getStatus())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("INV-ATT-05: CLEAN sem binário não libera — o arquivo não existe mais")
    void cleanWithoutBinaryIsBlocked() {
        // Corrida entre a exclusão do último referenciador e um download em curso. Afirmar que
        // está limpo prometeria um conteúdo que não pode ser entregue.
        assertThatThrownBy(() -> guard.assertDownloadable(attachment(ScanStatus.CLEAN, false)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(
                        failure ->
                                org.assertj.core.api.Assertions.assertThat(
                                                ((BusinessRuleException) failure).getStatus())
                                        .isEqualTo(HttpStatus.CONFLICT));
    }

    private Attachment attachment(ScanStatus status, boolean binaryPresent) {
        Attachment attachment = new Attachment();
        attachment.setScanStatus(status);
        attachment.setBinaryPresent(binaryPresent);
        return attachment;
    }
}
