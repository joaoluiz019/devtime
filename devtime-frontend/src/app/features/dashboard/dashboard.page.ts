import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AuthStore } from '../../core/auth/auth.store';

/**
 * Dashboard — placeholder de rota desta sprint.
 *
 * O dashboard real é a feature 010, que consome o cálculo de saldo da 011 (ordem 12 e 10 de
 * `specs/implementation-order.md`). Construí-lo agora exigiria dados falsos e produziria testes que não
 * provam nada — a justificativa registrada na §6 daquele documento.
 *
 * Existe aqui apenas para dar destino à rota raiz autenticada e permitir verificar shell, guards e tema
 * de ponta a ponta.
 */
@Component({
  selector: 'dt-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="dt-dashboard__title" i18n="@@dashboard.title">Dashboard</h1>
    <p class="dt-dashboard__text" i18n="@@dashboard.placeholder">
      Os indicadores aparecem aqui quando houver contratos e horas registradas.
    </p>
    @if (tenantName() !== null) {
      <p class="dt-dashboard__tenant">{{ tenantName() }}</p>
    }
  `,
  styles: `
    .dt-dashboard__title {
      margin: 0 0 var(--dt-space-2);
      font-size: var(--dt-text-2xl);
      line-height: var(--dt-text-2xl-line);
    }

    .dt-dashboard__text,
    .dt-dashboard__tenant {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }
  `,
})
export class DashboardPage {
  private readonly authStore = inject(AuthStore);

  /** ST-02 / FR-042: dado derivado usa computed, nunca é recalculado no template. */
  protected readonly tenantName = computed(() => this.authStore.tenant()?.name ?? null);
}
