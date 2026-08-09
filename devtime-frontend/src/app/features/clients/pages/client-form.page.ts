import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import {
  AddressFormComponent,
  buildAddressForm,
} from '../../../shared/components/address-form/address-form.component';
import { DocumentInputComponent } from '../../../shared/components/document-input/document-input.component';
import { documentValidator } from '../../../shared/utils/document';
import { ClientApi } from '../data/client.api';
import { Address, Client, DocumentType } from '../data/client.model';

/**
 * Formulário de cliente — P12, layout L7 (T-003-19).
 *
 * Cria e edita na mesma tela: os campos são os mesmos e o que muda é o verbo HTTP. Duas telas
 * divergiriam na primeira validação nova.
 *
 * RN-004: a edição envia `version`. Conflito responde `409 DEVTIME-2004` e a mensagem manda recarregar
 * — sobrescrever o trabalho de outra pessoa em silêncio é pior do que pedir a repetição do próprio.
 */
@Component({
  selector: 'dt-client-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AddressFormComponent,
    ButtonModule,
    DocumentInputComponent,
    InputTextModule,
    MessageModule,
    SelectModule,
    TextareaModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="dt-client-form__back">
      <a routerLink="/clients" i18n="@@action.back">Voltar</a>
    </nav>

    <h1 class="dt-client-form__title">{{ title() }}</h1>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    <form class="dt-client-form" [formGroup]="form" (ngSubmit)="submit()">
      <!-- FM-01 / FM-02: seções com título e no máximo 7 campos. -->
      <fieldset class="dt-client-form__section">
        <legend i18n="@@client.section.identification">Identificação</legend>

        <div class="dt-client-form__field">
          <label for="client-name" i18n="@@client.name">Nome *</label>
          <input
            id="client-name"
            type="text"
            pInputText
            formControlName="name"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('name')"
            [attr.aria-describedby]="isInvalid('name') ? 'client-name-error' : null"
          />
          @if (isInvalid('name')) {
            <small id="client-name-error" class="dt-client-form__error">
              @if (serverError('name') !== null) {
                {{ serverError('name') }}
              } @else {
                <ng-container i18n="@@client.name.invalid"
                  >Informe o nome, com 2 a 150 caracteres.</ng-container
                >
              }
            </small>
          }
        </div>

        <div class="dt-client-form__field">
          <label for="client-legal-name" i18n="@@client.legalName">Razão social</label>
          <input id="client-legal-name" type="text" pInputText formControlName="legalName" />
        </div>

        <div class="dt-client-form__row">
          <div class="dt-client-form__field">
            <label for="client-document-type" i18n="@@client.documentType">Tipo de documento</label>
            <p-select
              inputId="client-document-type"
              [options]="documentTypes"
              optionLabel="label"
              optionValue="value"
              formControlName="documentType"
              [showClear]="true"
            />
          </div>

          <div class="dt-client-form__field">
            <label for="client-document" i18n="@@client.document">Documento</label>
            <dt-document-input
              inputId="client-document"
              formControlName="documentNumber"
              [documentType]="documentType()"
              [invalid]="isInvalid('documentNumber')"
              describedBy="client-document-error"
            />
            @if (isInvalid('documentNumber')) {
              <small id="client-document-error" class="dt-client-form__error">
                @if (serverError('documentNumber') !== null) {
                  {{ serverError('documentNumber') }}
                } @else {
                  <ng-container i18n="@@client.document.invalid"
                    >Documento inválido: confira os dígitos.</ng-container
                  >
                }
              </small>
            }
          </div>
        </div>
      </fieldset>

      <fieldset class="dt-client-form__section">
        <legend i18n="@@client.section.contact">Contato</legend>

        <div class="dt-client-form__row">
          <div class="dt-client-form__field">
            <label for="client-email" i18n="@@client.email">E-mail</label>
            <input
              id="client-email"
              type="email"
              pInputText
              formControlName="email"
              autocomplete="email"
            />
            @if (isInvalid('email')) {
              <small class="dt-client-form__error" i18n="@@client.email.invalid">
                Informe um e-mail válido.
              </small>
            }
          </div>

          <div class="dt-client-form__field">
            <label for="client-phone" i18n="@@client.phone">Telefone</label>
            <input id="client-phone" type="tel" pInputText formControlName="phone" />
            @if (isInvalid('phone')) {
              <small class="dt-client-form__error" i18n="@@client.phone.invalid">
                Use de 8 a 20 dígitos, com o código do país opcional.
              </small>
            }
          </div>
        </div>

        <div class="dt-client-form__field">
          <label for="client-website" i18n="@@client.website">Site</label>
          <input
            id="client-website"
            type="url"
            pInputText
            formControlName="website"
            i18n-placeholder="@@client.website.placeholder"
            placeholder="https://"
          />
          @if (isInvalid('website')) {
            <small class="dt-client-form__error" i18n="@@client.website.invalid">
              O endereço precisa começar com http:// ou https://.
            </small>
          }
        </div>
      </fieldset>

      <fieldset class="dt-client-form__section">
        <dt-address-form [group]="addressGroup" [prefix]="'client'" />
      </fieldset>

      <fieldset class="dt-client-form__section">
        <legend i18n="@@client.section.notes">Observações</legend>
        <textarea
          id="client-notes"
          pTextarea
          rows="4"
          formControlName="notes"
          maxlength="4000"
          i18n-aria-label="@@client.section.notes"
          aria-label="Observações"
        ></textarea>
      </fieldset>

      <!-- FM-04 / FM-05: barra de ações no rodapé, primária à direita. -->
      <div class="dt-client-form__actions">
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
  styleUrl: './client-form.page.scss',
})
export class ClientFormPage implements HasUnsavedChanges {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(ClientApi);
  private readonly router = inject(Router);

