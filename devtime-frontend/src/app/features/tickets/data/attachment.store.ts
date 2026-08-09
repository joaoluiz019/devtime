import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { AttachmentApi } from './attachment.api';
import { Attachment, AttachmentQuota, MAX_ATTACHMENT_BYTES } from './attachment.model';

/**
 * Anexos de um ticket (T-015).
 *
 * O limite de arquivos (RN-806) vem do servidor em `maxCount`, e não de uma constante daqui: a tela
 * desabilita o envio quando o alvo está cheio, em vez de deixar a pessoa escolher um arquivo para
 * receber `422` depois do upload inteiro.
 *
 * O tamanho, esse sim, é verificado no cliente antes de subir: mandar 40 MB por uma conexão móvel
 * para receber `413` é o desperdício que RN-802 não obriga ninguém a pagar.
 */
@Injectable()
export class AttachmentStore {
  private readonly api = inject(AttachmentApi);

  private readonly _attachments = signal<readonly Attachment[]>([]);
  private readonly _maxCount = signal(0);
  private readonly _quota = signal<AttachmentQuota | null>(null);
  private readonly _loading = signal(false);
  private readonly _uploading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _localError = signal<string | null>(null);

  readonly attachments = this._attachments.asReadonly();
  readonly maxCount = this._maxCount.asReadonly();
  readonly quota = this._quota.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly uploading = this._uploading.asReadonly();
  readonly error = this._error.asReadonly();

  /** Recusa feita pela própria tela — arquivo grande demais —, sem `ProblemDetail` do servidor. */
  readonly localError = this._localError.asReadonly();

  readonly isFull = computed(
    () => this._maxCount() > 0 && this._attachments().length >= this._maxCount(),
  );

  /** §29: acima de 80% o aviso de quota aparece antes de o envio começar a falhar. */
  readonly quotaWarning = computed(() => (this._quota()?.percentage ?? 0) >= 80);

  async load(ticketId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const [list, quota] = await Promise.all([
        firstValueFrom(this.api.byTicket(ticketId)),
        firstValueFrom(this.api.quota()),
      ]);
      this._attachments.set(list.attachments);
      this._maxCount.set(list.maxCount);
      this._quota.set(quota);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    } finally {
      this._loading.set(false);
    }
  }

  async upload(ticketId: string, file: File): Promise<boolean> {
    this._localError.set(null);
    this._error.set(null);

    if (file.size > MAX_ATTACHMENT_BYTES) {
      this._localError.set(
        $localize`:@@attachments.tooLarge:O arquivo passa de 10 MB e não pode ser enviado.`,
      );
      return false;
    }

    this._uploading.set(true);
    try {
      await firstValueFrom(this.api.upload(ticketId, file));
      await this.load(ticketId);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._uploading.set(false);
    }
  }

  async remove(ticketId: string, id: string): Promise<boolean> {
    this._error.set(null);
    try {
      await firstValueFrom(this.api.delete(id));
      await this.load(ticketId);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    }
  }

  /**
   * Baixa o arquivo.
   *
   * RN-803: só `CLEAN` chega aqui — o botão nem existe nos demais estados —, mas o servidor recusa
   * de novo se algo mudou entre a listagem e o clique. É a mesma regra verificada duas vezes de
   * propósito: a primeira é ergonomia, a segunda é a que vale.
   */
  async download(attachment: Attachment): Promise<boolean> {
    this._error.set(null);
    try {
      const blob = await firstValueFrom(this.api.download(attachment.id));
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = attachment.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    }
  }
}
