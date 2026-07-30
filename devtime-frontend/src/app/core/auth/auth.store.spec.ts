import { TestBed } from '@angular/core/testing';
import { AuthSessionResponse } from './auth.model';
import { AuthStore } from './auth.store';
import { TokenStorage } from './token.storage';

/** Sessão de exemplo espelhando `04-api/authentication.md` §5.3. */
function sessionResponse(overrides: Partial<AuthSessionResponse> = {}): AuthSessionResponse {
  return {
    accessToken: 'token-de-acesso',
    tokenType: 'Bearer',
    expiresIn: 900,
    tenantSelectionRequired: false,
    user: {
      id: '0192f3a4-1111-7890-abcd-ef0123456789',
      fullName: 'Rafael Mendes',
      displayName: 'Rafael',
      email: 'rafael@exemplo.com',
      avatarUrl: null,
    },
    tenant: {
      id: '0192f3a4-2222-7890-abcd-ef0123456789',
      name: 'Rafael Mendes Dev',
      slug: 'rafael-mendes-dev',
      timezone: 'America/Sao_Paulo',
      currency: 'BRL',
      logoUrl: null,
    },
    role: 'OWNER',
    permissions: ['TENANT_VIEW', 'CLIENT_CREATE'],
    ...overrides,
  };
}

describe('AuthStore', () => {
  let store: AuthStore;
  let tokenStorage: TokenStorage;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [AuthStore, TokenStorage] });
    store = TestBed.inject(AuthStore);
    tokenStorage = TestBed.inject(TokenStorage);
  });

  it('inicia sem sessão', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.hasTenantSelected()).toBe(false);
    expect(store.permissions().size).toBe(0);
  });

  it('aplica a sessão e guarda o access token apenas em memória (FR-066)', () => {
    store.applySession(sessionResponse());

    expect(store.isAuthenticated()).toBe(true);
    expect(store.hasTenantSelected()).toBe(true);
    expect(tokenStorage.accessToken()).toBe('token-de-acesso');
    expect(store.tenant()?.name).toBe('Rafael Mendes Dev');
    expect(store.role()).toBe('OWNER');
  });

  it('TK-03: as permissões vêm do servidor e não são derivadas no cliente', () => {
    store.applySession(sessionResponse({ permissions: ['CLIENT_VIEW'] }));

    expect(store.hasPermission('CLIENT_VIEW')).toBe(true);
    // OWNER teria muitas outras permissões na matriz; o store guarda só o que o servidor mandou,
    // evitando uma segunda fonte de verdade que divergiria na primeira mudança de papel.
    expect(store.hasPermission('TENANT_DELETE')).toBe(false);
  });

  it('hasAnyPermission concede quando ao menos uma permissão está presente (OWN-08)', () => {
    store.applySession(sessionResponse({ permissions: ['WORKLOG_VIEW_OWN'] }));

    expect(store.hasAnyPermission(['WORKLOG_VIEW_ANY', 'WORKLOG_VIEW_OWN'])).toBe(true);
    expect(store.hasAnyPermission(['PERIOD_CLOSE', 'PERIOD_REOPEN'])).toBe(false);
  });

  it('CE-P-11: sessão com múltiplos tenants fica sem tenant selecionado', () => {
    store.applySession(
      sessionResponse({
        tenantSelectionRequired: true,
        tenant: undefined,
        role: undefined,
        permissions: undefined,
        tenants: [
          { id: 'a', name: 'Rafael Mendes Dev', role: 'OWNER', logoUrl: null },
          { id: 'b', name: 'Acme Software', role: 'MEMBER', logoUrl: null },
        ],
      }),
    );

    expect(store.isAuthenticated()).toBe(true);
    expect(store.hasTenantSelected()).toBe(false);
    expect(store.tenantSelectionRequired()).toBe(true);
    expect(store.availableTenants()).toHaveLength(2);
  });

  it('clearSession remove a sessão e o token', () => {
    store.applySession(sessionResponse());

    store.clearSession();

    expect(store.isAuthenticated()).toBe(false);
    expect(tokenStorage.accessToken()).toBeNull();
    expect(store.permissions().size).toBe(0);
  });

  it('displayName prefere o apelido e recorre ao nome completo', () => {
    store.applySession(sessionResponse());
    expect(store.displayName()).toBe('Rafael');

    store.applySession(
      sessionResponse({
        user: {
          id: 'x',
          fullName: 'Rafael Mendes',
          displayName: null,
          email: 'rafael@exemplo.com',
          avatarUrl: null,
        },
      }),
    );
    expect(store.displayName()).toBe('Rafael Mendes');
  });

  it('§17 do design system: iniciais servem de avatar substituto', () => {
    store.applySession(
      sessionResponse({
        user: {
          id: 'x',
          fullName: 'Rafael Mendes',
          displayName: null,
          email: 'rafael@exemplo.com',
          avatarUrl: null,
        },
      }),
    );

    expect(store.initials()).toBe('RM');
  });

  it('ST-03: expõe loading e error', () => {
    store.setLoading(true);
    expect(store.loading()).toBe(true);

    const problem = {
      type: 'about:blank',
      title: 'Autenticação necessária',
      status: 401,
      code: 'DEVTIME-1001',
      detail: 'Credenciais inválidas',
      traceId: 'abc',
    };
    store.setError(problem);
    expect(store.error()?.code).toBe('DEVTIME-1001');

    store.applySession(sessionResponse());
    expect(store.error()).toBeNull();
  });
});
