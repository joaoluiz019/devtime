import { criticalityIcon, criticalityLabel, criticalityOf } from './criticality';

/**
 * Faixas de severidade (design-system.md §5.3).
 *
 * A tabela é normativa e as bordas são o que importa: 49,99 ainda é `OK`, 50 já é `INFO`. Um erro de
 * `>` para `>=` aqui pinta de verde um contrato que já deveria estar em atenção — e o usuário só
 * descobre quando o cliente reclama.
 */
describe('criticalityOf', () => {
  it.each([
    [0, 'OK'],
    [49.99, 'OK'],
    [50, 'INFO'],
    [79.99, 'INFO'],
    [80, 'WARNING'],
    [99.99, 'WARNING'],
    [100, 'CRITICAL'],
    [105.07, 'CRITICAL'],
  ])('classifica %s%% como %s', (rate, expected) => {
    expect(criticalityOf(rate)).toBe(expected);
  });

  it('DS-05: toda severidade tem ícone e rótulo textual, nunca só cor', () => {
    for (const rate of [10, 60, 90, 120]) {
      const criticality = criticalityOf(rate);
      expect(criticalityIcon(criticality)).not.toBe('');
      expect(criticalityLabel(criticality)).not.toBe('');
    }
  });
});
