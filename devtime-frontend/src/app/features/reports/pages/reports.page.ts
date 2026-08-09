import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { PartialWarningComponent } from '../../../shared/components/partial-warning/partial-warning.component';
import { EmptyReportComponent } from '../components/empty-report.component';
import { ExportChoice, ExportDialogComponent } from '../components/export-dialog.component';
import { ExportListComponent } from '../components/export-list.component';
import { GroupingSelectorComponent } from '../components/grouping-selector.component';
import { ReportFiltersComponent } from '../components/report-filters.component';
import { ReportHeaderPreviewComponent } from '../components/report-header-preview.component';
import { ReportTypeSelectorComponent } from '../components/report-type-selector.component';
import { ReportViewerComponent } from '../components/report-viewer.component';
import { ExportStore } from '../data/export.store';
import {
  ExportExecution,
  ExportRequest,
  ReportCriteria,
  ReportGrouping,
  ReportType,
} from '../data/report.model';
import { isCriteriaComplete, isRangeTooLong, ReportStore } from '../data/report.store';

/** P24: a prévia acompanha o formulário, mas não a cada tecla. */
const PREVIEW_DEBOUNCE_MS = 500;

/**
 * Relatórios — P24, layout L8 (T-012-29).
 *
 * O produto entrega horas em forma de documento, e esta é a tela onde o documento nasce. A prévia
 * **reflete fielmente o arquivo**: mesma marcação de parcial, mesmo cabeçalho, mesmas colunas. Uma
 * prévia aproximada faria com que o PDF só fosse conferido depois de enviado ao cliente.
 *
 * RN-702 / CP-02: o aviso de parcial é proeminente e vem antes do conteúdo. É a diferença entre um
 * número em evolução e um documento definitivo, e é a única informação da tela que, se ignorada,
 * transforma o relatório numa cobrança contestada.
 */
@Component({
  selector: 'dt-reports-page',
  imports: [
    ButtonModule,
    MessageModule,
    SkeletonModule,
    EmptyReportComponent,
    ExportDialogComponent,
    ExportListComponent,
    GroupingSelectorComponent,
    PartialWarningComponent,
    ReportFiltersComponent,
    ReportHeaderPreviewComponent,
    ReportTypeSelectorComponent,
    ReportViewerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ReportStore, ExportStore],
  template: `
    <header class="dt-reports__header">
      <div>
        <h1 class="dt-reports__title" i18n="@@reports.title">Relatórios</h1>
        <p class="dt-reports__subtitle" i18n="@@reports.subtitle">
          Escolha o recorte, confira a prévia e exporte o documento.
        </p>
      </div>
      @if (canExport()) {
        <p-button
          i18n-label="@@export.open"
          label="Exportar"
          icon="pi pi-download"
          [disabled]="store.report() === null || store.isEmpty()"
          (onClick)="exportDialogVisible.set(true)"
        />
      }
    </header>

    <section class="dt-reports__panel">
      <dt-report-type-selector [value]="criteria().reportType" (selected)="selectType($event)" />

      <dt-report-filters
        [reportType]="criteria().reportType"
        [criteria]="criteria()"
        (changed)="patchCriteria($event)"
      />

      <dt-grouping-selector
        [reportType]="criteria().reportType"
        [value]="criteria().filters.groupBy ?? 'DATE'"
        (changed)="selectGrouping($event)"
      />
    </section>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full" />
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="18rem" />
    } @else if (!ready()) {
      <p class="dt-reports__pending" i18n="@@reports.pending">
        Complete o recorte acima para ver a prévia do relatório.
      </p>
    } @else if (store.report(); as report) {
      <!-- RN-702: antes do conteúdo, e proeminente. -->
      <dt-partial-warning
        [isPartial]="report.isPartial"
        [periodStatus]="periodStatus()"
        [reopenCount]="reopenCount()"
      />

      <dt-report-header-preview [report]="report" />

      @if (store.isEmpty()) {
        <dt-empty-report [criteria]="criteria()" />
      } @else {
        <dt-report-viewer [report]="report" />
      }
    }

    @if (canExport()) {
      <section class="dt-reports__exports">
        <h2 class="dt-reports__exports-title" i18n="@@export.list.title">Exportações recentes</h2>
        <dt-export-list
          [executions]="exportStore.executions()"
          (download)="download($event)"
          (cancelled)="cancelExport($event)"
          (retry)="retry($event)"
        />
      </section>

      <dt-export-dialog
        [visible]="exportDialogVisible()"
        (visibleChange)="exportDialogVisible.set($event)"
        [rowCount]="store.rowCount()"
        [requesting]="exportStore.requesting()"
        (confirmed)="requestExport($event)"
        (cancelled)="exportDialogVisible.set(false)"
      />
    }
  `,
  styleUrl: './reports.page.scss',
})
export class ReportsPage {
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(ReportStore);
  protected readonly exportStore = inject(ExportStore);