  /** Vem do parâmetro de rota (`withComponentInputBinding`); ausente em `/clients/new`. */
  readonly id = input<string | undefined>(undefined);

  protected readonly addressGroup = buildAddressForm(this.formBuilder);

  protected readonly documentTypes = [
    { label: 'CPF', value: 'CPF' },
    { label: 'CNPJ', value: 'CNPJ' },
    { label: $localize`:@@client.documentType.other:Outro`, value: 'OTHER' },
  ];

  /**
   * Declarado fora do grupo porque o validador do número precisa consultá-lo.
   *
   * Lê-lo por `this.form.controls.documentType` faria o tipo do formulário depender de si mesmo, e o
   * TypeScript perderia a inferência do grupo inteiro.
   *
   * Nasce em `CPF` porque o contrato exige o tipo sempre que há número (`isDocumentTypeConsistent`):
   * começar vazio faria o primeiro documento digitado ser recusado pelo servidor por falta de um
   * campo que a pessoa nem percebeu existir.
   */
  private readonly documentTypeControl = this.formBuilder.control<DocumentType | null>('CPF');

  protected readonly form = this.formBuilder.group({
    name: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(2),
      Validators.maxLength(150),
    ]),
    legalName: this.formBuilder.control('', [Validators.maxLength(200)]),
    documentType: this.documentTypeControl,
    documentNumber: this.formBuilder.control('', [
      documentValidator(() => this.documentTypeControl.value),
    ]),
    email: this.formBuilder.control('', [Validators.email, Validators.maxLength(255)]),
    phone: this.formBuilder.control('', [Validators.pattern(/^\+?[0-9]{8,20}$/)]),
    website: this.formBuilder.control('', [Validators.pattern(/^https?:\/\/.+/)]),
    notes: this.formBuilder.control('', [Validators.maxLength(4000)]),
    address: this.addressGroup,
  });

  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _loaded = signal<Client | null>(null);
  private readonly _saved = signal(false);

  protected readonly saving = this._saving.asReadonly();

  /** O tipo é lido como Signal para que a máscara do documento reaja à troca. */
  protected readonly documentType = computed<DocumentType>(() => this.documentTypeValue() ?? 'CPF');

  private readonly documentTypeValue = toSignal(this.documentTypeControl.valueChanges, {
    initialValue: this.documentTypeControl.value,
  });

  protected readonly isEdit = computed(() => this.id() !== undefined);

  protected readonly title = computed(() =>
    this.isEdit()
      ? $localize`:@@client.form.editTitle:Editar cliente`
      : $localize`:@@client.form.newTitle:Novo cliente`,
  );

  protected readonly submitLabel = computed(() =>
    this.isEdit()
      ? $localize`:@@action.saveChanges:Salvar alterações`
      : $localize`:@@client.form.create:Criar cliente`,
  );

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    // O parâmetro de rota chega **depois** da construção do componente: lê-lo aqui direto devolveria
    // sempre `undefined` e a edição abriria com o formulário em branco.
    effect(() => {
      const id = this.id();
      if (id !== undefined && this._loaded()?.id !== id) {
        void this.load(id);
      }
    });
  }

  /** FM-08: o guard pergunta antes de descartar o que foi digitado. */
  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this._saved();
  }

  private async load(id: string): Promise<void> {
    try {
      const client = await firstValueFrom(this.api.getById(id));
      this._loaded.set(client);
      this.form.patchValue({
        name: client.name,
        legalName: client.legalName ?? '',
        documentType: client.documentType ?? null,
        documentNumber: client.documentNumber ?? '',
        email: client.email ?? '',
        phone: client.phone ?? '',
        website: client.website ?? '',
        notes: client.notes ?? '',
        address: {
          street: client.address?.street ?? '',
          number: client.address?.number ?? '',
          complement: client.address?.complement ?? '',
          district: client.address?.district ?? '',
          city: client.address?.city ?? '',
          state: client.address?.state ?? '',
          postalCode: client.address?.postalCode ?? '',
          country: client.address?.country ?? 'BR',
        },
      });
      // `patchValue` não suja o formulário, então o guard não dispara por dados carregados.
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
    await this.router.navigate(['/clients']);
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    if (this.form.invalid) {
      this.focusFirstInvalidField();
      return;
    }

    const value = this.form.getRawValue();
    const payload = {
      name: value.name,
      legalName: blankToUndefined(value.legalName),
      documentType: value.documentType ?? undefined,
      documentNumber: blankToUndefined(value.documentNumber),
      email: blankToUndefined(value.email),
      phone: blankToUndefined(value.phone),
      website: blankToUndefined(value.website),
      notes: blankToUndefined(value.notes),
      address: toAddress(value.address),
    };

    this._saving.set(true);
    try {
      const loaded = this._loaded();
      const client =
        loaded === null
          ? await firstValueFrom(this.api.create(payload))
          : await firstValueFrom(
              this.api.update(loaded.id, { ...payload, version: loaded.version }),
            );
      this._saved.set(true);
      await this.router.navigate(['/clients', client.id]);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
        this.applyFieldErrors(error);
      }
    } finally {
      this._saving.set(false);
    }
  }

  /**
   * FR-070 / FM-06: o erro do servidor vai para o campo que o causou.
   *
   * Os conflitos de unicidade (RN-403/RN-404) chegam sem `errors[]`, por serem conflito de recurso e
   * não falha de formato. Ainda assim pertencem a um campo específico: sem este mapeamento, a pessoa
   * lê "já existe um cliente com este nome" e precisa adivinhar qual dos campos repetir.
   */
  private applyFieldErrors(problem: ProblemDetail): void {
    if (problem.code === 'DEVTIME-2404') {
      this.form.controls.name.setErrors({
        server: $localize`:@@client.name.duplicated:Já existe um cliente com este nome.`,
      });
      return;
    }
    if (problem.code === 'DEVTIME-2403') {
      this.form.controls.documentNumber.setErrors({
        server: $localize`:@@client.document.duplicated:Já existe um cliente com este documento.`,
      });
      return;
    }
    if (problem.code === 'DEVTIME-2402') {
      this.form.controls.documentNumber.setErrors({
        server: $localize`:@@client.document.invalidServer:CPF/CNPJ inválido.`,
      });
      return;
    }
    for (const fieldError of problem.errors ?? []) {
      this.form.get(fieldError.field)?.setErrors({ server: fieldError.message });
    }
  }

  /** FM-07 / FR-105: foco no primeiro campo inválido. */
  private focusFirstInvalidField(): void {
    const order: readonly [keyof typeof this.form.controls, string][] = [
      ['name', 'client-name'],
      ['legalName', 'client-legal-name'],
      ['documentNumber', 'client-document'],
      ['email', 'client-email'],
      ['phone', 'client-phone'],
      ['website', 'client-website'],
      ['notes', 'client-notes'],
    ];
    const first = order.find(([field]) => this.form.controls[field].invalid);
    if (first !== undefined) {
      document.getElementById(first[1])?.focus();
    }
  }
}

function blankToUndefined(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

/**
 * Endereço vazio não viaja.
 *
 * O `PUT` substitui o recurso; enviar um objeto com oito strings vazias grava um endereço em branco
 * onde não havia endereço nenhum.
 */
function toAddress(value: Record<string, string>): Address | undefined {
  const entries = Object.entries(value).filter(
    ([field, fieldValue]) => field !== 'country' && fieldValue.trim() !== '',
  );
  if (entries.length === 0) {
    return undefined;
  }
  return {
    ...Object.fromEntries(entries.map(([field, fieldValue]) => [field, fieldValue.trim()])),
    country: value['country']?.trim() || undefined,
  };
}
