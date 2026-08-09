import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { AuthStore } from '../../../core/auth/auth.store';
import { CategoryLookupApi, CategoryOption } from '../../../shared/data/category-lookup.api';
import { ClientLookupApi, ClientOption } from '../../../shared/data/client-lookup.api';
import { ContractLookupApi, ContractOption } from '../../../shared/data/contract-lookup.api';
import { MemberLookupApi, MemberOption } from '../../../shared/data/member-lookup.api';
import { PeriodLookupApi, PeriodOption } from '../../../shared/data/period-lookup.api';
import { TagLookupApi, TagOption } from '../../../shared/data/tag-lookup.api';
import { TicketLookupApi, TicketOption } from '../../../shared/data/ticket-lookup.api';
import {
  MAX_RANGE_DAYS,
  rangeDays,
  ReportCriteria,
  ReportFilters,
  ReportType,
} from '../data/report.model';

/**
 * Painel de configuração do relatório — `dt-report-filters` (T-012-23, P24).
 *
 * O recorte muda com o tipo: período de contrato pede um período, resumo por cliente pede cliente e
 * intervalo, folha de horas pede só o intervalo. Um painel único com todos os campos sempre
 * visíveis obrigaria o usuário a descobrir sozinho quais deles o seu relatório usa.
 *
 * RN-705 é verificada aqui, no cliente: acima de 366 dias a mensagem aparece imediatamente e a
 * consulta nem parte. Deixar o servidor recusar com `DEVTIME-3001` gastaria uma viagem para dizer
 * algo que já era visível no formulário.
 *
 * CE-P-10 / §21.4: para `MEMBER` o filtro de pessoa é **removido** — o relatório dele já é restrito
 * aos próprios registros, e oferecer o campo sugeriria um recorte que o servidor recusaria com
 * `403`.
 */
