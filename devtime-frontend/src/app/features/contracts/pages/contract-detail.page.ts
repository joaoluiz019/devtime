import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { messageForCode } from '../../../core/error/error-messages';
import { ConsumptionGaugeComponent } from '../../../shared/components/consumption-gauge/consumption-gauge.component';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { ContractStatusBadgeComponent } from '../components/contract-status-badge.component';
import {
  TransitionDialogComponent,
  TransitionKind,
} from '../components/transition-dialog.component';
import { ContractDetailStore } from '../data/contract-detail.store';
import { ContractTransitionRequest } from '../data/contract.model';

/**
 * Detalhe do contrato — P14, layout L6 (T-004-22).
 *
 * DT-02 / ME-06: as ações vêm de `availableActions`; nada é deduzido do estado no cliente.
 *
 * As abas entregues são as que têm backend nesta sprint: visão geral, períodos e histórico. Tickets e
 * registros de horas pertencem a `007` e `008`, cujas telas ainda não existem — uma aba vazia
 * prometeria conteúdo que não há.
 */
@Component({
  selector: 'dt-contract-detail-page',
  imports: [
    RouterLink,
    ButtonModule,
    ConsumptionGaugeComponent,
    ContractStatusBadgeComponent,
    DurationPipe,
    MessageModule,
    SkeletonModule,
    TableModule,
    TabsModule,
    TransitionDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ContractDetailStore],
  template: `
    <nav
      class="dt-contract__breadcrumb"
      i18n-aria-label="@@breadcrumb.label"
      aria-label="Trilha de navegação"
    >
      <a routerLink="/contracts" i18n="@@contracts.title">Contratos</a>
      @if (store.contract(); as contract) {
        <span aria-hidden="true">/</span>
        <span>{{ contract.code }}</span>
      }
    </nav>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading() && store.contract() === null) {
      <p-skeleton height="14rem" />
    } @else if (store.contract(); as contract) {
      <header class="dt-contract__header">
        <div>
          <h1 class="dt-contract__title">
            {{ contract.name }}
            <dt-contract-status-badge [status]="contract.status" />
          </h1>
          <p class="dt-contract__subtitle">
            <a [routerLink]="['/clients', contract.client.id]">{{ contract.client.name }}</a>
            <span aria-hidden="true">·</span>
            @if (contract.monthlyMinutes) {
              <span>{{ contract.monthlyMinutes | duration }}</span>
              <span i18n="@@contract.perMonth">por mês</span>
            } @else {
              <span i18n="@@contract.type.hourlyOpen">Por hora</span>
            }
            <span aria-hidden="true">·</span>
            <span i18n="@@contract.billingCycle">Ciclo dia {{ contract.billingDay }}</span>
          </p>
        </div>

        <div class="dt-contract__actions">
          @if (store.canUpdate()) {
            <p-button
              i18n-label="@@action.edit"
              label="Editar"
              icon="pi pi-pencil"
              [routerLink]="['/contracts', contract.id, 'edit']"
            />
          }
          @if (store.canActivate()) {
            <p-button
              i18n-label="@@contract.activate.action"
              label="Ativar"
              [loading]="store.saving()"
              (onClick)="activate()"
            />
          }
          @if (store.canResume()) {
            <p-button
              i18n-label="@@contract.resume.action"
              label="Retomar"
              [loading]="store.saving()"
              (onClick)="resume()"
            />
          }
          @if (store.canSuspend()) {
            <p-button
              i18n-label="@@contract.suspend.action"
              label="Suspender"
              severity="secondary"
              [outlined]="true"
              (onClick)="openTransition('SUSPEND')"
            />
          }
          @if (store.canEnd()) {
            <p-button
              i18n-label="@@contract.end.action"
              label="Encerrar"
              severity="secondary"
              [outlined]="true"
              (onClick)="openTransition('END')"
            />
          }
          @if (store.canCancel()) {
            <p-button
              i18n-label="@@contract.cancel.action"
              label="Cancelar contrato"
              severity="danger"
              [text]="true"
              (onClick)="openTransition('CANCEL')"
            />
          }
          @if (store.canDelete()) {
            <p-button
              i18n-label="@@action.delete"
              label="Excluir"
              severity="danger"
              [text]="true"
              [loading]="store.saving()"
              (onClick)="remove()"
            />
          }
        </div>
      </header>

      <!-- Banners de estado (P14, "estados especiais"). -->
      @if (contract.status === 'DRAFT') {
        <p-message severity="info" styleClass="w-full mb-3">
          <span i18n="@@contract.banner.draft">
            Este contrato ainda não está ativo: nenhum período foi gerado e não é possível registrar
            horas. Ative-o para começar.
          </span>
        </p-message>
      } @else if (contract.status === 'SUSPENDED') {
        <p-message severity="warn" styleClass="w-full mb-3">
          <span i18n="@@contract.banner.suspended">
            Contrato suspenso: o período aberto continua aberto, mas nenhum período novo é gerado.
          </span>
        </p-message>
      } @else if (store.isTerminal()) {
        <p-message severity="secondary" styleClass="w-full mb-3">
          <span i18n="@@contract.banner.terminal">
            Contrato encerrado ou cancelado. Os registros são preservados para consulta e nenhuma
            alteração é possível.
          </span>
        </p-message>
      }

      <p-tabs value="overview">
        <p-tablist>
          <p-tab value="overview" i18n="@@contract.tab.overview">Visão geral</p-tab>
          <p-tab value="periods" i18n="@@contract.tab.periods">Períodos</p-tab>
          <p-tab value="history" i18n="@@contract.tab.history">Histórico</p-tab>
        </p-tablist>

        <p-tabpanels>
          <p-tabpanel value="overview">
            @if (contract.currentPeriod; as period) {
              <section class="dt-contract__overview">
                <dt-consumption-gauge [rate]="consumptionRate()" />
                <dl class="dt-contract__facts">
                  <dt i18n="@@contract.currentPeriod">Período atual</dt>
                  <dd>
                    <a [routerLink]="['/contracts', contract.id, 'periods', period.id]">
                      {{ period.label }}
                    </a>
                  </dd>
                  <dt i18n="@@contract.contracted">Contratado</dt>
                  <dd>{{ period.contractedMinutes | duration }}</dd>
                  <dt i18n="@@contract.carriedIn">Transportado</dt>
                  <dd>{{ period.carriedInMinutes | duration }}</dd>
                  <dt i18n="@@contract.consumed">Consumido</dt>
                  <dd>{{ period.consumedMinutes | duration }}</dd>
                  <dt i18n="@@contract.nonBillable">Não faturável</dt>
                  <dd>{{ period.nonBillableMinutes | duration }}</dd>
                </dl>
              </section>
            } @else {
              <!-- CE-D-05: sem período aberto não há saldo; o texto diz o motivo em vez de "0". -->
              <p class="dt-contract__empty" i18n="@@contract.overview.noPeriod">
                Não há período aberto. O saldo aparece assim que o contrato estiver ativo com um
                período em curso.
              </p>
            }
          </p-tabpanel>

          <p-tabpanel value="periods">
            @if (store.periods().length === 0) {
              <p class="dt-contract__empty" i18n="@@contract.periods.empty">
                Nenhum período gerado até agora.
              </p>
            } @else {
              <div class="dt-contract__table">
                <p-table [value]="periodRows()" [rowHover]="true" [dataKey]="'id'">
                  <ng-template #header>
                    <tr>
                      <th scope="col" i18n="@@period.column.label">Período</th>
                      <th scope="col" i18n="@@period.column.range">Vigência</th>
                      <th scope="col" i18n="@@contract.contracted">Contratado</th>
                      <th scope="col" i18n="@@contract.consumed">Consumido</th>
                      <th scope="col" i18n="@@period.column.status">Situação</th>
                    </tr>
                  </ng-template>
                  <ng-template #body let-period>
                    <tr>
                      <td>
                        <a [routerLink]="['/contracts', contract.id, 'periods', period.id]">
                          {{ period.label }}
                        </a>
                      </td>
                      <td>{{ period.startDate }} — {{ period.endDate }}</td>
                      <td>{{ period.contractedMinutes | duration }}</td>
                      <td>{{ period.consumedMinutes | duration }}</td>
                      <td>{{ period.status }}</td>
                    </tr>
                  </ng-template>
                </p-table>
              </div>
            }
          </p-tabpanel>

          <p-tabpanel value="history">
            @if (store.history(); as history) {
              <p class="dt-contract__totals">
                <span i18n="@@contract.history.summary">
                  {{ history.aggregates.periodsCount }} períodos ·
                  {{ history.aggregates.periodsWithOverage }} com excedente
                </span>
              </p>
              <div class="dt-contract__table">
                <p-table [value]="historyRows()" [dataKey]="'sequence'">
                  <ng-template #header>
                    <tr>
                      <th scope="col" i18n="@@period.column.label">Período</th>
                      <th scope="col" i18n="@@contract.consumed">Consumido</th>
                      <th scope="col" i18n="@@contract.remaining">Saldo</th>
                      <th scope="col" i18n="@@contract.overage">Excedente</th>
                      <th scope="col" i18n="@@contract.carriedOut">Transportado</th>
                    </tr>
                  </ng-template>
                  <ng-template #body let-period>
                    <tr>
                      <td>{{ period.label }}</td>
                      <td>{{ period.consumedMinutes | duration }}</td>
                      <td>{{ period.remainingMinutes | duration }}</td>
                      <td>{{ period.overageMinutes | duration }}</td>
                      <td>{{ period.carriedOutMinutes | duration }}</td>
                    </tr>
                  </ng-template>
                </p-table>
              </div>
            } @else {
              <p class="dt-contract__empty" i18n="@@contract.history.empty">
                O histórico aparece depois do primeiro período fechado.
              </p>
            }
          </p-tabpanel>
        </p-tabpanels>
      </p-tabs>

      <dt-transition-dialog
        [visible]="transitionOpen()"
        [kind]="transitionKind()"
        [saving]="store.saving()"
        (visibleChange)="transitionOpen.set($event)"
        (confirmed)="confirmTransition($event)"
      />
    }
  `,
  styleUrl: './contract-detail.page.scss',
})
export class ContractDetailPage {
  private readonly router = inject(Router);

