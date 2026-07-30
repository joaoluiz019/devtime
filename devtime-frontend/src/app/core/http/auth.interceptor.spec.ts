import {
  HttpClient,
  HttpErrorResponse,
  HttpStatusCode,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSessionResponse } from '../auth/auth.model';
import { AuthStore } from '../auth/auth.store';
import { TokenStorage } from '../auth/token.storage';
import { authInterceptor } from './auth.interceptor';

const SESSION: AuthSessionResponse = {
  accessToken: 'token-renovado',
  tokenType: 'Bearer',
  expiresIn: 900,
  tenantSelectionRequired: false,
  user: {
    id: 'u1',
    fullName: 'Rafael Mendes',
    displayName: 'Rafael',
    email: 'rafael@exemplo.com',
    avatarUrl: null,
  },
  tenant: {
    id: 't1',
    name: 'Rafael Mendes Dev',
    slug: 'rafael-dev',
    timezone: 'America/Sao_Paulo',
    currency: 'BRL',
    logoUrl: null,
  },
  role: 'OWNER',
  permissions: ['TENANT_VIEW'],
};

/**
 * Renovação de token no interceptor (frontend.md §7.3).
 *
 * O teste de fila única (FR-068) é o mais importante da suíte de frontend: sem ela, três abas expirando
 * juntas disparam três rotações, e a segunda usa um token já rotacionado — que RT-04 interpreta como
 * roubo e responde revogando toda a cadeia, derrubando a sessão legítima.
 */
describe('authInterceptor', () => {
  let http: HttpTestingController;
  let tokenStorage: TokenStorage;
  let authStore: AuthStore;

  const PROTECTED_URL = `${environment.apiBaseUrl}/clients`;
  const REFRESH_URL = `${environment.apiBaseUrl}/auth/refresh`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        // A rota de login precisa existir: o interceptor redireciona para ela quando o refresh
        // falha, e um roteador sem rotas transformaria o cenário em falha de infraestrutura de
        // teste em vez de exercitar o comportamento.
        provideRouter([{ path: 'auth/login', children: [] }]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    tokenStorage = TestBed.inject(TokenStorage);
    authStore = TestBed.inject(AuthStore);
  });

  afterEach(() => http.verify());

  function get(url: string): Promise<unknown> {
    return firstValueFrom(TestBed.inject(HttpClient).get(url));
  }

  it('anexa o access token quando existe', async () => {
    tokenStorage.set('token-atual');
    const pending = get(PROTECTED_URL);

    const request = http.expectOne(PROTECTED_URL);
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-atual');
    request.flush({});
    await pending;
  });

  it('não anexa header quando não há token', async () => {
    const pending = get(PROTECTED_URL);

    const request = http.expectOne(PROTECTED_URL);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
    await pending;
  });

  it('não anexa o header aos próprios endpoints de sessão', async () => {
    tokenStorage.set('token-atual');
    const pending = get(`${environment.apiBaseUrl}/auth/login`);

    const request = http.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
    await pending;
  });

  it('§7.3: ao receber 401, renova o token e reenvia a requisição original', async () => {
    tokenStorage.set('token-expirado');
    const pending = get(PROTECTED_URL);

    http
      .expectOne(PROTECTED_URL)
      .flush(
        { code: 'DEVTIME-1001' },
        { status: HttpStatusCode.Unauthorized, statusText: 'Unauthorized' },
      );

    http.expectOne(REFRESH_URL).flush(SESSION);

    const retried = http.expectOne(PROTECTED_URL);
    expect(retried.request.headers.get('Authorization')).toBe('Bearer token-renovado');
    retried.flush({ ok: true });

    await expect(pending).resolves.toEqual({ ok: true });
  });

  it('FR-068: dois 401 concorrentes disparam uma única chamada de refresh', async () => {
    tokenStorage.set('token-expirado');
    const first = get(PROTECTED_URL);
    const second = get(`${environment.apiBaseUrl}/contracts`);

    http
      .expectOne(PROTECTED_URL)
      .flush({}, { status: HttpStatusCode.Unauthorized, statusText: 'Unauthorized' });
    http
      .expectOne(`${environment.apiBaseUrl}/contracts`)
      .flush({}, { status: HttpStatusCode.Unauthorized, statusText: 'Unauthorized' });

    // Uma única rotação atende às duas requisições — é o que impede a revogação em cadeia de RT-04.
    http.expectOne(REFRESH_URL).flush(SESSION);

    http.expectOne(PROTECTED_URL).flush({ ok: 1 });
    http.expectOne(`${environment.apiBaseUrl}/contracts`).flush({ ok: 2 });

    await expect(first).resolves.toEqual({ ok: 1 });
    await expect(second).resolves.toEqual({ ok: 2 });
  });

  it('um 401 do próprio refresh não dispara outro refresh, evitando laço infinito', async () => {
    const pending = get(REFRESH_URL);

    http
      .expectOne(REFRESH_URL)
      .flush({}, { status: HttpStatusCode.Unauthorized, statusText: 'Unauthorized' });

    await expect(pending).rejects.toBeInstanceOf(HttpErrorResponse);
  });

  it('refresh falho limpa a sessão', async () => {
    authStore.applySession(SESSION);
    tokenStorage.set('token-expirado');
    const pending = get(PROTECTED_URL);

    http
      .expectOne(PROTECTED_URL)
      .flush({}, { status: HttpStatusCode.Unauthorized, statusText: 'Unauthorized' });
    http
      .expectOne(REFRESH_URL)
      .flush({}, { status: HttpStatusCode.Unauthorized, statusText: 'Unauthorized' });

    await expect(pending).rejects.toBeDefined();
    expect(authStore.isAuthenticated()).toBe(false);
    expect(tokenStorage.accessToken()).toBeNull();
  });

  it('erro diferente de 401 não dispara refresh', async () => {
    tokenStorage.set('token-atual');
    const pending = get(PROTECTED_URL);

    http
      .expectOne(PROTECTED_URL)
      .flush({}, { status: HttpStatusCode.Forbidden, statusText: 'Forbidden' });

    await expect(pending).rejects.toBeInstanceOf(HttpErrorResponse);
  });
});
