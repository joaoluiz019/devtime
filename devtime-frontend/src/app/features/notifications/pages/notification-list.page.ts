import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { PaginatorModule } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { NotificationStore } from '../../../core/notifications/notification.store';
import { DEFAULT_PAGE_SIZE, emptyPage, PageResponse } from '../../../shared/models/page.model';
import { NotificationApi } from '../data/notification.api';
import { AppNotification, NotificationSeverity } from '../data/notification.model';

/**
 * Central de notificações — P25, layout L4.
 *
 * Ordenação fixa por data decrescente, como o servidor entrega: quem abre a central procura o que
 * acabou de acontecer.
 *
 * Marcar como lida é **reversível** aqui. Em outras telas ler já marca; nesta, a leitura é o objetivo
 * e desmarcar é a forma de deixar um alerta pendente para depois.
 */
@Component({
  selector: 'dt-notification-list-page',
  imports: [
    DatePipe,
    RouterLink,
    ButtonModule,
    MessageModule,
    PaginatorModule,
    SkeletonModule,
    TagModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="dt-notifications__header">
      <div>
        <h1 class="dt-notifications__title" i18n="@@notifications.title">Notificações</h1>
        <p class="dt-notifications__subtitle">{{ subtitle() }}</p>
      </div>
      @if (store.hasUnread()) {
        <p-button
          i18n-label="@@notifications.markAll"
          label="Marcar todas como lidas"
          severity="secondary"
          [outlined]="true"
          [loading]="saving()"
          (onClick)="markAllRead()"
        />
      }
    </header>

    <section class="dt-notifications__filters">
      <p-button
        i18n-label="@@notifications.filter.all"
        label="Todas"
        severity="secondary"
        [outlined]="unreadOnly()"
        (onClick)="setUnreadOnly(false)"
      />
      <p-button
        i18n-label="@@notifications.filter.unread"
        label="Não lidas"
        severity="secondary"
        [outlined]="!unreadOnly()"
        (onClick)="setUnreadOnly(true)"
      />
    </section>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (loading()) {
      <p-skeleton height="12rem" />
    } @else if (notifications().length === 0) {
      <div class="dt-notifications__empty">
        <h2 i18n="@@notifications.empty.title">Nada por aqui</h2>
        <p i18n="@@notifications.empty.text">
          Avisos de saldo, prazo e menções aparecem nesta central.
        </p>
      </div>
    } @else {
      <ul class="dt-notifications__list" role="list">
        @for (notification of notifications(); track notification.id) {
          <li
            class="dt-notifications__item"
            [class.dt-notifications__item--unread]="notification.readAt === undefined"
          >
            <div class="dt-notifications__body">
              <div class="dt-notifications__meta">
                <p-tag
                  [value]="severityLabel(notification.severity)"
                  [severity]="tagSeverity(notification.severity)"
                />
                <span>{{ notification.createdAt | date: 'short' }}</span>
                @if (notification.readAt === undefined) {
                  <span class="dt-notifications__dot" i18n="@@notifications.unread">não lida</span>
                }
              </div>
              <h2 class="dt-notifications__item-title">{{ notification.title }}</h2>
              <p class="dt-notifications__text">{{ notification.body }}</p>
              @if (notification.action; as action) {
                <a class="dt-notifications__action" [routerLink]="action.route">{{
                  action.label
                }}</a>
              }
            </div>

            <div class="dt-notifications__actions">
              @if (notification.readAt === undefined) {
                <p-button
                  icon="pi pi-check"
                  severity="secondary"
                  [text]="true"
                  i18n-ariaLabel="@@notifications.markRead"
                  ariaLabel="Marcar como lida"
                  (onClick)="markRead(notification)"
                />
              } @else {
                <p-button
                  icon="pi pi-undo"
                  severity="secondary"
                  [text]="true"
                  i18n-ariaLabel="@@notifications.markUnread"
                  ariaLabel="Marcar como não lida"
                  (onClick)="markUnread(notification)"
                />
              }
              <p-button
                icon="pi pi-trash"
                severity="danger"
                [text]="true"
                i18n-ariaLabel="@@notifications.delete"
                ariaLabel="Excluir notificação"
                (onClick)="remove(notification)"
              />
            </div>
          </li>
        }
      </ul>

      <p-paginator
        [first]="page().page * page().size"
        [rows]="page().size"
        [totalRecords]="page().totalElements"
        (onPageChange)="onPageChange($event)"
      />
    }
  `,
  styleUrl: './notification-list.page.scss',
})
export class NotificationListPage {
  private readonly api = inject(NotificationApi);

  protected readonly store = inject(NotificationStore);

  private readonly _page = signal<PageResponse<AppNotification>>(emptyPage<AppNotification>());
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _unreadOnly = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly page = this._page.asReadonly();
  protected readonly loading = this._loading.asReadonly();
  protected readonly saving = this._saving.asReadonly();
  protected readonly unreadOnly = this._unreadOnly.asReadonly();

  protected readonly notifications = computed(() => this._page().content);

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly subtitle = computed(() => {
    const unread = this.store.unreadCount();
    return unread === 0
      ? $localize`:@@notifications.subtitle.none:Nenhuma pendente`
      : $localize`:@@notifications.subtitle:${unread}:count: não lidas`;
  });

  constructor() {
    void this.load(0);
  }

  protected severityLabel(severity: NotificationSeverity): string {
    switch (severity) {
      case 'CRITICAL':
        return $localize`:@@notifications.severity.critical:Crítico`;
      case 'WARNING':
        return $localize`:@@notifications.severity.warning:Atenção`;
      default:
        return $localize`:@@notifications.severity.info:Informação`;
    }
  }

  protected tagSeverity(severity: NotificationSeverity): 'danger' | 'warn' | 'info' {
    switch (severity) {
      case 'CRITICAL':
        return 'danger';
      case 'WARNING':
        return 'warn';
      default:
        return 'info';
    }
  }

  protected async setUnreadOnly(unreadOnly: boolean): Promise<void> {
    this._unreadOnly.set(unreadOnly);
    await this.load(0);
  }

  protected onPageChange(event: { first?: number; rows?: number }): void {
    const size = event.rows ?? DEFAULT_PAGE_SIZE;
    void this.load(Math.floor((event.first ?? 0) / size), size);
  }

  private async load(page: number, size = DEFAULT_PAGE_SIZE): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._page.set(
        await firstValueFrom(
          this.api.list({
            page,
            size,
            read: this._unreadOnly() ? false : undefined,
          }),
        ),
      );
      // A contagem da barra superior acompanha a central: abrir e ler aqui precisa refletir lá.
      await this.store.refresh();
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._page.set(emptyPage<AppNotification>(size));
    } finally {
      this._loading.set(false);
    }
  }

  protected async markRead(notification: AppNotification): Promise<void> {
    await this.mutate(async () => {
      const result = await firstValueFrom(this.api.markRead(notification.id));
      this.store.setUnreadCount(result.unreadCount);
    });
  }

  protected async markUnread(notification: AppNotification): Promise<void> {
    await this.mutate(async () => {
      const result = await firstValueFrom(this.api.markUnread(notification.id));
      this.store.setUnreadCount(result.unreadCount);
    });
  }

  protected async markAllRead(): Promise<void> {
    await this.mutate(async () => {
      const result = await firstValueFrom(this.api.markAllRead());
      this.store.setUnreadCount(result.unreadCount);
    });
  }

  protected async remove(notification: AppNotification): Promise<void> {
    await this.mutate(() => firstValueFrom(this.api.delete(notification.id)));
  }

  /**
   * Toda alteração recarrega a página corrente.
   *
   * O servidor devolve a contagem nova, mas a lista pode ter mudado por outro motivo — inclusive por
   * um evento do fluxo chegado enquanto a tela estava aberta.
   */
  private async mutate(operation: () => Promise<unknown>): Promise<void> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation();
      await this.load(this._page().page, this._page().size);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    } finally {
      this._saving.set(false);
    }
  }
}
