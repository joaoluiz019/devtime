import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { ProgressBarModule } from 'primeng/progressbar';
import { TagModule } from 'primeng/tag';
import { ExportExecution, ExportStatus } from '../data/report.model';

/**
 * Exportações do solicitante — `dt-export-list` (T-012-28).
 *
 * SG-04: a lista é **só do solicitante**; a exportação de um colega é indistinguível de
 * inexistente. Não há nada a filtrar aqui.
 *
 * §11.1: cancelar aparece apenas em `QUEUED` — em `PROCESSING` o worker já está gerando. Baixar
 * aparece apenas em `COMPLETED`. `FAILED` mostra o motivo e oferece nova tentativa, que é uma nova
 * solicitação com os mesmos parâmetros: o backend não retenta a pedido do cliente.
 */
@Component({
  selector: 'dt-export-list',
  imports: [DatePipe, ButtonModule, ProgressBarModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="dt-export-list">
      @for (execution of executions(); track execution.id) {
        <li class="dt-export-list__item">
          <div class="dt-export-list__main">
            <span class="dt-export-list__name">
              {{ execution.fileName ?? execution.format }}
            </span>
            <p-tag [severity]="severityOf(execution.status)" [value]="labelOf(execution.status)" />
            <span class="dt-export-list__meta">
              {{ execution.createdAt | date: 'short' }}
              @if (execution.rowCount !== null) {
                · <span i18n="@@export.rows">{{ execution.rowCount }} linhas</span>
              }
            </span>
          </div>

          @if (execution.status === 'PROCESSING' && execution.progress !== null) {
            <p-progressBar [value]="execution.progress.percentage" [showValue]="true" />
          }

          @if (execution.status === 'FAILED' && execution.failureReason !== null) {
            <p class="dt-export-list__failure">{{ execution.failureReason }}</p>
          }

          @if (execution.status === 'EXPIRED') {
            <p class="dt-export-list__meta" i18n="@@export.expired">
              O arquivo foi removido após sete dias. Gere novamente para obter uma cópia.
            </p>
          }

          <div class="dt-export-list__actions">
            @if (execution.status === 'COMPLETED') {
              <p-button
                i18n-label="@@export.download"
                label="Baixar"
                icon="pi pi-download"
                [text]="true"
                (onClick)="download.emit(execution)"
              />
            }
            @if (execution.status === 'QUEUED') {
              <p-button
                i18n-label="@@action.cancel"
                label="Cancelar"
                icon="pi pi-times"
                severity="secondary"
                [text]="true"
                (onClick)="cancelled.emit(execution.id)"
              />
            }
            @if (execution.status === 'FAILED' || execution.status === 'EXPIRED') {
              <p-button
                i18n-label="@@export.retry"
                label="Gerar novamente"
                icon="pi pi-refresh"
                [text]="true"
                (onClick)="retry.emit(execution)"
              />
            }
          </div>
        </li>
      } @empty {
        <li class="dt-export-list__meta" i18n="@@export.none">Nenhuma exportação recente.</li>
      }
    </ul>
  `,
  styles: `
    .dt-export-list {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .dt-export-list__item {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background: var(--dt-surface-card);
    }

    .dt-export-list__main {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--dt-space-2);
    }

    .dt-export-list__name {
      font-size: var(--dt-text-sm);
      font-weight: 600;
      word-break: break-all;
    }

    .dt-export-list__meta {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-export-list__failure {
      margin: 0;
      color: var(--dt-color-danger);
      font-size: var(--dt-text-sm);
    }

    .dt-export-list__actions {
      display: flex;
      gap: var(--dt-space-2);
    }
  `,
})
export class ExportListComponent {
  readonly executions = input.required<readonly ExportExecution[]>();

  readonly download = output<ExportExecution>();
  readonly cancelled = output<string>();
  readonly retry = output<ExportExecution>();

  /** DS-05: a situação é sempre texto; a cor do selo é reforço, nunca a informação. */
  protected labelOf(status: ExportStatus): string {
    return STATUS_LABELS[status];
  }

  protected severityOf(status: ExportStatus): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (status) {
      case 'COMPLETED':
        return 'success';
      case 'PROCESSING':
        return 'info';
      case 'QUEUED':
        return 'warn';
      case 'FAILED':
        return 'danger';
      case 'EXPIRED':
        return 'secondary';
    }
  }
}

const STATUS_LABELS: Readonly<Record<ExportStatus, string>> = {
  QUEUED: $localize`:@@export.status.queued:Na fila`,
  PROCESSING: $localize`:@@export.status.processing:Gerando`,
  COMPLETED: $localize`:@@export.status.completed:Pronto`,
  FAILED: $localize`:@@export.status.failed:Falhou`,
  EXPIRED: $localize`:@@export.status.expired:Expirado`,
};
