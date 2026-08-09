/**
 * Página do backend (`shared/pagination/PageResponse`).
 *
 * AP-02 / FR-061: espelha o DTO exatamente, sem transformação. `page` é base zero, como no servidor —
 * converter para base 1 aqui obrigaria toda chamada a lembrar da conversão inversa.
 */
export interface PageResponse<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly last: boolean;
}

/** RN-012: o servidor recusa `size` acima de 100 com `DEVTIME-2006`. */
export const MAX_PAGE_SIZE = 100;

export const DEFAULT_PAGE_SIZE = 20;

/** Opções oferecidas no seletor de itens por página (L4). */
export const PAGE_SIZE_OPTIONS: readonly number[] = [10, 20, 50, 100];

export function emptyPage<T>(size = DEFAULT_PAGE_SIZE): PageResponse<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0, last: true };
}
