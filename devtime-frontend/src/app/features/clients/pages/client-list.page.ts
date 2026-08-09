import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PaginatorModule } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '../../../shared/models/page.model';
import { formatDocument } from '../../../shared/utils/document';
import { ClientCardComponent } from '../components/client-card.component';
import { ClientListStore } from '../data/client-list.store';
import { ClientListItem, ClientListQuery, ClientStatus } from '../data/client.model';

/** Chip de filtro ativo (LS-01). */
interface FilterChip {
  readonly key: 'search' | 'status' | 'hasActiveContracts';
  readonly label: string;
}

/**
 * Lista de clientes — P10, layout L4 (T-003-20).
 *
 * LS-03 / §6.1 de `frontend.md`: busca, filtro, ordenação e página vivem **na URL**. O componente lê
 * a consulta dos query params e a repassa ao store; nenhum filtro é guardado em Signal local. É o que
 * torna a listagem compartilhável por link e faz o botão "voltar" do navegador desfazer um filtro em
 * vez de sair da tela.
 */
@Component({
  selector: 'dt-client-list-page',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    ClientCardComponent,
    InputTextModule,
    MessageModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    TableModule,
    TagModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ClientListStore],
  template: `
    <header class="dt-clients__header">
      <div>
        <h1 class="dt-clients__title" i18n="@@clients.title">Clientes</h1>
        <p class="dt-clients__subtitle">{{ subtitle() }}</p>
      </div>
      @if (canCreate()) {
        <p-button
          i18n-label="@@clients.new"
          label="Novo cliente"
          icon="pi pi-plus"
          routerLink="/clients/new"
        />
      }
    </header>

    <section class="dt-clients__filters">
      <input
        id="clients-search"
        type="search"
        pInputText
        i18n-placeholder="@@clients.search"
        placeholder="Buscar por nome, razão social ou documento"
        i18n-aria-label="@@clients.search"
        aria-label="Buscar por nome, razão social ou documento"
        [ngModel]="query().search ?? ''"
        (keyup.enter)="applySearch($any($event.target).value)"
        (search)="applySearch($any($event.target).value)"
      />

      <p-select
        [options]="statusOptions"
        [ngModel]="query().status ?? null"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@clients.filter.status"
        placeholder="Situação"
        i18n-ariaLabel="@@clients.filter.status"
        ariaLabel="Situação"
        [showClear]="true"
        (onChange)="applyFilter('status', $event.value)"
      />

      <p-select
        [options]="contractOptions"
        [ngModel]="query().hasActiveContracts ?? null"
        optionLabel="label"
        optionValue="value"
        i18n-placeholder="@@clients.filter.contracts"
        placeholder="Contratos"
        i18n-ariaLabel="@@clients.filter.contracts"
        ariaLabel="Contratos"
        [showClear]="true"
        (onChange)="applyFilter('hasActiveContracts', $event.value)"
      />

      @if (chips().length > 0) {
        <p-button
          i18n-label="@@clients.filter.clear"
          label="Limpar filtros"
          severity="secondary"
          [text]="true"
          (onClick)="clearFilters()"
        />
      }
    </section>

    <!-- LS-01: cada filtro ativo é removível individualmente. -->
    @if (chips().length > 0) {
      <ul class="dt-clients__chips" role="list">
        @for (chip of chips(); track chip.key) {
          <li>
            <button type="button" class="dt-clients__chip" (click)="removeFilter(chip.key)">
              <span>{{ chip.label }}</span>
              <i class="pi pi-times" aria-hidden="true"></i>
            </button>
          </li>
        }
      </ul>
    }

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="12rem" />
    } @else if (store.isEmpty()) {
      <div class="dt-clients__empty">
        <h2 i18n="@@clients.empty.title">Nenhum cliente encontrado</h2>
        <p i18n="@@clients.empty.text">
          Ajuste a busca e os filtros, ou cadastre o primeiro cliente da carteira.
        </p>
      </div>
    } @else {
      <!-- LS-02: o total é o do conjunto filtrado, informado pelo servidor. -->
      <p class="dt-clients__totals">{{ totalsLabel() }}</p>

      <!-- LS-06: em telas estreitas a tabela vira cartões; a mesma informação, outro arranjo. -->
      <div class="dt-clients__cards">
        @for (client of store.clients(); track client.id) {
          <dt-client-card [client]="client" />
        }
      </div>

      <div class="dt-clients__table">
        <p-table [value]="rows()" [rowHover]="true" [dataKey]="'id'">
          <ng-template #header>
            <tr>
              <th scope="col" i18n="@@clients.column.name">Nome</th>
              <th scope="col" i18n="@@clients.column.document">Documento</th>
              <th scope="col" i18n="@@clients.column.contact">Contato</th>
              <th scope="col" i18n="@@clients.column.contracts">Contratos ativos</th>
              <th scope="col" i18n="@@clients.column.status">Situação</th>
            </tr>
          </ng-template>
          <ng-template #body let-client>
            <tr>
              <td>
                <!-- LS-05: o nome é o link do detalhe; um clique de linha inteira não é anunciado
                     por leitor de tela nem alcançável por teclado. -->
                <a [routerLink]="['/clients', client.id]">{{ client.name }}</a>
                @if (client.legalName) {
                  <span class="dt-clients__legal">{{ client.legalName }}</span>
                }
              </td>
              <td>{{ documentOf(client) }}</td>
              <td>{{ client.email || client.phone || '—' }}</td>
              <td>{{ client.activeContractsCount }}</td>
              <td>
                <p-tag
                  [value]="statusLabel(client.status)"
                  [severity]="client.status === 'ACTIVE' ? 'success' : 'warn'"
                />
              </td>
            </tr>
          </ng-template>
        </p-table>
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
  styleUrl: './client-list.page.scss',
})
export class ClientListPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(ClientListStore);

  protected readonly pageSizeOptions = [...PAGE_SIZE_OPTIONS];

  protected readonly statusOptions = [
    { label: $localize`:@@client.status.active:Ativo`, value: 'ACTIVE' },
    { label: $localize`:@@client.status.inactive:Inativo`, value: 'INACTIVE' },
  ];

  protected readonly contractOptions = [
    { label: $localize`:@@clients.filter.withContracts:Com contrato ativo`, value: true },
    { label: $localize`:@@clients.filter.withoutContracts:Sem contrato ativo`, value: false },
  ];

  private readonly params = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  /** A consulta é sempre derivada da URL — não existe cópia dela em estado local. */
  protected readonly query = computed<ClientListQuery>(() => {
    const params = this.params();
    const status = params.get('status');
    const hasActiveContracts = params.get('hasActiveContracts');
    return {
      search: params.get('search') ?? undefined,
      status: status === 'ACTIVE' || status === 'INACTIVE' ? status : undefined,
      hasActiveContracts: hasActiveContracts === null ? undefined : hasActiveContracts === 'true',
      documentNumber: params.get('documentNumber') ?? undefined,
      page: Number(params.get('page') ?? 0),
      size: Number(params.get('size') ?? DEFAULT_PAGE_SIZE),
      sort: params.get('sort') ?? 'name,asc',
    };
  });

  protected readonly canCreate = computed(() => this.authStore.hasPermission('CLIENT_CREATE'));

  protected readonly rows = computed(() => [...this.store.clients()]);

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly subtitle = computed(() => {
    const total = this.store.total();
    return total === 1
      ? $localize`:@@clients.subtitle.one:1 cliente`
      : $localize`:@@clients.subtitle.many:${total}:count: clientes`;
  });

  protected readonly totalsLabel = computed(
    () => $localize`:@@clients.totals:${this.store.total()}:total: no total`,
  );

  protected readonly chips = computed<readonly FilterChip[]>(() => {
    const query = this.query();
    const chips: FilterChip[] = [];
    if (query.search !== undefined && query.search !== '') {
      chips.push({
        key: 'search',
        label: $localize`:@@clients.chip.search:Busca: ${query.search}:term:`,
      });
    }
    if (query.status !== undefined) {
      chips.push({
        key: 'status',
        label: $localize`:@@clients.chip.status:Situação: ${this.statusLabel(query.status)}:status:`,
      });
    }
    if (query.hasActiveContracts !== undefined) {
      chips.push({
        key: 'hasActiveContracts',
        label: query.hasActiveContracts
          ? $localize`:@@clients.filter.withContracts:Com contrato ativo`
          : $localize`:@@clients.filter.withoutContracts:Sem contrato ativo`,
      });
    }
    return chips;
  });

  constructor() {
    // A URL é o gatilho: qualquer mudança de filtro passa por ela e só então recarrega.
    effect(() => {
      const query = this.query();
      void this.store.load(query);
    });
  }

  protected statusLabel(status: ClientStatus): string {
    return status === 'ACTIVE'
      ? $localize`:@@client.status.active:Ativo`
      : $localize`:@@client.status.inactive:Inativo`;
  }

  protected documentOf(client: ClientListItem): string {
    if (client.documentNumber === undefined || client.documentType === undefined) {
      return '—';
    }
    return formatDocument(client.documentType, client.documentNumber);
  }

  protected applySearch(value: string): void {
    void this.updateParams({ search: value === '' ? null : value, page: 0 });
  }

  protected applyFilter(key: 'status' | 'hasActiveContracts', value: unknown): void {
    void this.updateParams({
      [key]: value === null || value === undefined ? null : String(value),
      page: 0,
    });
  }

  protected removeFilter(key: FilterChip['key']): void {
    void this.updateParams({ [key]: null, page: 0 });
  }

  protected clearFilters(): void {
    void this.updateParams({
      search: null,
      status: null,
      hasActiveContracts: null,
      documentNumber: null,
      page: 0,
    });
  }

  protected onPageChange(event: { first?: number; rows?: number }): void {
    const size = event.rows ?? DEFAULT_PAGE_SIZE;
    const page = Math.floor((event.first ?? 0) / size);
    void this.updateParams({ page, size });
  }

  private updateParams(params: Record<string, string | number | null>): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
    });
  }
}
