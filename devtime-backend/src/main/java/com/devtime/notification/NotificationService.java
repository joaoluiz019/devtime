package com.devtime.notification;

import com.devtime.notification.dto.NotificationCommand;

/**
 * Criação de notificações com deduplicação (spec 013 §22.2).
 *
 * <p><b>Esta feature não expõe interface pública a outras features.</b> Ela é consumidora terminal:
 * reage a eventos e não é chamada por ninguém. As features de origem publicam sem conhecê-la — o
 * que permite cortá-la sem alterar nenhuma delas (§22.2).
 *
 * <p>Este serviço é interno à feature, consumido pelos ouvintes de evento e pelos jobs de lembrete.
 */
public interface NotificationService {

    /**
     * Cria a notificação para cada destinatário, na ordem da §6.2.
     *
     * <p><b>RN-601: destinatário que já possui a chave é ignorado silenciosamente</b>, sem erro e
     * sem exceção propagada. A avaliação de limiares roda a cada alteração de {@code
     * consumedMinutes} (RN-602): em um dia com 20 registros de horas, o limiar de 50% é avaliado 20
     * vezes. Retornar erro na duplicata obrigaria cada chamador a tratar uma condição normal.
     *
     * <p>RN-608: a notificação in-app é criada <b>sempre</b>, antes de qualquer decisão sobre
     * e-mail. Preferência silencia o e-mail, nunca o histórico.
     *
     * @return quantidade efetivamente criada; zero quando todas as chaves já existiam
     */
    int notify(NotificationCommand command);
}
