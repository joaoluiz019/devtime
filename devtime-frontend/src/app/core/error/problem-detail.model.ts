/**
 * Resposta de erro em RFC 7807, espelhando o backend (frontend.md §11, ADR-017 EX-02).
 *
 * AP-02 / FR-061: os tipos espelham exatamente os DTOs do backend, sem transformação.
 */
export interface FieldError {
  readonly field: string;
  readonly message: string;
}

export interface ConflictingResource {
  readonly type: string;
  readonly id: string;
}

export interface ProblemDetail {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  /** Código estável `DEVTIME-XXXX`. É o identificador programático; `detail` é apresentação. */
  readonly code: string;
  readonly detail: string;
  readonly instance?: string;
  /** Correlaciona a resposta com o log do servidor; exibido em texto discreto para o suporte. */
  readonly traceId: string;
  readonly timestamp?: string;
  /** FR-070 / FM-06: mapeado para os campos do formulário, nunca exibido em toast. */
  readonly errors?: readonly FieldError[];
  readonly conflictingResource?: ConflictingResource;
}

/**
 * Erro genérico usado quando a resposta não é um Problem Detail válido.
 *
 * CE-F "API retorna formato inesperado": a falha é tratada como erro genérico. Nunca renderizar
 * `undefined` na interface.
 */
export const UNEXPECTED_PROBLEM: ProblemDetail = {
  type: 'about:blank',
  title: 'Erro inesperado',
  status: 0,
  code: 'DEVTIME-9001',
  detail: 'Não foi possível concluir a operação.',
  traceId: '',
};

export function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Partial<ProblemDetail>;
  return typeof candidate.code === 'string' && typeof candidate.status === 'number';
}
