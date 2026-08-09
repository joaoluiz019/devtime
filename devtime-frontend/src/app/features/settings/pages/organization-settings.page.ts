import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { SettingsApi } from '../data/settings.api';
import { Tenant } from '../data/settings.model';

/**
 * Organização — P29, layout L9.
 *
 * Dois formulários com `version` própria porque o backend expõe dois recursos: o cadastro
 * (`PATCH /tenant`) e as políticas operacionais (`PATCH /tenant/settings`). Juntá-los num só envio
 * faria uma alteração de telefone competir por versão com uma mudança de arredondamento.
 *
 * O arredondamento é a política mais cara de errar: ele muda **quanto** é cobrado de todo registro
 * futuro, então o campo diz o efeito em vez de só pedir um número.
 */
@Component({
  selector: 'dt-organization-settings-page',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    CheckboxModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.organization">Organização</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.organization.subtitle">
      Dados de cadastro e políticas que valem para toda a equipe.
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

    <form class="dt-setting__form" [formGroup]="profileForm" (ngSubmit)="saveProfile()">
      <h3 class="dt-setting__title" i18n="@@settings.organization.identification">Identificação</h3>

      <div class="dt-setting__row">
        <div class="dt-setting__field">
          <label for="org-name" i18n="@@settings.organization.name">Nome *</label>
          <input
            id="org-name"
            type="text"
            pInputText
            formControlName="name"
            [attr.aria-invalid]="nameInvalid()"
          />
          @if (nameInvalid()) {
            <small class="dt-setting__error" i18n="@@settings.organization.name.invalid">
              Informe de 2 a 120 caracteres.
            </small>
          }
        </div>

        <div class="dt-setting__field">
          <label for="org-legal-name" i18n="@@client.legalName">Razão social</label>
          <input id="org-legal-name" type="text" pInputText formControlName="legalName" />
        </div>
      </div>

      <div class="dt-setting__row">
        <div class="dt-setting__field">
          <label for="org-email" i18n="@@client.email">E-mail</label>
          <input id="org-email" type="email" pInputText formControlName="email" />
        </div>

        <div class="dt-setting__field">
          <label for="org-phone" i18n="@@client.phone">Telefone</label>
          <input id="org-phone" type="tel" pInputText formControlName="phone" />
        </div>

        <div class="dt-setting__field">
          <label for="org-currency" i18n="@@settings.organization.currency">Moeda</label>
          <input
            id="org-currency"
            type="text"
            pInputText
            formControlName="currency"
            maxlength="3"
          />
          <small class="dt-setting__hint" i18n="@@settings.organization.currency.hint">
            Código de três letras, como BRL.
          </small>
        </div>
      </div>

      <div class="dt-setting__actions">
        <p-button
          type="submit"
          i18n-label="@@settings.organization.saveProfile"
          label="Salvar cadastro"
          [loading]="savingProfile()"
        />
      </div>
    </form>

    <form class="dt-setting__form" [formGroup]="settingsForm" (ngSubmit)="saveSettings()">
      <h3 class="dt-setting__title" i18n="@@settings.organization.policies">Políticas</h3>

      <div class="dt-setting__row">
        <div class="dt-setting__field">
          <label for="org-rounding" i18n="@@settings.organization.rounding">
            Arredondamento (min)
          </label>
          <p-input-number inputId="org-rounding" formControlName="roundingMinutes" [min]="0" />
          <!-- RN-113: sempre para baixo. O texto evita a leitura de "arredondar para o mais próximo". -->
          <small class="dt-setting__hint" i18n="@@settings.organization.rounding.hint">
            Cada registro é arredondado para baixo ao múltiplo escolhido. Zero desliga.
          </small>
        </div>

        <div class="dt-setting__field">
          <label for="org-retroactive" i18n="@@settings.organization.retroactive">
            Limite retroativo (dias)
          </label>
          <p-input-number
            inputId="org-retroactive"
            formControlName="retroactiveLimitDays"
            [min]="0"
          />
          <small class="dt-setting__hint" i18n="@@settings.organization.retroactive.hint">
            Além deste prazo, o lançamento exige um administrador.
          </small>
        </div>

        <div class="dt-setting__field">
          <label for="org-workday" i18n="@@settings.organization.workDay">
            Jornada diária (min)
          </label>
          <p-input-number inputId="org-workday" formControlName="workDayMinutes" [min]="1" />
        </div>
      </div>

      <div class="dt-setting__check">
        <p-checkbox inputId="org-future" formControlName="allowFutureWorkLogs" [binary]="true" />
        <label for="org-future" i18n="@@settings.organization.future">
          Permitir lançar horas em data futura
        </label>
      </div>

      <div class="dt-setting__actions">
        <p-button
          type="submit"
          i18n-label="@@settings.organization.savePolicies"
          label="Salvar políticas"
          [loading]="savingSettings()"
        />
      </div>
    </form>
  `,
  styleUrl: './settings-form.scss',
})
export class OrganizationSettingsPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(SettingsApi);

  protected readonly profileForm = this.formBuilder.group({
    name: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(2),
      Validators.maxLength(120),
    ]),
    legalName: this.formBuilder.control(''),
    email: this.formBuilder.control('', [Validators.email]),
    phone: this.formBuilder.control(''),
    currency: this.formBuilder.control('BRL', [Validators.pattern(/^[A-Z]{3}$/)]),
  });

  protected readonly settingsForm = this.formBuilder.group({
    roundingMinutes: this.formBuilder.control(0, [Validators.min(0)]),
    retroactiveLimitDays: this.formBuilder.control(30, [Validators.min(0)]),
    workDayMinutes: this.formBuilder.control(480, [Validators.min(1)]),
    allowFutureWorkLogs: this.formBuilder.control(false),
  });

  private readonly _tenant = signal<Tenant | null>(null);
  private readonly _savingProfile = signal(false);
  private readonly _savingSettings = signal(false);
  private readonly _submitted = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly savingProfile = this._savingProfile.asReadonly();
  protected readonly savingSettings = this._savingSettings.asReadonly();
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
      this.apply(await firstValueFrom(this.api.tenant()));
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  private apply(tenant: Tenant): void {
    this._tenant.set(tenant);
    this.profileForm.patchValue({
      name: tenant.name,
      legalName: tenant.legalName ?? '',
      email: tenant.email ?? '',
      phone: tenant.phone ?? '',
      currency: tenant.currency,
    });
    this.settingsForm.patchValue({
      roundingMinutes: tenant.settings.roundingMinutes,
      retroactiveLimitDays: tenant.settings.retroactiveLimitDays,
      workDayMinutes: tenant.settings.workDayMinutes,
      allowFutureWorkLogs: tenant.settings.allowFutureWorkLogs,
    });
    this.profileForm.markAsPristine();
    this.settingsForm.markAsPristine();
  }

  protected nameInvalid(): boolean {
    const control = this.profileForm.controls.name;
    return control.invalid && (control.touched || this._submitted());
  }

  protected async saveProfile(): Promise<void> {
    this._submitted.set(true);
    if (this.profileForm.invalid) {
      document.getElementById('org-name')?.focus();
      return;
    }
    const tenant = this._tenant();
    if (tenant === null) {
      return;
    }

    const value = this.profileForm.getRawValue();
    await this.run(this._savingProfile, () =>
      firstValueFrom(
        this.api.updateTenant({
          name: value.name,
          legalName: blank(value.legalName),
          email: blank(value.email),
          phone: blank(value.phone),
          currency: value.currency,
          version: tenant.version,
        }),
      ),
    );
  }

  protected async saveSettings(): Promise<void> {
    const tenant = this._tenant();
    if (tenant === null || this.settingsForm.invalid) {
      return;
    }

    const value = this.settingsForm.getRawValue();
    await this.run(this._savingSettings, () =>
      firstValueFrom(
        this.api.updateTenantSettings({
          roundingMinutes: value.roundingMinutes,
          retroactiveLimitDays: value.retroactiveLimitDays,
          workDayMinutes: value.workDayMinutes,
          allowFutureWorkLogs: value.allowFutureWorkLogs,
          version: tenant.version,
        }),
      ),
    );
  }

  /**
   * A resposta traz a `version` nova e é reaplicada nos dois formulários.
   *
   * Sem isso, salvar o cadastro e em seguida as políticas usaria uma versão vencida e responderia
   * `409 DEVTIME-2004` — um conflito que a pessoa não causou.
   */
  private async run(
    flag: { set: (value: boolean) => void },
    operation: () => Promise<Tenant>,
  ): Promise<void> {
    flag.set(true);
    this._saved.set(false);
    this._error.set(null);
    try {
      this.apply(await operation());
      this._saved.set(true);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      flag.set(false);
    }
  }
}

function blank(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}
