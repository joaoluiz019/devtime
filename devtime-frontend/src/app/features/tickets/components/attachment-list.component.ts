import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { Attachment, AttachmentQuota, formatBytes, ScanStatus } from '../data/attachment.model';

/**
 * Anexos do ticket — `dt-attachment-list` (tickets.md §11, T-015).
 *
 * **O estado da verificação é explicado, não apenas exibido** (CP-20). `PENDING` diz que o arquivo
 * ainda está sendo verificado; `INFECTED` diz que o binário foi removido; `FAILED` diz que a
 * verificação não concluiu e que o caminho é reenviar. Um ícone cinza sem texto faria o usuário
 * concluir que o produto está quebrado.
 *
 * Não existe "baixar mesmo assim" (§6.3, CP-02): liberar um arquivo não verificado por decisão de
 * quem clica converteria três camadas de defesa em uma caixa de diálogo.
 */
@Component({
  selector: 'dt-attachment-list',
  imports: [ButtonModule, MessageModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-attachments">
      <h2 class="dt-attachments__title">
        <span i18n="@@attachments.title">Anexos</span>
        <span class="dt-attachments__count">{{ attachments().length }}/{{ maxCount() }}</span>
      </h2>

      @if (quotaWarning() && quota(); as usage) {
        <p-message severity="warn" styleClass="w-full">
          <span i18n="@@attachments.quota">
            O armazenamento da organização está em {{ usage.percentage }}%. Novos envios podem ser
            recusados.
          </span>
        </p-message>
      }

      @if (localError() !== null) {
        <p-message severity="error" [text]="localError()!" styleClass="w-full" />
      }

      @if (attachments().length === 0) {
        <p class="dt-attachments__empty" i18n="@@attachments.empty">Nenhum arquivo anexado.</p>
      } @else {
        <ul class="dt-attachments__list" role="list">
          @for (attachment of attachments(); track attachment.id) {
            <li class="dt-attachments__item">
              <i class="pi pi-paperclip" aria-hidden="true"></i>

              <span class="dt-attachments__identity">
                <!-- SG-13: o nome original é texto, nunca marcação. -->
                <span class="dt-attachments__name">{{ attachment.originalFileName }}</span>
                <small class="dt-attachments__meta">
                  {{ size(attachment.sizeBytes) }} ·
                  {{ attachment.uploadedBy?.name ?? removedAuthor }}
                </small>
              </span>

              <p-tag
                [severity]="scanSeverity(attachment.scanStatus)"
                [value]="scanLabel(attachment.scanStatus)"
              />

              @if (!attachment.canDownload) {
                <small class="dt-attachments__blocked">
                  {{ scanExplanation(attachment.scanStatus) }}
                </small>
              }

              <span class="dt-attachments__actions">
                @if (attachment.canDownload) {
                  <p-button
                    icon="pi pi-download"
                    severity="secondary"
                    [text]="true"
                    i18n-ariaLabel="@@attachments.download"
                    ariaLabel="Baixar"
                    (onClick)="downloaded.emit(attachment)"
                  />
                }
                @if (attachment.canDelete) {
                  <p-button
                    icon="pi pi-trash"
                    severity="danger"
                    [text]="true"
                    i18n-ariaLabel="@@action.delete"
                    ariaLabel="Excluir"
                    (onClick)="removed.emit(attachment.id)"
                  />
                }
              </span>
            </li>
          }
        </ul>
      }

      @if (canUpload()) {
        <div class="dt-attachments__upload">
          <label class="dt-attachments__upload-label" for="attachment-file">
            <span i18n="@@attachments.add">Anexar arquivo</span>
          </label>
          <input
            id="attachment-file"
            type="file"
            [disabled]="full() || uploading()"
            (change)="onFileSelected($event)"
          />
          @if (full()) {
            <!-- RN-806: o limite do alvo é do servidor; a tela apenas para antes da tentativa. -->
            <small class="dt-attachments__hint" i18n="@@attachments.full">
              Este ticket atingiu o limite de {{ maxCount() }} anexos.
            </small>
          } @else {
            <small class="dt-attachments__hint" i18n="@@attachments.limits">
              Até 10 MB por arquivo. O download é liberado após a verificação antivírus.
            </small>
          }
        </div>
      }
    </section>
  `,
  styleUrl: './attachment-list.component.scss',
})
export class AttachmentListComponent {
  readonly attachments = input.required<readonly Attachment[]>();
  readonly maxCount = input(0);
  readonly quota = input<AttachmentQuota | null>(null);
  readonly quotaWarning = input(false);
  readonly canUpload = input(false);
  readonly uploading = input(false);
  readonly full = input(false);
  readonly localError = input<string | null>(null);

  readonly selected = output<File>();
  readonly downloaded = output<Attachment>();
  readonly removed = output<string>();

  protected readonly removedAuthor = $localize`:@@user.removed:Usuário Removido`;

  protected readonly scanLabels = computed<Readonly<Record<ScanStatus, string>>>(() => ({
    PENDING: $localize`:@@attachments.scan.pending:Verificando`,
    CLEAN: $localize`:@@attachments.scan.clean:Disponível`,
    INFECTED: $localize`:@@attachments.scan.infected:Bloqueado`,
    FAILED: $localize`:@@attachments.scan.failed:Verificação falhou`,
  }));

  protected size(bytes: number): string {
    return formatBytes(bytes);
  }

  protected scanLabel(status: ScanStatus): string {
    return this.scanLabels()[status];
  }

  protected scanSeverity(status: ScanStatus): 'success' | 'info' | 'danger' | 'warn' {
    switch (status) {
      case 'CLEAN':
        return 'success';
      case 'PENDING':
        return 'info';
      case 'INFECTED':
        return 'danger';
      case 'FAILED':
        return 'warn';
    }
  }

  /** CP-20: o motivo do bloqueio em linguagem natural, incluindo o que fazer a seguir. */
  protected scanExplanation(status: ScanStatus): string {
    switch (status) {
      case 'PENDING':
        return $localize`:@@attachments.scan.pending.text:O download libera assim que a verificação terminar.`;
      case 'INFECTED':
        return $localize`:@@attachments.scan.infected.text:Ameaça detectada. O arquivo foi removido e não pode ser baixado.`;
      case 'FAILED':
        return $localize`:@@attachments.scan.failed.text:A verificação não concluiu. Envie o arquivo novamente.`;
      case 'CLEAN':
        return '';
    }
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file !== undefined) {
      this.selected.emit(file);
    }
    // O campo é limpo para que enviar o mesmo arquivo duas vezes seguidas dispare o evento de novo.
    input.value = '';
  }
}
