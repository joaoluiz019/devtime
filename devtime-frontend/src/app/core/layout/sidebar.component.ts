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
 * Bloco de itens com um rótulo de seção.
 *
 * O rótulo vazio identifica o grupo de abertura: um título sobre um item só é ruído, e Dashboard é
 * o único item que não pertence nem a operação nem a análise.
 */
interface NavigationGroup {
  readonly label: string;
  readonly items: readonly NavigationItem[];
}

/**
 * Barra lateral — `layouts.md` §6.4.
 *
 * SB-01: item sem permissão é **ocultado**, não desabilitado. SB-02: o item ativo é marcado por cor
 * **e** pela barra à esquerda. SB-03: no modo recolhido, apenas ícones com tooltip.
 *
 * Os itens são agrupados por finalidade — operação (o trabalho do dia) e análise (o que se olha
 * depois) — porque sete itens sem separação viram uma lista que se lê inteira toda vez. Grupo cujos
 * itens foram todos ocultados por permissão não renderiza o rótulo: uma seção "Análise" vazia
 * anuncia a existência de telas que aquele papel não pode abrir.
 *
 * Configurações fica no grupo inferior, empurrado pelo espaçador, como manda §6.4.
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
        <span class="dt-sidebar__mark" aria-hidden="true">
          <i class="pi pi-stopwatch"></i>
        </span>
        @if (!collapsed()) {
          <span class="dt-sidebar__brand-name" i18n="@@brand.name">DevTime</span>
        }
      </div>

      @for (group of visibleGroups(); track group.label) {
        <div class="dt-sidebar__group">
          @if (group.label !== '' && !collapsed()) {
            <span class="dt-sidebar__group-label" aria-hidden="true">{{ group.label }}</span>
          }
          <ul class="dt-sidebar__list" [attr.aria-label]="group.label || null">
            @for (item of group.items; track item.route) {
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
        </div>
      }

      <div class="dt-sidebar__spacer"></div>

      @for (group of visibleBottomGroups(); track group.label) {
        <div class="dt-sidebar__group dt-sidebar__group--bottom">
          <ul class="dt-sidebar__list">
            @for (item of group.items; track item.route) {
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
        </div>
      }
    </nav>
  `,
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  private readonly authStore = inject(AuthStore);

  readonly collapsed = input.required<boolean>();

  /**
   * Grupos superiores (`layouts.md` §6.4).
   *
   * Os rótulos passam por `$localize` porque nenhum texto visível pode ser fixo (ART-095, FR-029) —
   * inclusive os das seções, que são texto de interface como qualquer outro.
   */
  private readonly groups: readonly NavigationGroup[] = [
    {
      label: '',
      items: [
        {
          route: '/dashboard',
          label: $localize`:@@nav.dashboard:Dashboard`,
          icon: 'pi-home',
          permission: 'DASHBOARD_VIEW_OWN',
        },
      ],
    },
    {
      label: $localize`:@@nav.group.operation:Operação`,
      items: [
        {
          route: '/clients',
          label: $localize`:@@nav.clients:Clientes`,
          icon: 'pi-briefcase',
          permission: 'CLIENT_VIEW',
        },
        {
          route: '/contracts',
          label: $localize`:@@nav.contracts:Contratos`,
          icon: 'pi-file',
          permission: 'CONTRACT_VIEW',
        },
        {
          route: '/tickets',
          label: $localize`:@@nav.tickets:Tickets`,
          icon: 'pi-ticket',
          permission: 'TICKET_VIEW',
        },
        {
          route: '/work-logs',
          label: $localize`:@@nav.workLogs:Horas`,
          icon: 'pi-clock',
          permission: 'WORKLOG_VIEW_OWN',
        },
      ],
    },
    {
      label: $localize`:@@nav.group.analysis:Análise`,
      items: [
        {
          route: '/reports',
          label: $localize`:@@nav.reports:Relatórios`,
          icon: 'pi-chart-bar',
          permission: 'REPORT_VIEW_OWN',
        },
      ],
    },
  ];

  /** Grupo inferior de §6.4 — separado dos demais pelo espaçador, não por um rótulo. */
  private readonly bottomGroups: readonly NavigationGroup[] = [
    {
      label: '',
      items: [
        {
          route: '/settings',
          label: $localize`:@@nav.settings:Configurações`,
          icon: 'pi-cog',
          // Sem permissão declarada: perfil e preferências existem para todo papel; o que exige
          // permissão é ocultado dentro da própria tela (SB-01).
          permission: '',
        },
      ],
    },
  ];

  protected readonly visibleGroups = computed(() => this.filter(this.groups));

  protected readonly visibleBottomGroups = computed(() => this.filter(this.bottomGroups));

  private filter(groups: readonly NavigationGroup[]): readonly NavigationGroup[] {
    return groups
      .map((group) => ({
        ...group,
        items: group.items.filter(
          (item) => item.permission === '' || this.authStore.hasPermission(item.permission),
        ),
      }))
      .filter((group) => group.items.length > 0);
  }
}
