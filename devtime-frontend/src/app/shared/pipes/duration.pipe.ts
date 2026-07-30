import { Pipe, PipeTransform } from '@angular/core';

/** Formatos de exibição de duração. */
export type DurationFormat = 'plain' | 'signed';

/**
 * Minutos inteiros para `HH:MM` (ART-035, I18-03, FR-128).
 *
 * A formatação vive num pipe, e não no store (ST-04) nem na entidade (BR-106/MP-04): o mesmo valor é
 * exibido em contextos diferentes, e formatar na origem obrigaria a desformatar para calcular.
 *
 * CE-D-01: durações acima de 999 horas são exibidas integralmente (`1024:30`), nunca convertidas para
 * dias — o usuário compara horas contratadas com horas consumidas, e a conversão quebraria a comparação.
 */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(minutes: number | null | undefined, format: DurationFormat = 'plain'): string {
    if (minutes === null || minutes === undefined || Number.isNaN(minutes)) {
      // CE-D-05: nunca exibir um número possivelmente errado; o travessão sinaliza ausência de valor.
      return '—';
    }

    const truncated = Math.trunc(minutes);
    const isNegative = truncated < 0;
    const absolute = Math.abs(truncated);
    const hours = Math.floor(absolute / 60);
    const remainder = absolute % 60;
    const formatted = `${pad(hours)}:${pad(remainder)}`;

    if (isNegative) {
      // Duração negativa é sempre excedente e sempre exibida com sinal (§10 do design system).
      return `-${formatted}`;
    }
    return format === 'signed' && truncated > 0 ? `+${formatted}` : formatted;
  }
}

/** Horas não são limitadas a 2 dígitos: `1024:30` é uma exibição válida (CE-D-01). */
function pad(value: number): string {
  return value.toString().padStart(2, '0');
}
