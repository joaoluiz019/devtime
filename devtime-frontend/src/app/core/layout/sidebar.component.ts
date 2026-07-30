import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TooltipModule } from 'primeng/tooltip';
import { AuthStore } from '../auth/auth.store';

/** Item de navegação da barra lateral. */
interface NavigationItem {
  readonly route: string;
  readonly label: string;
  readonly icon: string;
  /** SB-01: o item é ocultado quando o papel não tem a permissão. */
  readonly permission: string;
}

/**
 * Barra lateral — `layouts.md` §6.4.
 *
 * SB-01: item sem permissão é **ocultado**, não desabilitado. SB-03: no modo recolhido, apenas ícones
 * com tooltip.
 *
 * Apenas Dashboard aparece nesta sprint: os demais itens apontariam para rotas inexistentes. Cada
 * feature acrescenta o seu ao entregar a tela — um menu que leva a página em branco é pior que um menu
 * curto.
 */
@Component({
  selector: 'dt-sidebar',
  imports: [RouterLink, RouterLinkActive, TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- AC-09 / A11Y-09: região de marco de navegação. -->
    <nav
      class="dt-sidebar"
      [class.dt-sidebar--collapsed]="collapsed()"
      i18n-aria-label="@@sidebar.label"
      aria-label="Navegação principal"
    >
      <div class="dt-sidebar__brand">
        <i class="pi pi-stopwatch" aria-hidden="true"></i>
        @if (!collapsed()) {
          <span class="dt-sidebar__brand-name" i18n="@@brand.name">DevTime</span>
        }
      </div>

      <ul class="dt-sidebar__list">
        @for (item of visibleItems(); track item.route) {
          <li>
            <a
              class="dt-sidebar__item"
              [routerLink]="item.route"
              routerLinkActive="dt-sidebar__item--active"
              [pTooltip]="collapsed() ? item.label : ''"
              tooltipPosition="right"
              [attr.aria-label]="item.label"
            >
              <i class="pi" [class]="item.icon" aria-hidden="true"></i>
              @if (!collapsed()) {
                <span>{{ item.label }}</span>
              }
            </a>
          </li>
        }
      </ul>
    </nav>
  `,
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  private readonly authStore = inject(AuthStore);

  readonly collapsed = input.required<boolean>();

  /**
   * Itens do grupo principal (`layouts.md` §6.4).
   *
   * Os rótulos passam por `$localize` porque nenhum texto visível pode ser fixo (ART-095, FR-029).
   */
  private readonly items: readonly NavigationItem[] = [
    {
      route: '/dashboard',
      label: $localize`:@@nav.dashboard:Dashboard`,
      icon: 'pi-home',
      permission: 'DASHBOARD_VIEW_OWN',
    },
  ];

  protected readonly visibleItems = computed(() =>
    this.items.filter((item) => this.authStore.hasPermission(item.permission)),
  );
}
