import { render, screen } from '@testing-library/angular';
import { PartialBadgeComponent } from './partial-badge.component';

/**
 * RN-702: o selo é obrigatório em todo período aberto ou reaberto.
 *
 * Um número em evolução exibido sem a marcação será lido como final — e é assim que um saldo parcial
 * vira uma cobrança contestada.
 */
describe('PartialBadgeComponent', () => {
  it('marca período aberto como parcial', async () => {
    await render(PartialBadgeComponent, { inputs: { status: 'OPEN', reopenCount: 0 } });

    expect(screen.getByText('Parcial')).toBeVisible();
  });

  it('menciona a reabertura em período reaberto', async () => {
    await render(PartialBadgeComponent, { inputs: { status: 'REOPENED', reopenCount: 2 } });

    expect(screen.getByText('Parcial · reaberto')).toBeVisible();
  });

  it.each(['CLOSED', 'SCHEDULED', 'CLOSING'] as const)('não marca período %s', async (status) => {
    await render(PartialBadgeComponent, { inputs: { status, reopenCount: 0 } });

    expect(screen.queryByText(/Parcial/)).toBeNull();
  });
});
