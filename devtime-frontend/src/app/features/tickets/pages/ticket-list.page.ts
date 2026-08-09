import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { PaginatorModule } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import {
  ticketPriorityLabel,
  ticketStatusLabel,
} from '../../../shared/components/ticket-badges/ticket-badges.component';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/models/page.model';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { TicketCardComponent } from '../components/ticket-card.component';
import { TicketListStore } from '../data/ticket-list.store';
import { TicketListQuery, TicketPriority, TicketStatus, TicketType } from '../data/ticket.model';

const STATUSES: readonly TicketStatus[] = [
  'BACKLOG',
  'TODO',
  'IN_PROGRESS',
  'BLOCKED',
  'IN_REVIEW',
  'DONE',
];

const PRIORITIES: readonly TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

const TYPES: readonly TicketType[] = [
  'FEATURE',
  'BUG',
  'SUPPORT',
  'MEETING',
  'MAINTENANCE',
  'OTHER',
];

/**
 * Lista de tickets — P17, layout L5 (T-007-28).
 *
 * LS-03: os filtros compostos vivem na URL, inclusive os de múltipla escolha, que viajam como
 * parâmetro repetido. É o que permite mandar a alguém o link de "meus bugs urgentes em andamento"
 * em vez de descrever a sequência de cliques.
 */
