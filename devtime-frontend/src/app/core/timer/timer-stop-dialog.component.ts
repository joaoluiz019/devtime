import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';
import { ElapsedTimePipe } from '../../shared/pipes/elapsed-time.pipe';

/**
 * Encerramento do cronômetro — `dt-timer-stop-dialog` (§21.2 de `specs/009-timer/spec.md`).
 *
 * TB-03: "Parar" **abre este diálogo**, nunca encerra direto. RN-158 exige descrição, e um
 * encerramento imediato falharia com `DEVTIME-2105` na cara de quem só queria parar de contar.
 *
 * TB-05 / RN-160: o erro do servidor aparece **aqui dentro**, com o cronômetro ainda ativo por trás.
 * Fechar o diálogo e mostrar um toast genérico faria a pessoa acreditar que perdeu o tempo
 * trabalhado — que é exatamente o que RN-160 existe para impedir.
 */
@Component({
  selector: 'dt-timer-stop-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    MessageModule,
    TextareaModule,
    ElapsedTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visibleChange.emit($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      [header]="title"
    >
      <form class="dt-timer-stop" [formGroup]="form" (ngSubmit)="submit()">
        <p class="dt-timer-stop__summary">
          <span i18n="@@timer.stop.summary">Serão registrados</span>
          <strong class="dt-timer-stop__elapsed">{{ elapsed() | elapsedTime }}</strong>
          <span>{{ ticketLabel() }}</span>
        </p>

        <div aria-live="polite">
          @if (errorMessage() !== null) {
            <!-- RN-160: o cronômetro continua ativo; a mensagem diz o que corrigir. -->
            <p-message severity="error" [text]="errorMessage()!" styleClass="w-full" />
          }
        </div>

        <div class="dt-timer-stop__field">
          <label for="timer-stop-description" i18n="@@timer.stop.description">
            O que foi feito
          </label>
          <textarea
            id="timer-stop-description"
            pTextarea
            rows="4"
            formControlName="description"
            maxlength="2000"
            aria-required="true"
            [attr.aria-invalid]="invalid()"
          ></textarea>
          @if (invalid()) {
            <small class="dt-timer-stop__error" i18n="@@timer.stop.description.invalid">
              A descrição precisa ter de 3 a 2.000 caracteres.
            </small>
          }
          <small class="dt-timer-stop__hint" i18n="@@timer.stop.description.hint">
            É o texto que o cliente lê no relatório do período.
          </small>
        </div>

        <div class="dt-timer-stop__actions">
          <p-button
            type="button"
            i18n-label="@@timer.stop.keep"
            label="Continuar contando"
            severity="secondary"
            [text]="true"
            (onClick)="visibleChange.emit(false)"
          />
          <p-button
            type="submit"
            i18n-label="@@timer.stop.submit"
            label="Encerrar e registrar"
            icon="pi pi-stop-circle"
            [loading]="busy()"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-timer-stop {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-timer-stop__summary {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: var(--dt-space-2);
      margin: 0;
      font-size: var(--dt-text-sm);
    }

    .dt-timer-stop__elapsed {
      font-family: var(--dt-font-mono);
      font-size: var(--dt-text-lg);
    }

    .dt-timer-stop__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-timer-stop__field textarea {
      width: 100%;
    }

    .dt-timer-stop__hint {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-timer-stop__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-timer-stop__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class TimerStopDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly elapsed = input.required<number>();
  readonly ticketLabel = input('');
  readonly busy = input(false);
  /** Mensagem já traduzida pelo mapa de códigos; o diálogo não conhece `ProblemDetail`. */
  readonly errorMessage = input<string | null>(null);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<string>();

  protected readonly title = $localize`:@@timer.stop.title:Encerrar cronômetro`;

  protected readonly form = this.formBuilder.group({
    description: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(2000),
    ]),
  });

  private readonly submitted = signal(false);

  /** A descrição que já estava no cronômetro entra preenchida, quando existir. */
  setDescription(description: string | null): void {
    this.form.controls.description.setValue(description ?? '');
  }

  protected invalid(): boolean {
    const control = this.form.controls.description;
    return control.invalid && (control.touched || this.submitted());
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      document.getElementById('timer-stop-description')?.focus();
      return;
    }
    this.confirmed.emit(this.form.getRawValue().description.trim());
  }
}
