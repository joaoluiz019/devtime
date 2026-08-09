import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';

/** Controles do endereço, espelhando `AddressRequest` do backend. */
export interface AddressFormControls {
  street: string;
  number: string;
  complement: string;
  district: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

/**
 * Cria o subgrupo de endereço com os limites do contrato (`AddressRequest`).
 *
 * A fábrica vive junto do componente porque os dois precisam concordar sobre os nomes dos controles;
 * separá-los faria um `formGroupName` inexistente falhar só em tempo de execução.
 */
export function buildAddressForm(formBuilder: NonNullableFormBuilder) {
  return formBuilder.group({
    street: formBuilder.control('', [Validators.maxLength(200)]),
    number: formBuilder.control('', [Validators.maxLength(20)]),
    complement: formBuilder.control('', [Validators.maxLength(100)]),
    district: formBuilder.control('', [Validators.maxLength(100)]),
    city: formBuilder.control('', [Validators.maxLength(100)]),
    state: formBuilder.control('', [Validators.maxLength(50)]),
    postalCode: formBuilder.control('', [Validators.maxLength(20)]),
    // ISO 3166-1 alfa-2: o backend declara `@Size(min = 2, max = 2)`.
    country: formBuilder.control('BR', [Validators.minLength(2), Validators.maxLength(2)]),
  });
}

/**
 * Endereço — `dt-address-form` (T-003-18).
 *
 * Todos os campos são opcionais: o cadastro de cliente precisa funcionar para quem só tem o nome e o
 * e-mail do contato. Exigir endereço completo aqui empurraria o usuário a inventar dados.
 */
@Component({
  selector: 'dt-address-form',
  imports: [ReactiveFormsModule, InputTextModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="dt-address" [formGroup]="group()">
      <legend i18n="@@address.legend">Endereço</legend>

      <div class="dt-address__grid">
        <div class="dt-address__field dt-address__field--wide">
          <label [attr.for]="prefix() + '-street'" i18n="@@address.street">Logradouro</label>
          <input
            [id]="prefix() + '-street'"
            type="text"
            pInputText
            formControlName="street"
            autocomplete="address-line1"
          />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-number'" i18n="@@address.number">Número</label>
          <input [id]="prefix() + '-number'" type="text" pInputText formControlName="number" />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-complement'" i18n="@@address.complement">
            Complemento
          </label>
          <input
            [id]="prefix() + '-complement'"
            type="text"
            pInputText
            formControlName="complement"
          />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-district'" i18n="@@address.district">Bairro</label>
          <input [id]="prefix() + '-district'" type="text" pInputText formControlName="district" />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-city'" i18n="@@address.city">Cidade</label>
          <input
            [id]="prefix() + '-city'"
            type="text"
            pInputText
            formControlName="city"
            autocomplete="address-level2"
          />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-state'" i18n="@@address.state">Estado</label>
          <input
            [id]="prefix() + '-state'"
            type="text"
            pInputText
            formControlName="state"
            autocomplete="address-level1"
          />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-postal-code'" i18n="@@address.postalCode">CEP</label>
          <input
            [id]="prefix() + '-postal-code'"
            type="text"
            pInputText
            formControlName="postalCode"
            autocomplete="postal-code"
          />
        </div>

        <div class="dt-address__field">
          <label [attr.for]="prefix() + '-country'" i18n="@@address.country">País</label>
          <input
            [id]="prefix() + '-country'"
            type="text"
            pInputText
            formControlName="country"
            maxlength="2"
            autocomplete="country"
          />
        </div>
      </div>
    </fieldset>
  `,
  styles: `
    .dt-address {
      margin: 0;
      padding: 0;
      border: 0;
    }

    .dt-address legend {
      padding: 0;
      color: var(--dt-text-primary);
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-address__grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: var(--dt-space-3);
      margin-top: var(--dt-space-3);
    }

    .dt-address__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-address__field--wide {
      grid-column: span 2;
    }

    .dt-address__field label {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-address__field input {
      width: 100%;
    }
  `,
})
export class AddressFormComponent {
  readonly group = input.required<FormGroup>();

  /** Prefixo dos `id`, para que dois endereços na mesma tela não colidam (A11Y-04). */
  readonly prefix = input('address');
}
