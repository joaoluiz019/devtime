import { computed, Directive, input } from '@angular/core';
import { criticalityClass, criticalityOf } from '../utils/criticality';

/**
 * Aplica a cor de severidade correspondente à taxa de consumo (§21.4 de
 * `specs/011-bank-hours/spec.md`).
 *
 * Existe como diretiva, e não como classe escrita à mão em cada template, porque a tabela §5.3 do
 * design system é normativa: um `[class.dt-severity-critical]` calculado no template abriria espaço
 * para uma faixa escrita errado em uma tela e certo em outra — exatamente o que DS-04 proíbe.
 *
 * CE-F-02 / FR-034: manipular a apresentação do elemento é papel de diretiva, nunca do componente.
 *
 * **A cor não basta.** Quem usa esta diretiva continua obrigado a exibir ícone e rótulo (DS-05,
 * FR-127); ela colore, não comunica sozinha.
 */
@Directive({
  selector: '[dtCriticality]',
  host: { '[class]': 'hostClass()' },
})
export class CriticalityDirective {
  /** Taxa de consumo percentual, como vem do servidor. */
  readonly dtCriticality = input.required<number>();

  protected readonly hostClass = computed(() =>
    criticalityClass(criticalityOf(this.dtCriticality())),
  );
}