@Component({
  selector: 'dt-ticket-list-page',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    DurationPipe,
    InputTextModule,
    MessageModule,
    MultiSelectModule,
    PaginatorModule,
    SkeletonModule,
    TicketCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [TicketListStore],
  template: `
    <header class="dt-tickets__header">
      <div>
        <h1 class="dt-tickets__title" i18n="@@tickets.title">Tickets</h1>
        <p class="dt-tickets__subtitle">{{ subtitle() }}</p>
      </div>
      <div class="dt-tickets__header-actions">
        <p-button
          i18n-label="@@tickets.board"
          label="Ver quadro"
          icon="pi pi-th-large"
          severity="secondary"
          [outlined]="true"
          routerLink="/tickets/board"
        />
        @if (canCreate()) {
          <p-button
            i18n-label="@@tickets.new"
            label="Novo ticket"
            icon="pi pi-plus"
            routerLink="/tickets/new"
          />
        }
      </div>
    </header>

    <section class="dt-tickets__filters">
      <input
        id="tickets-search"
        type="search"
        pInputText
        i18n-placeholder="@@tickets.search"
        placeholder="Buscar por chave ou título"
        i18n-aria-label="@@tickets.search"
        aria-label="Buscar por chave ou título"
        [ngModel]="query().search ?? ''"
        (keyup.enter)="applySearch($any($event.target).value)"
        (search)="applySearch($any($event.target).value)"
      />

      <p-multiselect
        [options]="statusOptions"
        [ngModel]="statusValue()"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@tickets.filter.status"
        placeholder="Situação"
        i18n-ariaLabel="@@tickets.filter.status"
        ariaLabel="Situação"
        (onChange)="applyMulti('status', $event.value)"
      />

      <p-multiselect
        [options]="priorityOptions"
        [ngModel]="priorityValue()"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@tickets.filter.priority"
        placeholder="Prioridade"
        i18n-ariaLabel="@@tickets.filter.priority"
        ariaLabel="Prioridade"
        (onChange)="applyMulti('priority', $event.value)"
      />

      <p-multiselect
        [options]="typeOptions"
        [ngModel]="typeValue()"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@tickets.filter.type"
        placeholder="Tipo"
        i18n-ariaLabel="@@tickets.filter.type"
        ariaLabel="Tipo"
        (onChange)="applyMulti('type', $event.value)"
      />

      <p-button
        [label]="mineLabel()"
        severity="secondary"
        [outlined]="!isMineActive()"
        (onClick)="toggleMine()"
      />

      @if (hasFilters()) {
        <p-button
          i18n-label="@@clients.filter.clear"
          label="Limpar filtros"
          severity="secondary"
          [text]="true"
          (onClick)="clearFilters()"
        />
      }
    </section>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="12rem" />
    } @else if (store.isEmpty()) {
      <div class="dt-tickets__empty">
        <h2 i18n="@@tickets.empty.title">Nenhum ticket encontrado</h2>
        <p i18n="@@tickets.empty.text">
          Ajuste os filtros ou crie um ticket para começar a registrar trabalho.
        </p>
      </div>
    } @else {
      <p class="dt-tickets__totals">{{ totalsLabel() }}</p>

      <div class="dt-tickets__list">
        @for (ticket of store.tickets(); track ticket.id) {
          <dt-ticket-card [ticket]="ticket" [showStatus]="true" />
        }
      </div>

      <p-paginator
        [first]="query().page * query().size"
        [rows]="query().size"
        [totalRecords]="store.total()"
        [rowsPerPageOptions]="pageSizeOptions"
        (onPageChange)="onPageChange($event)"
      />
    }
  `,
  styleUrl: './ticket-list.page.scss',
})
export class TicketListPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(TicketListStore);

  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];

  protected readonly statusOptions = STATUSES.map((status) => ({
    label: ticketStatusLabel(status),
    value: status,
  }));

  protected readonly priorityOptions = PRIORITIES.map((priority) => ({
    label: ticketPriorityLabel(priority),
    value: priority,
  }));

  protected readonly typeOptions = TYPES.map((type) => ({ label: typeLabel(type), value: type }));

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  protected readonly query = computed<TicketListQuery>(() => {
    const params = this.params();
    return {
      contractId: params.get('contractId') ?? undefined,
      clientId: params.get('clientId') ?? undefined,
      status: asArray<TicketStatus>(params.getAll('status')),
      type: asArray<TicketType>(params.getAll('type')),
      priority: asArray<TicketPriority>(params.getAll('priority')),
      assigneeId: params.get('assigneeId') ?? undefined,
      tagIds: asArray<string>(params.getAll('tagIds')),
      search: params.get('search') ?? undefined,
      page: Number(params.get('page') ?? 0),
      size: Number(params.get('size') ?? DEFAULT_PAGE_SIZE),
      sort: params.get('sort') ?? 'updatedAt,desc',
    };
  });

  protected readonly statusValue = computed(() => [...(this.query().status ?? [])]);
  protected readonly priorityValue = computed(() => [...(this.query().priority ?? [])]);
  protected readonly typeValue = computed(() => [...(this.query().type ?? [])]);

  protected readonly canCreate = computed(() => this.authStore.hasPermission('TICKET_CREATE'));

  /** "Meus tickets" é o filtro mais usado; ele é um atalho para `assigneeId` do próprio usuário. */
  protected readonly isMineActive = computed(
    () => this.query().assigneeId === this.authStore.user()?.id,
  );

  protected readonly mineLabel = computed(() =>
    this.isMineActive()
      ? $localize`:@@tickets.filter.allPeople:Todos os responsáveis`
      : $localize`:@@tickets.filter.mine:Meus tickets`,
  );

  protected readonly hasFilters = computed(() => {
    const query = this.query();
    return (
      (query.search ?? '') !== '' ||
      (query.status?.length ?? 0) > 0 ||
      (query.priority?.length ?? 0) > 0 ||
      (query.type?.length ?? 0) > 0 ||
      query.assigneeId !== undefined ||
      query.contractId !== undefined ||
      query.clientId !== undefined
    );
  });

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly subtitle = computed(() => {
    const total = this.store.total();
    return total === 1
      ? $localize`:@@tickets.subtitle.one:1 ticket`
      : $localize`:@@tickets.subtitle.many:${total}:count: tickets`;
  });

  /** RN-309: o número de estourados aparece no topo, porque é o que muda a conversa de contrato. */
  protected readonly totalsLabel = computed(
    () =>
      $localize`:@@tickets.totals:${this.store.pageSpentMinutes()}:minutes: registrados nesta página · ${this.store.overEstimateCount()}:over: acima da estimativa`,
  );

  constructor() {
    effect(() => {
      const query = this.query();
      void this.store.load(query);
    });
  }

  protected applySearch(value: string): void {
    void this.updateParams({ search: value === '' ? null : value, page: 0 });
  }

  protected applyMulti(key: 'status' | 'priority' | 'type', values: unknown): void {
    const list = Array.isArray(values) ? (values as string[]) : [];
    void this.updateParams({ [key]: list.length === 0 ? null : list, page: 0 });
  }

  protected toggleMine(): void {
    const userId = this.authStore.user()?.id ?? null;
    void this.updateParams({ assigneeId: this.isMineActive() ? null : userId, page: 0 });
  }

  protected clearFilters(): void {
    void this.updateParams({
      search: null,
      status: null,
      priority: null,
      type: null,
      assigneeId: null,
      contractId: null,
      clientId: null,
      tagIds: null,
      page: 0,
    });
  }

  protected onPageChange(event: { first?: number; rows?: number }): void {
    const size = event.rows ?? DEFAULT_PAGE_SIZE;
    void this.updateParams({ page: Math.floor((event.first ?? 0) / size), size });
  }

  private updateParams(
    params: Record<string, string | number | readonly string[] | null>,
  ): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
    });
  }
}

function asArray<T extends string>(values: readonly string[]): readonly T[] | undefined {
  return values.length === 0 ? undefined : (values as readonly T[]);
}

function typeLabel(type: TicketType): string {
  switch (type) {
    case 'FEATURE':
      return $localize`:@@ticket.type.feature:Funcionalidade`;
    case 'BUG':
      return $localize`:@@ticket.type.bug:Defeito`;
    case 'SUPPORT':
      return $localize`:@@ticket.type.support:Suporte`;
    case 'MEETING':
      return $localize`:@@ticket.type.meeting:Reunião`;
    case 'MAINTENANCE':
      return $localize`:@@ticket.type.maintenance:Manutenção`;
    default:
      return $localize`:@@ticket.type.other:Outro`;
  }
}

export { typeLabel as ticketTypeLabel };
