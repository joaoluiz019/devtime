import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { balanceFixture } from '../../../shared/models/balance.fixture';
import { AdjustmentDialogComponent } from './adjustment-dialog.component';

/**
 * Diálogo de ajuste (T-011-17, T-011-42).
 *
 * O teste central é o da **prévia**: o ajuste é imutável (RN-236) e a única correção é um estorno que
 * fica para sempre no extrato do cliente. Se a prévia parar de refletir o valor digitado, a defesa
 * contra um ajuste errado desaparece sem que nada quebre visivelmente (risco R-08).
 */
describe('AdjustmentDialogComponent', () => {
  async function setup(available = 2760) {
    const balance = balanceFixture({ availableMinutes: available });
    const result = await render(AdjustmentDialogComponent, {
      inputs: { visible: true, balance, submitting: false },
    });
    return { ...result, user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }) };
  }

  it('exibe a prévia do saldo resultante ao informar os minutos', async () => {
    const { user } = await setup();

    await user.type(screen.getByLabelText(/Horas a creditar ou debitar/), '1:00');

    // 46:00 disponíveis + 01:00 de crédito = 47:00.
    expect(await screen.findByText('47:00')).toBeVisible();
  });

  it('a prévia acompanha um débito', async () => {
    const { user } = await setup();

    await user.type(screen.getByLabelText(/Horas a creditar ou debitar/), '-2:00');

    expect(await screen.findByText('44:00')).toBeVisible();
  });

  it('RN-237: avisa quando o débito deixaria o disponível negativo', async () => {
    const { user } = await setup(60);

    await user.type(screen.getByLabelText(/Horas a creditar ou debitar/), '-2:00');

    expect(
      await screen.findByText('Este débito deixaria o saldo disponível negativo e será recusado.'),
    ).toBeVisible();
  });

  it('FR-104: o botão não é desabilitado por formulário inválido', async () => {
    await setup();

    expect(screen.getByRole('button', { name: 'Aplicar ajuste' })).toBeEnabled();
  });

  it('RN-215: recusa justificativa com menos de 10 caracteres e não emite', async () => {
    const { user, fixture } = await setup();
    const applied = jest.fn();
    fixture.componentInstance.apply.subscribe(applied);

    await user.type(screen.getByLabelText(/Horas a creditar ou debitar/), '1:00');
    await user.type(screen.getByLabelText(/Justificativa/), 'curta');
    await user.click(screen.getByRole('button', { name: 'Aplicar ajuste' }));

    expect(
      await screen.findByText('A justificativa precisa ter ao menos 10 caracteres.'),
    ).toBeVisible();
    expect(applied).not.toHaveBeenCalled();
  });

  it('emite o ajuste quando o formulário está completo', async () => {
    const { user, fixture } = await setup();
    const applied = jest.fn();
    fixture.componentInstance.apply.subscribe(applied);

    await user.type(screen.getByLabelText(/Horas a creditar ou debitar/), '1:00');
    await user.type(screen.getByLabelText(/Justificativa/), 'Cortesia por indisponibilidade');
    await user.click(screen.getByRole('button', { name: 'Aplicar ajuste' }));

    expect(applied).toHaveBeenCalledWith({
      minutes: 60,
      reason: 'COURTESY',
      justification: 'Cortesia por indisponibilidade',
    });
  });

  it('não emite ajuste de zero minutos — nunca é intencional', async () => {
    const { user, fixture } = await setup();
    const applied = jest.fn();
    fixture.componentInstance.apply.subscribe(applied);

    await user.type(screen.getByLabelText(/Horas a creditar ou debitar/), '0');
    await user.type(screen.getByLabelText(/Justificativa/), 'Justificativa suficiente');
    await user.click(screen.getByRole('button', { name: 'Aplicar ajuste' }));

    expect(applied).not.toHaveBeenCalled();
  });
});
