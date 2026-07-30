package com.devtime.shared.event;

/**
 * Marcador dos eventos de domínio (backend.md §10).
 *
 * <p>BR-180 exige que cada evento seja um {@code record} imutável implementando esta interface, e
 * BR-181 que ele carregue apenas identificadores, nunca entidades — um evento consumido após o
 * commit poderia receber uma entidade destacada da sessão.
 *
 * <p>backend.md §10 declara a interface como {@code sealed}. Ela ainda não é: em Java, {@code
 * sealed} exige ao menos um subtipo permitido, e nenhum evento de domínio existe nesta sprint —
 * todos pertencem às features. O selamento acompanha o primeiro evento, quando a cláusula {@code
 * permits} passa a ter valor de verificação.
 */
public interface DomainEvent {}
