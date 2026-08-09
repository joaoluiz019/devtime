import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { LoadingCounter } from '../http/loading.interceptor';
import { NotificationStore } from '../notifications/notification.store';
import { TimerBarComponent } from '../timer/timer-bar.component';
import { TimerStore } from '../timer/timer.store';
import { SidebarComponent } from './sidebar.component';
import { TopbarComponent } from './topbar.component';

/**
 * Shell da aplicação — layout L2 de `layouts.md` §6.
 *
 * Estrutura persistente que envolve todas as telas autenticadas. As regiões fixas seguem §6.1: barra
 * superior de 56px e barra lateral de 240px (64px recolhida).
 *
 * A barra do cronômetro fica acima da barra superior e **só ocupa espaço quando existe cronômetro
 * ativo**: o próprio componente não renderiza nada fora disso, então o layout não é deslocado por um
 * elemento ausente.
 */
@Component({
  selector: 'dt-shell',
  imports: [RouterOutlet, ToastModule, TimerBarComponent, TopbarComponent, SidebarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- §6.2: acima da barra superior, ocupando toda a largura quando ativa. -->
    <dt-timer-bar />

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
  private readonly notifications = inject(NotificationStore);
  private readonly timer = inject(TimerStore);

  constructor() {
    // O contador é carregado uma vez e mantido pelo fluxo; o shell é o único lugar em que os dois
    // ciclos de vida coincidem com o da sessão autenticada.
    void this.notifications.refresh();
    void this.notifications.connect();
    // O cronômetro é global e sobrevive à navegação; o shell é quem o liga e desliga com a sessão.
    void this.timer.connect();
    inject(DestroyRef).onDestroy(() => {
      this.notifications.disconnect();
      this.timer.disconnect();
    });
  }

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
