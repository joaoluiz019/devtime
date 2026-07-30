package com.devtime.shared.tenancy;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marca a exceção explícita à obrigatoriedade de filtro por tenant (ART-023, BR-044/BR-045).
 *
 * <p>Usos permitidos e <b>exaustivos</b> no MVP, conforme backend.md §7.4:
 *
 * <ul>
 *   <li>{@code UserRepository.findByEmail} — o login precede a seleção de tenant
 *   <li>{@code MembershipRepository.findActiveByUserId} — lista os tenants do usuário
 *   <li>{@code RefreshTokenRepository.findByTokenHash} — a renovação pode preceder o tenant
 *   <li>Jobs de plataforma — operam sobre todos os tenants, definindo o contexto a cada iteração
 * </ul>
 *
 * <p>Qualquer novo uso exige aprovação explícita em revisão e teste de isolamento adicional.
 *
 * <p><b>Esta anotação não desativa o filtro em tempo de execução.</b> Ela é um marcador de revisão,
 * verificado por ArchUnit (BR-044/BR-045). Os métodos marcados funcionam sem filtro porque são
 * chamados quando ainda não existe tenant selecionado — o {@link TenantAwareInterceptor} só ativa o
 * filtro quando há tenant no contexto. Um método marcado que venha a ser chamado <i>com</i> tenant
 * selecionado continuará filtrado; se algum uso futuro precisar ignorar um tenant já selecionado, a
 * desativação explícita da sessão precisará ser implementada, com ADR.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface CrossTenant {

    /** Justificativa obrigatória, revisada em PR (ART-023, BR-045). */
    String reason();
}
