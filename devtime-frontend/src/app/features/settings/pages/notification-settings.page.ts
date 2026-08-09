import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { SettingsApi } from '../data/settings.api';
import { NotificationTypeOption } from '../data/settings.model';

/**
 * Notificações — P28, layout L9.
 *
 * A lista de tipos vem do servidor com a marca `canMute`: alertas críticos — estouro de saldo,
 * segurança — não são silenciáveis. A tela os mostra desabilitados com a explicação em vez de
 * omiti-los; esconder o que não se pode desligar faz procurar a opção em outro lugar.
 */
@Component({
  selector: 'dt-notification-settings-page',
  imports: [FormsModule, ButtonModule, CheckboxModule, MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.notifications">Notificações</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.notifications.subtitle">
      Escolha o que chega até você e por qual canal.
    </p>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (saved()) {
        <p-message
          severity="success"
          i18n-text="@@settings.saved"
          text="Alterações salvas."
          styleClass="w-full mb-3"
        />
      }
    </div>

    <div class="dt-setting__form">
      <div class="dt-setting__check">
        <p-checkbox
          inputId="notif-email"
          [binary]="true"
          [ngModel]="emailEnabled()"
          (onChange)="emailEnabled.set($event.checked)"
        />
        <label for="notif-email" i18n="@@settings.notifications.email">
          Receber notificações também por e-mail
        </label>
      </div>

      <ul class="dt-setting__list" role="list">
        @for (type of types(); track type.type) {
          <li class="dt-setting__item">
            <span class="dt-setting__item-name">
              <label [attr.for]="'notif-' + type.type">{{ type.label }}</label>
              @if (!type.canMute) {
                <span class="dt-setting__meta" i18n="@@settings.notifications.required">
                  sempre enviada
                </span>
              }
            </span>
            <p-checkbox
              [inputId]="'notif-' + type.type"
              [binary]="true"
              [disabled]="!type.canMute"
              [ngModel]="isEnabled(type.type)"
              (onChange)="toggle(type.type, $event.checked)"
            />
          </li>
        }
      </ul>

      <div class="dt-setting__actions">
        <p-button
          i18n-label="@@action.saveChanges"
          label="Salvar alterações"
          [loading]="saving()"
          (onClick)="save()"
        />
      </div>
    </div>
  `,
  styleUrl: './settings-form.scss',
})
export class NotificationSettingsPage {
  private readonly api = inject(SettingsApi);

  private readonly _types = signal<readonly NotificationTypeOption[]>([]);
  private readonly _muted = signal<readonly string[]>([]);
  private readonly _saving = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly emailEnabled = signal(true);
  protected readonly types = computed(() => this._types());
  protected readonly saving = this._saving.asReadonly();
  protected readonly saved = this._saved.asReadonly();

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const preferences = await firstValueFrom(this.api.notificationPreferences());
      this._types.set(preferences.availableTypes);
      this._muted.set(preferences.mutedNotificationTypes);
      this.emailEnabled.set(preferences.emailNotifications);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  /** A caixa marca "receber"; o que o servidor guarda é a lista dos silenciados — o oposto. */
  protected isEnabled(type: string): boolean {
    return !this._muted().includes(type);
  }

  protected toggle(type: string, enabled: boolean): void {
    const muted = new Set(this._muted());
    if (enabled) {
      muted.delete(type);
    } else {
      muted.add(type);
    }
    this._muted.set([...muted]);
  }

  protected async save(): Promise<void> {
    this._saving.set(true);
    this._saved.set(false);
    this._error.set(null);
    try {
      const preferences = await firstValueFrom(
        this.api.updateNotificationPreferences({
          emailNotifications: this.emailEnabled(),
          mutedNotificationTypes: this._muted(),
        }),
      );
      this._types.set(preferences.availableTypes);
      this._muted.set(preferences.mutedNotificationTypes);
      this._saved.set(true);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._saving.set(false);
    }
  }
}
