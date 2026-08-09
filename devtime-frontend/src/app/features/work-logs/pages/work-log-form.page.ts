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
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { debounceTime, firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { CategoryLookupApi, CategoryOption } from '../../../shared/data/category-lookup.api';
import { TicketLookupApi, TicketOption } from '../../../shared/data/ticket-lookup.api';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import {
  calculateWorkLog,
  combine,
  isPausedValid,
  MAX_GROSS_MINUTES,
  resolveEnd,
} from '../../../shared/utils/work-log-calculator';
import { BalancePreviewComponent } from '../components/balance-preview.component';
import { OverlapWarningComponent } from '../components/overlap-warning.component';
import { isoDate } from '../data/work-log-calendar.store';
import { WorkLogApi } from '../data/work-log.api';
import { WorkLog, WorkLogValidation } from '../data/work-log.model';

/** Espera antes de pedir a validação ao servidor; evita uma chamada por tecla. */
const VALIDATE_DEBOUNCE_MS = 400;

/**
 * Formulário de registro de horas — P23, layout L7 (T-008-31).
 *
 * O cálculo local (RN-110 a RN-113) mostra a duração **enquanto** se digita; a validação do servidor
 * (`POST /work-logs/validate`) traz o que só ele sabe: sobreposição com outras sessões (RN-102),
 * período de destino (RN-107) e efeito no saldo. As duas coisas convivem porque respondem a perguntas
 * diferentes — "quanto deu?" e "isto vai ser aceito?".
 *
 * RN-121: registro de período fechado não é editável. A tela não abre o formulário nesse caso.
 */
@Component({
  selector: 'dt-work-log-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    BalancePreviewComponent,
    ButtonModule,
    CheckboxModule,
    DurationPipe,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    OverlapWarningComponent,
    SelectModule,
    TextareaModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="dt-worklog-form__back">
      <a routerLink="/work-logs" i18n="@@action.back">Voltar</a>
    </nav>

    <h1 class="dt-worklog-form__title">{{ title() }}</h1>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (locked()) {
        <!-- RN-121: o período foi fechado; alterar aqui mudaria um saldo já entregue. -->
        <p-message severity="warn" styleClass="w-full mb-3">
          <span i18n="@@workLog.locked.message">
            Este registro pertence a um período fechado e não pode mais ser alterado.
          </span>
        </p-message>
      }
    </div>

    <div class="dt-worklog-form__layout">
      <form class="dt-worklog-form" [formGroup]="form" (ngSubmit)="submit()">
        <fieldset class="dt-worklog-form__section" [disabled]="locked()">
          <legend i18n="@@workLog.section.what">O que foi feito</legend>

          <div class="dt-worklog-form__field">
            <label for="worklog-ticket" i18n="@@workLog.ticket">Ticket *</label>
            <p-select
              inputId="worklog-ticket"
              [options]="tickets()"
              optionLabel="label"
              optionValue="id"
              formControlName="ticketId"
              [filter]="true"
              [filterBy]="'label'"
              i18n-placeholder="@@workLog.ticket.placeholder"
              placeholder="Selecione o ticket"
            />
            @if (isInvalid('ticketId')) {
              <small class="dt-worklog-form__error" i18n="@@workLog.ticket.required">
                Todo registro pertence a um ticket.
              </small>
            }
          </div>

          <div class="dt-worklog-form__field">
            <label for="worklog-description" i18n="@@workLog.description">Descrição *</label>
            <textarea
              id="worklog-description"
              pTextarea
              rows="3"
              formControlName="description"
              maxlength="2000"
              aria-required="true"
              [attr.aria-invalid]="isInvalid('description')"
            ></textarea>
            @if (isInvalid('description')) {
              <small class="dt-worklog-form__error" i18n="@@workLog.description.invalid">
                Descreva o trabalho com 3 a 2.000 caracteres.
              </small>
            }
          </div>
        </fieldset>

        <fieldset class="dt-worklog-form__section" [disabled]="locked()">
          <legend i18n="@@workLog.section.when">Quando</legend>

          <div class="dt-worklog-form__row">
            <div class="dt-worklog-form__field">
              <label for="worklog-date" i18n="@@workLog.date">Data *</label>
              <input id="worklog-date" type="date" pInputText formControlName="workDate" />
            </div>

            <div class="dt-worklog-form__field">
              <label for="worklog-start" i18n="@@workLog.start">Início *</label>
              <input id="worklog-start" type="time" pInputText formControlName="startTime" />
            </div>

            <div class="dt-worklog-form__field">
              <label for="worklog-end" i18n="@@workLog.end">Fim *</label>
              <input id="worklog-end" type="time" pInputText formControlName="endTime" />
              @if (crossesMidnight()) {
                <!-- Sessão que passa da meia-noite: o fim vai para o dia seguinte. -->
                <small class="dt-worklog-form__hint" i18n="@@workLog.crossesMidnight">
                  O fim é no dia seguinte.
                </small>
              }
            </div>

            <div class="dt-worklog-form__field">
              <label for="worklog-paused" i18n="@@workLog.paused">Pausa (min)</label>
              <p-input-number
                inputId="worklog-paused"
                formControlName="pausedMinutes"
                [min]="0"
                [showButtons]="true"
              />
              @if (pausedInvalid()) {
                <small class="dt-worklog-form__error" i18n="@@workLog.paused.invalid">
                  A pausa precisa ser menor que a duração bruta.
                </small>
              }
            </div>
          </div>

          @if (tooLong()) {
            <small class="dt-worklog-form__error" i18n="@@workLog.tooLong">
              Um registro não pode passar de 24 horas. Divida em sessões.
            </small>
          }
        </fieldset>

        <fieldset class="dt-worklog-form__section" [disabled]="locked()">
          <legend i18n="@@workLog.section.classification">Classificação</legend>

          <div class="dt-worklog-form__row">
            <div class="dt-worklog-form__field">
              <label for="worklog-category" i18n="@@workLog.category">Categoria</label>
              <p-select
                inputId="worklog-category"
                [options]="categories()"
                optionLabel="name"
                optionValue="id"
                formControlName="categoryId"
                [showClear]="true"
                i18n-placeholder="@@workLog.category.placeholder"
                placeholder="Padrão do ticket"
                (onChange)="applyCategoryDefault($event.value)"
              />
            </div>

            <div class="dt-worklog-form__checkbox">
              <p-checkbox inputId="worklog-billable" formControlName="billable" [binary]="true" />
              <label for="worklog-billable" i18n="@@workLog.billable"> Faturável ao cliente </label>
            </div>
          </div>
        </fieldset>

        <div class="dt-worklog-form__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="cancel()"
          />
          <p-button
            type="submit"
            [label]="submitLabel()"
            [loading]="saving()"
            [disabled]="locked()"
          />
        </div>
      </form>

      <aside class="dt-worklog-form__side">
        <section class="dt-worklog-form__duration">
          <h2 class="dt-worklog-form__duration-title" i18n="@@workLog.duration.title">Duração</h2>
          <dl class="dt-worklog-form__duration-grid">
            <dt i18n="@@workLog.duration.gross">Bruto</dt>
            <dd>{{ calculation().grossMinutes | duration }}</dd>
            <dt i18n="@@workLog.duration.paused">Pausa</dt>
            <dd>{{ calculation().pausedMinutes | duration }}</dd>
            <dt i18n="@@workLog.duration.net">Líquido</dt>
            <dd>
              <strong>{{ calculation().netMinutes | duration }}</strong>
            </dd>
          </dl>
          @if (rounded()) {
            <!-- OB-05: bruto e arredondado lado a lado quando divergem. -->
            <p class="dt-worklog-form__hint" i18n="@@workLog.duration.rounded">
              Arredondado para baixo a partir de
              {{ calculation().netMinutesBeforeRounding | duration }}.
            </p>
          }
        </section>

        <dt-overlap-warning [conflicts]="validation()?.conflicts ?? []" />

        <dt-balance-preview
          [preview]="validation()?.balancePreview"
          [warnings]="validation()?.warnings ?? []"
        />
      </aside>
    </div>
  `,
  styleUrl: './work-log-form.page.scss',
})
export class WorkLogFormPage implements HasUnsavedChanges {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(WorkLogApi);
  private readonly ticketLookup = inject(TicketLookupApi);
  private readonly categoryLookup = inject(CategoryLookupApi);
  private readonly router = inject(Router);

  readonly id = input<string | undefined>(undefined);

  protected readonly form = this.formBuilder.group({
    ticketId: this.formBuilder.control('', [Validators.required]),
    description: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(2000),
    ]),
    workDate: this.formBuilder.control(isoDate(new Date()), [Validators.required]),
    startTime: this.formBuilder.control('09:00', [Validators.required]),
    endTime: this.formBuilder.control('10:00', [Validators.required]),
    pausedMinutes: this.formBuilder.control(0, [Validators.min(0)]),
    categoryId: this.formBuilder.control<string | null>(null),
    billable: this.formBuilder.control(true),
  });

  private readonly _tickets = signal<readonly TicketOption[]>([]);
  private readonly _categories = signal<readonly CategoryOption[]>([]);
  private readonly _loaded = signal<WorkLog | null>(null);
  private readonly _validation = signal<WorkLogValidation | null>(null);
  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _formValue = signal(this.form.getRawValue());

  protected readonly tickets = computed(() => [...this._tickets()]);
  protected readonly categories = computed(() => [...this._categories()]);
  protected readonly validation = this._validation.asReadonly();
  protected readonly saving = this._saving.asReadonly();

  protected readonly isEdit = computed(() => this.id() !== undefined);

  protected readonly locked = computed(() => this._loaded()?.lockedAt !== undefined);

  protected readonly title = computed(() =>
    this.isEdit()
      ? $localize`:@@workLog.form.editTitle:Editar registro de horas`
      : $localize`:@@workLog.form.newTitle:Lançar horas`,
  );

  protected readonly submitLabel = computed(() =>
    this.isEdit()
      ? $localize`:@@action.saveChanges:Salvar alterações`
      : $localize`:@@workLog.form.create:Registrar horas`,
  );

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /** Intervalo resolvido: o fim pode cair no dia seguinte (sessão que atravessa a meia-noite). */
  private readonly range = computed(() => {
    const value = this._formValue();
    const workDate = parseIsoDate(value.workDate);
    const start = parseTime(value.startTime);
    const end = parseTime(value.endTime);
    if (workDate === null || start === null || end === null) {
      return null;
    }
    const startedAt = combine(workDate, start);
    return { startedAt, endedAt: resolveEnd(workDate, startedAt, end) };
  });

  protected readonly crossesMidnight = computed(() => {
    const range = this.range();
    return range !== null && range.endedAt.getDate() !== range.startedAt.getDate();
  });

  /** RN-110 a RN-113 no cliente: o número aparece enquanto se digita. */
  protected readonly calculation = computed(() => {
    const range = this.range();
    const value = this._formValue();
    return calculateWorkLog({
      startedAt: range?.startedAt ?? null,
      endedAt: range?.endedAt ?? null,
      pausedMinutes: value.pausedMinutes,
      billable: value.billable,
    });
  });

  protected readonly rounded = computed(
    () => this.calculation().netMinutes !== this.calculation().netMinutesBeforeRounding,
  );

  protected readonly pausedInvalid = computed(
    () =>
      !isPausedValid(this.calculation().grossMinutes, this._formValue().pausedMinutes) &&
      this.calculation().grossMinutes > 0,
  );

  /** RN-103: registro de mais de 24 horas é sempre erro de digitação. */
  protected readonly tooLong = computed(() => this.calculation().grossMinutes > MAX_GROSS_MINUTES);

  constructor() {
    void this.loadOptions();

    this.form.valueChanges.subscribe(() => this._formValue.set(this.form.getRawValue()));

    this.form.valueChanges.pipe(debounceTime(VALIDATE_DEBOUNCE_MS)).subscribe(() => {
      void this.revalidate();
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

  private async loadOptions(): Promise<void> {
    const [tickets, categories] = await Promise.all([
      firstValueFrom(this.ticketLookup.search()).catch(() => []),
      firstValueFrom(this.categoryLookup.search()).catch(() => []),
    ]);
    this._tickets.set(tickets);
    this._categories.set(categories);
  }

  private async load(id: string): Promise<void> {
    try {
      const entry = await firstValueFrom(this.api.getById(id));
      this._loaded.set(entry);
      const start = new Date(entry.startedAt);
      const end = new Date(entry.endedAt);
      this.form.patchValue(
        {
          ticketId: entry.ticket.id,
          description: entry.description,
          workDate: entry.workDate,
          startTime: formatTime(start),
          endTime: formatTime(end),
          pausedMinutes: entry.pausedMinutes,
          categoryId: entry.category?.id ?? null,
          billable: entry.billable,
        },
        { emitEvent: false },
      );
      this._formValue.set(this.form.getRawValue());
      this.form.markAsPristine();
      if (entry.lockedAt !== undefined) {
        this.form.disable({ emitEvent: false });
      }
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  /** RN-112: a categoria define o padrão de faturável; a escolha manual continua vencendo. */
  protected applyCategoryDefault(categoryId: string | null): void {
    const category = this._categories().find((candidate) => candidate.id === categoryId);
    if (category !== undefined && !this.form.controls.billable.dirty) {
      this.form.controls.billable.setValue(category.billableByDefault, { emitEvent: false });
    }
  }

  /**
   * Validação prévia no servidor.
   *
   * Só dispara com ticket e intervalo válidos: pedir validação de um formulário pela metade gastaria
   * requisição para receber os mesmos erros que a tela já mostra.
   */
  private async revalidate(): Promise<void> {
    const value = this.form.getRawValue();
    const range = this.range();
    if (value.ticketId === '' || range === null || this.calculation().netMinutes <= 0) {
      this._validation.set(null);
      return;
    }
    try {
      this._validation.set(
        await firstValueFrom(
          this.api.validate({
            ticketId: value.ticketId,
            startedAt: range.startedAt.toISOString(),
            endedAt: range.endedAt.toISOString(),
            pausedMinutes: value.pausedMinutes,
            description: value.description,
            categoryId: value.categoryId ?? undefined,
            billable: value.billable,
            excludeWorkLogId: this._loaded()?.id,
          }),
        ),
      );
    } catch {
      // A validação é auxílio: sua falha não pode impedir o envio, que o servidor validará de novo.
      this._validation.set(null);
    }
  }

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this._submitted());
  }

  protected async cancel(): Promise<void> {
    await this.router.navigate(['/work-logs']);
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    const range = this.range();
    if (this.form.invalid || range === null || this.pausedInvalid() || this.tooLong()) {
      this.focusFirstInvalidField();
      return;
    }

    const value = this.form.getRawValue();
    this._saving.set(true);
    try {
      const loaded = this._loaded();
      const saved =
        loaded === null
          ? await firstValueFrom(
              this.api.create({
                ticketId: value.ticketId,
                startedAt: range.startedAt.toISOString(),
                endedAt: range.endedAt.toISOString(),
                pausedMinutes: value.pausedMinutes,
                description: value.description,
                categoryId: value.categoryId ?? undefined,
                billable: value.billable,
              }),
            )
          : await firstValueFrom(
              this.api.update(loaded.id, {
                ticketId: value.ticketId,
                startedAt: range.startedAt.toISOString(),
                endedAt: range.endedAt.toISOString(),
                pausedMinutes: value.pausedMinutes,
                description: value.description,
                categoryId: value.categoryId ?? loaded.category?.id ?? '',
                billable: value.billable,
                version: loaded.version,
              }),
            );
      this._saved.set(true);
      await this.router.navigate(['/work-logs'], {
        queryParams: { dateFrom: saved.workLog.workDate, dateTo: saved.workLog.workDate },
      });
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
        for (const fieldError of error.errors ?? []) {
          this.form.get(fieldError.field)?.setErrors({ server: fieldError.message });
        }
      }
    } finally {
      this._saving.set(false);
    }
  }

  private focusFirstInvalidField(): void {
    const order: readonly [keyof typeof this.form.controls, string][] = [
      ['ticketId', 'worklog-ticket'],
      ['description', 'worklog-description'],
      ['workDate', 'worklog-date'],
      ['startTime', 'worklog-start'],
      ['endTime', 'worklog-end'],
    ];
    const first = order.find(([field]) => this.form.controls[field].invalid);
    document.getElementById(first?.[1] ?? 'worklog-paused')?.focus();
  }
}

function parseIsoDate(value: string): Date | null {
  const [year, month, day] = value.split('-').map(Number);
  if (year === undefined || month === undefined || day === undefined) {
    return null;
  }
  return new Date(year, month - 1, day);
}

function parseTime(value: string): Date | null {
  const [hour, minute] = value.split(':').map(Number);
  if (hour === undefined || minute === undefined) {
    return null;
  }
  return new Date(2000, 0, 1, hour, minute);
}

function formatTime(date: Date): string {
  return `${`${date.getHours()}`.padStart(2, '0')}:${`${date.getMinutes()}`.padStart(2, '0')}`;
}
