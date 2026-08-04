package com.devtime.notification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Título e corpo de cada notificação (§19.1, NT-03).
 *
 * <p><b>Nenhum texto daqui contém descrição de work log nem valor monetário</b> (CP-15). O corpo é
 * entregue a um provedor de e-mail externo e pode ser armazenado fora do controle do tenant: ele
 * informa "o contrato X atingiu 83% do saldo" e leva ao sistema, sem reproduzir o dado.
 *
 * <p>O corpo é <b>completo e autoexplicativo</b> (§7): a notificação não deve exigir ser aberta
 * para ser entendida. Um título isolado como "Contrato atualizado" obriga o usuário a abrir para
 * descobrir se precisa agir — e ele deixa de abrir.
 *
 * <p>Durações são formatadas em {@code HH:MM}, nunca em minutos crus: "restam 02:00" é lido
 * imediatamente; "restam 120" exige conversão mental.
 */
@Component
public class NotificationTemplateRenderer {

    /** Texto renderizado, pronto para a central e para o e-mail. */
    public record RenderedText(String title, String body) {}

    /** RN-602: limiar de consumo atingido. */
    public RenderedText consumption(
            String contractName,
            String periodLabel,
            int threshold,
            int consumedMinutes,
            int availableMinutes,
            int remainingMinutes) {
        String title = "Contrato atingiu " + threshold + "% do saldo";
        String body =
                contractName
                        + " (período "
                        + periodLabel
                        + ") consumiu "
                        + duration(consumedMinutes)
                        + " de "
                        + duration(availableMinutes)
                        + " disponíveis. "
                        + (remainingMinutes >= 0
                                ? "Restam " + duration(remainingMinutes) + "."
                                : "O saldo já foi ultrapassado em "
                                        + duration(-remainingMinutes)
                                        + ".");
        return new RenderedText(title, body);
    }

    /** RN-604: excedente. */
    public RenderedText overage(String contractName, String periodLabel, int overageMinutes) {
        return new RenderedText(
                "Contrato excedido",
                contractName
                        + " (período "
                        + periodLabel
                        + ") ultrapassou o saldo contratado em "
                        + duration(overageMinutes)
                        + ". Verifique o extrato antes do fechamento.");
    }

    /** RN-605. */
    public RenderedText periodClosing(String periodLabel, LocalDate endDate, int daysRemaining) {
        return new RenderedText(
                "Período próximo do fechamento",
                "O período "
                        + periodLabel
                        + " termina em "
                        + endDate
                        + ", daqui a "
                        + daysRemaining
                        + (daysRemaining == 1 ? " dia." : " dias.")
                        + " Confira os registros de horas antes de fechá-lo.");
    }

    /** RN-241. */
    public RenderedText periodClosed(
            String periodLabel, int consumedMinutes, int carriedOutMinutes) {
        return new RenderedText(
                "Período fechado",
                "O período "
                        + periodLabel
                        + " foi fechado com "
                        + duration(consumedMinutes)
                        + " consumidos e "
                        + duration(carriedOutMinutes)
                        + " transportados para o período seguinte.");
    }

    /** RN-242: um relatório já entregue foi alterado — quem o recebeu precisa saber. */
    public RenderedText periodReopened(String periodLabel, int reopenCount) {
        return new RenderedText(
                "Período reaberto",
                "O período "
                        + periodLabel
                        + " foi reaberto para correção (reabertura nº "
                        + reopenCount
                        + "). Os relatórios desse período podem mudar até o novo fechamento.");
    }

    /** RN-606. */
    public RenderedText contractEnding(String contractName, LocalDate endDate, int daysRemaining) {
        return new RenderedText(
                "Contrato próximo do fim",
                contractName
                        + " termina em "
                        + endDate
                        + ", daqui a "
                        + daysRemaining
                        + (daysRemaining == 1 ? " dia." : " dias.")
                        + " Renove ou encerre antes dessa data.");
    }

    /** RN-215: quem responde pelo tenant precisa saber que o saldo foi alterado manualmente. */
    public RenderedText adjustmentApplied(String periodLabel, int minutes) {
        String verb = minutes > 0 ? "creditadas" : "debitadas";
        return new RenderedText(
                "Ajuste de saldo aplicado",
                duration(Math.abs(minutes))
                        + " foram "
                        + verb
                        + " no período "
                        + periodLabel
                        + ". Consulte o extrato para ver o motivo registrado.");
    }

    /** RN-163. */
    public RenderedText timerLongRunning(String ticketKey, long grossElapsedSeconds) {
        return new RenderedText(
                "Cronômetro em execução há muito tempo",
                "Seu cronômetro no ticket "
                        + ticketKey
                        + " está ativo há "
                        + duration((int) (grossElapsedSeconds / 60))
                        + ". Se você esqueceu de encerrá-lo, ajuste o horário antes de registrar.");
    }

