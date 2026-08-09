import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { ThemePreference, ThemeStore } from '../../../core/theme/theme.store';
import { CategoryLookupApi, CategoryOption } from '../../../shared/data/category-lookup.api';
import { SettingsApi } from '../data/settings.api';
import { DashboardPeriodPreference } from '../data/settings.model';

/**
 * Preferências — P27, layout L9.
 *
 * O tema é aplicado **imediatamente** ao selecionar e só então persistido: uma preferência visual que
 * exige salvar para ser vista obriga a pessoa a imaginar o resultado. Se a gravação falhar, a
 * mensagem aparece e a escolha local permanece — ela ainda vale para esta sessão.
 */
@Component({
  selector: 'dt-preferences-settings-page',
  imports: [FormsModule, ButtonModule, CheckboxModule, MessageModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.preferences">Preferências</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.preferences.subtitle">
      Ajustes que valem apenas para você, em qualquer organização.
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
      <div class="dt-setting__field">
        <label for="pref-theme" i18n="@@settings.preferences.theme">Tema</label>
        <p-select
          inputId="pref-theme"
          [options]="themeOptions"
          optionLabel="label"
          optionValue="value"
          [ngModel]="theme()"
          (onChange)="applyTheme($event.value)"
        />
        <small class="dt-setting__hint" i18n="@@settings.preferences.theme.hint">
          A opção Sistema acompanha a configuração do seu aparelho.
        </small>
      </div>

      <div class="dt-setting__field">
        <label for="pref-period" i18n="@@settings.preferences.period">
          Período padrão do painel
        </label>
        <p-select
          inputId="pref-period"
          [options]="periodOptions"
          optionLabel="label"
          optionValue="value"
          [ngModel]="dashboardPeriod()"
          (onChange)="dashboardPeriod.set($event.value)"
        />
      </div>

      <div class="dt-setting__field">
        <label for="pref-category" i18n="@@settings.preferences.category">Categoria padrão</label>
        <p-select
          inputId="pref-category"
          [options]="categories()"
          optionLabel="name"
          optionValue="id"
          [ngModel]="defaultCategoryId()"
          [showClear]="true"
          i18n-placeholder="@@settings.preferences.category.placeholder"
          placeholder="Usar a do contrato"
          (onChange)="defaultCategoryId.set($event.value)"
        />
        <small class="dt-setting__hint" i18n="@@settings.preferences.category.hint">
          Pré-seleciona a categoria ao lançar horas; a do contrato continua tendo precedência.
        </small>
      </div>

      <div class="dt-setting__check">
        <p-checkbox
          inputId="pref-timer-reminder"
          [binary]="true"
          [ngModel]="timerReminder()"
          (onChange)="timerReminder.set($event.checked)"
        />
        <label for="pref-timer-reminder" i18n="@@settings.preferences.timerReminder">
          Avisar quando um cronômetro ficar muito tempo em andamento
        </label>
      </div>

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
export class PreferencesSettingsPage {
  private readonly api = inject(SettingsApi);
  private readonly categoryLookup = inject(CategoryLookupApi);
  private readonly themeStore = inject(ThemeStore);

  protected readonly themeOptions = [
    { label: $localize`:@@settings.theme.system:Sistema`, value: 'SYSTEM' },
    { label: $localize`:@@settings.theme.light:Claro`, value: 'LIGHT' },
    { label: $localize`:@@settings.theme.dark:Escuro`, value: 'DARK' },
  ];

  protected readonly periodOptions = [
    { label: $localize`:@@dashboard.period.current:Período atual`, value: 'CURRENT_PERIOD' },
    { label: $localize`:@@dashboard.period.week:7 dias`, value: 'LAST_7_DAYS' },
    { label: $localize`:@@dashboard.period.month:30 dias`, value: 'LAST_30_DAYS' },
  ];

  protected readonly theme = signal<ThemePreference>(this.themeStore.preference());
  protected readonly dashboardPeriod = signal<DashboardPeriodPreference>('CURRENT_PERIOD');
  protected readonly defaultCategoryId = signal<string | null>(null);
  protected readonly timerReminder = signal(true);

  private readonly _categories = signal<readonly CategoryOption[]>([]);
  private readonly _saving = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly categories = computed(() => [...this._categories()]);
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
    const [profile, categories] = await Promise.all([
      firstValueFrom(this.api.profile()).catch(() => null),
      firstValueFrom(this.categoryLookup.search()).catch(() => []),
    ]);
    this._categories.set(categories);
    if (profile !== null) {
      this.theme.set(profile.preferences.theme);
      this.dashboardPeriod.set(profile.preferences.dashboardPeriod);
      this.defaultCategoryId.set(profile.preferences.defaultCategoryId ?? null);
      this.timerReminder.set(profile.preferences.timerReminderEnabled);
      // O tema da conta vence o escolhido localmente: ele acompanha a pessoa entre aparelhos.
      this.themeStore.setPreference(profile.preferences.theme);
    }
  }

  protected applyTheme(theme: ThemePreference): void {
    this.theme.set(theme);
    this.themeStore.setPreference(theme);
  }

  protected async save(): Promise<void> {
    this._saving.set(true);
    this._saved.set(false);
    this._error.set(null);
    try {
      await firstValueFrom(
        this.api.updatePreferences({
          theme: this.theme(),
          dashboardPeriod: this.dashboardPeriod(),
          defaultCategoryId: this.defaultCategoryId() ?? undefined,
          timerReminderEnabled: this.timerReminder(),
        }),
      );
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
