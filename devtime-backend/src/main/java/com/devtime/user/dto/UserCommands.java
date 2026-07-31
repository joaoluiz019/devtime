package com.devtime.user.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Comandos de escrita da conta, emitidos por {@code 001-authentication}.
 *
 * <p>Records agrupados no mesmo arquivo pelo padrão já usado em {@code ClientRequests} e {@code
 * CategoryRequests}: são um único contrato, versionado em conjunto.
 */
public final class UserCommands {

    private UserCommands() {}

    /**
     * Criação de conta (spec 001 §13.2).
     *
     * @param normalizedEmail já normalizado por {@code EmailNormalizer} (RN-452, AU-03). A
     *     normalização é responsabilidade de quem cria, não deste serviço: repeti-la aqui daria a
     *     falsa impressão de que a verificação de unicidade poderia ser feita com o valor bruto
     * @param rawPassword senha em claro; o hash BCrypt é produzido dentro da feature {@code user} e
     *     jamais retorna
     */
    public record NewAccount(
            String normalizedEmail, String rawPassword, String fullName, String timezone) {}

    /**
     * Parâmetros de RN-453, fornecidos por {@code LoginAttemptService}.
     *
     * <p>Os números vivem em {@code auth} porque a regra é de autenticação; a feature {@code user}
     * apenas aplica o efeito sobre a entidade que lhe pertence. Duplicá-los aqui criaria duas
     * fontes de verdade para "5 falhas em 15 minutos".
     */
    public record LoginLockPolicy(int maxAttempts, Duration window, Duration lockDuration) {}

    /**
     * Resultado do registro de uma falha de login.
     *
     * @param attempts contador após o incremento, já considerando o reinício da janela
     * @param lockedUntil preenchido apenas quando esta falha provocou o bloqueio; permanece nulo
     *     nas falhas subsequentes dentro do mesmo bloqueio, para que apenas <b>um</b> alerta de
     *     segurança seja enviado (AC-001-43)
     */
    public record LoginFailureOutcome(int attempts, Instant lockedUntil) {

        public boolean justLocked() {
            return lockedUntil != null;
        }
    }
}
