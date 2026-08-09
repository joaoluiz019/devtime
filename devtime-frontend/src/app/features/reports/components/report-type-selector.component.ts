import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { AuthStore } from '../../../core/auth/auth.store';
import { ReportType } from '../data/report.model';

interface ReportTypeOption {
  readonly type: ReportType;
  readonly label: string;
  readonly description: string;
  readonly icon: string;
  /** Permissão exigida pelo tipo (§16); vazio significa que basta `REPORT_VIEW_OWN`. */
  readonly permission: string;
}

/**
 * Escolha do tipo de relatório — `dt-report-type-selector` (T-012-22).
 *
 * Os tipos indisponíveis ao papel são **desabilitados e explicados**, não ocultados. É a exceção
 * deliberada a SB-01: ali o argumento é que um botão desabilitado sugere que falta um clique; aqui
 * o usuário está escolhendo entre cinco documentos nomeados, e sumir com dois deles faz a lista
 * parecer completa — quem procura "resumo por cliente" e não o encontra conclui que o produto não
 * tem, em vez de descobrir que o papel dele não alcança.
 */
@Component({
  selector: 'dt-report-type-selector',
  imports: [TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="dt-report-types">
      <legend class="dt-report-types__legend" i18n="@@report.type.legend">Tipo de relatório</legend>
      <div class="dt-report-types__grid" role="radiogroup">
        @for (option of options(); track option.type) {
          <button
            type="button"
            role="radio"
            class="dt-report-types__option"
            [class.dt-report-types__option--selected]="option.type === value()"
            [attr.aria-checked]="option.type === value()"
            [disabled]="!isAllowed(option)"
            [pTooltip]="isAllowed(option) ? '' : restrictionMessage"
            tooltipPosition="top"
            (click)="select(option)"
          >
            <i class="pi" [class]="option.icon" aria-hidden="true"></i>
            <span class="dt-report-types__label">{{ option.label }}</span>
            <small class="dt-report-types__description">{{ option.description }}</small>
            @if (!isAllowed(option)) {
              <small class="dt-report-types__restriction">{{ restrictionMessage }}</small>
            }
          </button>
        }
      </div>
    </fieldset>
  `,
  styles: `
    .dt-report-types {
      margin: 0;
      padding: 0;
      border: 0;
    }

    .dt-report-types__legend {
      margin-bottom: var(--dt-space-2);
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-report-types__grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      gap: var(--dt-space-2);
    }

    .dt-report-types__option {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background: var(--dt-surface-card);
      color: inherit;
      text-align: start;
      cursor: pointer;
    }

    .dt-report-types__option:disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }

    .dt-report-types__option--selected {
      border-color: var(--dt-color-primary);
      box-shadow: inset 0 0 0 1px var(--dt-color-primary);
    }

    .dt-report-types__label {
      font-weight: 600;
      font-size: var(--dt-text-sm);
    }

    .dt-report-types__description,
    .dt-report-types__restriction {
      font-size: var(--dt-text-xs);
      color: var(--dt-text-secondary);
    }

    .dt-report-types__restriction {
      color: var(--dt-color-warning);
    }
  `,
})
export class ReportTypeSelectorComponent {
  private readonly authStore = inject(AuthStore);

  readonly value = input.required<ReportType>();

  readonly selected = output<ReportType>();

  protected readonly restrictionMessage = $localize`:@@report.type.restricted:Seu papel não alcança os dados de toda a organização.`;

  protected readonly options = computed<readonly ReportTypeOption[]>(() => [
    {
      type: 'CONTRACT_PERIOD',
      label: $localize`:@@report.type.contractPeriod:Período de contrato`,
      description: $localize`:@@report.type.contractPeriod.description:O documento entregue ao cliente ao fechar o período.`,
      icon: 'pi-file-check',
      permission: '',
    },
    {
      type: 'CLIENT_SUMMARY',
      label: $localize`:@@report.type.clientSummary:Resumo por cliente`,
      description: $localize`:@@report.type.clientSummary.description:Todos os contratos de um cliente no intervalo, com totais por moeda.`,
      icon: 'pi-briefcase',
      permission: 'REPORT_VIEW_ANY',
    },
    {
      type: 'TIMESHEET',
      label: $localize`:@@report.type.timesheet:Folha de horas`,
      description: $localize`:@@report.type.timesheet.description:Intervalo livre, independente de contrato.`,
      icon: 'pi-calendar',
      permission: '',
    },
    {
      type: 'TICKET_DETAIL',
      label: $localize`:@@report.type.ticketDetail:Detalhe de ticket`,
      description: $localize`:@@report.type.ticketDetail.description:Estimativa contra realizado e todos os registros do ticket.`,
      icon: 'pi-ticket',
      permission: '',
    },
    {
      type: 'PRODUCTIVITY',
      label: $localize`:@@report.type.productivity:Produtividade`,
      description: $localize`:@@report.type.productivity.description:Métricas por pessoa e por semana, em valores absolutos.`,
      icon: 'pi-chart-bar',
      permission: 'REPORT_VIEW_ANY',
    },
  ]);

  protected isAllowed(option: ReportTypeOption): boolean {
    return option.permission === '' || this.authStore.hasPermission(option.permission);
  }

  protected select(option: ReportTypeOption): void {
    if (this.isAllowed(option)) {
      this.selected.emit(option.type);
    }
  }
}
