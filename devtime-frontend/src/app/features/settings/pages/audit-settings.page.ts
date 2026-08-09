import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PaginatorModule } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { DEFAULT_PAGE_SIZE, emptyPage, PageResponse } from '../../../shared/models/page.model';
import { SettingsApi } from '../data/settings.api';
import { AuditLog } from '../data/settings.model';

/** Intervalo máximo aceito por requisição (users.md §10.1). */
const MAX_RANGE_DAYS = 90;

/**
 * Auditoria — P33, layout L9.
 *
 * A trilha responde "quem mudou o quê e quando", e o intervalo é sempre explícito: sem datas o
 * servidor aplica os últimos 30 dias, e quem lê precisa saber que está vendo um recorte, não tudo.
 *
 * Acima de 90 dias o servidor recusa (`DEVTIME-3001`); a tela verifica antes para não gastar uma
 * requisição em uma consulta que já se sabe inválida.
 */
@Component({
  selector: 'dt-audit-settings-page',
  imports: [
    DatePipe,
    FormsModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    PaginatorModule,
    SkeletonModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.audit">Auditoria</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.audit.subtitle">
      Registro de alterações da organização, da mais recente para a mais antiga.
    </p>

    <div class="dt-setting__row">
      <div class="dt-setting__field">
        <label for="audit-from" i18n="@@settings.audit.from">De</label>
        <input
          id="audit-from"
          type="date"
          pInputText
          [ngModel]="from()"
          (ngModelChange)="from.set($event)"
        />
      </div>

      <div class="dt-setting__field">
        <label for="audit-to" i18n="@@settings.audit.to">Até</label>
        <input
          id="audit-to"
          type="date"
          pInputText
          [ngModel]="to()"
          (ngModelChange)="to.set($event)"
        />
      </div>

      <div class="dt-setting__field">
        <label for="audit-entity" i18n="@@settings.audit.entity">Tipo de registro</label>
        <input
          id="audit-entity"
          type="text"
          pInputText
          [ngModel]="entityType()"
          (ngModelChange)="entityType.set($event)"
          placeholder="CONTRACT"
          i18n-placeholder="@@settings.audit.entity.placeholder"
        />
      </div>
    </div>

    <div class="dt-setting__actions">
      <p-button
        i18n-label="@@settings.audit.search"
        label="Consultar"
        [loading]="loading()"
        (onClick)="search()"
      />
    </div>

    <div aria-live="polite">
      @if (rangeTooLong()) {
        <p-message severity="warn" styleClass="w-full mb-3">
          <span i18n="@@settings.audit.rangeTooLong">
            O intervalo máximo por consulta é de 90 dias. Ajuste as datas.
          </span>
        </p-message>
      }
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (loading()) {
      <p-skeleton height="12rem" />
    } @else if (entries().length === 0) {
      <p class="dt-setting__hint" i18n="@@settings.audit.empty">
        Nenhuma alteração registrada no intervalo consultado.
      </p>
    } @else {
      <ul class="dt-setting__list" role="list">
        @for (entry of entries(); track entry.id) {
          <li class="dt-setting__item">
            <span class="dt-setting__item-name">
              <strong>{{ entry.action }}</strong>
              <span class="dt-setting__meta">{{ entry.entityType }}</span>
              <span class="dt-setting__meta">{{ entry.actor?.name ?? systemLabel }}</span>
            </span>
            <span class="dt-setting__meta">{{ entry.occurredAt | date: 'short' }}</span>
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
  styleUrl: './settings-form.scss',
})
export class AuditSettingsPage {
  private readonly api = inject(SettingsApi);

  protected readonly systemLabel = $localize`:@@ticket.timeline.system:Sistema`;

  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly entityType = signal('');

  private readonly _page = signal<PageResponse<AuditLog>>(emptyPage<AuditLog>());
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly page = this._page.asReadonly();
  protected readonly loading = this._loading.asReadonly();

  protected readonly entries = computed(() => this._page().content);

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /** Verificado no cliente para não gastar requisição com um intervalo já recusado. */
  protected readonly rangeTooLong = computed(() => {
    const from = this.from();
    const to = this.to();
    if (from === '' || to === '') {
      return false;
    }
    const days = (Date.parse(to) - Date.parse(from)) / 86400000;
    return days > MAX_RANGE_DAYS;
  });

  constructor() {
    void this.load(0);
  }

  protected async search(): Promise<void> {
    await this.load(0);
  }

  protected onPageChange(event: { first?: number; rows?: number }): void {
    const size = event.rows ?? DEFAULT_PAGE_SIZE;
    void this.load(Math.floor((event.first ?? 0) / size), size);
  }

  private async load(page: number, size = DEFAULT_PAGE_SIZE): Promise<void> {
    if (this.rangeTooLong()) {
      return;
    }
    this._loading.set(true);
    this._error.set(null);
    try {
      this._page.set(
        await firstValueFrom(
          this.api.auditLogs({
            page,
            size,
            entityType: this.entityType() === '' ? undefined : this.entityType(),
            // O backend espera instantes; a data escolhida cobre o dia inteiro no fuso local.
            occurredFrom: this.from() === '' ? undefined : `${this.from()}T00:00:00Z`,
            occurredTo: this.to() === '' ? undefined : `${this.to()}T23:59:59Z`,
          }),
        ),
      );
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._page.set(emptyPage<AuditLog>(size));
    } finally {
      this._loading.set(false);
    }
  }
}
