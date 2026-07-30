import { Injectable, signal } from '@angular/core';

/**
 * Armazenamento do access token (security.md §5.4, FR-066/FR-067).
 *
 * O access token vive **apenas em memória**, num Signal. Isso o torna imune a exfiltração persistente
 * por XSS: um script injetado não encontra nada em `localStorage` nem em cookie legível. A perda ao
 * recarregar a página é resolvida pelo refresh automático, que usa o cookie `HttpOnly`.
 *
 * O refresh token **não** aparece nesta classe por decisão de projeto: ele trafega em cookie
 * `HttpOnly`, `Secure`, `SameSite=Strict`, inacessível a JavaScript (FR-067). Se ele fosse manipulável
 * aqui, a proteção contra XSS deixaria de existir.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private readonly _accessToken = signal<string | null>(null);

  readonly accessToken = this._accessToken.asReadonly();

  set(token: string): void {
    this._accessToken.set(token);
  }

  clear(): void {
    this._accessToken.set(null);
  }

  hasToken(): boolean {
    return this._accessToken() !== null;
  }
}
