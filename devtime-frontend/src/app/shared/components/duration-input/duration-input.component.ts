import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { formatDuration, parseDuration } from './duration.parser';

/**
 * Campo de duração com entrada flexível (components.md §6.2, FR-112).
 *
 * **Justificativa de componente customizado (FR-125):** nenhum componente PrimeNG aceita `1,5h`,
 * `1h30` e `90` como a mesma duração. `p-inputNumber` impõe um formato único, e é justamente o
 * formato único que cria o atrito que PR-01 identifica como risco de adoção.
 *
 * Implementa `ControlValueAccessor` para participar de Reactive Forms tipados (FR-100): o modelo
 * exposto ao formulário é sempre `number | null` em minutos inteiros (DI-04), nunca o texto digitado.
 */
@Component({
  selector: 'dt-duration-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DurationInputComponent),
      multi: true,
    },
  ],
  template: `
    <input
      class="dt-duration-input dt-duration"
      type="text"
      inputmode="text"
      autocomplete="off"
      [id]="inputId()"
      [attr.aria-describedby]="describedBy()"
      [attr.aria-required]="required() ? 'true' : null"
      [attr.aria-invalid]="invalid() ? 'true' : null"
      [disabled]="disabled()"
      [value]="text()"
      (input)="onInput($event)"
      (blur)="onBlur()"
      (keydown.arrowup)="step($event, 15)"
      (keydown.arrowdown)="step($event, -15)"
    />
  `,
  styles: `
    .dt-duration-input {
      width: 100%;
      min-height: var(--dt-touch-target-min);
      padding: var(--dt-space-2) var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-sm);
      background-color: var(--dt-surface-card);
      color: var(--dt-text-primary);
      font-family: var(--dt-font-mono);
      font-size: var(--dt-text-base);
      line-height: var(--dt-text-base-line);
    }

    .dt-duration-input[aria-invalid='true'] {
      border-color: var(--dt-color-danger);
    }

    .dt-duration-input:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  `,
})
export class DurationInputComponent implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly describedBy = input<string | null>(null);
  readonly required = input<boolean>(false);
  readonly invalid = input<boolean>(false);
  /** DI: ajustes de saldo debitam horas, e o débito é um valor negativo (components.md §6.2). */
  readonly allowNegative = input<boolean>(false);

  /** DI-02: o texto digitado é preservado mesmo quando não corresponde a um formato válido. */
  protected readonly text = signal('');
  protected readonly disabled = signal(false);

  private value: number | null = null;
  private onChange: (value: number | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: number | null): void {
    this.value = value;
    this.text.set(value === null ? '' : formatDuration(value));
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  protected onInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.text.set(raw);
    this.emit(parseDuration(raw));
  }

  /** DI-01: ao perder o foco, o valor válido é normalizado; o inválido permanece como digitado. */
  protected onBlur(): void {
    this.onTouched();
    if (this.value !== null) {
      this.text.set(formatDuration(this.value));
    }
  }

  /** DI-03: as setas ajustam em 15 minutos. */
  protected step(event: Event, delta: number): void {
    event.preventDefault();
    this.emit((this.value ?? 0) + delta);
    if (this.value !== null) {
      this.text.set(formatDuration(this.value));
    }
  }

  private emit(parsed: number | null): void {
    const normalized = parsed !== null && !this.allowNegative() && parsed < 0 ? null : parsed;
    this.value = normalized;
    this.onChange(normalized);
  }
}
