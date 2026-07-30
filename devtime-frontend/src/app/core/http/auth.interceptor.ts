import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { AuthStore } from '../auth/auth.store';
import { TokenStorage } from '../auth/token.storage';

/** Endpoints que não devem receber o header de autorização nem disparar refresh. */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/register', '/auth/logout'];

/**
 * Interceptor 2 de 5 (frontend.md §7.2 e §7.3).
 *
 * Anexa o access token e, ao receber `401`, tenta o refresh **uma única vez** antes de reenviar a
 * requisição original. Requisições concorrentes compartilham o mesmo refresh: a fila vive em
 * {@link AuthService}, não aqui, porque três abas expirando juntas produziriam três rotações — e a
 * segunda usaria um token já rotacionado, que RT-04 interpreta como roubo e responde revogando toda a
 * cadeia.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const tokenStorage = inject(TokenStorage);
  const authService = inject(AuthService);
  const authStore = inject(AuthStore);
  const router = inject(Router);

  const authorized = withAccessToken(request, tokenStorage.accessToken());

  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (!shouldAttemptRefresh(error, request)) {
        return throwError(() => error);
      }
      return authService.refresh().pipe(
        switchMap(() => next(withAccessToken(request, tokenStorage.accessToken()))),
        catchError((refreshError: unknown) => {
          // Refresh falhou: a sessão acabou de verdade. Limpar e levar ao login preservando a rota,
          // para que o usuário volte exatamente onde estava depois de entrar (§11 de frontend.md).
          authStore.clearSession();
          // O `catch` é obrigatório: uma rejeição de navegação não tratada dentro do interceptor
          // derruba o handler global e mascara o erro original que o chamador precisa receber.
          router
            .navigate(['/auth/login'], { queryParams: { returnUrl: router.url } })
            .catch((navigationError: unknown) =>
              console.error('Falha ao redirecionar para o login', navigationError),
            );
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};

function withAccessToken(
  request: HttpRequest<unknown>,
  token: string | null,
): HttpRequest<unknown> {
  if (token === null || isAuthEndpoint(request.url)) {
    return request;
  }
  return request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Um `401` só justifica refresh quando não veio dos próprios endpoints de sessão.
 *
 * Sem esta guarda, um `401` de `/auth/refresh` dispararia outro refresh, formando laço infinito.
 */
function shouldAttemptRefresh(error: unknown, request: HttpRequest<unknown>): boolean {
  return error instanceof HttpErrorResponse && error.status === 401 && !isAuthEndpoint(request.url);
}

function isAuthEndpoint(url: string): boolean {
  return AUTH_ENDPOINTS.some((endpoint) => url.startsWith(`${environment.apiBaseUrl}${endpoint}`));
}
