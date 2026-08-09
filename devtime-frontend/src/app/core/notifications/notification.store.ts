import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../auth/auth.store';
import { TokenStorage } from '../auth/token.storage';

/** Evento do fluxo, como o servidor publica em `StreamEventDto`. */
export interface NotificationStreamEvent {
  readonly id: string;
  readonly type: string;
  readonly severity: string;
  readonly title: string;
  readonly unreadCount: number;
}

interface UnreadCountResponse {
  readonly unreadCount: number;
  readonly bySeverity: Record<string, number>;
}

/**
 * Contagem de não lidas e fluxo em tempo real (`core`).
 *
 * Vive em `core` porque a barra superior a consome em toda tela: um store provido na rota morreria a
 * cada navegação e o contador piscaria a cada clique.
 *
 * **O fluxo não usa `EventSource`.** O endpoint exige `Authorization: Bearer`, e a API do navegador
 * não permite cabeçalhos personalizados; a alternativa comum — mandar o token na URL — deixaria a
 * credencial em log de servidor, histórico e referenciador. A leitura é feita com `fetch` sobre o
 * corpo em fluxo, que aceita cabeçalho e é cancelável.
 *
 * ST-02 / CP-10: a conexão expira com o access token e o cliente **recarrega** o histórico ao
 * reconectar, em vez de assumir que nada se perdeu. Por isso a contagem definitiva vem sempre do
 * endpoint leve, e o fluxo apenas antecipa o aviso.
 */
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly http = inject(HttpClient);
  private readonly authStore = inject(AuthStore);
  private readonly tokenStorage = inject(TokenStorage);

  private readonly _unreadCount = signal(0);
  private readonly _lastEvent = signal<NotificationStreamEvent | null>(null);
  private readonly _connected = signal(false);

  private controller: AbortController | null = null;

  readonly unreadCount = this._unreadCount.asReadonly();
  readonly lastEvent = this._lastEvent.asReadonly();
  readonly connected = this._connected.asReadonly();

  readonly hasUnread = computed(() => this._unreadCount() > 0);

  /** Acima de 99 o número deixa de caber e de importar: o que importa é "muitas". */
  readonly badge = computed(() => {
    const count = this._unreadCount();
    return count > 99 ? '99+' : `${count}`;
  });

  setUnreadCount(count: number): void {
    this._unreadCount.set(Math.max(0, count));
  }

  async refresh(): Promise<void> {
    if (!this.authStore.hasTenantSelected()) {
      return;
    }
    try {
      const response = await firstValueFrom(
        this.http.get<UnreadCountResponse>(`${environment.apiBaseUrl}/notifications/unread-count`),
      );
      this._unreadCount.set(response.unreadCount);
    } catch {
      // Falha de rede não zera o contador: um zero errado esconde alerta que existe.
    }
  }

  /**
   * Abre o fluxo de eventos.
   *
   * Idempotente: uma conexão por vez. Sem laço de reconexão automático — a reconexão acontece quando
   * a sessão é renovada e o shell chama este método de novo; um laço próprio competiria com a
   * renovação de token e poderia manter conexões órfãs contra o limite de três (ST-03).
   */
  async connect(): Promise<void> {
    if (this.controller !== null || typeof fetch === 'undefined') {
      return;
    }
    const token = this.tokenStorage.accessToken();
    if (token === null) {
      return;
    }

    const controller = new AbortController();
    this.controller = controller;
    try {
      const response = await fetch(`${environment.apiBaseUrl}/notifications/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        credentials: 'include',
        signal: controller.signal,
      });
      if (!response.ok || response.body === null) {
        this.disconnect();
        return;
      }
      this._connected.set(true);
      await this.read(response.body);
    } catch {
      // Inclui o cancelamento deliberado; em qualquer caso a conexão deixa de existir.
    } finally {
      this._connected.set(false);
      this.controller = null;
    }
  }

  disconnect(): void {
    this.controller?.abort();
    this.controller = null;
    this._connected.set(false);
  }

  /**
   * Lê o corpo em fluxo e recorta os eventos.
   *
   * O formato SSE separa eventos por linha em branco e prefixa o conteúdo com `data:`. O buffer é
   * necessário porque um pedaço da rede pode terminar no meio de um evento — processá-lo cedo
   * produziria JSON truncado.
   */
  private async read(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        return;
      }
      buffer += decoder.decode(value, { stream: true });

      let separator = buffer.indexOf('\n\n');
      while (separator !== -1) {
        this.handleChunk(buffer.slice(0, separator));
        buffer = buffer.slice(separator + 2);
        separator = buffer.indexOf('\n\n');
      }
    }
  }

  private handleChunk(chunk: string): void {
    const data = chunk
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trim())
      .join('');
    if (data === '') {
      // Batimento: mantém a conexão viva e não carrega informação.
      return;
    }
    try {
      const parsed: Partial<NotificationStreamEvent> = JSON.parse(data);
      if (typeof parsed.unreadCount === 'number') {
        this._unreadCount.set(parsed.unreadCount);
      }
      if (typeof parsed.id === 'string') {
        this._lastEvent.set(parsed as NotificationStreamEvent);
      }
    } catch {
      // Evento malformado é descartado; a contagem correta virá do endpoint leve.
    }
  }
}
