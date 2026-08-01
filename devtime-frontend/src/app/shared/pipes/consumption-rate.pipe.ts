import { Pipe, PipeTransform } from '@angular/core';

/**
 * Taxa de consumo para percentual com 1 casa decimal (§21.4 de `specs/011-bank-hours/spec.md`).
 *
 * O design system fixa 1 casa na exibição (§10, "Percentual | 1 casa decimal | `83,7%`"), enquanto o
 * servidor calcula com 2 e arredondamento `HALF_UP`. A diferença é deliberada: o valor **canônico**
 * tem duas casas para não perder precisão em comparações; a **exibição** tem uma para não sugerir
 * uma precisão que o número não carrega para o usuário.
 *
 * O arredondamento repete `HALF_UP` do servidor. `toFixed` do JavaScript usa "half to even" em
 * alguns motores para valores binários exatos, e a divergência apareceria como `83,6%` na interface
 * contra `83,65%` no relatório — o tipo de diferença que o cliente cobra.
 */
@Pipe({ name: 'consumptionRate' })
export class ConsumptionRatePipe implements PipeTransform {
  transform(rate: number | null | undefined): string {
    if (rate === null || rate === undefined || Number.isNaN(rate)) {
      // CE-D-05: nunca exibir um número possivelmente errado.
      return '—';
    }

    const rounded = halfUp(rate, 1);
    // Locale pt-BR: separador decimal é vírgula (I18-02).
    return `${rounded.toFixed(1).replace('.', ',')}%`;
  }
}

/** `HALF_UP` explícito, espelhando `RoundingMode.HALF_UP` do `BalanceCalculator`. */
function halfUp(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  const scaled = value * factor;
  // O deslocamento por `Number.EPSILON` corrige a representação binária de casos como 83.65, que é
  // armazenado como 83.6499999...; sem ele o meio exato arredondaria para baixo.
  const corrected = scaled + Math.sign(scaled) * Number.EPSILON * Math.abs(scaled);
  return (Math.sign(corrected) * Math.round(Math.abs(corrected))) / factor;
}
