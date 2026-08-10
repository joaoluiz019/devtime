package com.devtime.worklog;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Serializa as escritas de work log <b>do mesmo usuário</b> (RN-102, INV-WKL-05).
 *
 * <p>{@link OverlapDetector} consulta e depois a transação insere. Entre uma coisa e outra existe
 * uma janela, e duas requisições simultâneas do mesmo usuário para o mesmo intervalo passam ambas
 * pela consulta antes de qualquer uma gravar. O teste de concorrência T-008-36 mediu o tamanho real
 * do problema: de 16 tentativas idênticas disparadas juntas, <b>10 foram persistidas</b> — dez
 * vezes a mesma hora na fatura do cliente, que é exatamente o dano que RP-01 classifica como
 * crítico e irrecuperável. Um duplo clique no botão de salvar basta para reproduzir.
 *
 * <p><b>Por que lock consultivo e não {@code EXCLUDE USING gist}.</b> A constraint seria a garantia
 * estrutural, e é como V013 protege os períodos. Aqui ela colide com a exclusão lógica: o registro
 * excluído continua fisicamente na tabela e bloquearia o intervalo, impedindo a pessoa de recriar
 * um registro que ela mesma apagou — o motivo já documentado em V016 e em OB-02. Um índice parcial
 * de exclusão sobre {@code deleted_at IS NULL} resolveria isso, mas mudaria o erro devolvido de
 * {@code DEVTIME-2102}, com o registro conflitante nos detalhes, para uma violação de integridade
 * genérica — perdendo a informação que faz o usuário entender o que aconteceu.
 *
 * <p>O lock consultivo preserva a mensagem e o comportamento existentes: quem chega depois espera,
 * e então o mesmo {@code OverlapDetector} de sempre encontra o registro já gravado e devolve o erro
 * de negócio correto. É o mesmo mecanismo, e o mesmo espaço de nomes separado, já usado por {@code
 * TicketNumberGenerator} para a numeração atômica de tickets.
 *
 * <p>A chave é o <b>usuário</b>, não o tenant nem o ticket: RN-102 fala de uma pessoa que não pode
 * estar em dois lugares ao mesmo tempo. Travar por tenant serializaria pessoas que não competem
 * entre si.
 */
@Component
@RequiredArgsConstructor
public class WorkLogWriteLock {

    /** Espaço de nomes do lock consultivo, isolado do de {@code TicketNumberGenerator} (7007). */
    private static final int WORK_LOG_LOCK_NAMESPACE = 8008;

    private final EntityManager entityManager;

    /**
     * Adquire o lock do usuário até o fim da transação corrente.
     *
     * <p>Precisa ser chamado <b>dentro</b> da transação que grava e <b>antes</b> da verificação de
     * sobreposição: adquirido depois, a janela que ele fecha já teria sido atravessada.
     */
    public void acquireFor(UUID userId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:namespace, hashtext(:key))")
                .setParameter("namespace", WORK_LOG_LOCK_NAMESPACE)
                // BR-168: parâmetro vinculado, nunca concatenação de string em SQL.
                .setParameter("key", userId.toString())
                .getSingleResult();
    }
}
