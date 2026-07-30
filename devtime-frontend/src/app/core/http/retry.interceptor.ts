import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { retry, timer } from 'rxjs';

/** Apenas indisponibilidade transitória de infraestrutura justifica retentativa. */
const RETRYABLE_STATUSES = new Set([502, 503, 504]);
const MAX_RETRIES = 2;
const BASE_DELAY_MS = 300;

/**
 * Interceptor 4 de 5 (frontend.md §7.2).
 *
 * Retenta apenas `GET` em `502`, `503` e `504`, no máximo duas vezes com backoff.
 *
 * FR-069: nenhuma retentativa em operação não idempotente. Repetir um `POST` que já chegou ao servidor
 * criaria um segundo registro de horas; o cliente não tem como distinguir "não chegou" de "chegou e a
 * resposta se perdeu". Escritas só poderão ser retentadas quando enviarem `Idempotency-Key` (ART-074).
 */
export const retryInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.method !== 'GET') {
    return next(request);
  }
  return next(request).pipe(
    retry({
      count: MAX_RETRIES,
      delay: (error: unknown, retryCount: number) => {
        if (!(error instanceof HttpErrorResponse) || !RETRYABLE_STATUSES.has(error.status)) {
          throw error;
        }
        // Backoff exponencial: retentar imediatamente contra um servidor sobrecarregado
        // acrescenta carga exatamente quando ele menos suporta.
        return timer(BASE_DELAY_MS * 2 ** (retryCount - 1));
      },
    }),
  );
};