@Component({
  selector: 'dt-report-filters',
  imports: [FormsModule, CheckboxModule, MessageModule, MultiSelectModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-report-filters">
      @switch (reportType()) {
        @case ('CONTRACT_PERIOD') {
          <div class="dt-report-filters__field">
            <label for="report-contract" i18n="@@report.filter.contract">Contrato</label>
            <p-select
              inputId="report-contract"
              [options]="contracts()"
              [ngModel]="contractId()"
              optionLabel="name"
              optionValue="id"
              [filter]="true"
              [showClear]="true"
              i18n-placeholder="@@report.filter.contract.placeholder"
              placeholder="Escolha o contrato"
              (onChange)="selectContract($event.value)"
            />
          </div>

          <div class="dt-report-filters__field">
            <label for="report-period" i18n="@@report.filter.period">Período</label>
            <p-select
              inputId="report-period"
              [options]="periods()"
              [ngModel]="criteria().contractPeriodId ?? null"
              optionLabel="label"
              optionValue="id"
              [disabled]="contractId() === null"
              [showClear]="true"
              i18n-placeholder="@@report.filter.period.placeholder"
              placeholder="Escolha o período"
              (onChange)="emit({ contractPeriodId: $event.value ?? undefined })"
            />
          </div>
        }

        @case ('CLIENT_SUMMARY') {
          <div class="dt-report-filters__field">
            <label for="report-client" i18n="@@report.filter.client">Cliente</label>
            <p-select
              inputId="report-client"
              [options]="clients()"
              [ngModel]="criteria().clientId ?? null"
              optionLabel="name"
              optionValue="id"
              [filter]="true"
              [showClear]="true"
              i18n-placeholder="@@report.filter.client.placeholder"
              placeholder="Escolha o cliente"
              (onChange)="emit({ clientId: $event.value ?? undefined })"
            />
          </div>
        }

        @case ('TICKET_DETAIL') {
          <div class="dt-report-filters__field">
            <label for="report-ticket" i18n="@@report.filter.ticket">Ticket</label>
            <p-select
              inputId="report-ticket"
              [options]="tickets()"
              [ngModel]="criteria().ticketId ?? null"
              optionLabel="label"
              optionValue="id"
              [filter]="true"
              [showClear]="true"
              i18n-placeholder="@@report.filter.ticket.placeholder"
              placeholder="Escolha o ticket"
              (onChange)="emit({ ticketId: $event.value ?? undefined })"
            />
          </div>
        }
      }

      @if (usesRange()) {
        <div class="dt-report-filters__field">
          <label for="report-from" i18n="@@report.filter.from">De</label>
          <input
            id="report-from"
            type="date"
            class="dt-report-filters__date"
            [ngModel]="criteria().filters.from ?? ''"
            (change)="changeFilter({ from: dateOf($event) })"
          />
        </div>

        <div class="dt-report-filters__field">
          <label for="report-to" i18n="@@report.filter.to">Até</label>
          <input
            id="report-to"
            type="date"
            class="dt-report-filters__date"
            [ngModel]="criteria().filters.to ?? ''"
            (change)="changeFilter({ to: dateOf($event) })"
          />
        </div>
      }

      <div class="dt-report-filters__field">
        <label for="report-categories" i18n="@@report.filter.categories">Categorias</label>
        <p-multiSelect
          inputId="report-categories"
          [options]="categories()"
          [ngModel]="criteria().filters.categoryIds ?? []"
          optionLabel="name"
          optionValue="id"
          i18n-placeholder="@@report.filter.all"
          placeholder="Todas"
          (onChange)="changeFilter({ categoryIds: listOf($event.value) })"
        />
      </div>

      <div class="dt-report-filters__field">
        <label for="report-tags" i18n="@@report.filter.tags">Etiquetas</label>
        <p-multiSelect
          inputId="report-tags"
          [options]="tags()"
          [ngModel]="criteria().filters.tagIds ?? []"
          optionLabel="name"
          optionValue="id"
          i18n-placeholder="@@report.filter.all"
          placeholder="Todas"
          (onChange)="changeFilter({ tagIds: listOf($event.value) })"
        />
      </div>

      @if (canFilterByUser()) {
        <div class="dt-report-filters__field">
          <label for="report-users" i18n="@@report.filter.users">Pessoas</label>
          <p-multiSelect
            inputId="report-users"
            [options]="members()"
            [ngModel]="criteria().filters.userIds ?? []"
            optionLabel="name"
            optionValue="id"
            i18n-placeholder="@@report.filter.all"
            placeholder="Todas"
            (onChange)="changeFilter({ userIds: listOf($event.value) })"
          />
        </div>
      }

      <div class="dt-report-filters__field">
        <label for="report-billable" i18n="@@report.filter.billable">Faturável</label>
        <p-select
          inputId="report-billable"
          [options]="billableOptions"
          [ngModel]="criteria().filters.billable ?? null"
          optionLabel="label"
          optionValue="value"
          [showClear]="true"
          i18n-placeholder="@@report.filter.all"
          placeholder="Todas"
          (onChange)="changeFilter({ billable: $event.value ?? undefined })"
        />
      </div>

      <div class="dt-report-filters__options">
        <!-- CP-05: incluir os não faturáveis é o padrão; excluí-los é escolha explícita. -->
        <div class="dt-report-filters__option">
          <p-checkbox
            inputId="report-non-billable"
            [binary]="true"
            [ngModel]="criteria().filters.includeNonBillable ?? true"
            (onChange)="changeFilter({ includeNonBillable: $event.checked })"
          />
          <label for="report-non-billable" i18n="@@report.option.nonBillable">
            Incluir horas não faturáveis
          </label>
        </div>

        @if (canSeeFinancial()) {
          <div class="dt-report-filters__option">
            <p-checkbox
              inputId="report-financial"
              [binary]="true"
              [ngModel]="criteria().filters.includeFinancial ?? true"
              (onChange)="changeFilter({ includeFinancial: $event.checked })"
            />
            <label for="report-financial" i18n="@@report.option.financial">
              Incluir valores monetários
            </label>
          </div>
        }
      </div>
    </div>

    @if (rangeTooLong()) {
      <!-- RN-705: a recusa aparece antes da consulta, não depois do DEVTIME-3001. -->
      <p-message severity="error" styleClass="w-full">
        <span i18n="@@report.range.tooLong">
          O intervalo não pode passar de 366 dias. Reduza as datas para continuar.
        </span>
      </p-message>
    } @else if (rangeInverted()) {
      <p-message severity="error" styleClass="w-full">
        <span i18n="@@report.range.inverted"
          >A data final precisa ser igual ou posterior à inicial.</span
        >
      </p-message>
    }
  `,
  styles: `
    .dt-report-filters {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
      gap: var(--dt-space-3);
      align-items: end;
    }

    .dt-report-filters__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-report-filters__date {
      padding: var(--dt-space-2);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-sm);
      background: var(--dt-surface-card);
      color: var(--dt-text-primary);
      font: inherit;
    }

    .dt-report-filters__options {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      font-size: var(--dt-text-sm);
    }

    .dt-report-filters__option {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
    }
  `,
})
export class ReportFiltersComponent {
  private readonly authStore = inject(AuthStore);
  private readonly clientLookup = inject(ClientLookupApi);
  private readonly contractLookup = inject(ContractLookupApi);
  private readonly periodLookup = inject(PeriodLookupApi);
  private readonly ticketLookup = inject(TicketLookupApi);
  private readonly categoryLookup = inject(CategoryLookupApi);
  private readonly tagLookup = inject(TagLookupApi);
  private readonly memberLookup = inject(MemberLookupApi);

  readonly reportType = input.required<ReportType>();
  readonly criteria = input.required<ReportCriteria>();

  readonly changed = output<Partial<ReportCriteria>>();

  private readonly _clients = signal<readonly ClientOption[]>([]);
  private readonly _contracts = signal<readonly ContractOption[]>([]);
  private readonly _periods = signal<readonly PeriodOption[]>([]);
  private readonly _tickets = signal<readonly TicketOption[]>([]);
  private readonly _categories = signal<readonly CategoryOption[]>([]);
  private readonly _tags = signal<readonly TagOption[]>([]);
  private readonly _members = signal<readonly MemberOption[]>([]);

  // PrimeNG tipa `options` como array mutável; a cópia é o que permite manter os sinais imutáveis.
  protected readonly clients = computed(() => [...this._clients()]);
  protected readonly contracts = computed(() => [...this._contracts()]);
  protected readonly periods = computed(() => [...this._periods()]);
  protected readonly tickets = computed(() => [...this._tickets()]);
  protected readonly categories = computed(() => [...this._categories()]);
  protected readonly tags = computed(() => [...this._tags()]);
  protected readonly members = computed(() => [...this._members()]);

  protected readonly contractId = signal<string | null>(null);

  protected readonly billableOptions = [
    { label: $localize`:@@report.filter.billableYes:Somente faturáveis`, value: true },
    { label: $localize`:@@report.filter.billableNo:Somente não faturáveis`, value: false },
  ];

  /** §7.1 e §7.2: os relatórios de intervalo livre. Período e ticket trazem o seu próprio recorte. */
  protected readonly usesRange = computed(
    () => this.reportType() !== 'CONTRACT_PERIOD' && this.reportType() !== 'TICKET_DETAIL',
  );

  /** CE-P-10: `MEMBER` não recorta por pessoa — o relatório dele já é o dos próprios registros. */
  protected readonly canFilterByUser = computed(() =>
    this.authStore.hasPermission('REPORT_VIEW_ANY'),
  );

  /** CP-03: pedir valores sem a permissão não os concede; o campo nem aparece. */
  protected readonly canSeeFinancial = computed(() =>
    this.authStore.hasPermission('CONTRACT_VIEW_FINANCIAL'),
  );

  protected readonly rangeTooLong = computed(() => {
    const { from, to } = this.criteria().filters;
    return from !== undefined && to !== undefined && rangeDays(from, to) > MAX_RANGE_DAYS;
  });

  protected readonly rangeInverted = computed(() => {
    const { from, to } = this.criteria().filters;
    return from !== undefined && to !== undefined && to < from;
  });

  constructor() {
    // As listas de apoio são carregadas conforme o tipo escolhido as exige: quem emite uma folha de
    // horas não deve pagar pela busca de tickets do tenant inteiro.
    effect(() => {
      const type = this.reportType();
      if (type === 'CONTRACT_PERIOD' && this._contracts().length === 0) {
        this.contractLookup.search().subscribe((options) => this._contracts.set(options));
      }
      if (type === 'CLIENT_SUMMARY' && this._clients().length === 0) {
        this.clientLookup.search().subscribe((options) => this._clients.set(options));
      }
      if (type === 'TICKET_DETAIL' && this._tickets().length === 0) {
        this.ticketLookup.search().subscribe((options) => this._tickets.set(options));
      }
    });

    this.categoryLookup.search().subscribe((options) => this._categories.set(options));
    this.tagLookup.autocomplete().subscribe((options) => this._tags.set(options));
    if (this.authStore.hasPermission('REPORT_VIEW_ANY')) {
      this.memberLookup.search().subscribe((options) => this._members.set(options));
    }
  }

  /**
   * Trocar de contrato limpa o período escolhido.
   *
   * Manter o anterior deixaria na tela um período que pertence a outro contrato — o servidor
   * responderia com o relatório errado, sem erro nenhum, porque o identificador continua válido.
   */
  protected selectContract(id: string | null): void {
    this.contractId.set(id);
    this._periods.set([]);
    this.emit({ contractPeriodId: undefined });
    if (id !== null) {
      this.periodLookup.byContract(id).subscribe((options) => this._periods.set(options));
    }
  }

  protected emit(patch: Partial<ReportCriteria>): void {
    this.changed.emit(patch);
  }

  protected changeFilter(patch: Partial<ReportFilters>): void {
    this.changed.emit({ filters: { ...this.criteria().filters, ...patch } });
  }

  protected dateOf(event: Event): string | undefined {
    const value = (event.target as HTMLInputElement).value;
    return value === '' ? undefined : value;
  }

  protected listOf(value: readonly string[] | null): readonly string[] | undefined {
    return value === null || value.length === 0 ? undefined : value;
  }
}
