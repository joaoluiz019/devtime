import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStore } from '../auth/auth.store';

/**
 * Interceptor 3 de 5 (frontend.md §7.2).
 *
 * Anexa `X-Tenant-Id` **exclusivamente para correlação de logs**. O backend ignora este header para
 * qualquer decisão de autorização: ART-021 e TI-01 exigem que o `tenantId` venha somente da claim
 * `tid` do JWT. Se o header fosse considerado, um cliente poderia trocá-lo e ler dados de outro tenant
 * — a falha mais grave prevista no modelo de ameaças.
 */
export const tenantInterceptor: HttpInterceptorFn = (request, next) => {
  const tenant = inject(AuthStore).tenant();
  if (tenant === null) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'X-Tenant-Id': tenant.id } }));
};
