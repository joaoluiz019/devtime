import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { DocumentType, formatDocument, onlyDigits } from '../../utils/document';

/**
 * Campo de CPF/CNPJ com máscara dinâmica — `dt-document-input` (T-003-17).
 *
 * **O valor do formulário é sempre só dígito** (CX-03). A máscara existe na tela, onde ajuda a
 * conferir o número; enviá-la ao servidor obrigaria o backend a normalizar o que o cliente sujou.
 *
 * A máscara acompanha o tipo escolhido no formulário: trocar de CPF para CNPJ reformata o que já foi
 * digitado, em vez de deixar um número com pontuação de outro documento.
 */
@Component({
  selector: 'dt-document-input',
  imports: [InputTextModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DocumentInputComponent),
      multi: true,
    },
  ],
  template: `
    <input
      [id]="inputId()"
      type="text"
      pInputText
      inputmode="numeric"
      autocomplete="off"
      [attr.aria-required]="required() ? 'true' : null"
      [attr.aria-invalid]="invalid()"
      [attr.aria-describedby]="describedBy()"
      [value]="display()"
      [disabled]="disabled()"
      (input)="onInput($event)"
      (blur)="onBlur()"
    />
  `,
  styles: `
    input {
      width: 100%;
    }
  `,
})
export class DocumentInputComponent implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly documentType = input<DocumentType>('CPF');
  readonly required = input(false);
  readonly invalid = input(false);
  readonly describedBy = input<string | null>(null);

  private readonly raw = signal('');
  protected readonly disabled = signal(false);

  /**
   * O que o usuário vê; o formulário guarda `raw`, sem pontuação.
   *
   * É derivado do tipo para que trocar CPF por CNPJ reformate o número já digitado — com um Signal
   * de escrita, a máscara antiga permaneceria na tela até a próxima tecla.
   */
  protected readonly display = computed(() => formatDocument(this.documentType(), this.raw()));

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.raw.set(value === null ? '' : onlyDigits(value));
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
    const typed = (event.target as HTMLInputElement).value;
    // `OTHER` é documento estrangeiro: não há formato conhecido, então nada é removido.
    const value = this.documentType() === 'OTHER' ? typed : onlyDigits(typed);
    this.raw.set(value);
    this.onChange(value);
  }

  protected onBlur(): void {
    this.onTouched();
  }
}
