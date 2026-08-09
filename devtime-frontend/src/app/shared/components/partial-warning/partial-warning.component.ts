import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MessageModule } from 'primeng/message';

/**
 * Aviso de relatório parcial — `dt-partial-warning` (T-012-24, RN-702).
 *
 * **É proeminente, não é nota de rodapé.** Um relatório parcial exibido discretamente será
 * impresso e enviado ao cliente como se fosse final — e é exatamente esse o cenário que RN-702
 * existe para evitar (§21.2 de `specs/012-reports/spec.md`).
 *
 * Difere de `dt-partial-badge`: o selo marca um número dentro de uma tabela; este bloco ocupa a
 * largura do documento, acima do conteúdo, e diz **por que** os valores ainda mudam.
 *
 * O motivo é obrigatório: "parcial" sozinho não diz se falta fechar o período ou se ele foi
 * reaberto depois de fechado — e a segunda situação é a que mais precisa ser dita, porque o número
 * já foi dado como definitivo uma vez.
 */
@Component({
  selector: 'dt-partial-warning',
  imports: [MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isPartial()) {
      <p-message severity="warn" styleClass="dt-partial-warning">
        <div class="dt-partial-warning__body" role="status">
          <strong class="dt-partial-warning__title" i18n="@@report.partial.title">
            Relatório parcial
          </strong>
          <span class="dt-partial-warning__reason">{{ reason() }}</span>
        </div>
      </p-message>
    }
  `,
  styles: `
    :host ::ng-deep .dt-partial-warning {
      width: 100%;
    }

    .dt-partial-warning__body {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      text-align: start;
    }

    .dt-partial-warning__title {
      font-size: var(--dt-text-sm);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .dt-partial-warning__reason {
      font-size: var(--dt-text-sm);
    }
  `,
})
export class PartialWarningComponent {
  /** Vem do próprio relatório (`isPartial`), nunca inferido na tela. */
  readonly isPartial = input.required<boolean>();

  /** Estado do período, quando o relatório tem um. Ausente na folha de horas por intervalo livre. */
  readonly periodStatus = input<string | null>(null);

  readonly reopenCount = input<number>(0);

  protected readonly reason = computed(() => {
    if (this.reopenCount() > 0) {
      return $localize`:@@report.partial.reopened:Este período foi reaberto e os valores voltaram a mudar. Emita novamente após o novo fechamento antes de enviar ao cliente.`;
    }
    if (this.periodStatus() === 'OPEN') {
      return $localize`:@@report.partial.open:O período ainda está aberto: cada hora lançada altera estes números. Feche o período para obter o documento definitivo.`;
    }
    // CX-23: a folha de horas por intervalo livre é sempre parcial — o intervalo pode conter um
    // período aberto, e não há como o relatório saber que não contém.
    return $localize`:@@report.partial.range:O intervalo consultado pode conter períodos ainda abertos: estes números podem mudar.`;
  });
}
