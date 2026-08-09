import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { TextareaModule } from 'primeng/textarea';
import { MarkdownViewComponent } from './markdown-view.component';

/**
 * Editor de Markdown com prévia — `dt-markdown-editor` (T-007-25).
 *
 * Escrever e visualizar são abas, não painéis lado a lado: em `xs` não há largura para dois, e uma
 * prévia espremida é pior que nenhuma. A prévia usa o mesmo renderizador da exibição, então o que a
 * pessoa confere aqui é exatamente o que os outros verão.
 */
@Component({
  selector: 'dt-markdown-editor',
  imports: [TextareaModule, MarkdownViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MarkdownEditorComponent),
      multi: true,
    },
  ],
  template: `
    <div class="dt-md-editor">
      <div class="dt-md-editor__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          class="dt-md-editor__tab"
          [class.dt-md-editor__tab--active]="!previewing()"
          [attr.aria-selected]="!previewing()"
          (click)="previewing.set(false)"
          i18n="@@markdown.write"
        >
          Escrever
        </button>
        <button
          type="button"
          role="tab"
          class="dt-md-editor__tab"
          [class.dt-md-editor__tab--active]="previewing()"
          [attr.aria-selected]="previewing()"
          (click)="previewing.set(true)"
          i18n="@@markdown.preview"
        >
          Visualizar
        </button>
      </div>

      @if (previewing()) {
        <div class="dt-md-editor__preview">
          @if (value() === '') {
            <p class="dt-md-editor__empty" i18n="@@markdown.empty">Nada para visualizar.</p>
          } @else {
            <dt-markdown-view [source]="value()" />
          }
        </div>
      } @else {
        <textarea
          [id]="inputId()"
          pTextarea
          [rows]="rows()"
          [value]="value()"
          [disabled]="disabled()"
          [attr.maxlength]="maxLength()"
          [attr.aria-describedby]="inputId() + '-hint'"
          (input)="onInput($event)"
          (blur)="onBlur()"
        ></textarea>
      }

      <small [id]="inputId() + '-hint'" class="dt-md-editor__hint" i18n="@@markdown.hint">
        Aceita negrito, itálico, trecho de código, listas, títulos e links em Markdown.
      </small>
    </div>
  `,
  styles: `
    .dt-md-editor {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-md-editor__tabs {
      display: flex;
      gap: var(--dt-space-1);
    }

    .dt-md-editor__tab {
      padding: var(--dt-space-1) var(--dt-space-2);
      border: 1px solid transparent;
      border-radius: var(--dt-radius-sm);
      background: none;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
      cursor: pointer;
    }

    .dt-md-editor__tab--active {
      border-color: var(--dt-border);
      color: var(--dt-text-primary);
    }

    .dt-md-editor textarea {
      width: 100%;
      font-family: inherit;
    }

    .dt-md-editor__preview {
      min-height: 8rem;
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
    }

    .dt-md-editor__empty,
    .dt-md-editor__hint {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class MarkdownEditorComponent implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly rows = input(8);
  readonly maxLength = input(20000);

  protected readonly value = signal('');
  protected readonly disabled = signal(false);
  protected readonly previewing = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.value.set(value);
    this.onChange(value);
  }

  protected onBlur(): void {
    this.onTouched();
  }
}
