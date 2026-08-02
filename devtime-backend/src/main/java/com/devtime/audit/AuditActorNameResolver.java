package com.devtime.audit;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Nomes de exibição dos autores da trilha, resolvidos em lote.
 *
 * <p>Inversão de dependência deliberada (AR-03, BR-008). {@code user} já depende de {@code audit} —
 * toda alteração de perfil é auditada —, então uma chamada direta de {@code audit} para {@code
 * UserService} fecharia um ciclo entre as duas features. A interface é declarada aqui e
 * implementada em {@code user}, no mesmo padrão de {@code TicketWorkLogCountSource}: quem precisa
 * do dado declara o contrato, quem o possui o satisfaz.
 *
 * <p>É lote por exigência de desempenho: uma página de 20 entradas costuma repetir dois ou três
 * autores, e uma consulta por linha transformaria a tela de auditoria no endpoint mais caro do
 * sistema.
 */
public interface AuditActorNameResolver {

    /**
     * @return nome de exibição por identificador; identificadores desconhecidos ficam
     *     <b>ausentes</b> do mapa, e o chamador decide como apresentá-los (RN-458)
     */
    Map<UUID, String> namesOf(Collection<UUID> actorIds);
}
