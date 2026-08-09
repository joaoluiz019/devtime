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
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { DurationInputComponent } from '../../../shared/components/duration-input/duration-input.component';
import { MarkdownEditorComponent } from '../../../shared/components/markdown/markdown-editor.component';
import { ticketPriorityLabel } from '../../../shared/components/ticket-badges/ticket-badges.component';
import { ContractLookupApi, ContractOption } from '../../../shared/data/contract-lookup.api';
import { MemberLookupApi, MemberOption } from '../../../shared/data/member-lookup.api';
import { TagLookupApi, TagOption } from '../../../shared/data/tag-lookup.api';
import { TicketApi } from '../data/ticket.api';
import { Ticket, TicketPriority, TicketType } from '../data/ticket.model';
import { ticketTypeLabel } from './ticket-list.page';

const TYPES: readonly TicketType[] = [
  'FEATURE',
  'BUG',
  'SUPPORT',
  'MEETING',
  'MAINTENANCE',
  'OTHER',
];

const PRIORITIES: readonly TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

/**
 * Formulário de ticket — P20, layout L7 (T-007-27).
 *
 * RN-302: o contrato é escolhido na criação e **não** muda por edição — para isso existe a ação de
 * mover, que tem regra própria (RN-305). Na edição o campo aparece desabilitado, com a explicação.
 *
 * A chave só existe depois de criado (o servidor a gera a partir do contrato e do número), então o
 * formulário mostra o prefixo do contrato escolhido como prévia — o suficiente para reconhecer o
 * ticket antes de salvar, sem inventar o número.
 */
