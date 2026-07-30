import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';
import { messageForCode } from '../error/error-messages';
import { isProblemDetail, ProblemDetail, UNEXPECTED_PROBLEM } from '../error/problem-detail.model';

/** Status tratados pela tela que originou a requisição, não por toast. */
const HANDLED_BY_FORM = new Set([400, 422]);
/** Status tratados por navegação ou diálogo específico. */
const HANDLED_BY_ROUTE = new Set([401, 404, 409]);

/**
 * Interceptor 5 de 5 (frontend.md §7.2 e §11).
 *
 * Converte a resposta em {@link ProblemDetail} tipado e exibe toast **apenas** quando não há outro
 * responsável pela mensagem.
 *
 * FR-070 / FM-03: erro de validação (`400`/`422`) é mapeado para os campos do formulário, nunca
 * exibido em toast — um toast tira a mensagem de perto do campo errado e desaparece antes de o usuário
 * localizar o problema. `401` é tratado pelo `authInterceptor`, `404` e `409` pela tela.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const messageService = inject(MessageService);

  return next(request).pipe(
    catchError((error: unknown) => {
      const problem = toProblemDetail(error);
      if (shouldNotify(problem.status)) {
        messageService.add({
          severity: problem.status >= 500 ? 'error' : 'warn',
          summary: problem.title,
          detail: buildDetail(problem),
          life: problem.status >= 500 ? 6000 : 3000,
        });
      }
      return throwError(() => problem);
    }),
  );
};

function shouldNotify(status: number): boolean {
  return !HANDLED_BY_FORM.has(status) && !HANDLED_BY_ROUTE.has(status);
}

/**
 * Acrescenta o `traceId` a erros de servidor.
 *
 * §11 de frontend.md: em `5xx`, o `traceId` é exibido de forma copiável para o suporte. Em `4xx` ele
 * seria ruído, porque a causa está na requisição e o usuário pode agir sem acionar o suporte.
 */
function buildDetail(problem: ProblemDetail): string {
  const message = messageForCode(problem.code, problem.detail);
  if (problem.status < 500 || problem.traceId === '') {
    return message;
  }
  return `${message} (${problem.code} · ${problem.traceId})`;
}

/**
 * Normaliza qualquer falha em {@link ProblemDetail}.
 *
 * Falha de rede não produz corpo algum, e um servidor mal configurado pode responder HTML. Nos dois
 * casos o resultado é o problema genérico: a interface nunca renderiza `undefined`.
 */
function toProblemDetail(error: unknown): ProblemDetail {
  if (!(error instanceof HttpErrorResponse)) {
    return UNEXPECTED_PROBLEM;
  }
  if (isProblemDetail(error.error)) {
    return error.error;
  }
  return { ...UNEXPECTED_PROBLEM, status: error.status, title: resolveTitle(error.status) };
}

function resolveTitle(status: number): string {
  if (status === 0) {
    return $localize`:@@error.offline.title:Sem conexão`;
  }
  return UNEXPECTED_PROBLEM.title;
}
