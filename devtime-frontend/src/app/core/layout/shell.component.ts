import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { LoadingCounter } from '../http/loading.interceptor';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';

/**
 * Shell da aplicação — layout L2 de `layouts.md` §6.
 *
 * Estrutura persistente que envolve todas as telas autenticadas. As regiões fixas seguem §6.1: barra
 * superior de 56px e barra lateral de 240px (64px recolhida).
 *
 * A barra do cronômetro (48px, acima da barra superior) **não** está aqui: ela só existe quando há
 * cronômetro ativo, e o cronômetro é a feature 009. Reservar o espaço agora deslocaria todo o layout
 * por um elemento que nunca aparece.
 */
@Component({
  selector: 'dt-shell',
  imports: [RouterOutlet, ToastModule, TopbarComponent, SidebarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-shell">
      <dt-sidebar [collapsed]="sidebarCollapsed()" />

      <div class="dt-shell__main">
        <dt-topbar (toggleSidebar)="toggleSidebar()" />

        @if (isLoading()) {
          <div
            class="dt-shell__progress"
            role="progressbar"
            i18n-aria-label="@@shell.loading"
            aria-label="Carregando"
          ></div>
        }

        <!-- AC-09 / A11Y-09: região de marco presente em todas as páginas. -->
        <main class="dt-shell__content" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>

    <p-toast position="bottom-right" />
  `,
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly loadingCounter = inject(LoadingCounter);

  /**
   * SB-04 determina que o estado da barra lateral persista nas preferências do usuário. Nesta sprint
   * ele é local: persistir exige o endpoint de preferências, que pertence à feature 002.
   */
  private readonly _sidebarCollapsed = signal(false);

  protected readonly sidebarCollapsed = this._sidebarCollapsed.asReadonly();

  /** FR-170: barra de progresso no topo em vez de tela em branco. */
  protected readonly isLoading = computed(() => this.loadingCounter.pending() > 0);

  protected toggleSidebar(): void {
    this._sidebarCollapsed.update((collapsed) => !collapsed);
  }
}
