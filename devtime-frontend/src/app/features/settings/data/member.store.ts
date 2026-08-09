import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { Role } from '../../../core/auth/auth.model';
import { MemberApi } from './member.api';
import { InvitationRequest, Member, MemberInvitation, MemberRemoval } from './member.model';

/** A equipe cabe numa página: o produto atende micro software houses, não corporações. */
const PAGE_SIZE = 100;

/**
 * Estado da equipe em P32 (T-002-29).
 *
 * As duas listas são carregadas juntas porque o convite pendente aparece **nas duas**: como vínculo
 * `INVITED` na listagem de membros e como convite na lista dedicada, que é a única com `expiresAt` e
 * com o identificador que reenvio e revogação exigem. A tela mostra cada um no seu lugar, e é este
 * store que os liga pelo e-mail.
 */
@Injectable()
export class MemberStore {
  private readonly api = inject(MemberApi);

  private readonly _members = signal<readonly Member[]>([]);
  private readonly _invitations = signal<readonly MemberInvitation[]>([]);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _lastRemoval = signal<MemberRemoval | null>(null);

  readonly members = this._members.asReadonly();
  readonly invitations = this._invitations.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly error = this._error.asReadonly();

  /** Efeitos da última remoção, exibidos após a confirmação para tornar RN-458 visível. */
  readonly lastRemoval = this._lastRemoval.asReadonly();

  /** Vínculos já aceitos. O convite pendente vive na outra lista, com data de expiração. */
  readonly activeMembers = computed(() =>
    this._members().filter((member) => member.status !== 'INVITED' && member.status !== 'REMOVED'),
  );

  /**
   * Proprietários ativos.
   *
   * RN-455: o último deles não pode ser removido, suspenso nem rebaixado. O servidor recusa com
   * `DEVTIME-2455`, mas a contagem existe aqui para que a tela **explique** antes de a pessoa
   * tentar — um tenant sem proprietário é irrecuperável, e o erro depois do clique não ensina isso.
   */
  readonly owners = computed(() =>
    this.activeMembers().filter((member) => member.role === 'OWNER' && member.status === 'ACTIVE'),
  );

  readonly isLastOwner = computed(() => {
    const owners = this.owners();
    return (member: Member) =>
      member.role === 'OWNER' && owners.length === 1 && owners[0].id === member.id;
  });

  async load(): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const [page, invitations] = await Promise.all([
        firstValueFrom(this.api.list({ page: 0, size: PAGE_SIZE })),
        firstValueFrom(this.api.invitations()),
      ]);
      this._members.set(page.content);
      this._invitations.set(invitations);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    } finally {
      this._loading.set(false);
    }
  }

  async invite(request: InvitationRequest): Promise<boolean> {
    return this.mutate(() => firstValueFrom(this.api.invite(request)));
  }

  async resendInvitation(id: string): Promise<boolean> {
    return this.mutate(() => firstValueFrom(this.api.resendInvitation(id)));
  }

  async revokeInvitation(id: string): Promise<boolean> {
    return this.mutate(() => firstValueFrom(this.api.revokeInvitation(id)));
  }

  /**
   * Altera o papel (FA-08).
   *
   * A `version` vai junto (RN-004): duas pessoas alterando o mesmo vínculo produziriam a última
   * gravação vencendo em silêncio, e é justamente aqui que isso importaria — a segunda alteração
   * poderia devolver a alguém um alcance que a primeira acabou de retirar.
   */
  async changeRole(member: Member, role: Role): Promise<boolean> {
    return this.mutate(() =>
      firstValueFrom(this.api.changeRole(member.id, { role, version: member.version })),
    );
  }

  async suspend(id: string): Promise<boolean> {
    return this.mutate(() => firstValueFrom(this.api.suspend(id)));
  }

  async reactivate(id: string): Promise<boolean> {
    return this.mutate(() => firstValueFrom(this.api.reactivate(id)));
  }

  async remove(id: string, reassignTicketsTo?: string): Promise<boolean> {
    this._lastRemoval.set(null);
    return this.mutate(async () => {
      this._lastRemoval.set(await firstValueFrom(this.api.remove(id, reassignTicketsTo)));
    });
  }

  /**
   * Executa a operação e recarrega as duas listas.
   *
   * Recarregar em vez de aplicar a resposta no lugar: alterar um papel muda o `availableActions`
   * **dos outros** membros — quem era o único proprietário deixa de ser —, e atualizar só a linha
   * tocada deixaria a tela oferecendo ações que o servidor recusaria.
   */
  private async mutate(operation: () => Promise<unknown>): Promise<boolean> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation();
      await this.load();
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._saving.set(false);
    }
  }
}
