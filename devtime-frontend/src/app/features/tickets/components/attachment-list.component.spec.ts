import { render, screen } from '@testing-library/angular';
import { Attachment } from '../data/attachment.model';
import { AttachmentListComponent } from './attachment-list.component';

const CLEAN: Attachment = {
  id: 'a-1',
  ticketId: 'tk-1',
  commentId: null,
  fileName: 'relatorio.pdf',
  originalFileName: 'relatório do cliente.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2_400_000,
  scanStatus: 'CLEAN',
  uploadedBy: { id: 'u1', name: 'Rafael', handle: null, avatarUrl: null },
  createdAt: '2026-07-29T12:00:00Z',
  canDownload: true,
  canDelete: true,
};

const PENDING: Attachment = {
  ...CLEAN,
  id: 'a-2',
  scanStatus: 'PENDING',
  canDownload: false,
};

const INFECTED: Attachment = {
  ...CLEAN,
  id: 'a-3',
  scanStatus: 'INFECTED',
  canDownload: false,
  canDelete: false,
};

describe('AttachmentListComponent', () => {
  async function setup(
    attachments: readonly Attachment[] = [CLEAN],
    inputs: Record<string, unknown> = {},
  ) {
    return render(AttachmentListComponent, {
      inputs: { attachments, maxCount: 20, canUpload: true, ...inputs },
    });
  }

  it('SG-13: o nome original é exibido como texto, e o download fica disponível em CLEAN', async () => {
    await setup();

    expect(await screen.findByText('relatório do cliente.pdf')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Baixar' })).toBeVisible();
  });

  it('RN-803: PENDING não oferece download e explica o motivo', async () => {
    await setup([PENDING]);

    expect(screen.queryByRole('button', { name: 'Baixar' })).toBeNull();
    expect(await screen.findByText(/libera assim que a verificação terminar/)).toBeVisible();
  });

  it('CP-02: INFECTED não oferece nenhum caminho de liberação', async () => {
    await setup([INFECTED]);

    expect(screen.queryByRole('button', { name: 'Baixar' })).toBeNull();
    expect(await screen.findByText(/Ameaça detectada/)).toBeVisible();
    // Não existe "baixar mesmo assim": a decisão não é de quem clica.
    expect(screen.queryByText(/mesmo assim/)).toBeNull();
  });

  it('RN-806: com o alvo cheio, o envio é desabilitado antes da tentativa', async () => {
    await setup([CLEAN], { full: true, maxCount: 20 });

    expect(screen.getByLabelText<HTMLInputElement>('Anexar arquivo')).toBeDisabled();
    expect(screen.getByText(/atingiu o limite de 20 anexos/)).toBeVisible();
  });

  it('§29: acima de 80% da quota o aviso aparece antes de o envio falhar', async () => {
    await setup([CLEAN], {
      quotaWarning: true,
      quota: { usedBytes: 900, limitBytes: 1000, percentage: 90 },
    });

    expect(await screen.findByText(/está em 90%/)).toBeVisible();
  });
});
