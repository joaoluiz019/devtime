import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { SettingsApi } from '../data/settings.api';
import { Tag } from '../data/settings.model';

/**
 * Etiquetas — P31, layout L9.
 *
 * A contagem de uso fica visível porque é o que decide o destino da etiqueta: uma com zero uso é
 * lixo de digitação; uma com trinta é vocabulário da equipe. Excluir remove o vínculo dos tickets —
 * o número diz de quantos.
 */
@Component({
  selector: 'dt-tag-settings-page',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, InputTextModule, MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.tags">Etiquetas</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.tags.subtitle">
      Vocabulário livre para agrupar tickets e registros.
    </p>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (tags().length === 0) {
      <p class="dt-setting__hint" i18n="@@settings.tags.empty">
        Nenhuma etiqueta criada. Elas também nascem ao digitar no formulário de ticket.
      </p>
    } @else {
      <ul class="dt-setting__list" role="list">
        @for (tag of tags(); track tag.id) {
          <li class="dt-setting__item">
            <span class="dt-setting__item-name">
              <span class="dt-setting__swatch" [style.background-color]="tag.color"></span>
              {{ tag.name }}
              <span class="dt-setting__meta">{{ usageLabel(tag) }}</span>
            </span>

            <span class="dt-setting__item-actions">
              <p-button
                icon="pi pi-pencil"
                severity="secondary"
                [text]="true"
                i18n-ariaLabel="@@action.edit"
                ariaLabel="Editar"
                (onClick)="openEdit(tag)"
              />
              <p-button
                icon="pi pi-trash"
                severity="danger"
                [text]="true"
                i18n-ariaLabel="@@action.delete"
                ariaLabel="Excluir"
                (onClick)="openDelete(tag)"
              />
            </span>
          </li>
        }
      </ul>
    }

    <p-button
      i18n-label="@@settings.tags.new"
      label="Nova etiqueta"
      icon="pi pi-plus"
      severity="secondary"
      [outlined]="true"
      (onClick)="openCreate()"
    />

    <p-dialog
      [visible]="formOpen()"
      (visibleChange)="formOpen.set($event)"
      [modal]="true"
      [style]="{ width: '26rem' }"
      [header]="editing() === null ? newTitle : editTitle"
    >
      <form class="dt-setting__form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-setting__field">
          <label for="tag-name" i18n="@@settings.tags.name">Nome *</label>
          <input
            id="tag-name"
            type="text"
            pInputText
            formControlName="name"
            maxlength="40"
            [attr.aria-invalid]="nameInvalid()"
          />
          @if (nameInvalid()) {
            <small class="dt-setting__error" i18n="@@settings.tags.name.invalid">
              Informe de 2 a 40 caracteres.
            </small>
          }
        </div>

        <div class="dt-setting__field">
          <label for="tag-color" i18n="@@settings.categories.color">Cor</label>
          <input id="tag-color" type="color" formControlName="color" />
        </div>

        <div class="dt-setting__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="formOpen.set(false)"
          />
          <p-button type="submit" i18n-label="@@action.save" label="Salvar" [loading]="saving()" />
        </div>
      </form>
    </p-dialog>

    <p-dialog
      [visible]="deleteOpen()"
      (visibleChange)="deleteOpen.set($event)"
      [modal]="true"
      [style]="{ width: '26rem' }"
      [header]="deleteTitle"
    >
      <div class="dt-setting__form">
        <!-- A consequência é dita antes: o vínculo some dos tickets, os tickets permanecem. -->
        <p>{{ deleteMessage() }}</p>

        <div class="dt-setting__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="deleteOpen.set(false)"
          />
          <!-- O rótulo repete o objeto: há um botão "Excluir" por linha atrás do diálogo, e os
               dois seriam indistinguíveis para quem navega por leitor de tela. -->
          <p-button
            i18n-label="@@settings.tags.delete.confirm"
            label="Excluir etiqueta"
            severity="danger"
            [loading]="saving()"
            (onClick)="confirmDelete()"
          />
        </div>
      </div>
    </p-dialog>
  `,
  styleUrl: './settings-form.scss',
})
export class TagSettingsPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(SettingsApi);

  protected readonly newTitle = $localize`:@@settings.tags.new:Nova etiqueta`;
  protected readonly editTitle = $localize`:@@settings.tags.edit:Editar etiqueta`;
  protected readonly deleteTitle = $localize`:@@settings.tags.delete:Excluir etiqueta`;

  protected readonly form = this.formBuilder.group({
    name: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(2),
      Validators.maxLength(40),
    ]),
    color: this.formBuilder.control('#0ea5e9'),
  });

  private readonly _tags = signal<readonly Tag[]>([]);
  private readonly _editing = signal<Tag | null>(null);
  private readonly _deleting = signal<Tag | null>(null);
  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly deleteOpen = signal(false);

  protected readonly tags = computed(() => this._tags());
  protected readonly editing = this._editing.asReadonly();
  protected readonly saving = this._saving.asReadonly();

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly deleteMessage = computed(() => {
    const tag = this._deleting();
    const usage = tag?.usageCount ?? 0;
    return usage === 0
      ? $localize`:@@settings.tags.delete.unused:Esta etiqueta não está em uso e será removida.`
      : $localize`:@@settings.tags.delete.used:A etiqueta será removida de ${usage}:count: registros. Os registros permanecem.`;
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this._tags.set(await firstValueFrom(this.api.tags()));
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  protected usageLabel(tag: Tag): string {
    return tag.usageCount === 1
      ? $localize`:@@settings.tags.usage.one:1 uso`
      : $localize`:@@settings.tags.usage:${tag.usageCount}:count: usos`;
  }

  protected nameInvalid(): boolean {
    const control = this.form.controls.name;
    return control.invalid && (control.touched || this._submitted());
  }

  protected openCreate(): void {
    this._editing.set(null);
    this._submitted.set(false);
    this.form.reset({ name: '', color: '#0ea5e9' });
    this.formOpen.set(true);
  }

  protected openEdit(tag: Tag): void {
    this._editing.set(tag);
    this._submitted.set(false);
    this.form.reset({ name: tag.name, color: tag.color });
    this.formOpen.set(true);
  }

  protected openDelete(tag: Tag): void {
    this._deleting.set(tag);
    this.deleteOpen.set(true);
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    if (this.form.invalid) {
      document.getElementById('tag-name')?.focus();
      return;
    }

    const value = this.form.getRawValue();
    const editing = this._editing();
    await this.run(async () => {
      if (editing === null) {
        await firstValueFrom(this.api.createTag({ name: value.name, color: value.color }));
      } else {
        await firstValueFrom(
          this.api.updateTag(editing.id, {
            name: value.name,
            color: value.color,
            version: editing.version,
          }),
        );
      }
      this.formOpen.set(false);
    });
  }

  protected async confirmDelete(): Promise<void> {
    const tag = this._deleting();
    if (tag === null) {
      return;
    }
    await this.run(async () => {
      await firstValueFrom(this.api.deleteTag(tag.id));
      this.deleteOpen.set(false);
    });
  }

  private async run(operation: () => Promise<unknown>): Promise<void> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation();
      await this.load();
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._saving.set(false);
    }
  }
}
