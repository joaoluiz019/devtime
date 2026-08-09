import { Pipe, PipeTransform } from '@angular/core';

/**
 * Segundos para `HH:MM:SS` (§21.4 de `specs/009-timer/spec.md`).
 *
 * Distinto de `durationPipe`, que formata **minutos** em `HH:MM`: o cronômetro precisa dos segundos
 * à vista — é o que prova que ele está andando —, enquanto o registro de horas nunca os exibe,
 * porque ART-035 os trunca no cálculo.
 *
 * As horas não são limitadas a dois dígitos: um cronômetro esquecido no fim de semana mostra
 * `52:10:07`, e cortar o dígito extra transformaria isso em algo plausível.
 */
@Pipe({ name: 'elapsedTime' })
export class ElapsedTimePipe implements PipeTransform {
  transform(seconds: number | null | undefined): string {
    if (seconds === null || seconds === undefined || Number.isNaN(seconds)) {
      return '00:00:00';
    }
    const total = Math.max(0, Math.trunc(seconds));
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    return `${pad(hours)}:${pad(minutes)}:${pad(total % 60)}`;
  }
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}
