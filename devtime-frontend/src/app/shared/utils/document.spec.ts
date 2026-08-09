import { FormControl } from '@angular/forms';
import { documentValidator, formatDocument, isValidCnpj, isValidCpf, onlyDigits } from './document';

/**
 * RN-402 no cliente.
 *
 * Os casos extremos de §12.1 do spec são o que separa este validador de um `length === 11`: sequência
 * repetida (CX-04) passa na fórmula e precisa ser recusada; documento com máscara (CX-03) precisa ser
 * aceito, porque é assim que a pessoa digita.
 */
describe('isValidCpf', () => {
  it.each([
    ['529.982.247-25', true],
    ['52998224725', true],
    ['529.982.247-24', false],
    ['111.111.111-11', false], // CX-04
    ['5299822472', false],
  ])('valida %s como %s', (value, expected) => {
    expect(isValidCpf(value)).toBe(expected);
  });
});

describe('isValidCnpj', () => {
  it.each([
    ['11.222.333/0001-81', true],
    ['11222333000181', true],
    ['11.222.333/0001-82', false],
    ['11.111.111/1111-11', false], // CX-04
  ])('valida %s como %s', (value, expected) => {
    expect(isValidCnpj(value)).toBe(expected);
  });
});

describe('formatDocument', () => {
  it('aplica a máscara de CPF e de CNPJ', () => {
    expect(formatDocument('CPF', '52998224725')).toBe('529.982.247-25');
    expect(formatDocument('CNPJ', '11222333000181')).toBe('11.222.333/0001-81');
  });

  it('acompanha a digitação incompleta sem inventar dígitos', () => {
    expect(formatDocument('CPF', '529')).toBe('529');
    expect(formatDocument('CPF', '5299822')).toBe('529.982.2');
  });

  it('deixa o documento estrangeiro intacto', () => {
    expect(formatDocument('OTHER', 'AB-1234')).toBe('AB-1234');
  });
});

describe('onlyDigits', () => {
  it('CX-03: a máscara não trafega', () => {
    expect(onlyDigits('11.222.333/0001-81')).toBe('11222333000181');
  });
});

describe('documentValidator', () => {
  it('CX-06: cliente sem documento é válido', () => {
    const validate = documentValidator(() => 'CPF');
    expect(validate(new FormControl(''))).toBeNull();
  });

  it('acusa o documento pelo tipo escolhido', () => {
    const validate = documentValidator(() => 'CNPJ');
    // CPF válido é CNPJ inválido: validar sem o tipo acusaria erro em campo correto.
    expect(validate(new FormControl('529.982.247-25'))).toEqual({ document: true });
  });

  it('aprova documento consistente com o tipo', () => {
    const validate = documentValidator(() => 'CPF');
    expect(validate(new FormControl('529.982.247-25'))).toBeNull();
  });
});