@Component({
  selector: 'dt-ticket-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    DatePickerModule,
    DurationInputComponent,
    InputTextModule,
    MarkdownEditorComponent,
    MessageModule,
    MultiSelectModule,
    SelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="dt-ticket-form__back">
      <a routerLink="/tickets" i18n="@@action.back">Voltar</a>
    </nav>

    <h1 class="dt-ticket-form__title">{{ title() }}</h1>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    <form class="dt-ticket-form" [formGroup]="form" (ngSubmit)="submit()">
      <fieldset class="dt-ticket-form__section">
        <legend i18n="@@ticket.section.identification">Identificação</legend>

        <div class="dt-ticket-form__field">
          <label for="ticket-contract" i18n="@@ticket.contract">Contrato *</label>
          <p-select
            inputId="ticket-contract"
            [options]="contracts()"
            optionLabel="name"
            optionValue="id"
            formControlName="contractId"
            [filter]="true"
            [filterBy]="'name,code'"
            i18n-placeholder="@@ticket.contract.placeholder"
            placeholder="Selecione o contrato"
          />
          @if (isInvalid('contractId')) {
            <small class="dt-ticket-form__error" i18n="@@ticket.contract.required">
              Selecione o contrato ao qual o ticket pertence.
            </small>
          }
          @if (keyPreview() !== null) {
            <small class="dt-ticket-form__hint" i18n="@@ticket.keyPreview">
              A chave será {{ keyPreview() }}-N, gerada ao salvar.
            </small>
          }
          @if (isEdit()) {
            <small class="dt-ticket-form__hint" i18n="@@ticket.contract.immutable">
              O contrato não muda por edição: use a ação de mover, no detalhe do ticket.
            </small>
          }
        </div>

        <div class="dt-ticket-form__field">
          <label for="ticket-title" i18n="@@ticket.title">Título *</label>
          <input
            id="ticket-title"
            type="text"
            pInputText
            formControlName="title"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('title')"
          />
          @if (isInvalid('title')) {
            <small class="dt-ticket-form__error">
              @if (serverError('title') !== null) {
                {{ serverError('title') }}
              } @else {
                <ng-container i18n="@@ticket.title.invalid"
                  >Informe um título entre 3 e 200 caracteres.</ng-container
                >
              }
            </small>
          }
        </div>

        <div class="dt-ticket-form__field">
          <label for="ticket-description" i18n="@@ticket.description">Descrição</label>
          <dt-markdown-editor inputId="ticket-description" formControlName="description" />
        </div>
      </fieldset>

      <fieldset class="dt-ticket-form__section">
        <legend i18n="@@ticket.section.classification">Classificação</legend>

        <div class="dt-ticket-form__row">
          <div class="dt-ticket-form__field">
            <label for="ticket-type" i18n="@@ticket.type">Tipo</label>
            <p-select
              inputId="ticket-type"
              [options]="typeOptions"
              optionLabel="label"
              optionValue="value"
              formControlName="type"
            />
          </div>

          <div class="dt-ticket-form__field">
            <label for="ticket-priority" i18n="@@ticket.priority">Prioridade</label>
            <p-select
              inputId="ticket-priority"
              [options]="priorityOptions"
              optionLabel="label"
              optionValue="value"
              formControlName="priority"
            />
          </div>

          <div class="dt-ticket-form__field">
            <label for="ticket-assignee" i18n="@@ticket.assignee">Responsável</label>
            <p-select
              inputId="ticket-assignee"
              [options]="assignees()"
              optionLabel="name"
              optionValue="id"
              formControlName="assigneeId"
              [showClear]="true"
              i18n-placeholder="@@ticket.unassigned"
              placeholder="Sem responsável"
            />
          </div>
        </div>

        <div class="dt-ticket-form__row">
          <div class="dt-ticket-form__field">
            <label for="ticket-estimate" i18n="@@ticket.estimate">Estimativa</label>
            <dt-duration-input inputId="ticket-estimate" formControlName="estimatedMinutes" />
          </div>

          <div class="dt-ticket-form__field">
            <label for="ticket-due-date" i18n="@@ticket.dueDate">Prazo</label>
            <p-datepicker
              inputId="ticket-due-date"
              formControlName="dueDate"
              [dateFormat]="'dd/mm/yy'"
              [showIcon]="true"
            />
          </div>

          <div class="dt-ticket-form__field">
            <label for="ticket-tags" i18n="@@ticket.tags">Etiquetas</label>
            <p-multiselect
              inputId="ticket-tags"
              [options]="tags()"
              optionLabel="name"
              optionValue="id"
              formControlName="tagIds"
              [showClear]="true"
              i18n-placeholder="@@ticket.tags.placeholder"
              placeholder="Selecione etiquetas"
            />
            @if (tooManyTags()) {
              <small class="dt-ticket-form__error" i18n="@@ticket.tags.max">
                No máximo 10 etiquetas por ticket.
              </small>
            }
          </div>
        </div>
      </fieldset>

      <div class="dt-ticket-form__actions">
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
  `,
  styleUrl: './ticket-form.page.scss',
})
export class TicketFormPage implements HasUnsavedChanges {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(TicketApi);
  private readonly contractLookup = inject(ContractLookupApi);
  private readonly memberLookup = inject(MemberLookupApi);
  private readonly tagLookup = inject(TagLookupApi);
  private readonly router = inject(Router);

  readonly id = input<string | undefined>(undefined);

  protected readonly typeOptions = TYPES.map((type) => ({
    label: ticketTypeLabel(type),
    value: type,
  }));

  protected readonly priorityOptions = PRIORITIES.map((priority) => ({
    label: ticketPriorityLabel(priority),
    value: priority,
  }));

  protected readonly form = this.formBuilder.group({
    contractId: this.formBuilder.control('', [Validators.required]),
    title: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(200),
    ]),
    description: this.formBuilder.control(''),
    type: this.formBuilder.control<TicketType>('FEATURE'),
    priority: this.formBuilder.control<TicketPriority>('MEDIUM'),
    assigneeId: this.formBuilder.control<string | null>(null),
    estimatedMinutes: this.formBuilder.control<number | null>(null),
    dueDate: this.formBuilder.control<Date | null>(null),
    tagIds: this.formBuilder.control<string[]>([]),
  });

  private readonly _contracts = signal<readonly ContractOption[]>([]);
  private readonly _assignees = signal<readonly MemberOption[]>([]);
  private readonly _tags = signal<readonly TagOption[]>([]);
  private readonly _loaded = signal<Ticket | null>(null);
  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _saved = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _contractId = signal('');

  protected readonly contracts = computed(() => [...this._contracts()]);
  protected readonly assignees = computed(() => [...this._assignees()]);
  protected readonly tags = computed(() => [...this._tags()]);
  protected readonly saving = this._saving.asReadonly();

  protected readonly isEdit = computed(() => this.id() !== undefined);

  protected readonly title = computed(() =>
    this.isEdit()
      ? $localize`:@@ticket.form.editTitle:Editar ticket`
      : $localize`:@@ticket.form.newTitle:Novo ticket`,
  );

  protected readonly submitLabel = computed(() =>
    this.isEdit()
      ? $localize`:@@action.saveChanges:Salvar alterações`
      : $localize`:@@ticket.form.create:Criar ticket`,
  );

  /** Prévia da chave: o prefixo é o código do contrato; o número quem atribui é o servidor. */
  protected readonly keyPreview = computed(() => {
    const contractId = this._contractId();
    if (contractId === '') {
      return null;
    }
    return this._contracts().find((contract) => contract.id === contractId)?.code ?? null;
  });

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /** RN-313: o servidor recusa mais de 10 etiquetas; a tela avisa antes do envio. */
  protected readonly tooManyTags = computed(() => this.form.controls.tagIds.value.length > 10);

  constructor() {
    void this.loadOptions();

    this.form.controls.contractId.valueChanges.subscribe((value) => this._contractId.set(value));

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
    const [contracts, assignees, tags] = await Promise.all([
      firstValueFrom(this.contractLookup.search()).catch(() => []),
      firstValueFrom(this.memberLookup.search()).catch(() => []),
      firstValueFrom(this.tagLookup.autocomplete()).catch(() => []),
    ]);
    this._contracts.set(contracts);
    this._assignees.set(assignees);
    this._tags.set(tags);
  }

  private async load(id: string): Promise<void> {
    try {
      const ticket = await firstValueFrom(this.api.getById(id));
      this._loaded.set(ticket);
      this._contractId.set(ticket.contract.id);
      this.form.patchValue(
        {
          contractId: ticket.contract.id,
          title: ticket.title,
          description: ticket.description ?? '',
          type: ticket.type,
          priority: ticket.priority,
          assigneeId: ticket.assignee?.id ?? null,
          estimatedMinutes: ticket.estimatedMinutes ?? null,
          dueDate: ticket.dueDate === undefined ? null : parseDate(ticket.dueDate),
          tagIds: ticket.tags.map((tag) => tag.id),
        },
        { emitEvent: false },
      );
      // RN-302: o contrato não muda por edição.
      this.form.controls.contractId.disable({ emitEvent: false });
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

  protected serverError(field: keyof typeof this.form.controls): string | null {
    const error: unknown = this.form.controls[field].errors?.['server'];
    return typeof error === 'string' ? error : null;
  }

  protected async cancel(): Promise<void> {
    await this.router.navigate(['/tickets']);
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    if (this.form.invalid || this.tooManyTags()) {
      this.focusFirstInvalidField();
      return;
    }

    const value = this.form.getRawValue();
    this._saving.set(true);
    try {
      const loaded = this._loaded();
      const ticket =
        loaded === null
          ? await firstValueFrom(
              this.api.create({
                contractId: value.contractId,
                title: value.title,
                description: value.description === '' ? undefined : value.description,
                type: value.type,
                priority: value.priority,
                assigneeId: value.assigneeId ?? undefined,
                estimatedMinutes: value.estimatedMinutes ?? undefined,
                dueDate: value.dueDate === null ? undefined : toIsoDate(value.dueDate),
                tagIds: value.tagIds,
              }),
            )
          : await firstValueFrom(
              this.api.update(loaded.id, {
                title: value.title,
                description: value.description === '' ? undefined : value.description,
                type: value.type,
                priority: value.priority,
                estimatedMinutes: value.estimatedMinutes ?? undefined,
                dueDate: value.dueDate === null ? undefined : toIsoDate(value.dueDate),
                tagIds: value.tagIds,
                version: loaded.version,
              }),
            );
      this._saved.set(true);
      await this.router.navigate(['/tickets', ticket.id]);
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
      ['contractId', 'ticket-contract'],
      ['title', 'ticket-title'],
    ];
    const first = order.find(([field]) => this.form.controls[field].invalid);
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

function parseDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1);
}
