import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { SettingsApi } from '../data/settings.api';
import { UserProfile } from '../data/settings.model';

/**
 * Perfil — P26, layout L9.
 *
 * O e-mail aparece, mas não é editável: trocá-lo muda a identidade de acesso e passa por verificação
 * (feature 001). Um campo editável que só falha ao salvar promete o que a tela não entrega.
 *
 * A troca de senha também não vive aqui: ela exige a senha atual e revoga sessões (RN-454), o que é
 * um fluxo com consequência própria — não um campo entre outros.
 */
@Component({
  selector: 'dt-profile-settings-page',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.profile">Perfil</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.profile.subtitle">
      Como você aparece para o restante da organização.
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

    <form class="dt-setting__form" [formGroup]="form" (ngSubmit)="submit()">
      <div class="dt-setting__field">
        <label for="profile-email" i18n="@@settings.profile.email">E-mail</label>
        <input id="profile-email" type="email" pInputText [value]="email()" disabled />
        <small class="dt-setting__hint" i18n="@@settings.profile.email.hint">
          O e-mail de acesso é alterado pelo fluxo de verificação, não por aqui.
        </small>
      </div>

      <div class="dt-setting__row">
        <div class="dt-setting__field">
          <label for="profile-full-name" i18n="@@settings.profile.fullName">Nome completo</label>
          <input
            id="profile-full-name"
            type="text"
            pInputText
            formControlName="fullName"
            autocomplete="name"
            [attr.aria-invalid]="isInvalid('fullName')"
          />
          @if (isInvalid('fullName')) {
            <small class="dt-setting__error" i18n="@@settings.profile.fullName.invalid">
              Informe de 2 a 150 caracteres.
            </small>
          }
        </div>

        <div class="dt-setting__field">
          <label for="profile-display-name" i18n="@@settings.profile.displayName">
            Como prefere ser chamado
          </label>
          <input
            id="profile-display-name"
            type="text"
            pInputText
            formControlName="displayName"
            autocomplete="nickname"
          />
          <small class="dt-setting__hint" i18n="@@settings.profile.displayName.hint">
            Aparece na barra superior e nas menções.
          </small>
        </div>
      </div>

      <div class="dt-setting__row">
        <div class="dt-setting__field">
          <label for="profile-timezone" i18n="@@settings.profile.timezone">Fuso horário</label>
          <input id="profile-timezone" type="text" pInputText formControlName="timezone" />
          <small class="dt-setting__hint" i18n="@@settings.profile.timezone.hint">
            Define como seus horários de registro são exibidos.
          </small>
        </div>

        <div class="dt-setting__field">
          <label for="profile-locale" i18n="@@settings.profile.locale">Idioma</label>
          <input id="profile-locale" type="text" pInputText formControlName="locale" />
        </div>
      </div>

      <div class="dt-setting__actions">
        <p-button
          type="submit"
          i18n-label="@@action.saveChanges"
          label="Salvar alterações"
          [loading]="saving()"
        />
      </div>
    </form>
  `,
  styleUrl: './settings-form.scss',
})
export class ProfileSettingsPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(SettingsApi);

  protected readonly form = this.formBuilder.group({
    fullName: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(2),
      Validators.maxLength(150),
    ]),
    displayName: this.formBuilder.control('', [Validators.maxLength(60)]),
    timezone: this.formBuilder.control(''),
    locale: this.formBuilder.control(''),
  });

  private readonly _profile = signal<UserProfile | null>(null);
  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly saving = this._saving.asReadonly();
  protected readonly saved = this._saved.asReadonly();

  protected readonly email = computed(() => this._profile()?.email ?? '');

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const profile = await firstValueFrom(this.api.profile());
      this._profile.set(profile);
      this.form.patchValue({
        fullName: profile.fullName,
        displayName: profile.displayName ?? '',
        timezone: profile.timezone,
        locale: profile.locale,
      });
      this.form.markAsPristine();
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this._submitted());
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);
    this._saved.set(false);

    if (this.form.invalid) {
      document.getElementById('profile-full-name')?.focus();
      return;
    }

    const value = this.form.getRawValue();
    this._saving.set(true);
    try {
      this._profile.set(
        await firstValueFrom(
          this.api.updateProfile({
            fullName: value.fullName,
            // Apelido em branco significa "não usar apelido", e o backend distingue vazio de ausente.
            displayName: value.displayName === '' ? undefined : value.displayName,
            timezone: value.timezone === '' ? undefined : value.timezone,
            locale: value.locale === '' ? undefined : value.locale,
          }),
        ),
      );
      this._saved.set(true);
      this.form.markAsPristine();
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._saving.set(false);
    }
  }
}
