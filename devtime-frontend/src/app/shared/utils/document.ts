import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * CPF e CNPJ — espelho de RN-402 no cliente (T-003-17).
 *
 * A verificação existe aqui para acusar o erro de digitação **antes** do envio, no campo em que ele
 * foi cometido. A decisão continua sendo do servidor (`422 DEVTIME-2402`): este arquivo pode aprovar
 * um documento que o backend recuse, nunca o contrário.
 *
 * CX-03: a máscara é apresentação. O que trafega é só dígito.
 */
export type DocumentType = 'CPF' | 'CNPJ' | 'OTHER';

export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '');
}

/**
 * CX-04: sequência de dígitos iguais é rejeitada.
 *
 * `111.111.111-11` passa na fórmula dos dígitos verificadores — é o caso que todo validador ingênuo
 * aceita e que nenhum órgão emite.
 */
function isRepeated(digits: string): boolean {
  return new Set(digits).size === 1;
}

export function isValidCpf(value: string): boolean {
  const digits = onlyDigits(value);
  if (digits.length !== 11 || isRepeated(digits)) {
    return false;
  }
  return checkDigit(digits, 9, 10) && checkDigit(digits, 10, 11);
}

/** Dígito verificador do CPF: soma ponderada decrescente, módulo 11, resto < 2 vira zero. */
function checkDigit(digits: string, position: number, startWeight: number): boolean {
  let sum = 0;
  for (let index = 0; index < position; index += 1) {
    sum += Number(digits[index]) * (startWeight - index);
  }
  const remainder = (sum * 10) % 11;
  const expected = remainder === 10 || remainder === 11 ? 0 : remainder;
  return expected === Number(digits[position]);
}

const CNPJ_WEIGHTS_FIRST: readonly number[] = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
const CNPJ_WEIGHTS_SECOND: readonly number[] = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];

export function isValidCnpj(value: string): boolean {
  const digits = onlyDigits(value);
  if (digits.length !== 14 || isRepeated(digits)) {
    return false;
  }
  return (
    cnpjCheckDigit(digits, CNPJ_WEIGHTS_FIRST) === Number(digits[12]) &&
    cnpjCheckDigit(digits, CNPJ_WEIGHTS_SECOND) === Number(digits[13])
  );
}

function cnpjCheckDigit(digits: string, weights: readonly number[]): number {
  const sum = weights.reduce((total, weight, index) => total + Number(digits[index]) * weight, 0);
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}

export function isValidDocument(type: DocumentType, value: string): boolean {
  switch (type) {
    case 'CPF':
      return isValidCpf(value);
    case 'CNPJ':
      return isValidCnpj(value);
    default:
      // `OTHER` cobre documento estrangeiro, que não tem dígito verificador conhecido pelo produto.
      return onlyDigits(value).length > 0 || value.trim() !== '';
  }
}

/** Formata para leitura. Entrada incompleta é devolvida como está — a máscara acompanha a digitação. */
export function formatDocument(type: DocumentType, value: string): string {
  const digits = onlyDigits(value);
  if (type === 'CPF') {
    return applyMask(digits, [3, 3, 3, 2], ['.', '.', '-']);
  }
  if (type === 'CNPJ') {
    return applyMask(digits, [2, 3, 3, 4, 2], ['.', '.', '/', '-']);
  }
  return value;
}

function applyMask(
  digits: string,
  groups: readonly number[],
  separators: readonly string[],
): string {
  let result = '';
  let cursor = 0;
  for (let index = 0; index < groups.length && cursor < digits.length; index += 1) {
    const size = groups[index] ?? 0;
    if (index > 0) {
      result += separators[index - 1] ?? '';
    }
    result += digits.slice(cursor, cursor + size);
    cursor += size;
  }
  return result;
}

/**
 * Validador de documento para o formulário.
 *
 * O tipo vem de fora porque é outro controle do mesmo formulário: um CPF válido é um CNPJ inválido, e
 * validar sem saber o tipo produziria erro em campo correto.
 */
export function documentValidator(type: () => DocumentType | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    const documentType = type();
    if (documentType === null) {
      return null;
    }
    return isValidDocument(documentType, value) ? null : { document: true };
  };
}
