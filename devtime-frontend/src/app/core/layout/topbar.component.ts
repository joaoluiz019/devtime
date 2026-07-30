import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { AuthStore } from '../auth/auth.store';
import { ThemeStore } from '../theme/theme.store';

/**
 * Barra superior — `layouts.md` §6.3.
 *
 * Contém, desta sprint, o alternador da barra lateral, a identificação da organização, o alternador de
 * tema e o nome do usuário. Busca global, ação rápida "Novo" e o painel de notificações pertencem às
 * features que os alimentam; um campo de busca que não busca nada é pior que sua ausência.
 */
@Component({
  selector: 'dt-topbar',
  imports: [ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="dt-topbar">
      <p-button
        icon="pi pi-bars"
        severity="secondary"
        [text]="true"
        i18n-ariaLabel="@@topbar.toggleSidebar"
        ariaLabel="Alternar menu lateral"
        (onClick)="toggleSidebar.emit()"
      />

      @if (tenantName() !== '') {
        <span class="dt-topbar__tenant">{{ tenantName() }}</span>
      }

      <div class="dt-topbar__spacer"></div>

      <p-button
        [icon]="themeIcon()"
        severity="secondary"
        [text]="true"
        [ariaLabel]="themeLabel()"
        (onClick)="toggleTheme()"
      />

      @if (displayName() !== '') {
        <span class="dt-topbar__user">
          <span class="dt-topbar__avatar" aria-hidden="true">{{ initials() }}</span>
          <span class="dt-topbar__user-name">{{ displayName() }}</span>
        </span>
      }
    </header>
  `,
  styleUrl: './topbar.component.scss',
})
export class TopbarComponent {
  private readonly authStore = inject(AuthStore);
  private readonly themeStore = inject(ThemeStore);

  readonly toggleSidebar = output<void>();

  protected readonly displayName = this.authStore.displayName;
  protected readonly initials = this.authStore.initials;

  protected readonly tenantName = computed(() => this.authStore.tenant()?.name ?? '');

  protected readonly themeIcon = computed(() =>
    this.themeStore.isDark() ? 'pi pi-sun' : 'pi pi-moon',
  );

  /** FR-143: ícone sem texto exige rótulo acessível, e o rótulo descreve a ação, não o estado. */
  protected readonly themeLabel = computed(() =>
    this.themeStore.isDark()
      ? $localize`:@@topbar.theme.light:Mudar para o tema claro`
      : $localize`:@@topbar.theme.dark:Mudar para o tema escuro`,
  );

  protected toggleTheme(): void {
    this.themeStore.toggle();
  }
}
