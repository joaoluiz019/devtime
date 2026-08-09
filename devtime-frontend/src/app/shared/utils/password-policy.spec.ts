import { FormControl } from '@angular/forms';
import {
  evaluatePassword,
  isPasswordCompliant,
  passwordPolicyValidator,
  satisfiedCount,
} from './password-policy';

/**
 * Política de senha (RN-451).
 *
 * As bordas são o que importa: nove caracteres reprova, dez aprova. Um `>` no lugar de `>=` aqui
 * deixa passar uma senha que o servidor recusará com `422`, e o usuário vê o formulário falhar sem
 * entender por quê.
 */
describe('evaluatePassword', () => {
  it.each([
    ['Senha12345', true],
    ['Senha1234', false], // nove caracteres
    ['senha12345', false], // sem maiúscula
    ['SENHA12345', false], // sem minúscula
    ['SenhaSenhaX', false], // sem dígito
  ])('classifica %s como conforme=%s', (password, expected) => {
    expect(isPasswordCompliant(password)).toBe(expected);
  });

  it('recusa senha acima do máximo do contrato (128)', () => {
    expect(isPasswordCompliant(`A1${'a'.repeat(127)}`)).toBe(false);
  });

  it('DS-05: cada requisito tem rótulo textual, nunca só um indicador de cor', () => {
    for (const requirement of evaluatePassword('abc')) {
      expect(requirement.label).not.toBe('');
    }
  });

  it('conta apenas os requisitos cumpridos', () => {
    expect(satisfiedCount('')).toBe(0);
    expect(satisfiedCount('abcdefghij')).toBe(2); // comprimento e minúscula
    expect(satisfiedCount('Senha12345')).toBe(4);
  });
});

describe('passwordPolicyValidator', () => {
  const validate = passwordPolicyValidator();

  it('não acusa erro no campo vazio: quem exige preenchimento é o required', () => {
    expect(validate(new FormControl(''))).toBeNull();
  });

  it('acusa um único erro de política, não um por requisito', () => {
    expect(validate(new FormControl('senha'))).toEqual({ passwordPolicy: true });
  });

  it('aceita senha conforme', () => {
    expect(validate(new FormControl('Senha12345'))).toBeNull();
  });
});
