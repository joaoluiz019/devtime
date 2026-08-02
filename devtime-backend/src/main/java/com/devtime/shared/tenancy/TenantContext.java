package com.devtime.shared.tenancy;

import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Portador do tenant e do usuário da requisição corrente (backend.md §7.1).
 *
 * <p>A implementação é um singleton apoiado em {@link ThreadLocal}, uma das duas opções previstas
 * no fluxo de backend.md §7.2 ("set ThreadLocal / RequestScope"). {@code ThreadLocal} foi escolhido
 * em vez de {@code @RequestScope} porque o contexto também precisa existir fora de uma requisição
 * HTTP — jobs de plataforma definem o tenant a cada iteração (JB-06, TI-08) e ações de sistema
 * executam sem usuário (CE-S-06). Um bean {@code @RequestScope} lançaria exceção nesses cenários.
 *
 * <p><b>Contrato de limpeza:</b> quem chama {@link #set} é obrigado a chamar {@link #clear} em
 * {@code finally}. Sem isso, uma thread reutilizada do pool carregaria o tenant da requisição
 * anterior — vazamento entre tenants, a falha mais grave prevista no modelo de ameaças ({@code
 * security.md} §4.3).
 */
@Component
public class TenantContext {

    private static final ThreadLocal<TenantSession> CURRENT = new ThreadLocal<>();

    public void set(TenantSession session) {
        CURRENT.set(session);
    }

    public void clear() {
        CURRENT.remove();
    }

    public Optional<TenantSession> session() {
        return Optional.ofNullable(CURRENT.get());
    }

    public boolean isAuthenticated() {
        return CURRENT.get() != null;
    }

    /**
     * Retorna o tenant da sessão corrente.
     *
     * <p>TI-06 / BR-043: lança exceção quando o contexto está vazio. <b>Nunca</b> retorna {@code
     * null} nem um valor padrão — degradar aqui produziria consulta sem filtro de tenant, expondo
     * dados de todos os tenants (CE-A-07 de {@code architecture.md}).
     *
     * @throws TenantContextNotInitializedException se não houver sessão ou tenant selecionado
     */
    public UUID requireTenantId() {
        TenantSession session = CURRENT.get();
        if (session == null || session.tenantId() == null) {
            throw new TenantContextNotInitializedException();
        }
        return session.tenantId();
    }

    public Optional<UUID> currentTenantId() {
        return session().map(TenantSession::tenantId);
    }

    /**
     * Retorna o usuário autenticado.
     *
     * <p>Falha também quando a sessão é de <b>sistema</b> ({@link TenantSession#system}), que não
     * possui usuário: devolver {@code null} faria um job gravar {@code createdBy} nulo em um campo
     * que o chamador acredita preenchido, e o defeito só apareceria muito depois, na trilha. Quem
     * pode operar sem usuário usa {@link #currentUserId()} e trata o {@link Optional} (CG-06).
     *
     * @throws TenantContextNotInitializedException se não houver sessão ou se ela for de sistema
     */
    public UUID requireUserId() {
        TenantSession session = CURRENT.get();
        if (session == null || session.userId() == null) {
            throw new TenantContextNotInitializedException();
        }
        return session.userId();
    }

    public Optional<UUID> currentUserId() {
        return session().map(TenantSession::userId);
    }

    public Optional<Role> currentRole() {
        return session().map(TenantSession::role);
    }

    /** AZ-02 / TK-03: as permissões vêm do papel resolvido na requisição, nunca do token. */
    public Set<Permission> currentPermissions() {
        return session().map(TenantSession::permissions).orElseGet(Set::of);
    }

    /** ART-032: fuso do tenant, usado por {@code TenantClock} para resolver a data local. */
    public Optional<String> currentTimezone() {
        return session().map(TenantSession::timezone);
    }
}
