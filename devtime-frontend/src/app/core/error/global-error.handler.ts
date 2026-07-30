import { ErrorHandler, inject, Injectable, Provider } from '@angular/core';
import { MessageService } from 'primeng/api';
import { isProblemDetail } from './problem-detail.model';

/**
 * Tratamento de erro não capturado (frontend.md §17).
 *
 * Toda falha que escapa registra no console e exibe um toast genérico com o `traceId`, em vez de deixar
 * a interface em estado indefinido.
 *
 * `ProblemDetail` é ignorado aqui: erros de API já foram tratados e notificados pelo `errorInterceptor`.
 * Sem essa exclusão, cada falha de requisição produziria dois toasts.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly messageService = inject(MessageService);

  handleError(error: unknown): void {
    if (isProblemDetail(error)) {
      return;
    }

    // O console é a única saída de diagnóstico do cliente; sem ele, um erro de renderização vira
    // apenas "não funcionou" no relato do usuário.
    console.error('Erro não tratado', error);

    this.messageService.add({
      severity: 'error',
      summary: $localize`:@@error.unexpected.title:Erro inesperado`,
      detail: $localize`:@@error.unexpected:Não foi possível concluir a operação. Informe o código ao suporte.`,
      life: 6000,
    });
  }
}

export function provideGlobalErrorHandler(): Provider {
  return { provide: ErrorHandler, useClass: GlobalErrorHandler };
}