  protected readonly exportDialogVisible = signal(false);

  /**
   * O recorte pedido.
   *
   * Vive na tela e não na URL: os filtros de relatório incluem listas de identificadores e opções de
   * composição, e serializá-los em query params produziria um endereço ilegível que ninguém
   * compartilharia. O link compartilhável do produto é o arquivo exportado, que carrega o `issueId`.
   */
  protected readonly criteria = signal<ReportCriteria>({
    reportType: 'CONTRACT_PERIOD',
    filters: { groupBy: 'DATE' },
  });

  protected readonly canExport = computed(() => this.authStore.hasPermission('REPORT_EXPORT'));

  protected readonly ready = computed(
    () => isCriteriaComplete(this.criteria()) && !isRangeTooLong(this.criteria()),
  );

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error() ?? this.exportStore.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly periodStatus = computed(() => {
    const report = this.store.report();
    return report?.reportType === 'CONTRACT_PERIOD' ? report.period.status : null;
  });

  protected readonly reopenCount = computed(() => {
    const report = this.store.report();
    return report?.reportType === 'CONTRACT_PERIOD' ? report.reopenCount : 0;
  });

  private previewTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // P24: a prévia atualiza com debounce de 500ms. Sem ele, escolher um intervalo de datas
    // dispararia uma consulta a cada dígito do ano — e a mais lenta poderia chegar por último.
    effect((onCleanup) => {
      const criteria = this.criteria();
      if (!isCriteriaComplete(criteria) || isRangeTooLong(criteria)) {
        this.store.clear();
        return;
      }
      this.previewTimer = setTimeout(() => void this.store.load(criteria), PREVIEW_DEBOUNCE_MS);
      onCleanup(() => {
        if (this.previewTimer !== null) {
          clearTimeout(this.previewTimer);
          this.previewTimer = null;
        }
      });
    });

    if (this.authStore.hasPermission('REPORT_EXPORT')) {
      void this.exportStore.load();
    }
  }

  /**
   * Trocar de tipo descarta o alvo anterior e o agrupamento incompatível.
   *
   * Manter `clientId` ao passar de resumo por cliente para folha de horas mandaria ao servidor um
   * parâmetro que aquele tipo não aceita (`DEVTIME-3003`), e manter `groupBy=TAG` num relatório de
   * ticket produziria `DEVTIME-3007` — os dois com o formulário aparentemente correto na tela.
   */
  protected selectType(reportType: ReportType): void {
    this.criteria.update((criteria) => ({
      reportType,
      filters: { ...criteria.filters, groupBy: 'DATE' },
    }));
  }

  protected selectGrouping(groupBy: ReportGrouping): void {
    this.criteria.update((criteria) => ({
      ...criteria,
      filters: { ...criteria.filters, groupBy },
    }));
  }

  protected patchCriteria(patch: Partial<ReportCriteria>): void {
    this.criteria.update((criteria) => ({ ...criteria, ...patch }));
  }

  protected async requestExport(choice: ExportChoice): Promise<void> {
    const response = await this.exportStore.request(this.toExportRequest(choice));
    if (response !== null) {
      this.exportDialogVisible.set(false);
    }
  }

  protected async download(execution: ExportExecution): Promise<void> {
    await this.exportStore.download(execution);
  }

  protected async cancelExport(id: string): Promise<void> {
    await this.exportStore.cancel(id);
  }

  /**
   * Nova tentativa é uma **nova solicitação** com o recorte que está na tela.
   *
   * O backend guarda os parâmetros aplicados como texto (RN-707), para reprodutibilidade, e não
   * expõe endpoint de reprocessamento. Reenviar o recorte atual é honesto quanto ao que acontece:
   * um arquivo novo, com um `issueId` novo.
   */
  protected async retry(execution: ExportExecution): Promise<void> {
    await this.exportStore.request(this.toExportRequest({ format: execution.format, options: {} }));
  }

  /** §8.1: a exportação recebe **os mesmos** parâmetros da consulta — é o que a faz reproduzir a tela. */
  private toExportRequest(choice: ExportChoice): ExportRequest {
    const criteria = this.criteria();
    return {
      reportType: criteria.reportType,
      format: choice.format,
      parameters: {
        contractPeriodId: criteria.contractPeriodId,
        clientId: criteria.clientId,
        ticketId: criteria.ticketId,
        filters: criteria.filters,
      },
      options: choice.options,
    };
  }
}
