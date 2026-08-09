import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { AuthStore } from '../auth/auth.store';
import { NotificationStore } from '../notifications/notification.store';
import { ThemeStore } from '../theme/theme.store';

/**
 * Barra superior — `layouts.md` §6.3.
 *
 * Contém o alternador da barra lateral, a identificação da organização, o sino de notificações, o
 * alternador de tema e o nome do usuário. Busca global e ação rápida "Novo" pertencem às features que
 * as alimentam; um campo de busca que não busca nada é pior que sua ausência.
 */
@Component({
  selector: 'dt-topbar',
  imports: [RouterLink, ButtonModule],
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

      <!-- O contador vem do núcleo: a barra existe em toda tela e não pode depender de uma rota. -->
      <a
        class="dt-topbar__bell"
        routerLink="/notifications"
        [attr.aria-label]="notificationsLabel()"
      >
        <i class="pi pi-bell" aria-hidden="true"></i>
        @if (notifications.hasUnread()) {
          <span class="dt-topbar__badge" aria-hidden="true">{{ notifications.badge() }}</span>
        }
      </a>

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

  protected readonly notifications = inject(NotificationStore);

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

  /** O rótulo diz quantas estão pendentes: o número no selo é decorativo para leitor de tela. */
  protected readonly notificationsLabel = computed(() =>
    this.notifications.hasUnread()
      ? $localize`:@@topbar.notifications.unread:Notificações, ${this.notifications.unreadCount()}:count: não lidas`
      : $localize`:@@topbar.notifications:Notificações`,
  );

  protected toggleTheme(): void {
    this.themeStore.toggle();
  }
}
