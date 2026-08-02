package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.domain.AttachmentExceptions;
import com.devtime.attachment.domain.ScanStatus;
import org.springframework.stereotype.Component;

/**
 * RN-803 / INV-ATT-02 — o download exige {@code CLEAN}, sem exceção (CE-08).
 *
 * <p>A guarda vive no <b>serviço</b>, não apenas no controller (T-015-17, BR-161): uma verificação
 * só no controller seria contornada por qualquer caminho interno que viesse a existir depois.
 *
 * <p>CP-01 e CP-02: <b>não existe nenhum parâmetro, papel ou configuração que libere um arquivo não
 * verificado.</b> A ausência é a implementação da regra. OB-02 antecipa que esta é a decisão que
 * mais sofrerá pressão — um usuário com arquivo importante inacessível pedirá exceção — e registra
 * o motivo de não ceder: ceder criaria um caminho que converte três camadas de defesa em uma caixa
 * de diálogo, e quem clica em "liberar mesmo assim" não tem como avaliar o risco. A alternativa
 * oferecida é reenviar o arquivo, o que reinicia a verificação.
 *
 * <p>Se essa decisão for revista, ela precisa ser revista em {@code business-rules.md} <b>antes</b>
 * do código, com trilha de auditoria e restrição de papel.
 */
@Component
public class DownloadGuard {

    /**
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2703} — {@code 409} em
     *     {@code PENDING} e {@code FAILED} (FA-03, FA-05); {@code 403} em {@code INFECTED} (FA-04)
     */
    public void assertDownloadable(Attachment attachment) {
        if (attachment.getScanStatus() == ScanStatus.INFECTED) {
            throw AttachmentExceptions.infected(); // 403 — o binário já foi removido
        }
        if (attachment.getScanStatus() != ScanStatus.CLEAN) {
            throw AttachmentExceptions.notScanned(attachment.getScanStatus()); // 409
        }
        // CLEAN sem binário só ocorre se o último referenciador foi excluído em corrida com este
        // download. Tratar como "ainda não verificado" é a única resposta correta: o arquivo não
        // existe mais, e afirmar que está limpo prometeria um conteúdo que não pode ser entregue.
        if (!attachment.isBinaryPresent()) {
            throw AttachmentExceptions.notScanned(attachment.getScanStatus());
        }
    }
}
