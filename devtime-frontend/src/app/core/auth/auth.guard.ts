import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthStore } from './auth.store';

/**
 * Guards de rota (frontend.md §8, FR-081 a FR-083).
 *
 * FR-083 / IMP-06: guards são **apenas ergonomia**. Toda decisão real de autorização é do backend; um
 * guard nunca é a única barreira. Ele existe para evitar renderizar uma tela que falharia e para
 * levar o usuário ao lugar certo.
 */

/** Verifica sessão válida; tenta restaurar antes de desistir (consequência de §5.4 de security.md). */
export const authGuard: CanActivateFn = async (_route, state) => {
  const store = inject(AuthStore);
  const authService = inject(AuthService);
  const router = inject(Router);

  if (store.isAuthenticated()) {
    return true;
  }

  // O access token vive em memória e foi perdido no reload; o cookie de refresh pode salvar a sessão.
  if (await authService.restoreSession()) {
    return true;
  }

  return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
};

/** Verifica que a organização foi escolhida (CE-S-05, CE-P-11). */
export const tenantSelectedGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const router = inject(Router);

  if (store.hasTenantSelected()) {
    return true;
  }
  return router.createUrlTree(['/auth/select-tenant']);
};

/**
 * Impede que quem já está autenticado volte às telas de autenticação.
 *
 * Sem ele, o usuário logado que navegasse para `/auth/login` veria um formulário de entrada, o que
 * sugere que a sessão caiu.
 */
export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const router = inject(Router);

  return store.isAuthenticated() ? router.createUrlTree(['/']) : true;
};

/**
 * Verifica as permissões do papel para a rota (FR-082).
 *
 * Basta **uma** das permissões informadas: as rotas declaram o conjunto que dá acesso à área, e
 * exigir todas bloquearia papéis que legitimamente acessam a tela por outro caminho da matriz.
 */
export function permissionGuard(permissions: readonly string[]): CanActivateFn {
  return () => {
    const store = inject(AuthStore);
    const router = inject(Router);

    return store.hasAnyPermission(permissions) ? true : router.createUrlTree(['/forbidden']);
  };
}
