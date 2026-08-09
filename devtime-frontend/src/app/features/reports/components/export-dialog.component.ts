import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ExportFormat, ExportOptions, SYNC_EXPORT_ROW_LIMIT } from '../data/report.model';

/** O que o diálogo devolve quando a exportação é confirmada. */
export interface ExportChoice {
  readonly format: ExportFormat;
  readonly options: ExportOptions;
}

/**
 * Escolha de formato e opções de saída — `dt-export-dialog` (T-012-27).
 *
 * RN-706: acima de 5.000 linhas a geração é assíncrona, e o aviso aparece **antes** da confirmação.
 * Quem clica em "Exportar" esperando um download e recebe um `202` silencioso conclui que o botão
 * não funcionou — e clica de novo, gerando a segunda exportação de 40.000 linhas.
 *
 * Página de rosto e gráficos só existem em PDF (§8.1). Oferecê-los nos outros formatos produziria
 * uma opção sem efeito, que é uma forma de mentir sobre o que o arquivo conterá.
 */
@Component({
  selector: 'dt-export-dialog',
  imports: [
    FormsModule,
    ButtonModule,
    CheckboxModule,
    DialogModule,
    MessageModule,
    SelectButtonModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visibleChange.emit($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      [header]="title"
    >
      <div class="dt-export-dialog">
        <div class="dt-export-dialog__field">
          <span id="export-format-label" i18n="@@export.format">Formato</span>
          <p-selectButton
            [options]="formats"
            [ngModel]="format()"
            optionLabel="label"
            optionValue="value"
            [allowEmpty]="false"
            aria-labelledby="export-format-label"
            (onChange)="format.set($event.value)"
          />
        </div>

        @if (format() === 'PDF') {
          <div class="dt-export-dialog__option">
            <p-checkbox inputId="export-cover" [binary]="true" [(ngModel)]="coverPage" />
            <label for="export-cover" i18n="@@export.coverPage">Incluir página de rosto</label>
          </div>

          <div class="dt-export-dialog__option">
            <p-checkbox inputId="export-charts" [binary]="true" [(ngModel)]="charts" />
            <label for="export-charts" i18n="@@export.charts">
              Incluir gráficos de distribuição
            </label>
          </div>
        }

        @if (isAsync()) {
          <!-- RN-706: o aviso vem antes da confirmação, não como surpresa depois dela. -->
          <p-message severity="info" styleClass="w-full">
            <span i18n="@@export.async">
              Este relatório tem {{ rowCount() }} linhas. O arquivo será gerado em segundo plano:
              você pode continuar navegando e receberá uma notificação quando ficar pronto.
            </span>
          </p-message>
        } @else {
          <p class="dt-export-dialog__hint" i18n="@@export.sync">
            O download começa assim que o arquivo for gerado.
          </p>
        }

        <div class="dt-export-dialog__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="cancelled.emit()"
          />
          <p-button
            type="button"
            i18n-label="@@export.submit"
            label="Exportar"
            icon="pi pi-download"
            [loading]="requesting()"
            (onClick)="confirm()"
          />
        </div>
      </div>
    </p-dialog>
  `,
  styles: `
    .dt-export-dialog {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-export-dialog__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-export-dialog__option {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
      font-size: var(--dt-text-sm);
    }

    .dt-export-dialog__hint {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-export-dialog__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class ExportDialogComponent {
  readonly visible = input.required<boolean>();
  /** Linhas do relatório atualmente na tela; é o que decide o modo (RN-706). */
  readonly rowCount = input.required<number>();
  readonly requesting = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<ExportChoice>();
  readonly cancelled = output<void>();

  protected readonly title = $localize`:@@export.title:Exportar relatório`;

  protected readonly formats = [
    { label: 'PDF', value: 'PDF' as const },
    { label: 'Excel', value: 'XLSX' as const },
    { label: 'CSV', value: 'CSV' as const },
  ];

  protected readonly format = signal<ExportFormat>('PDF');
  protected readonly coverPage = signal(true);
  protected readonly charts = signal(true);

  protected readonly isAsync = computed(() => this.rowCount() > SYNC_EXPORT_ROW_LIMIT);

  protected confirm(): void {
    const isPdf = this.format() === 'PDF';
    this.confirmed.emit({
      format: this.format(),
      options: {
        coverPage: isPdf ? this.coverPage() : undefined,
        includeSummaryCharts: isPdf ? this.charts() : undefined,
      },
    });
  }
}