  protected readonly store = inject(ContractDetailStore);

  readonly id = input.required<string>();

  protected readonly transitionOpen = signal(false);
  protected readonly transitionKind = signal<TransitionKind>('SUSPEND');

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly periodRows = computed(() => [...this.store.periods()]);

  protected readonly historyRows = computed(() => [...(this.store.history()?.periods ?? [])]);

  /**
   * Taxa de consumo do período corrente.
   *
   * Calculada sobre contratado + transportado + ajustes, que é a definição de "disponível" de
   * `011`. O endpoint de contrato não devolve a taxa pronta — só o de saldo do período —, e o
   * medidor precisa de um número; quando não há base, não há medidor.
   */
  protected readonly consumptionRate = computed(() => {
    const period = this.store.contract()?.currentPeriod;
    if (period === undefined) {
      return 0;
    }
    const available = period.contractedMinutes + period.carriedInMinutes + period.adjustmentMinutes;
    return available <= 0 ? 0 : (period.consumedMinutes / available) * 100;
  });

  constructor() {
    effect(() => {
      void this.store.load(this.id());
    });
  }

  protected openTransition(kind: TransitionKind): void {
    this.transitionKind.set(kind);
    this.transitionOpen.set(true);
  }

  protected async confirmTransition(request: ContractTransitionRequest): Promise<void> {
    const kind = this.transitionKind();
    const done =
      kind === 'SUSPEND'
        ? await this.store.suspend(request)
        : kind === 'END'
          ? await this.store.end(request)
          : await this.store.cancel(request);
    if (done) {
      this.transitionOpen.set(false);
    }
  }

  protected async activate(): Promise<void> {
    await this.store.activate();
  }

  protected async resume(): Promise<void> {
    await this.store.resume();
  }

  protected async remove(): Promise<void> {
    if (await this.store.delete()) {
      await this.router.navigate(['/contracts']);
    }
  }
}
