/**
 * Interpretação flexível de duração (components.md §6.2).
 *
 * O campo de duração é o mais usado do produto (RF-111) e seu atrito impacta diretamente a adoção
 * (PR-01). Aceitar um único formato obrigaria o usuário a traduzir mentalmente o que ele já sabe:
 * quem trabalhou "uma hora e meia" digita `1,5h`, `1h30` ou `90` — os três significam a mesma coisa.
 *
 * | Entrada | Resultado |
 * |---|---|
 * | `90`, `90m` | 90 |
 * | `1:30`, `1h30`, `1h30m` | 90 |
 * | `1,5h`, `1.5h` | 90 |
 * | `2h` | 120 |
 *
 * DI-04 / ART-034: o valor produzido é sempre inteiro em minutos.
 */

/** Padrões aceitos, na ordem em que são testados. */
const CLOCK = /^(-?)(\d+):([0-5]\d)$/;
const HOURS_AND_MINUTES = /^(-?)(\d+)h(?:\s*(\d{1,2})m?)?$/i;
const DECIMAL_HOURS = /^(-?)(\d+(?:[.,]\d+)?)h$/i;
const MINUTES = /^(-?)(\d+)m?$/i;

/**
 * Converte texto livre em minutos inteiros.
 *
 * Devolve `null` quando o texto não corresponde a nenhum formato conhecido — DI-02 exige que a
 * entrada inválida **mantenha o que foi digitado** e exiba erro, em vez de ser silenciosamente
 * descartada ou truncada.
 */
export function parseDuration(raw: string): number | null {
  const text = raw.trim().replace(/\s+/g, '');
  if (text === '') {
    return null;
  }

  const clock = CLOCK.exec(text);
  if (clock !== null) {
    return sign(clock[1]) * (Number(clock[2]) * 60 + Number(clock[3]));
  }

  const composed = HOURS_AND_MINUTES.exec(text);
  if (composed !== null) {
    const minutes = composed[3] === undefined ? 0 : Number(composed[3]);
    // `1h75` não é uma duração — é erro de digitação, e aceitá-lo produziria 2h15 sem aviso.
    if (minutes > 59) {
      return null;
    }
    return sign(composed[1]) * (Number(composed[2]) * 60 + minutes);
  }

  const decimal = DECIMAL_HOURS.exec(text);
  if (decimal !== null) {
    const hours = Number(decimal[2].replace(',', '.'));
    // Arredonda para o minuto: `1,51h` é 90,6 min, e meio minuto não existe no domínio (ART-034).
    return sign(decimal[1]) * Math.round(hours * 60);
  }

  const minutesOnly = MINUTES.exec(text);
  if (minutesOnly !== null) {
    return sign(minutesOnly[1]) * Number(minutesOnly[2]);
  }

  return null;
}

/** DI-01: ao perder o foco, o valor é normalizado para `HH:MM`. */
export function formatDuration(minutes: number): string {
  const absolute = Math.abs(Math.trunc(minutes));
  const hours = `${Math.floor(absolute / 60)}`.padStart(2, '0');
  const rest = `${absolute % 60}`.padStart(2, '0');
  return `${minutes < 0 ? '-' : ''}${hours}:${rest}`;
}

function sign(marker: string | undefined): number {
  return marker === '-' ? -1 : 1;
}
