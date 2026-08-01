package com.devtime.timer;

import com.devtime.timer.dto.TimerRequests.TimerRecoverRequest;
import com.devtime.timer.dto.TimerRequests.TimerStartRequest;
import com.devtime.timer.dto.TimerRequests.TimerStopRequest;
import com.devtime.timer.dto.TimerRequests.TimerUpdateRequest;
import com.devtime.timer.dto.TimerResponses.AbandonedTimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerResponse;
import com.devtime.timer.dto.TimerResponses.TimerStopResponse;
import java.util.List;
import java.util.UUID;

/**
 * Ciclo de vida do cronômetro (spec 009 §22.2).
 *
 * <p><b>RN-160 atravessa toda esta interface:</b> nenhum método de escrita descarta tempo
 * trabalhado por falha de validação. Quando o encerramento falha — sobreposição, saldo estourado,
 * contrato encerrado —, o cronômetro permanece exatamente no estado em que estava, e a exceção
 * carrega o que precisa ser corrigido. O tempo trabalhado nunca é descartado pelo sistema; só o
 * usuário o descarta, explicitamente (RN-162).
 */
public interface TimerService {

    /**
     * RN-152: inicia com {@code startedAt = lastResumedAt = now()}.
     *
     * @param stopCurrent RN-166 — encerra o cronômetro atual e inicia o novo em <b>uma</b> operação
     *     atômica. CX-17: se o encerramento falhar, nada acontece — o atual permanece e o novo não
     *     é criado
     */
    TimerResponse start(TimerStartRequest request, boolean stopCurrent);

    /** RN-161: ticket, categoria, descrição e faturável durante a execução. */
    TimerResponse update(TimerUpdateRequest request);

    /** RN-154: acumula o trecho ativo e abre uma {@code TimerPause}. */
    TimerResponse pause();

    /** RN-156: fecha a pausa, recalcula {@code pausedMinutes} e retoma. */
    TimerResponse resume();

    /**
     * RN-158 a RN-160: encerra e gera o work log pelas <b>mesmas</b> validações de {@code 008}.
     *
     * @throws com.devtime.shared.error.BusinessRuleException com o código da regra violada; o
     *     cronômetro permanece ativo (RN-160)
     */
    TimerStopResponse stop(TimerStopRequest request);

    /**
     * RN-162: descarte explícito e irreversível; nenhum work log é gerado.
     *
     * @param confirmed confirmação obrigatória — é a única operação do sistema que destrói trabalho
     *     registrado sem gerar contrapartida
     */
    void discard(boolean confirmed);

    /** RN-165: cronômetros abandonados do usuário, ainda dentro da janela de 7 dias. */
    List<AbandonedTimerResponse> abandoned();

    /** RN-165: recupera um abandonado com o horário real de término informado pelo usuário. */
    TimerStopResponse recover(UUID timerId, TimerRecoverRequest request);

    /** FA-16 / OWN-05: {@code TIMER_STOP_ANY} encerra o de terceiro e <b>notifica o dono</b>. */
    TimerStopResponse forceStop(UUID timerId, TimerStopRequest request);

    /**
     * RN-460: descarta os cronômetros de um membro removido.
     *
     * <p>Interface pública para {@code 002-users}, aplicada <b>dentro</b> da transação de remoção:
     * um cronômetro ativo de alguém que não é mais membro não pode ser encerrado por ninguém. O
     * tempo fica registrado apenas em auditoria e o {@code OWNER} é notificado (CE-ME-06).
     *
     * @return quantidade descartada
     */
    int discardForUser(UUID userId);
}
