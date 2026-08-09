import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../core/auth/auth.store';

/** Item da navegação lateral de configurações. */
interface SettingsLink {
  readonly route: string;
  readonly label: string;
  /** Vazio quando a área é do próprio usuário e não depende de papel. */
  readonly permission?: string;
}

/**
 * Layout de configurações — L9 de `layouts.md` §13.
 *
 * Navegação lateral própria: as áreas de configuração são muitas e mudam de contexto (pessoa versus
 * organização), e empurrá-las para a barra lateral principal misturaria "onde trabalho" com "como o
 * produto se comporta".
 *
 * SB-01: item sem permissão é **ocultado**, não desabilitado — oferecer uma aba que responde `403` é
 * pior do que não oferecê-la.
 */
@Component({
  selector: 'dt-settings-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-settings">
      <nav
        class="dt-settings__nav"
        i18n-aria-label="@@settings.nav"
        aria-label="Navegação de configurações"
      >
        <h1 class="dt-settings__title" i18n="@@settings.title">Configurações</h1>
        <ul role="list">
          @for (link of visibleLinks(); track link.route) {
            <li>
              <a
                [routerLink]="link.route"
                routerLinkActive="dt-settings__link--active"
                class="dt-settings__link"
              >
                {{ link.label }}
              </a>
            </li>
          }
        </ul>
      </nav>

      <section class="dt-settings__content">
        <router-outlet />
      </section>
    </div>
  `,
  styleUrl: './settings-layout.component.scss',
})
export class SettingsLayoutComponent {
  private readonly authStore = inject(AuthStore);

  private readonly links: readonly SettingsLink[] = [
    { route: 'profile', label: $localize`:@@settings.profile:Perfil` },
    { route: 'preferences', label: $localize`:@@settings.preferences:Preferências` },
    { route: 'notifications', label: $localize`:@@settings.notifications:Notificações` },
    {
      route: 'organization',
      label: $localize`:@@settings.organization:Organização`,
      permission: 'TENANT_UPDATE',
    },
    { route: 'team', label: $localize`:@@settings.team:Equipe`, permission: 'MEMBER_VIEW' },
    {
      route: 'categories',
      label: $localize`:@@settings.categories:Categorias`,
      permission: 'CATEGORY_MANAGE',
    },
    { route: 'tags', label: $localize`:@@settings.tags:Etiquetas`, permission: 'TAG_MANAGE' },
    {
      route: 'audit',
      label: $localize`:@@settings.audit:Auditoria`,
      permission: 'TENANT_AUDIT_VIEW',
    },
  ];

  protected readonly visibleLinks = computed(() =>
    this.links.filter(
      (link) => link.permission === undefined || this.authStore.hasPermission(link.permission),
    ),
  );
}
