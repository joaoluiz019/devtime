import { render, screen } from '@testing-library/angular';
import { axe, toHaveNoViolations } from 'jest-axe';
import { balanceFixture } from '../../models/balance.fixture';
import { BalanceSummaryComponent } from './balance-summary.component';

expect.extend(toHaveNoViolations);

/** Cartão de saldo. Os números vêm do exemplo normativo da §6.1 de `011-bank-hours/spec.md`. */
describe('BalanceSummaryComponent', () => {
  it('exibe os quatro números sem exigir interação (DS-03)', async () => {
    await render(BalanceSummaryComponent, { inputs: { balance: balanceFixture() } });

    expect(screen.getByText('46:00')).toBeVisible(); // disponível
    expect(screen.getByText('48:20')).toBeVisible(); // consumido
    expect(screen.getByText('02:20')).toBeVisible(); // excedente
    expect(screen.getByText('03:15')).toBeVisible(); // não faturáveis
  });

  it('rotula o saldo negativo como excedente, não como restante negativo', async () => {
    await render(BalanceSummaryComponent, { inputs: { balance: balanceFixture() } });

    expect(screen.getByText('Excedente')).toBeVisible();
    expect(screen.queryByText('Restante')).toBeNull();
  });

  it('rotula como restante quando ainda há saldo', async () => {
    const balance = balanceFixture({
      consumedMinutes: 1000,
      remainingMinutes: 1760,
      overageMinutes: 0,
      consumptionRate: 36.23,
    });
    await render(BalanceSummaryComponent, { inputs: { balance } });

    expect(screen.getByText('Restante')).toBeVisible();
    expect(screen.getByText('29:20')).toBeVisible();
  });

  it('BB-06 / CE-CO-03: sem horas disponíveis, o medidor não é renderizado', async () => {
    const balance = balanceFixture({ availableMinutes: 0, consumptionRate: 0 });
    await render(BalanceSummaryComponent, { inputs: { balance } });

    expect(screen.queryByRole('progressbar')).toBeNull();
    expect(screen.getByText('48:20')).toBeVisible();
  });

  it('RN-702: período aberto exibe o selo de parcial', async () => {
    await render(BalanceSummaryComponent, { inputs: { balance: balanceFixture() } });

    expect(screen.getByText('Parcial')).toBeVisible();
  });

  it('FR-140 / A11Y: sem violações do axe-core', async () => {
    const { container } = await render(BalanceSummaryComponent, {
      inputs: { balance: balanceFixture() },
    });

    // O axe-core agenda trabalho com `setTimeout`; com o relógio falso do setup global ele nunca
    // completaria. O relógio real vale apenas nesta asserção, que não depende de tempo.
    jest.useRealTimers();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
