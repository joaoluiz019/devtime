import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  FormsModule,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { SettingsApi } from '../data/settings.api';
import { Category } from '../data/settings.model';

/**
 * Categorias — P30, layout L9.
 *
 * RN-503: categoria de sistema é renomeável e inativável, **nunca** excluível. A ação de excluir
 * some para elas e o selo explica o motivo — um botão que responde `409` ensina o usuário a
 * desconfiar da interface.
 *
 * RN-505: excluir uma categoria com registros exige escolher a substituta, e as horas migram para
 * ela. O diálogo pede a substituta antes de tentar, porque a alternativa é um erro no meio da ação.
 */
@Component({
  selector: 'dt-category-settings-page',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    ButtonModule,
    CheckboxModule,
    DialogModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TagModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.categories">Categorias</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.categories.subtitle">
      Classificam o trabalho registrado e definem o padrão de faturável.
    </p>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    <ul class="dt-setting__list" role="list">
      @for (category of categories(); track category.id) {
        <li class="dt-setting__item" [class.dt-setting__item--inactive]="!category.active">
          <span class="dt-setting__item-name">
            <span class="dt-setting__swatch" [style.background-color]="category.color"></span>
            {{ category.name }}
            @if (category.isSystem) {
              <p-tag
                i18n-value="@@settings.categories.system"
                value="Padrão"
                severity="secondary"
              />
            }
            @if (!category.active) {
              <p-tag i18n-value="@@settings.categories.inactive" value="Inativa" severity="warn" />
            }
            @if (category.billableByDefault) {
              <span class="dt-setting__meta" i18n="@@settings.categories.billable">faturável</span>
            }
          </span>

          <span class="dt-setting__item-actions">
            <p-button
              icon="pi pi-pencil"
              severity="secondary"
              [text]="true"
              i18n-ariaLabel="@@action.edit"
              ariaLabel="Editar"
              (onClick)="openEdit(category)"
            />
            <p-button
              [label]="category.active ? deactivateLabel : activateLabel"
              severity="secondary"
              [text]="true"
              (onClick)="toggleActive(category)"
            />
            @if (!category.isSystem) {
              <p-button
                icon="pi pi-trash"
                severity="danger"
                [text]="true"
                i18n-ariaLabel="@@action.delete"
                ariaLabel="Excluir"
                (onClick)="openDelete(category)"
              />
            }
          </span>
        </li>
      }
    </ul>

    <p-button
      i18n-label="@@settings.categories.new"
      label="Nova categoria"
      icon="pi pi-plus"
      severity="secondary"
      [outlined]="true"
      (onClick)="openCreate()"
    />

    <p-dialog
      [visible]="formOpen()"
      (visibleChange)="formOpen.set($event)"
      [modal]="true"
      [style]="{ width: '28rem' }"
      [header]="editing() === null ? newTitle : editTitle"
    >
      <form class="dt-setting__form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-setting__field">
          <label for="category-name" i18n="@@settings.categories.name">Nome *</label>
          <input
            id="category-name"
            type="text"
            pInputText
            formControlName="name"
            [attr.aria-invalid]="nameInvalid()"
          />
          @if (nameInvalid()) {
            <small class="dt-setting__error" i18n="@@settings.categories.name.invalid">
              Informe de 2 a 60 caracteres.
            </small>
          }
        </div>

        <div class="dt-setting__field">
          <label for="category-color" i18n="@@settings.categories.color">Cor</label>
          <input id="category-color" type="color" formControlName="color" />
        </div>

        <div class="dt-setting__check">
          <p-checkbox
            inputId="category-billable"
            formControlName="billableByDefault"
            [binary]="true"
          />
          <label for="category-billable" i18n="@@settings.categories.billableDefault">
            Faturável por padrão
          </label>
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
      [style]="{ width: '28rem' }"
      [header]="deleteTitle"
    >
      <div class="dt-setting__form">
        <p i18n="@@settings.categories.delete.text">
          Os registros desta categoria precisam de um destino. Escolha para onde eles vão.
        </p>

        <div class="dt-setting__field">
          <label for="category-replacement" i18n="@@settings.categories.delete.replacement">
            Categoria substituta
          </label>
          <p-select
            inputId="category-replacement"
            [options]="replacements()"
            optionLabel="name"
            optionValue="id"
            [ngModel]="replacementId()"
            [ngModelOptions]="{ standalone: true }"
            [showClear]="true"
            i18n-placeholder="@@settings.categories.delete.none"
            placeholder="Sem registros vinculados"
            (onChange)="replacementId.set($event.value)"
          />
        </div>

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
            i18n-label="@@settings.categories.delete.confirm"
            label="Excluir categoria"
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
export class CategorySettingsPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(SettingsApi);

  protected readonly newTitle = $localize`:@@settings.categories.new:Nova categoria`;
  protected readonly editTitle = $localize`:@@settings.categories.edit:Editar categoria`;
  protected readonly deleteTitle = $localize`:@@settings.categories.delete:Excluir categoria`;
  protected readonly activateLabel = $localize`:@@settings.categories.activate:Ativar`;
  protected readonly deactivateLabel = $localize`:@@settings.categories.deactivate:Inativar`;

  protected readonly form = this.formBuilder.group({
    name: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(2),
      Validators.maxLength(60),
    ]),
    color: this.formBuilder.control('#6366f1'),
    billableByDefault: this.formBuilder.control(true),
  });

  private readonly _categories = signal<readonly Category[]>([]);
  private readonly _editing = signal<Category | null>(null);
  private readonly _deleting = signal<Category | null>(null);
  private readonly _saving = signal(false);
  private readonly _submitted = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly formOpen = signal(false);
  protected readonly deleteOpen = signal(false);
  protected readonly replacementId = signal<string | null>(null);

  protected readonly categories = computed(() => this._categories());
  protected readonly editing = this._editing.asReadonly();
  protected readonly saving = this._saving.asReadonly();

  /** A substituta precisa estar ativa e ser diferente da excluída (`DEVTIME-2605`). */
  protected readonly replacements = computed(() =>
    this._categories().filter(
      (category) => category.active && category.id !== this._deleting()?.id,
    ),
  );

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this._categories.set(await firstValueFrom(this.api.categories()));
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    }
  }

  protected nameInvalid(): boolean {
    const control = this.form.controls.name;
    return control.invalid && (control.touched || this._submitted());
  }

  protected openCreate(): void {
    this._editing.set(null);
    this._submitted.set(false);
    this.form.reset({ name: '', color: '#6366f1', billableByDefault: true });
    this.formOpen.set(true);
  }

  protected openEdit(category: Category): void {
    this._editing.set(category);
    this._submitted.set(false);
    this.form.reset({
      name: category.name,
      color: category.color,
      billableByDefault: category.billableByDefault,
    });
    this.formOpen.set(true);
  }

  protected openDelete(category: Category): void {
    this._deleting.set(category);
    this.replacementId.set(null);
    this.deleteOpen.set(true);
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    if (this.form.invalid) {
      document.getElementById('category-name')?.focus();
      return;
    }

    const value = this.form.getRawValue();
    const editing = this._editing();
    await this.run(async () => {
      if (editing === null) {
        await firstValueFrom(
          this.api.createCategory({
            name: value.name,
            color: value.color,
            billableByDefault: value.billableByDefault,
          }),
        );
      } else {
        await firstValueFrom(
          this.api.updateCategory(editing.id, {
            name: value.name,
            color: value.color,
            billableByDefault: value.billableByDefault,
            active: editing.active,
            version: editing.version,
          }),
        );
      }
      this.formOpen.set(false);
    });
  }

  /**
   * Inativar é a alternativa à exclusão, inclusive para categorias de sistema.
   *
   * CX-06 permite inativar todas: é decisão legítima de uma organização em pausa, e o produto avisa
   * no lançamento de horas (`DEVTIME-2104`) em vez de proibir aqui.
   */
  protected async toggleActive(category: Category): Promise<void> {
    await this.run(() =>
      firstValueFrom(
        this.api.updateCategory(category.id, {
          name: category.name,
          color: category.color,
          billableByDefault: category.billableByDefault,
          active: !category.active,
          version: category.version,
        }),
      ),
    );
  }

  protected async confirmDelete(): Promise<void> {
    const category = this._deleting();
    if (category === null) {
      return;
    }
    await this.run(async () => {
      await firstValueFrom(this.api.deleteCategory(category.id, this.replacementId() ?? undefined));
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