    /** RN-164: o texto precisa deixar claro que o tempo <b>não</b> foi perdido. */
    public RenderedText timerAbandoned(String ticketKey, LocalDate recoverableUntil) {
        return new RenderedText(
                "Cronômetro abandonado",
                "Seu cronômetro no ticket "
                        + ticketKey
                        + " foi marcado como abandonado e nenhum registro foi criado. Você pode"
                        + " recuperá-lo informando o horário real de término até "
                        + recoverableUntil
                        + ".");
    }

    /** OWN-05: o dono é informado de quem encerrou. */
    public RenderedText timerForceStopped(String ticketKey) {
        return new RenderedText(
                "Cronômetro encerrado por um administrador",
                "Seu cronômetro no ticket "
                        + ticketKey
                        + " foi encerrado por um administrador da organização e o registro de horas"
                        + " correspondente foi criado.");
    }

    /** RN-607. */
    public RenderedText ticketAssigned(String ticketKey) {
        return new RenderedText(
                "Ticket atribuído a você",
                "O ticket "
                        + ticketKey
                        + " foi atribuído a você. Abra para ver o que precisa ser feito.");
    }

    /** RN-312. */
    public RenderedText ticketReopened(String ticketKey) {
        return new RenderedText(
                "Ticket reaberto",
                "O ticket "
                        + ticketKey
                        + " voltou a receber horas e saiu de concluído. Verifique se ainda há"
                        + " trabalho pendente.");
    }

    /** RN-813. */
    public RenderedText ticketCommented(String ticketKey) {
        return new RenderedText(
                "Novo comentário no ticket",
                "O ticket " + ticketKey + " recebeu um novo comentário.");
    }

    /**
     * §6 de notifications.md: convite aceito.
     *
     * <p>Usa o nome de exibição, não o e-mail: ART-084 proíbe o endereço em claro fora do canal de
     * e-mail, e a notificação é lida na central por todos os {@code OWNER} e {@code ADMIN}.
     */
    public RenderedText memberJoined(String memberName) {
        return new RenderedText(
                "Novo membro na organização", memberName + " aceitou o convite e já tem acesso.");
    }

    /** §6 de notifications.md: membro removido. */
    public RenderedText memberRemoved(String memberName, long preservedWorkLogs) {
        return new RenderedText(
                "Membro removido da organização",
                memberName
                        + " foi removido. Os "
                        + preservedWorkLogs
                        + " registros de horas dele permanecem nos relatórios e no saldo.");
    }

    /** RN-813 / CE-N-07: mais específico que {@link #ticketCommented}. */
    public RenderedText ticketMentioned(String ticketKey) {
        return new RenderedText(
                "Você foi mencionado",
                "Você foi mencionado em um comentário do ticket " + ticketKey + ".");
    }

    /**
     * RN-803 / §15 de {@code specs/015}: ameaça detectada em anexo.
     *
     * <p><b>O nome do arquivo não entra</b> (§19.1 de {@code specs/015}, CP-19). É texto livre e
     * pode conter dado pessoal, e uma notificação é entregue por e-mail — o canal que menos
     * controla onde o texto termina. Quem enviou reconhece o arquivo pelo ticket e pelo momento.
     */
    public RenderedText attachmentInfected(String threat) {
        return new RenderedText(
                "Ameaça detectada em anexo",
                "Um arquivo que você enviou foi bloqueado pela verificação de segurança ("
                        + threat
                        + "). O arquivo foi removido e não pode ser baixado. Envie novamente um"
                        + " arquivo íntegro.");
    }

    /**
     * FA-10 de {@code specs/012}: a exportação assíncrona terminou.
     *
     * <p>§19.1: o texto nomeia formato e contagem, nunca o recorte. "Sua exportação do cliente Acme
     * está pronta" espalharia por e-mail o que o relatório recorta.
     */
    public RenderedText exportCompleted(String format, int rowCount) {
        return new RenderedText(
                "Exportação concluída",
                "Seu arquivo "
                        + format
                        + " com "
                        + rowCount
                        + " registros está pronto. O download fica disponível por 7 dias.");
    }

    /** CE-R-11: a falha é comunicada com o motivo já traduzido, nunca com a exceção crua. */
    public RenderedText exportFailed(String format, String failureReason) {
        return new RenderedText(
                "Exportação falhou",
                "Não foi possível gerar seu arquivo "
                        + format
                        + ": "
                        + failureReason
                        + ". Você pode solicitar a exportação novamente.");
    }

    /**
     * Payload estruturado para renderização rica na central.
     *
     * <p>§19.1 / CA-09: nenhuma chave carrega dado sensível. Os valores aqui são os mesmos que o
     * corpo já expõe em texto — o payload existe para a interface formatar melhor, não para
     * transportar mais.
     */
    public Map<String, Object> payload(Map<String, Object> entries) {
        return new LinkedHashMap<>(entries);
    }

    /**
     * Minutos em {@code HH:MM}.
     *
     * <p>As horas não são limitadas a 24: um total de 150 horas aparece como {@code 150:00}, não
     * como {@code 06:00} de um sétimo dia.
     */
    private String duration(int minutes) {
        int absolute = Math.abs(minutes);
        return String.format("%02d:%02d", absolute / 60, absolute % 60);
    }
}
