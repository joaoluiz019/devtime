import { CommentAuthor } from './comment.model';

/**
 * Modelos de anexo, espelhando `AttachmentResponses` (FR-061, AP-02).
 *
 * `storageKey` e `checksumSha256` **não existem no contrato** (CP-07): a chave revelaria a estrutura
 * do storage e o checksum permitiria descobrir se um arquivo específico já existe no tenant sem
 * tê-lo. O que não é emitido também não é modelado.
 */

/** §4.9. Só `CLEAN` libera o download (RN-803). */
export type ScanStatus = 'PENDING' | 'CLEAN' | 'INFECTED' | 'FAILED';

export interface Attachment {
  readonly id: string;
  readonly ticketId: string | null;
  readonly commentId: string | null;
  /** Nome sanitizado (RN-804): é com ele que o arquivo é baixado. */
  readonly fileName: string;
  /** O que a pessoa enviou. SG-13: exibido como texto, nunca interpretado. */
  readonly originalFileName: string;
  readonly contentType: string;
  readonly sizeBytes: number;
  readonly scanStatus: ScanStatus;
  readonly uploadedBy: CommentAuthor | null;
  readonly createdAt: string;
  /** RN-803 avaliado no servidor; reimplementar criaria uma segunda definição de "pode baixar". */
  readonly canDownload: boolean;
  readonly canDelete: boolean;
}

export interface AttachmentList {
  readonly attachments: readonly Attachment[];
  readonly count: number;
  /** RN-806 para este alvo: permite desabilitar o envio antes da tentativa. */
  readonly maxCount: number;
}

/** RN-801: consumo do tenant. Acima de 80% justifica aviso na interface. */
export interface AttachmentQuota {
  readonly usedBytes: number;
  readonly limitBytes: number;
  readonly percentage: number;
}

/** RN-802: acima disto o servidor responde `413` — a tela recusa antes de subir o arquivo. */
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
