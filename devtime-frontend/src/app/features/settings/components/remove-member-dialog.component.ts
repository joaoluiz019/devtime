import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { Member } from '../data/member.model';

/**
 * Confirmação de remoção de membro — parte de P32 (FA-09).
 *
 * RN-458 dita o texto: registros de horas, tickets e comentários **permanecem**. A leitura natural
 * de "remover membro" é "apagar o que essa pessoa fez", e quem confirma acreditando nisso vai
 * procurar as horas sumidas no fechamento do mês. Dizer o contrário, antes, é o ponto da caixa.
 *
 * RN-460: o cronômetro ativo é descartado — não é encerrado e convertido em registro. É perda de
 * trabalho em andamento, e precisa ser dita antes, não descoberta depois.
 *
 * A escolha de para quem vão os tickets abertos existe porque o padrão do servidor — o proprietário
 * que executou — raramente é onde eles devem ficar.
 */
@Component({
  selector: 'dt-remove-member-dialog',
  imports: [FormsModule, ButtonModule, DialogModule, MessageModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visibleChange.emit($event)"
      [modal]="true"
      [style]="{ width: '34rem' }"
      [header]="title"
    >
      @if (member(); as target) {
        <div class="dt-remove-member">
          <p class="dt-remove-member__text">
            <span i18n="@@member.remove.text">
              {{ name() }} perde o acesso à organização imediatamente e todas as sessões dela são
              encerradas.
            </span>
          </p>

          <!-- RN-458: o que permanece é dito antes da confirmação, não depois. -->
          <p-message severity="info" styleClass="w-full">
            <span i18n="@@member.remove.preserved">
              As horas registradas, os tickets e os comentários permanecem. Relatórios e saldos de
              contrato não mudam.
            </span>
          </p-message>

          <!-- RN-460: cronômetro ativo é descartado, não encerrado. -->
          <p-message severity="warn" styleClass="w-full">
            <span i18n="@@member.remove.timer">
              Se houver um cronômetro em andamento, o tempo dele é descartado.
            </span>
          </p-message>

          <div class="dt-remove-member__field">
            <label for="remove-reassign" i18n="@@member.remove.reassign">
              Reatribuir os tickets abertos para
            </label>
            <p-select
              inputId="remove-reassign"
              [options]="candidates()"
              [ngModel]="reassignTo()"
              optionLabel="name"
              optionValue="id"
              [showClear]="true"
              i18n-placeholder="@@member.remove.reassign.default"
              placeholder="Quem executar a remoção"
              (onChange)="reassignTo.set($event.value)"
            />
          </div>

          <div class="dt-remove-member__actions">
            <p-button
              type="button"
              i18n-label="@@action.cancel"
              label="Cancelar"
              severity="secondary"
              [text]="true"
              (onClick)="visibleChange.emit(false)"
            />
            <p-button
              type="button"
              i18n-label="@@member.remove.submit"
              label="Remover da organização"
              severity="danger"
              [loading]="saving()"
              (onClick)="confirmed.emit(reassignTo() ?? undefined)"
            />
          </div>
        </div>
      }
    </p-dialog>
  `,
  styles: `
    .dt-remove-member {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-remove-member__text {
      margin: 0;
      font-size: var(--dt-text-sm);
    }

    .dt-remove-member__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-remove-member__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class RemoveMemberDialogComponent {
  readonly visible = input.required<boolean>();
  readonly member = input<Member | null>(null);
  /** Membros ativos que podem receber os tickets; o alvo da remoção não está entre eles. */
  readonly members = input.required<readonly Member[]>();
  readonly saving = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<string | undefined>();

  protected readonly title = $localize`:@@member.remove.title:Remover membro`;

  protected readonly reassignTo = signal<string | null>(null);

  protected readonly name = computed(() => {
    const target = this.member();
    return target === null ? '' : (target.user.displayName ?? target.user.fullName);
  });

  protected readonly candidates = computed(() => {
    const targetId = this.member()?.id;
    return this.members()
      .filter((candidate) => candidate.id !== targetId && candidate.status === 'ACTIVE')
      .map((candidate) => ({
        // O backend espera o identificador do **usuário**, não o do vínculo.
        id: candidate.user.id,
        name: candidate.user.displayName ?? candidate.user.fullName,
      }));
  });
}
