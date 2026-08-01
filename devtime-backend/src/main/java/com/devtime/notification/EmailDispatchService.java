package com.devtime.notification;

import com.devtime.notification.domain.Notification;

/**
 * Entrega da notificação por e-mail (RN-608, RN-610).
 *
 * <p><b>INV-NOT-05: nada aqui pode reverter a notificação in-app.</b> Ela já foi criada quando este
 * serviço é chamado, e o envio ocorre depois — falha de provedor é degradação prevista, não erro de
 * requisição.
 */
public interface EmailDispatchService {

    /**
     * Avalia as preferências e tenta a primeira entrega.
     *
     * <p>Silenciado ou desligado: nenhum e-mail, e a in-app permanece (FA-06, FA-07).
     */
    void dispatch(Notification notification);

    /**
     * RN-610: nova tentativa a partir do job de reprocessamento.
     *
     * <p>CP-08: a quarta tentativa é proibida. Três falhas indicam problema que nova tentativa não
     * resolve, e insistir apenas atrasaria a percepção de que o destinatário não foi alcançado.
     *
     * @return {@code true} quando o provedor aceitou a mensagem
     */
    boolean retry(Notification notification);
}
