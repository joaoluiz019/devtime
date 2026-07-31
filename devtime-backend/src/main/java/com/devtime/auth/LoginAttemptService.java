package com.devtime.auth;

import com.devtime.user.dto.UserAccount;

/**
 * Contagem de falhas e bloqueio de conta (RN-453, T-001-23).
 *
 * <p>Os parâmetros da regra — 5 tentativas, janela de 15 minutos, bloqueio de 30 minutos — vivem
 * aqui e são repassados à feature {@code user}, que aplica o efeito sobre a entidade que lhe
 * pertence. Um único ponto de verdade para os números da regra.
 */
public interface LoginAttemptService {

    /**
     * Verificação 3 de {@code spec 001} §6.1, executada <b>antes</b> da comparação de senha.
     *
     * <p>A ordem é normativa (BR-062): se o bloqueio fosse verificado depois, uma conta bloqueada
     * responderia de forma diferente para senha certa e senha errada, servindo de oráculo de senha
     * correta justamente durante um ataque em andamento.
     *
     * <p>Desbloqueia automaticamente quando {@code lockedUntil} já passou (§11 de spec 001), sem
     * esperar o job de dez em dez minutos.
     *
     * @return a conta, relida quando o desbloqueio automático ocorreu — a instância recebida
     *     ficaria com {@code status = LOCKED} e faria a verificação seguinte responder o código
     *     errado
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-1006} / {@code 423}
     */
    UserAccount assertNotLocked(UserAccount account);

    /** Registra a falha, bloqueando ao atingir o limite dentro da janela (RN-453). */
    void registerFailure(UserAccount account);

    /** Zera o contador e registra o acesso (RN-453). */
    void registerSuccess(UserAccount account);
}
