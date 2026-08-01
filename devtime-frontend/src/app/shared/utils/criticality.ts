/**
 * Faixas de severidade de consumo (design-system.md §5.3).
 *
 * A tabela é normativa e **fixa**: 0–49 `OK`, 50–79 `INFO`, 80–99 `WARNING`, ≥ 100 `CRITICAL`. As
 * quatro cores correspondentes têm significado único em todo o produto (DS-04) e nunca decoram.
 *
 * Vive em `shared/utils` porque três features a consomem — `010-dashboard`, `011-bank-hours` e
 * `013-notifications` — e duplicar a tabela produziria duas leituras divergentes do mesmo saldo
 * (§21.2 de `specs/011-bank-hours/spec.md`).
 */
export type Criticality = 'OK' | 'INFO' | 'WARNING' | 'CRITICAL';

/** Limiares da tabela §5.3, em pontos percentuais. */
export const CRITICALITY_THRESHOLDS = { info: 50, warning: 80, critical: 100 } as const;

/**
 * Classifica uma taxa de consumo percentual.
 *
 * A comparação usa o percentual como vem do servidor: `BalanceCalculator` já arredonda para duas
 * casas com `HALF_UP`, e reclassificar a partir de minutos aqui reproduziria a fórmula canônica no
 * cliente — o que FR-045 proíbe e RP-03 identifica como a origem mais provável de divergência de
 * saldo.
 */
export function criticalityOf(consumptionRate: number): Criticality {
  if (consumptionRate >= CRITICALITY_THRESHOLDS.critical) {
    return 'CRITICAL';
  }
  if (consumptionRate >= CRITICALITY_THRESHOLDS.warning) {
    return 'WARNING';
  }
  if (consumptionRate >= CRITICALITY_THRESHOLDS.info) {
    return 'INFO';
  }
  return 'OK';
}

/** Classe utilitária de cor, definida em `styles.scss`. */
export function criticalityClass(criticality: Criticality): string {
  return `dt-severity-${criticality.toLowerCase()}`;
}

/**
 * Ícone da severidade (design-system.md §11).
 *
 * DS-05: a cor nunca é o único indicador; o ícone acompanha toda aplicação de severidade.
 */
export function criticalityIcon(criticality: Criticality): string {
  switch (criticality) {
    case 'CRITICAL':
      return 'pi-times-circle';
    case 'WARNING':
      return 'pi-exclamation-triangle';
    case 'INFO':
      return 'pi-info-circle';
    case 'OK':
      return 'pi-check-circle';
  }
}

/** Rótulo textual da severidade (design-system.md §5.3), exigido por DS-05 junto do ícone. */
export function criticalityLabel(criticality: Criticality): string {
  switch (criticality) {
    case 'CRITICAL':
      return $localize`:@@criticality.critical:Excedido`;
    case 'WARNING':
      return $localize`:@@criticality.warning:Atenção`;
    case 'INFO':
      return $localize`:@@criticality.info:Em andamento`;
    case 'OK':
      return $localize`:@@criticality.ok:Saudável`;
  }
}
