import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Política de senha do cliente — espelho parcial de RN-451.
 *
 * A regra completa é "≥ 10 caracteres, com maiúscula, minúscula e dígito, **fora de lista comum**".
 * Os quatro primeiros requisitos são verificáveis no navegador e existem aqui para dar retorno
 * imediato enquanto a pessoa digita. A lista de senhas comuns **não** é reproduzida: baixá-la
 * custaria centenas de kB e, mais grave, criaria uma segunda fonte de verdade que divergiria da do
 * servidor na primeira atualização — a decisão continua sendo do backend, que responde
 * `422 DEVTIME-2451`.
 */
export interface PasswordRequirement {
  readonly id: 'length' | 'uppercase' | 'lowercase' | 'digit';
  readonly label: string;
  readonly satisfied: boolean;
}

/** Mínimo de RN-451; o máximo de 128 vem do contrato (`@Size(min = 10, max = 128)`). */
export const PASSWORD_MIN_LENGTH = 10;
export const PASSWORD_MAX_LENGTH = 128;

export function evaluatePassword(password: string): readonly PasswordRequirement[] {
  return [
    {
      id: 'length',
      label: $localize`:@@password.requirement.length:Ao menos 10 caracteres`,
      satisfied: password.length >= PASSWORD_MIN_LENGTH,
    },
    {
      id: 'uppercase',
      label: $localize`:@@password.requirement.uppercase:Uma letra maiúscula`,
      satisfied: /[A-Z]/.test(password),
    },
    {
      id: 'lowercase',
      label: $localize`:@@password.requirement.lowercase:Uma letra minúscula`,
      satisfied: /[a-z]/.test(password),
    },
    {
      id: 'digit',
      label: $localize`:@@password.requirement.digit:Um número`,
      satisfied: /\d/.test(password),
    },
  ];
}

/** Quantos requisitos verificáveis no cliente a senha cumpre — base da barra de força. */
export function satisfiedCount(password: string): number {
  return evaluatePassword(password).filter((requirement) => requirement.satisfied).length;
}

export function isPasswordCompliant(password: string): boolean {
  return (
    password.length <= PASSWORD_MAX_LENGTH &&
    evaluatePassword(password).every((requirement) => requirement.satisfied)
  );
}

/**
 * Validador de formulário para a política.
 *
 * Devolve um único erro `passwordPolicy` em vez de um por requisito: a tela exibe a lista completa
 * de requisitos com o estado de cada um (`dt-password-strength`), então detalhar aqui produziria
 * duas apresentações do mesmo fato.
 */
export function passwordPolicyValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value !== 'string' || value === '') {
      return null;
    }
    return isPasswordCompliant(value) ? null : { passwordPolicy: true };
  };
}
