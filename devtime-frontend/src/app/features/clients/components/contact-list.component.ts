import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { Contact, ContactRequest } from '../data/client.model';

/** Telefone aceito pelo backend: `^\+?[0-9]{8,20}$`. */
const PHONE_PATTERN = /^\+?[0-9]{8,20}$/;

/**
 * Contatos do cliente — `dt-contact-list` (T-003-21).
 *
 * RN-406 / INV-CON-01: no máximo um contato principal. A promoção de um novo principal **rebaixa** o
 * anterior no servidor (`PrimaryContactPolicy`); esta tela apenas marca a caixa e mostra o resultado
 * depois do recarregamento. Rebaixar localmente adiantaria uma decisão que pode falhar.
 */
@Component({
  selector: 'dt-contact-list',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    CheckboxModule,
    DialogModule,
    InputTextModule,
    TagModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-contacts">
      <header class="dt-contacts__header">
        <h2 class="dt-contacts__title" i18n="@@contacts.title">Contatos</h2>
        @if (editable()) {
          <p-button
            i18n-label="@@contacts.add"
            label="Adicionar contato"
            icon="pi pi-plus"
            severity="secondary"
            [outlined]="true"
            (onClick)="openCreate()"
          />
        }
      </header>

      @if (contacts().length === 0) {
        <p class="dt-contacts__empty" i18n="@@contacts.empty">
          Nenhum contato cadastrado. O contato principal é quem recebe os relatórios do cliente.
        </p>
      } @else {
        <ul class="dt-contacts__list" role="list">
          @for (contact of contacts(); track contact.id) {
            <li class="dt-contacts__item">
              <span class="dt-contacts__body">
                <span class="dt-contacts__name">
                  {{ contact.name }}
                  @if (contact.isPrimary) {
                    <p-tag i18n-value="@@contacts.primary" value="Principal" severity="info" />
                  }
                  @if (contact.receivesReports) {
                    <p-tag
                      i18n-value="@@contacts.receivesReports"
                      value="Recebe relatórios"
                      severity="secondary"
                    />
                  }
                </span>
                @if (contact.role) {
                  <span class="dt-contacts__meta">{{ contact.role }}</span>
                }
                <span class="dt-contacts__meta">
                  {{ contact.email || '' }}
                  @if (contact.email && contact.phone) {
                    <span aria-hidden="true">·</span>
                  }
                  {{ contact.phone || '' }}
                </span>
              </span>

              @if (editable()) {
                <span class="dt-contacts__actions">
                  <p-button
                    icon="pi pi-pencil"
                    severity="secondary"
                    [text]="true"
                    i18n-ariaLabel="@@contacts.edit"
                    ariaLabel="Editar contato"
                    (onClick)="openEdit(contact)"
                  />
                  <p-button
                    icon="pi pi-trash"
                    severity="danger"
                    [text]="true"
                    i18n-ariaLabel="@@contacts.remove"
                    ariaLabel="Remover contato"
                    (onClick)="removed.emit(contact.id)"
                  />
                </span>
              }
            </li>
          }
        </ul>
      }
    </section>

    <p-dialog
      [visible]="dialogOpen()"
      (visibleChange)="onVisibleChange($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      [header]="editing() === null ? newContactTitle : editContactTitle"
    >
      <form class="dt-contacts__form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-contacts__field">
          <label for="contact-name" i18n="@@contacts.name">Nome *</label>
          <input
            id="contact-name"
            type="text"
            pInputText
            formControlName="name"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('name')"
          />
          @if (isInvalid('name')) {
            <small class="dt-contacts__error" i18n="@@contacts.name.invalid">
              Informe o nome, com ao menos 2 caracteres.
            </small>
          }
        </div>

        <div class="dt-contacts__field">
          <label for="contact-email" i18n="@@contacts.email">E-mail</label>
          <input id="contact-email" type="email" pInputText formControlName="email" />
          @if (isInvalid('email')) {
            <small class="dt-contacts__error" i18n="@@contacts.email.invalid">
              Informe um e-mail válido.
            </small>
          }
        </div>

        <div class="dt-contacts__field">
          <label for="contact-phone" i18n="@@contacts.phone">Telefone</label>
          <input id="contact-phone" type="tel" pInputText formControlName="phone" />
          @if (isInvalid('phone')) {
            <small class="dt-contacts__error" i18n="@@contacts.phone.invalid">
              Use de 8 a 20 dígitos, com o código do país opcional.
            </small>
          }
        </div>

        <div class="dt-contacts__field">
          <label for="contact-role" i18n="@@contacts.role">Cargo</label>
          <input id="contact-role" type="text" pInputText formControlName="role" />
        </div>

        <div class="dt-contacts__checkbox">
          <p-checkbox inputId="contact-primary" formControlName="isPrimary" [binary]="true" />
          <label for="contact-primary" i18n="@@contacts.isPrimary">
            Contato principal do cliente
          </label>
        </div>

        <div class="dt-contacts__checkbox">
          <p-checkbox inputId="contact-reports" formControlName="receivesReports" [binary]="true" />
          <label for="contact-reports" i18n="@@contacts.receives">Recebe os relatórios</label>
        </div>

        <div class="dt-contacts__form-actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="close()"
          />
          <p-button type="submit" i18n-label="@@action.save" label="Salvar" [loading]="saving()" />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-contacts__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--dt-space-3);
    }

    .dt-contacts__title {
      margin: 0;
      font-size: var(--dt-text-lg);
    }

    .dt-contacts__empty {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-contacts__list {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      margin: var(--dt-space-3) 0 0;
      padding: 0;
      list-style: none;
    }

    .dt-contacts__item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--dt-space-3);
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
    }

    .dt-contacts__body {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-contacts__name {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-contacts__meta {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-contacts__form {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-contacts__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-contacts__field label {
      font-size: var(--dt-text-sm);
      font-weight: 500;
    }

    .dt-contacts__field input {
      width: 100%;
    }

    .dt-contacts__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-contacts__checkbox {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
      font-size: var(--dt-text-sm);
    }

    .dt-contacts__form-actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class ContactListComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly contacts = input.required<readonly Contact[]>();
  readonly editable = input(false);
  readonly saving = input(false);

  readonly created = output<ContactRequest>();
  readonly updated = output<{ id: string; request: ContactRequest }>();
  readonly removed = output<string>();

  protected readonly newContactTitle = $localize`:@@contacts.dialog.new:Novo contato`;
  protected readonly editContactTitle = $localize`:@@contacts.dialog.edit:Editar contato`;

  protected readonly form = this.formBuilder.group({
    name: this.formBuilder.control('', [Validators.required, Validators.minLength(2)]),
    email: this.formBuilder.control('', [Validators.email]),
    phone: this.formBuilder.control('', [Validators.pattern(PHONE_PATTERN)]),
    role: this.formBuilder.control(''),
    isPrimary: this.formBuilder.control(false),
    receivesReports: this.formBuilder.control(false),
  });

  private readonly _dialogOpen = signal(false);
  private readonly _editing = signal<Contact | null>(null);
  private readonly _submitted = signal(false);

  protected readonly dialogOpen = this._dialogOpen.asReadonly();
  protected readonly editing = this._editing.asReadonly();

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this._submitted());
  }

  protected openCreate(): void {
    this._editing.set(null);
    this._submitted.set(false);
    this.form.reset({
      name: '',
      email: '',
      phone: '',
      role: '',
      isPrimary: false,
      receivesReports: false,
    });
    this._dialogOpen.set(true);
  }

  protected openEdit(contact: Contact): void {
    this._editing.set(contact);
    this._submitted.set(false);
    this.form.reset({
      name: contact.name,
      email: contact.email ?? '',
      phone: contact.phone ?? '',
      role: contact.role ?? '',
      isPrimary: contact.isPrimary,
      receivesReports: contact.receivesReports,
    });
    this._dialogOpen.set(true);
  }

  protected close(): void {
    this._dialogOpen.set(false);
  }

  protected onVisibleChange(visible: boolean): void {
    this._dialogOpen.set(visible);
  }

  protected submit(): void {
    this._submitted.set(true);
    if (this.form.invalid) {
      document.getElementById('contact-name')?.focus();
      return;
    }

    const value = this.form.getRawValue();
    // Campo em branco não viaja: o backend distingue ausente de vazio ao validar formato.
    const request: ContactRequest = {
      name: value.name,
      email: value.email === '' ? undefined : value.email,
      phone: value.phone === '' ? undefined : value.phone,
      role: value.role === '' ? undefined : value.role,
      isPrimary: value.isPrimary,
      receivesReports: value.receivesReports,
    };

    const editing = this._editing();
    if (editing === null) {
      this.created.emit(request);
    } else {
      this.updated.emit({ id: editing.id, request });
    }
    this._dialogOpen.set(false);
  }
}
