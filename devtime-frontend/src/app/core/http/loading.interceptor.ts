import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { finalize } from 'rxjs';

/**
 * Marca uma requisição como silenciosa, mantendo-a fora do contador global.
 *
 * Necessário porque requisições de fundo — a ressincronização periódica do cronômetro, por exemplo —
 * fariam a barra de progresso global piscar a cada minuto sem que o usuário tenha pedido nada.
 */
export const SILENT_REQUEST = new HttpContextToken<boolean>(() => false);

/** Contador global de requisições em andamento. */
@Injectable({ providedIn: 'root' })
export class LoadingCounter {
  private readonly _pending = signal(0);

  readonly pending = this._pending.asReadonly();

  increment(): void {
    this._pending.update((count) => count + 1);
  }

  decrement(): void {
    // Nunca abaixo de zero: um decremento órfão deixaria o contador negativo e a interface
    // permanentemente sem indicador de carregamento.
    this._pending.update((count) => Math.max(0, count - 1));
  }
}

/**
 * Interceptor 1 de 5 (frontend.md §7.2).
 *
 * É o primeiro da cadeia para que o tempo medido inclua o refresh de token e as retentativas — do
 * ponto de vista do usuário, a espera é uma só.
 */
export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.context.get(SILENT_REQUEST)) {
    return next(request);
  }
  const counter = inject(LoadingCounter);
  counter.increment();
  return next(request).pipe(finalize(() => counter.decrement()));
};
