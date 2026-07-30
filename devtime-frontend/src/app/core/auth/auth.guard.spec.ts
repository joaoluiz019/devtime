import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { AuthSessionResponse } from './auth.model';
import { guestGuard, permissionGuard, tenantSelectedGuard } from './auth.guard';
import { AuthStore } from './auth.store';

const SESSION: AuthSessionResponse = {
  accessToken: 'token',
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
  role: 'MEMBER',
  permissions: ['DASHBOARD_VIEW_OWN', 'WORKLOG_VIEW_OWN'],
};

/**
 * Guards de rota (FR-081 a FR-083).
 *
 * Os testes verificam a **ergonomia**: para onde o usuário é levado. FR-083 é explícito de que guards
 * nunca são a barreira de segurança — essa é verificada na suíte do backend.
 */
describe('guards de rota', () => {
  let authStore: AuthStore;

  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/contracts' } as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    authStore = TestBed.inject(AuthStore);
  });

  describe('tenantSelectedGuard', () => {
    it('libera quando há organização selecionada', () => {
      authStore.applySession(SESSION);

      const result = TestBed.runInInjectionContext(() => tenantSelectedGuard(route, state));

      expect(result).toBe(true);
    });

    it('CE-S-05: redireciona para a seleção quando não há organização', () => {
      authStore.applySession({
        ...SESSION,
        tenantSelectionRequired: true,
        tenant: undefined,
        role: undefined,
      });

      const result = TestBed.runInInjectionContext(() => tenantSelectedGuard(route, state));

      expect(result).toBeInstanceOf(UrlTree);
      expect((result as UrlTree).toString()).toBe('/auth/select-tenant');
    });
  });

  describe('guestGuard', () => {
    it('libera as telas de autenticação para quem não tem sessão', () => {
      const result = TestBed.runInInjectionContext(() => guestGuard(route, state));

      expect(result).toBe(true);
    });

    it('leva quem já está autenticado para a aplicação', () => {
      authStore.applySession(SESSION);

      const result = TestBed.runInInjectionContext(() => guestGuard(route, state));

      expect(result).toBeInstanceOf(UrlTree);
      expect((result as UrlTree).toString()).toBe('/');
    });
  });

  describe('permissionGuard', () => {
    it('FR-082: libera quando o papel possui a permissão exigida', () => {
      authStore.applySession(SESSION);

      const result = TestBed.runInInjectionContext(() =>
        permissionGuard(['DASHBOARD_VIEW_OWN'])(route, state),
      );

      expect(result).toBe(true);
    });

    it('basta uma das permissões informadas', () => {
      authStore.applySession(SESSION);

      const result = TestBed.runInInjectionContext(() =>
        permissionGuard(['REPORT_VIEW_ANY', 'WORKLOG_VIEW_OWN'])(route, state),
      );

      expect(result).toBe(true);
    });

    it('redireciona para /forbidden quando nenhuma permissão está presente', () => {
      authStore.applySession(SESSION);

      const result = TestBed.runInInjectionContext(() =>
        permissionGuard(['PERIOD_CLOSE'])(route, state),
      );

      expect(result).toBeInstanceOf(UrlTree);
      expect((result as UrlTree).toString()).toBe('/forbidden');
    });
  });
});
