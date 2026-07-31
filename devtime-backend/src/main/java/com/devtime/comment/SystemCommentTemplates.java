package com.devtime.comment;

import com.devtime.comment.domain.SystemCommentTrigger;
import org.springframework.stereotype.Component;

/**
 * Textos dos comentários de sistema (RN-815).
 *
 * <p>Reunidos em um ponto único para que o texto de cada gatilho tenha uma resposta só —
 * espalhá-los pelos publicadores produziria variações que o usuário lê como inconsistência do
 * produto.
 *
 * <p>NM-03: mensagens ao usuário em português. O texto é intencionalmente <b>factual e curto</b>: o
 * comentário de sistema divide espaço com a conversa das pessoas, e narrar demais transformaria a
 * linha do tempo em log.
 */
@Component
public class SystemCommentTemplates {

    /** Transição manual de situação. */
    public String statusChanged(String from, String to) {
        return "Situação alterada de " + from + " para " + to + ".";
    }

    /** RN-312: reabertura automática ao receber registro de horas. */
    public String statusChangedAutomatically(String from, String to) {
        return "Situação alterada automaticamente de "
                + from
                + " para "
                + to
                + " porque um registro de horas foi lançado neste ticket.";
    }

    /** Impedimento registrado; o motivo faz parte do fato e por isso entra no texto. */
    public String blocked(String reason) {
        return "Ticket bloqueado. Motivo: " + reason;
    }

    /** Atribuição, reatribuição ou remoção do responsável. */
    public String assigneeChanged(String previousName, String currentName) {
        if (currentName == null) {
            return "Responsável removido"
                    + (previousName == null ? "." : " (" + previousName + ").");
        }
        if (previousName == null) {
            return "Responsável definido: " + currentName + ".";
        }
        return "Responsável alterado de " + previousName + " para " + currentName + ".";
    }

    /** Movimentação entre contratos; a chave permanece e o texto diz isso explicitamente. */
    public String contractMoved(String fromCode, String toCode, String ticketKey) {
        return "Contrato alterado de "
                + fromCode
                + " para "
                + toCode
                + ". A chave "
                + ticketKey
                + " permanece inalterada.";
    }

    /** Rótulo legível do gatilho, usado na trilha de auditoria. */
    public String labelOf(SystemCommentTrigger trigger) {
        return switch (trigger) {
            case STATUS_CHANGED -> "mudança de situação";
            case ASSIGNEE_CHANGED -> "alteração de responsável";
            case CONTRACT_MOVED -> "alteração de contrato";
        };
    }
}
