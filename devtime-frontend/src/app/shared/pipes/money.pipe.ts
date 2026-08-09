import { Pipe, PipeTransform } from '@angular/core';

/**
 * Valor monetário com a moeda do contrato, 2 casas e arredondamento `HALF_UP` (RN-709, §21.4 de
 * `specs/012-reports/spec.md`).
 *
 * A moeda **vem do dado**, nunca de uma configuração de tela: o mesmo relatório pode conter
 * contratos em moedas diferentes, e CE-R-09 proíbe conversão. Um símbolo fixo transformaria dólar
 * em real na leitura.
 *
 * CP-03 / CP-08: valor ausente é omissão deliberada do servidor — sem `CONTRACT_VIEW_FINANCIAL` ou
 * sem valor hora no contrato. O travessão sinaliza a ausência sem sugerir zero.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency: string | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return '—';
    }
    return new Intl.NumberFormat(LOCALE, {
      style: 'currency',
      currency: currency ?? 'BRL',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(halfUp(value));
  }
}

/** `sourceLocale` de `angular.json`; a tradução do build troca o texto, não a formatação numérica. */
const LOCALE = 'pt-BR';

/**
 * `HALF_UP` do backend, aplicado antes do `Intl`.
 *
 * O arredondamento é explícito porque o padrão do `Intl` é `halfEven`: ele exibiria `2,42` onde o
 * servidor calculou `2,425 → 2,43`, e o total da tela deixaria de bater com o do PDF. Quase nunca
 * age — os valores já chegam arredondados —, e é exatamente nas bordas que a divergência apareceria.
 */
function halfUp(value: number): number {
  const absolute = Math.abs(value);
  // O deslocamento passa pela notação exponencial em texto porque `absolute * 100` erra a borda:
  // `2.425 * 100` vale 242.49999999999997 em ponto flutuante, e arredondaria para 2,42 — o número
  // que o backend não calculou.
  const rounded = Math.round(Number(`${absolute}e2`)) / 100;
  return value < 0 ? -rounded : rounded;
}
