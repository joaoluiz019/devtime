import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { debounceTime, firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { ClientLookupApi, ClientOption } from '../../../shared/data/client-lookup.api';
import { DurationInputComponent } from '../../../shared/components/duration-input/duration-input.component';
import { PeriodPreviewComponent } from '../components/period-preview.component';
import { ContractApi } from '../data/contract.api';
import {
  Contract,
  ContractType,
  OveragePolicy,
  PeriodPreviewItem,
  RolloverPolicy,
} from '../data/contract.model';

/** Espera antes de recalcular a prévia; evita uma chamada por tecla no dia de faturamento. */
const PREVIEW_DEBOUNCE_MS = 300;

/**
 * Formulário de contrato — P15, layout L7 (T-004-19).
 *
 * FM-09: o painel de prévia atualiza conforme o preenchimento, chamando `POST /contracts/preview-periods`.
 * CA-01 garante que a prévia usa o mesmo algoritmo da ativação, então o que está à direita é o que
 * será gravado — e é a única oportunidade de perceber um dia de faturamento errado antes do primeiro
 * fechamento torto, um mês depois.
 *
 * RN-206: cliente, tipo e data de início são **imutáveis** depois de criados. Na edição eles aparecem
 * desabilitados em vez de sumirem: quem edita precisa ver a que contrato está mexendo.
 */
@Component({
  selector: 'dt-contract-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CheckboxModule,
    DatePickerModule,
    DurationInputComponent,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    PeriodPreviewComponent,
    SelectModule,
    TextareaModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="dt-contract-form__back">
      <a routerLink="/contracts" i18n="@@action.back">Voltar</a>
    </nav>

    <h1 class="dt-contract-form__title">{{ title() }}</h1>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    <div class="dt-contract-form__layout">
      <form class="dt-contract-form" [formGroup]="form" (ngSubmit)="submit()">
        <fieldset class="dt-contract-form__section">
          <legend i18n="@@contract.section.identification">Identificação</legend>

          <div class="dt-contract-form__field">
            <label for="contract-client" i18n="@@contract.client">Cliente *</label>
            <p-select
              inputId="contract-client"
              [options]="clients()"
              optionLabel="name"
              optionValue="id"
              formControlName="clientId"
              [filter]="true"
              [filterBy]="'name'"
              i18n-placeholder="@@contract.client.placeholder"
              placeholder="Selecione o cliente"
            />
            @if (isInvalid('clientId')) {
              <small class="dt-contract-form__error" i18n="@@contract.client.required">
                Selecione o cliente do contrato.
              </small>
            }
            @if (isEdit()) {
              <small class="dt-contract-form__hint" i18n="@@contract.client.immutable">
                O cliente não pode ser alterado depois da criação.
              </small>
            }
          </div>

          <div class="dt-contract-form__field">
            <label for="contract-name" i18n="@@contract.name">Nome *</label>
            <input
              id="contract-name"
              type="text"
              pInputText
              formControlName="name"
              aria-required="true"
              [attr.aria-invalid]="isInvalid('name')"
            />
            @if (isInvalid('name')) {
              <small class="dt-contract-form__error">
                @if (serverError('name') !== null) {
                  {{ serverError('name') }}
                } @else {
                  <ng-container i18n="@@contract.name.invalid"
                    >Informe o nome, com 2 a 150 caracteres.</ng-container
                  >
                }
              </small>
            }
          </div>

          <div class="dt-contract-form__field">
            <label for="contract-code" i18n="@@contract.code">Código</label>
            <input id="contract-code" type="text" pInputText formControlName="code" />
            <small class="dt-contract-form__hint" i18n="@@contract.code.hint">
              Em branco, o sistema gera um código sequencial.
            </small>
          </div>
        </fieldset>

        <fieldset class="dt-contract-form__section">
          <legend i18n="@@contract.section.hours">Horas e ciclo</legend>

          <div class="dt-contract-form__row">
            <div class="dt-contract-form__field">
              <label for="contract-type" i18n="@@contract.type">Tipo *</label>
              <p-select
                inputId="contract-type"
                [options]="typeOptions"
                optionLabel="label"
                optionValue="value"
                formControlName="type"
              />
              @if (isEdit()) {
                <small class="dt-contract-form__hint" i18n="@@contract.type.immutable">
                  O tipo não pode ser alterado depois da criação.
                </small>
              }
            </div>

            @if (isMonthly()) {
              <div class="dt-contract-form__field">
                <label for="contract-monthly-minutes" i18n="@@contract.monthlyMinutes">
                  Horas por mês *
                </label>
                <dt-duration-input
                  inputId="contract-monthly-minutes"
                  formControlName="monthlyMinutes"
                />
                @if (isInvalid('monthlyMinutes')) {
                  <small class="dt-contract-form__error" i18n="@@contract.monthlyMinutes.invalid">
                    Informe as horas mensais contratadas.
                  </small>
                }
              </div>
            }
          </div>

          <div class="dt-contract-form__row">
            <div class="dt-contract-form__field">
              <label for="contract-start-date" i18n="@@contract.startDate">Início *</label>
              <p-datepicker
                inputId="contract-start-date"
                formControlName="startDate"
                [dateFormat]="'dd/mm/yy'"
                [showIcon]="true"
              />
            </div>

            <div class="dt-contract-form__field">
              <label for="contract-end-date" i18n="@@contract.endDate">Término</label>
              <p-datepicker
                inputId="contract-end-date"
                formControlName="endDate"
                [dateFormat]="'dd/mm/yy'"
                [showIcon]="true"
              />
              @if (form.hasError('dateRange')) {
                <small class="dt-contract-form__error" i18n="@@contract.endDate.invalid">
                  A data final deve ser igual ou posterior à inicial.
                </small>
              }
            </div>

            <div class="dt-contract-form__field">
              <label for="contract-billing-day" i18n="@@contract.billingDay">
                Dia de faturamento *
              </label>
              <p-input-number
                inputId="contract-billing-day"
                formControlName="billingDay"
                [min]="1"
                [max]="28"
                [showButtons]="true"
              />
              <small class="dt-contract-form__hint" i18n="@@contract.billingDay.hint">
                De 1 a 28: os dias 29 a 31 não existem em todos os meses.
              </small>
            </div>
          </div>

          <div class="dt-contract-form__checkbox">
            <p-checkbox
              inputId="contract-prorate"
              formControlName="prorateFirstPeriod"
              [binary]="true"
            />
            <label for="contract-prorate" i18n="@@contract.prorate">
              Cobrar o primeiro período proporcionalmente aos dias
            </label>
          </div>
        </fieldset>

        <fieldset class="dt-contract-form__section">
          <legend i18n="@@contract.section.policies">Políticas</legend>

          <div class="dt-contract-form__row">
            <div class="dt-contract-form__field">
              <label for="contract-rollover" i18n="@@contract.rollover">Transporte de saldo</label>
              <p-select
                inputId="contract-rollover"
                [options]="rolloverOptions"
                optionLabel="label"
                optionValue="value"
                formControlName="rolloverPolicy"
              />
            </div>

            @if (isCapped()) {
              <div class="dt-contract-form__field">
                <label for="contract-rollover-cap" i18n="@@contract.rolloverCap">
                  Limite transportado *
                </label>
                <dt-duration-input
                  inputId="contract-rollover-cap"
                  formControlName="rolloverCapMinutes"
                />
                @if (isInvalid('rolloverCapMinutes')) {
                  <small class="dt-contract-form__error" i18n="@@contract.rolloverCap.required">
                    A política limitada exige o teto de horas transportadas.
                  </small>
                }
              </div>
            }

            <div class="dt-contract-form__field">
              <label for="contract-overage" i18n="@@contract.overage">Excedente</label>
              <p-select
                inputId="contract-overage"
                [options]="overageOptions"
                optionLabel="label"
                optionValue="value"
                formControlName="overagePolicy"
              />
            </div>
          </div>
        </fieldset>

        <fieldset class="dt-contract-form__section">
          <legend i18n="@@contract.section.notes">Observações</legend>
          <textarea
            id="contract-notes"
            pTextarea
            rows="3"
            formControlName="notes"
            maxlength="4000"
            i18n-aria-label="@@contract.section.notes"
            aria-label="Observações"
          ></textarea>
        </fieldset>

        <div class="dt-contract-form__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="cancel()"
          />
          <p-button type="submit" [label]="submitLabel()" [loading]="saving()" />
        </div>
      </form>

      <!-- FM-09: a prévia acompanha o preenchimento em tempo real. -->
      <aside class="dt-contract-form__preview">
        <dt-period-preview [periods]="preview()" />
        @if (isEdit()) {
          <p class="dt-contract-form__hint" i18n="@@contract.preview.editNote">
            Períodos já fechados nunca são alterados por uma edição (RN-207).
          </p>
        } @else {
          <p class="dt-contract-form__hint" i18n="@@contract.preview.draftNote">
            Nenhum período é criado agora: eles passam a existir quando o contrato é ativado.
          </p>
        }
      </aside>
    </div>
  `,
  styleUrl: './contract-form.page.scss',
})
export class ContractFormPage implements HasUnsavedChanges {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(ContractApi);
  private readonly clientLookup = inject(ClientLookupApi);
  private readonly router = inject(Router);

  readonly id = input<string | undefined>(undefined);

  protected readonly typeOptions = [
    { label: $localize`:@@contract.type.monthly:Horas mensais`, value: 'MONTHLY_HOURS' },
    { label: $localize`:@@contract.type.hourlyOpen:Por hora`, value: 'HOURLY_OPEN' },
  ];

  protected readonly rolloverOptions = [
    { label: $localize`:@@contract.rollover.none:Não transporta`, value: 'NONE' },
    { label: $localize`:@@contract.rollover.full:Transporta tudo`, value: 'FULL' },
    { label: $localize`:@@contract.rollover.capped:Transporta até um limite`, value: 'CAPPED' },
  ];

  protected readonly overageOptions = [
    { label: $localize`:@@contract.overage.block:Bloqueia o registro`, value: 'BLOCK' },
    { label: $localize`:@@contract.overage.warn:Permite com aviso`, value: 'WARN' },
    { label: $localize`:@@contract.overage.allow:Permite e cobra`, value: 'ALLOW_BILLABLE' },
  ];

  protected readonly form = this.formBuilder.group({
    clientId: this.formBuilder.control('', [Validators.required]),
    code: this.formBuilder.control(''),
    name: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(2),
      Validators.maxLength(150),
    ]),
    type: this.formBuilder.control<ContractType>('MONTHLY_HOURS'),
    monthlyMinutes: this.formBuilder.control<number | null>(null),
    startDate: this.formBuilder.control<Date | null>(new Date(), [Validators.required]),
    endDate: this.formBuilder.control<Date | null>(null),
    billingDay: this.formBuilder.control(1, [Validators.min(1), Validators.max(28)]),
    prorateFirstPeriod: this.formBuilder.control(true),
    rolloverPolicy: this.formBuilder.control<RolloverPolicy>('NONE'),
    rolloverCapMinutes: this.formBuilder.control<number | null>(null),
    overagePolicy: this.formBuilder.control<OveragePolicy>('WARN'),
    notes: this.formBuilder.control(''),
  });

  private readonly _clients = signal<readonly ClientOption[]>([]);
  private readonly _preview = signal<readonly PeriodPreviewItem[]>([]);
  private readonly _loaded = signal<Contract | null>(null);
  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  /** `p-select` exige um array mutável; a cópia mantém o Signal do store imutável (ST-01). */
  protected readonly clients = computed(() => [...this._clients()]);
  protected readonly preview = this._preview.asReadonly();
  protected readonly saving = this._saving.asReadonly();

  protected readonly isEdit = computed(() => this.id() !== undefined);

  protected readonly title = computed(() =>
    this.isEdit()
      ? $localize`:@@contract.form.editTitle:Editar contrato`
      : $localize`:@@contract.form.newTitle:Novo contrato`,
  );

  protected readonly submitLabel = computed(() =>
    this.isEdit()
      ? $localize`:@@action.saveChanges:Salvar alterações`
      : $localize`:@@contract.form.create:Criar contrato`,
  );

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  private readonly typeValue = signal<ContractType>('MONTHLY_HOURS');
  private readonly rolloverValue = signal<RolloverPolicy>('NONE');

  /** INV-CTR-02/03: só `MONTHLY_HOURS` tem horas mensais e transporte de saldo. */
  protected readonly isMonthly = computed(() => this.typeValue() === 'MONTHLY_HOURS');
  protected readonly isCapped = computed(() => this.rolloverValue() === 'CAPPED');

  constructor() {
    void this.loadClients();

    this.form.controls.type.valueChanges.subscribe((type) => {
      this.typeValue.set(type);
      this.applyTypeCoherence(type);
    });
    this.form.controls.rolloverPolicy.valueChanges.subscribe((policy) =>
      this.rolloverValue.set(policy),
    );

    // FM-09: a prévia acompanha o formulário, com espera para não chamar a API a cada tecla.
    this.form.valueChanges.pipe(debounceTime(PREVIEW_DEBOUNCE_MS)).subscribe(() => {
      void this.refreshPreview();
    });

    effect(() => {
      const id = this.id();
      if (id !== undefined && this._loaded()?.id !== id) {
        void this.load(id);
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this._saved();
  }

  private async loadClients(): Promise<void> {
    try {
      this._clients.set(await firstValueFrom(this.clientLookup.search()));
    } catch {
      // A lista vazia já comunica que não há cliente selecionável; o erro de carga do seletor não
      // deve ocupar a área de erro do formulário, reservada à falha do que a pessoa fez.
      this._clients.set([]);
    }
  }

  /**
   * INV-CTR-03: `HOURLY_OPEN` não aceita horas mensais nem transporte.
   *
   * Os campos são limpos ao trocar o tipo, e não apenas ocultados: um valor invisível continuaria no
   * `getRawValue()` e o servidor recusaria o contrato por incoerência de tipo.
   */
  private applyTypeCoherence(type: ContractType): void {
    if (type === 'HOURLY_OPEN') {
      this.form.controls.monthlyMinutes.setValue(null, { emitEvent: false });
      this.form.controls.rolloverPolicy.setValue('NONE', { emitEvent: false });
      this.form.controls.rolloverCapMinutes.setValue(null, { emitEvent: false });
      this.rolloverValue.set('NONE');
    }
  }

  private async load(id: string): Promise<void> {
    try {
      const contract = await firstValueFrom(this.api.getById(id));
      this._loaded.set(contract);
      this.typeValue.set(contract.type);
      this.rolloverValue.set(contract.rolloverPolicy);
      this.form.patchValue(
        {
          clientId: contract.client.id,
          code: contract.code,
          name: contract.name,
          type: contract.type,
          monthlyMinutes: contract.monthlyMinutes ?? null,
          startDate: parseDate(contract.startDate),
          endDate: contract.endDate === undefined ? null : parseDate(contract.endDate),
          billingDay: contract.billingDay,
          prorateFirstPeriod: contract.prorateFirstPeriod,
          rolloverPolicy: contract.rolloverPolicy,
          rolloverCapMinutes: contract.rolloverCapMinutes ?? null,
          overagePolicy: contract.overagePolicy,
          notes: contract.notes ?? '',
        },
        { emitEvent: false },
      );
      // RN-206: imutáveis depois de criados — visíveis, porém não editáveis.
      this.form.controls.clientId.disable({ emitEvent: false });
      this.form.controls.type.disable({ emitEvent: false });
      this.form.controls.startDate.disable({ emitEvent: false });
      this.form.controls.code.disable({ emitEvent: false });
      this.form.markAsPristine();
      this._preview.set(contract.periodsPreview);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  /** A prévia exige tipo, início e dia de faturamento; sem eles não há o que projetar. */
  private async refreshPreview(): Promise<void> {
    const value = this.form.getRawValue();
    if (value.startDate === null || value.billingDay < 1 || value.billingDay > 28) {
      return;
    }
    if (value.type === 'MONTHLY_HOURS' && (value.monthlyMinutes ?? 0) <= 0) {
      return;
    }
    try {
      const result = await firstValueFrom(
        this.api.previewPeriods({
          type: value.type,
          monthlyMinutes: value.monthlyMinutes ?? undefined,
          startDate: toIsoDate(value.startDate),
          endDate: value.endDate === null ? undefined : toIsoDate(value.endDate),
          billingDay: value.billingDay,
          prorateFirstPeriod: value.prorateFirstPeriod,
          periodsCount: 3,
        }),
      );
      this._preview.set(result.periodsPreview);
    } catch {
      // Prévia é auxílio: uma falha aqui não pode impedir o preenchimento nem ocupar a área de erro.
      this._preview.set([]);
    }
  }

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    if (field === 'monthlyMinutes') {
      return (
        this._submitted() && this.isMonthly() && (this.form.controls.monthlyMinutes.value ?? 0) <= 0
      );
    }
    if (field === 'rolloverCapMinutes') {
      return (
        this._submitted() &&
        this.isCapped() &&
        (this.form.controls.rolloverCapMinutes.value ?? 0) <= 0
      );
    }
    return control.invalid && (control.touched || this._submitted());
  }

  protected serverError(field: keyof typeof this.form.controls): string | null {
    const error: unknown = this.form.controls[field].errors?.['server'];
    return typeof error === 'string' ? error : null;
  }

  protected async cancel(): Promise<void> {
    await this.router.navigate(['/contracts']);
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    if (
      this.form.invalid ||
      this.isInvalid('monthlyMinutes') ||
      this.isInvalid('rolloverCapMinutes')
    ) {
      this.focusFirstInvalidField();
      return;
    }

    const value = this.form.getRawValue();
    this._saving.set(true);
    try {
      const loaded = this._loaded();
      const contract =
        loaded === null
          ? await firstValueFrom(
              this.api.create({
                clientId: value.clientId,
                code: value.code === '' ? undefined : value.code,
                name: value.name,
                type: value.type,
                monthlyMinutes: value.monthlyMinutes ?? undefined,
                startDate: toIsoDate(value.startDate!),
                endDate: value.endDate === null ? undefined : toIsoDate(value.endDate),
                billingDay: value.billingDay,
                prorateFirstPeriod: value.prorateFirstPeriod,
                rolloverPolicy: value.rolloverPolicy,
                rolloverCapMinutes: value.rolloverCapMinutes ?? undefined,
                overagePolicy: value.overagePolicy,
                notes: value.notes === '' ? undefined : value.notes,
              }),
            )
          : await firstValueFrom(
              this.api.update(loaded.id, {
                name: value.name,
                monthlyMinutes: value.monthlyMinutes ?? undefined,
                endDate: value.endDate === null ? undefined : toIsoDate(value.endDate),
                billingDay: value.billingDay,
                rolloverPolicy: value.rolloverPolicy,
                rolloverCapMinutes: value.rolloverCapMinutes ?? undefined,
                overagePolicy: value.overagePolicy,
                notes: value.notes === '' ? undefined : value.notes,
                version: loaded.version,
              }),
            );
      this._saved.set(true);
      await this.router.navigate(['/contracts', contract.id]);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
        this.applyFieldErrors(error);
      }
    } finally {
      this._saving.set(false);
    }
  }

  /** FR-070 / FM-06: o erro do servidor vai para o campo que o originou. */
  private applyFieldErrors(problem: ProblemDetail): void {
    if (problem.code === 'DEVTIME-2206') {
      this.form.controls.code.setErrors({
        server: $localize`:@@contract.code.duplicated:Já existe um contrato com este código.`,
      });
      return;
    }
    for (const fieldError of problem.errors ?? []) {
      this.form.get(fieldError.field)?.setErrors({ server: fieldError.message });
    }
  }

  private focusFirstInvalidField(): void {
    const order: readonly [keyof typeof this.form.controls, string][] = [
      ['clientId', 'contract-client'],
      ['name', 'contract-name'],
      ['monthlyMinutes', 'contract-monthly-minutes'],
      ['startDate', 'contract-start-date'],
      ['billingDay', 'contract-billing-day'],
      ['rolloverCapMinutes', 'contract-rollover-cap'],
    ];
    const first = order.find(([field]) => this.isInvalid(field));
    if (first !== undefined) {
      document.getElementById(first[1])?.focus();
    }
  }
}

function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * `LocalDate` do backend para `Date` local.
 *
 * `new Date('2026-07-01')` é interpretado como UTC e, a oeste de Greenwich, exibe 30/06. Os
 * componentes são montados explicitamente para que a data mostrada seja a data gravada.
 */
function parseDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1);
}
