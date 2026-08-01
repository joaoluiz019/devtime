import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { BalanceSummaryComponent } from '../../../shared/components/balance-summary/balance-summary.component';
import { PartialBadgeComponent } from '../../../shared/components/partial-badge/partial-badge.component';
import { AdjustmentDialogComponent } from '../components/adjustment-dialog.component';
import { AdjustmentListComponent } from '../components/adjustment-list.component';
import { BalanceBreakdownComponent } from '../components/balance-breakdown.component';
import { ClosePeriodDialogComponent } from '../components/close-period-dialog.component';
import { PeriodStatementComponent } from '../components/period-statement.component';
import { PeriodTimelineComponent } from '../components/period-timeline.component';
import { ReopenDialogComponent } from '../components/reopen-dialog.component';
import { periodStatusIcon, periodStatusLabel } from '../data/period-status';
import {
  Adjustment,
  AdjustmentRequest,
  ClosePeriodRequest,
  ReopenPeriodRequest,
} from '../data/period.model';
import { PeriodStore } from '../data/period.store';
import { StatementStore } from '../data/statement.store';

/** Diálogo aberto no momento; apenas um por vez. */
type OpenDialog = 'none' | 'adjustment' | 'close' | 'reopen';

/**
 * P16 — detalhe do período e fechamento (layout L6, T-011-18 e T-011-33).
 *
 * É a tela onde o saldo é conferido e o período é faturado. DS-02/DS-03: o saldo aparece sem
 * interação, e nenhum dos números críticos fica atrás de um clique.
 *
 * DT-02 / §ME-06: ações indisponíveis por estado ou permissão são **ocultadas**, não desabilitadas.
 * Um botão "Fechar período" cinzento em um período já fechado só gera dúvida.
 *
 * **Alcance da tela.** A trilha de navegação exibe o contrato, mas não navega para ele: as telas de
 * contrato (P13/P14) pertencem a `004`, que ainda não tem frontend. Um link para uma rota
 * inexistente levaria à página de "não encontrado".
 */
@Component({
  selector: 'dt-period-detail-page',
  imports: [
    ButtonModule,
    SkeletonModule,
    BalanceSummaryComponent,
    PartialBadgeComponent,
    BalanceBreakdownComponent,
    PeriodStatementComponent,
    AdjustmentListComponent,
    AdjustmentDialogComponent,
    ClosePeriodDialogComponent,
    ReopenDialogComponent,
    PeriodTimelineComponent,
  ],
  providers: [PeriodStore, StatementStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './period-detail.page.html',
  styleUrl: './period-detail.page.scss',
})
export class PeriodDetailPage implements OnInit {
  private readonly authStore = inject(AuthStore);
  private readonly messageService = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly store = inject(PeriodStore);
  protected readonly statement = inject(StatementStore);

  /** Parâmetros de rota como entradas (`withComponentInputBinding`). */
  readonly id = input.required<string>();
  readonly periodId = input.required<string>();

  protected readonly dialog = signal<OpenDialog>('none');
  protected readonly submitting = signal(false);

  protected readonly statusLabel = computed(() => {
    const balance = this.store.balance();
    return balance === null ? '' : periodStatusLabel(balance.status);
  });

  protected readonly statusIcon = computed(() => {
    const balance = this.store.balance();
    return balance === null ? '' : periodStatusIcon(balance.status);
  });

  /** IMP-06 / FR-083: ergonomia. A autorização real é sempre do backend. */
  protected readonly mayAdjust = computed(
    () => this.store.canAdjust() && this.authStore.hasPermission('PERIOD_ADJUST'),
  );

  protected readonly mayClose = computed(
    () => this.store.canClose() && this.authStore.hasPermission('PERIOD_CLOSE'),
  );

  protected readonly mayReopen = computed(
    () => this.store.canReopen() && this.authStore.hasPermission('PERIOD_REOPEN'),
  );

  /**
   * A carga acontece em `ngOnInit`, não no construtor.
   *
   * `input.required()` só tem valor depois do binding, que ocorre entre a construção e o primeiro
   * `ngOnInit` — ler `periodId()` no construtor lança `NG0950`.
   */
  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    await Promise.all([this.store.load(this.periodId()), this.statement.load(this.periodId())]);
  }

  protected onSelectPeriod(periodId: string): void {
    // LD-02 / DT-03: a seleção vive na URL, o que torna o período compartilhável por link.
    void this.router.navigate(['/contracts', this.id(), 'periods', periodId]);
  }

  protected openDialog(dialog: OpenDialog): void {
    this.dialog.set(dialog);
  }

  protected closeDialog(): void {
    this.dialog.set('none');
  }

  protected async applyAdjustment(request: AdjustmentRequest): Promise<void> {
    await this.run(async () => {
      await this.store.applyAdjustment(this.periodId(), request);
      await this.statement.load(this.periodId());
      this.notify($localize`:@@adjustment.applied:Ajuste aplicado ao período.`);
    });
  }

  /** FA-05: o estorno é um novo ajuste de sinal contrário; o original nunca é editado (RN-236). */
  protected async reverseAdjustment(adjustment: Adjustment): Promise<void> {
    await this.applyAdjustment(this.store.reversalOf(adjustment));
  }

  protected async closePeriod(request: ClosePeriodRequest): Promise<void> {
    await this.run(async () => {
      const result = await this.store.close(this.periodId(), request);
      await this.statement.load(this.periodId());
      this.notify(
        $localize`:@@close.done:Período fechado. ${result.lockedWorkLogs}:count: registro(s) travado(s).`,
      );
    });
  }

  protected async reopenPeriod(request: ReopenPeriodRequest): Promise<void> {
    await this.run(async () => {
      const result = await this.store.reopen(this.periodId(), request);
      await this.statement.load(this.periodId());
      this.notify(
        $localize`:@@reopen.done:Período reaberto. ${result.unlockedWorkLogs}:count: registro(s) liberado(s).`,
      );
    });
  }

  /**
   * Executa a operação tratando estado de envio e erro.
   *
   * `409` e `422` não são notificados pelo `errorInterceptor` (são "tratados pela tela"), então a
   * mensagem em linguagem natural do código sai daqui — DS-07 exige que o erro explique o que houve.
   */
  private async run(operation: () => Promise<void>): Promise<void> {
    this.submitting.set(true);
    try {
      await operation();
      this.closeDialog();
    } catch (error) {
      const problem = error as { code?: string; detail?: string };
      this.messageService.add({
        severity: 'warn',
        summary: $localize`:@@period.action.failed:Não foi possível concluir`,
        detail: messageForCode(problem.code ?? '', problem.detail ?? ''),
        life: 6000,
      });
    } finally {
      this.submitting.set(false);
    }
  }

  private notify(detail: string): void {
    this.messageService.add({
      severity: 'success',
      summary: $localize`:@@period.action.done:Pronto`,
      detail,
      life: 3000,
    });
  }
}
