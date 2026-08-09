import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../core/error/problem-detail.model';
import { TimerStore } from '../../core/timer/timer.store';
import { ClientApi } from '../clients/data/client.api';
import { ContractApi } from '../contracts/data/contract.api';
import { TicketApi } from '../tickets/data/ticket.api';
import { DurationPipe } from '../../shared/pipes/duration.pipe';

/** Etapas de L10; o máximo de quatro é regra do layout, não coincidência. */
type Step = 'client' | 'contract' | 'ticket' | 'done';

/**
 * Onboarding — P08, layout L10.
 *
 * Existe para responder a CA-01 do PRD: do zero ao primeiro registro de horas em menos de cinco
 * minutos. Cada etapa cria o recurso de verdade pela API que a tela correspondente usaria — não há
 * rascunho nem estado paralelo, e é por isso que WZ-03 se cumpre sozinho: quem abandona no meio
 * encontra o cliente e o contrato já criados quando voltar.
 *
 * Cada etapa diz **por que** importa (WZ-04). "Cliente, contrato, ticket" é o modelo do produto, e
 * quem está entrando não tem por que já conhecê-lo — sem a explicação, o wizard vira formulário.
 *
 * "Pular configuração" está em todas as etapas (WZ-01/WZ-03): o wizard nunca é obrigatório, e o
 * dashboard vazio oferece os mesmos caminhos.
 */
@Component({
  selector: 'dt-onboarding-page',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    InputNumberModule,
    InputTextModule,
    MessageModule,
    DurationPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-wizard">
      <!-- WZ-02: o progresso fica sempre visível. -->
      <ol class="dt-wizard__steps" role="list">
        @for (item of steps; track item.id) {
          <li
            class="dt-wizard__step"
            [class.dt-wizard__step--current]="item.id === step()"
            [class.dt-wizard__step--done]="isDone(item.id)"
            [attr.aria-current]="item.id === step() ? 'step' : null"
          >
            <span class="dt-wizard__bullet" aria-hidden="true"></span>
            <span>{{ item.label }}</span>
          </li>
        }
      </ol>

      <div aria-live="polite">
        @if (errorMessage() !== null) {
          <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
        }
      </div>

      <section class="dt-wizard__panel">
        @switch (step()) {
          @case ('client') {
            <h1 class="dt-wizard__title" i18n="@@onboarding.client.title">Quem é o cliente?</h1>
            <p class="dt-wizard__why" i18n="@@onboarding.client.why">
              O cliente é quem contrata suas horas. É por ele que os relatórios são agrupados e
              entregues.
            </p>

            <form class="dt-wizard__form" [formGroup]="clientForm" (ngSubmit)="submitClient()">
              <div class="dt-wizard__field">
                <label for="onboarding-client-name" i18n="@@onboarding.client.name">
                  Nome do cliente
                </label>
                <input
                  id="onboarding-client-name"
                  type="text"
                  pInputText
                  formControlName="name"
                  aria-required="true"
                />
              </div>

              <div class="dt-wizard__field">
                <label for="onboarding-client-email" i18n="@@onboarding.client.email">
                  E-mail (opcional)
                </label>
                <input
                  id="onboarding-client-email"
                  type="email"
                  pInputText
                  formControlName="email"
                />
              </div>

              <div class="dt-wizard__actions">
                <p-button
                  type="button"
                  i18n-label="@@onboarding.skip"
                  label="Pular configuração"
                  severity="secondary"
                  [text]="true"
                  (onClick)="skip()"
                />
                <p-button
                  type="submit"
                  i18n-label="@@onboarding.continue"
                  label="Continuar"
                  [loading]="saving()"
                />
              </div>
            </form>
          }

          @case ('contract') {
            <h1 class="dt-wizard__title" i18n="@@onboarding.contract.title">
              Quantas horas por mês?
            </h1>
            <p class="dt-wizard__why" i18n="@@onboarding.contract.why">
              O contrato define quantas horas o cliente comprou e o que acontece com o saldo ao fim
              de cada período.
            </p>

            <form class="dt-wizard__form" [formGroup]="contractForm" (ngSubmit)="submitContract()">
              <div class="dt-wizard__field">
                <label for="onboarding-contract-name" i18n="@@onboarding.contract.name">
                  Nome do contrato
                </label>
                <input
                  id="onboarding-contract-name"
                  type="text"
                  pInputText
                  formControlName="name"
                  aria-required="true"
                />
              </div>

              <div class="dt-wizard__field">
                <label for="onboarding-contract-hours" i18n="@@onboarding.contract.hours">
                  Horas contratadas por mês
                </label>
                <p-inputNumber
                  inputId="onboarding-contract-hours"
                  formControlName="monthlyHours"
                  [min]="1"
                  [max]="744"
                  [showButtons]="true"
                />
                <small class="dt-wizard__hint">
                  <span i18n="@@onboarding.contract.preview">Equivale a</span>
                  {{ monthlyMinutes() | duration }}
                </small>
              </div>

              <div class="dt-wizard__field">
                <label for="onboarding-contract-billing" i18n="@@onboarding.contract.billingDay">
                  Dia de faturamento
                </label>
                <p-inputNumber
                  inputId="onboarding-contract-billing"
                  formControlName="billingDay"
                  [min]="1"
                  [max]="28"
                />
                <small class="dt-wizard__hint" i18n="@@onboarding.contract.billingDay.hint">
                  O período vai deste dia até o mesmo dia do mês seguinte. Até 28, para caber em
                  fevereiro.
                </small>
              </div>

              <div class="dt-wizard__actions">
                <p-button
                  type="button"
                  i18n-label="@@onboarding.skip"
                  label="Pular configuração"
                  severity="secondary"
                  [text]="true"
                  (onClick)="skip()"
                />
                <p-button
                  type="submit"
                  i18n-label="@@onboarding.continue"
                  label="Continuar"
                  [loading]="saving()"
                />
              </div>
            </form>
          }

          @case ('ticket') {
            <h1 class="dt-wizard__title" i18n="@@onboarding.ticket.title">
              No que você vai trabalhar?
            </h1>
            <p class="dt-wizard__why" i18n="@@onboarding.ticket.why">
              Todo registro de horas pertence a um ticket. É assim que o relatório diz em que o
              tempo foi gasto, e não apenas quanto.
            </p>

            <form class="dt-wizard__form" [formGroup]="ticketForm" (ngSubmit)="submitTicket()">
              <div class="dt-wizard__field">
                <label for="onboarding-ticket-title" i18n="@@onboarding.ticket.titleField">
                  Título do ticket
                </label>
                <input
                  id="onboarding-ticket-title"
                  type="text"
                  pInputText
                  formControlName="title"
                  aria-required="true"
                />
              </div>

              <div class="dt-wizard__actions">
                <p-button
                  type="button"
                  i18n-label="@@onboarding.skip"
                  label="Pular configuração"
                  severity="secondary"
                  [text]="true"
                  (onClick)="skip()"
                />
                <p-button
                  type="submit"
                  i18n-label="@@onboarding.continue"
                  label="Continuar"
                  [loading]="saving()"
                />
              </div>
            </form>
          }

          @case ('done') {
            <h1 class="dt-wizard__title" i18n="@@onboarding.done.title">Tudo pronto</h1>
            <p class="dt-wizard__why" i18n="@@onboarding.done.why">
              {{ clientName() }} tem um contrato de {{ monthlyMinutes() | duration }} por mês e um
              ticket aberto. Comece a contar o tempo agora — o cronômetro fica visível em qualquer
              tela.
            </p>

            <div class="dt-wizard__actions">
              <p-button
                type="button"
                i18n-label="@@onboarding.done.dashboard"
                label="Ir para o dashboard"
                severity="secondary"
                [text]="true"
                (onClick)="skip()"
              />
              <!-- WZ-06: o wizard termina no cronômetro, não numa tela de parabéns. -->
              <p-button
                type="button"
                i18n-label="@@onboarding.done.start"
                label="Iniciar cronômetro"
                icon="pi pi-play"
                [loading]="saving()"
                (onClick)="startTimer()"
              />
            </div>
          }
        }
      </section>
    </div>
  `,
  styleUrl: './onboarding.page.scss',
})
export class OnboardingPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly clientApi = inject(ClientApi);
  private readonly contractApi = inject(ContractApi);
  private readonly ticketApi = inject(TicketApi);
  private readonly timerStore = inject(TimerStore);
  private readonly router = inject(Router);

  protected readonly steps: readonly { id: Step; label: string }[] = [
    { id: 'client', label: $localize`:@@onboarding.step.client:Cliente` },
    { id: 'contract', label: $localize`:@@onboarding.step.contract:Contrato` },
    { id: 'ticket', label: $localize`:@@onboarding.step.ticket:Ticket` },
    { id: 'done', label: $localize`:@@onboarding.step.done:Pronto` },
  ];

  protected readonly step = signal<Step>('client');

  protected readonly clientForm = this.formBuilder.group({
    name: this.formBuilder.control('', [Validators.required, Validators.minLength(2)]),
    email: this.formBuilder.control('', [Validators.email]),
  });

  protected readonly contractForm = this.formBuilder.group({
    name: this.formBuilder.control('', [Validators.required, Validators.minLength(2)]),
    monthlyHours: this.formBuilder.control(20, [Validators.required, Validators.min(1)]),
    billingDay: this.formBuilder.control(1, [Validators.min(1), Validators.max(28)]),
  });

  protected readonly ticketForm = this.formBuilder.group({
    title: this.formBuilder.control('', [Validators.required, Validators.minLength(3)]),
  });

  private readonly _saving = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _clientId = signal<string | null>(null);
  private readonly _clientName = signal('');
  private readonly _contractId = signal<string | null>(null);
  private readonly _ticketId = signal<string | null>(null);

  protected readonly saving = this._saving.asReadonly();
  protected readonly clientName = this._clientName.asReadonly();

  /** ART-034: o contrato trafega em minutos; a pergunta é feita em horas porque é como se contrata. */
  protected readonly monthlyMinutes = computed(
    () => this.contractForm.controls.monthlyHours.value * 60,
  );

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected isDone(step: Step): boolean {
    const order = this.steps.map((item) => item.id);
    return order.indexOf(step) < order.indexOf(this.step());
  }

  protected async submitClient(): Promise<void> {
    if (this.clientForm.invalid) {
      document.getElementById('onboarding-client-name')?.focus();
      return;
    }
    const value = this.clientForm.getRawValue();
    await this.run(async () => {
      const client = await firstValueFrom(
        this.clientApi.create({
          name: value.name.trim(),
          email: value.email.trim() === '' ? undefined : value.email.trim(),
        }),
      );
      this._clientId.set(client.id);
      this._clientName.set(client.name);
      // O nome do contrato começa preenchido: quem tem um cliente só raramente inventa outro nome.
      this.contractForm.controls.name.setValue(client.name);
      this.step.set('contract');
    });
  }

  /**
   * Cria o contrato já `DRAFT`, como qualquer contrato novo.
   *
   * A ativação — e com ela a geração do primeiro período — é decisão da tela de contratos, e não do
   * onboarding: ativar por baixo dos panos faria o primeiro período nascer numa data que ninguém
   * escolheu.
   */
  protected async submitContract(): Promise<void> {
    const clientId = this._clientId();
    if (this.contractForm.invalid || clientId === null) {
      document.getElementById('onboarding-contract-name')?.focus();
      return;
    }
    const value = this.contractForm.getRawValue();
    await this.run(async () => {
      const contract = await firstValueFrom(
        this.contractApi.create({
          clientId,
          name: value.name.trim(),
          type: 'MONTHLY_HOURS',
          monthlyMinutes: value.monthlyHours * 60,
          billingDay: value.billingDay,
          startDate: today(),
        }),
      );
      this._contractId.set(contract.id);
      this.step.set('ticket');
    });
  }

  protected async submitTicket(): Promise<void> {
    const contractId = this._contractId();
    if (this.ticketForm.invalid || contractId === null) {
      document.getElementById('onboarding-ticket-title')?.focus();
      return;
    }
    const value = this.ticketForm.getRawValue();
    await this.run(async () => {
      const ticket = await firstValueFrom(
        this.ticketApi.create({ contractId, title: value.title.trim() }),
      );
      this._ticketId.set(ticket.id);
      this.step.set('done');
    });
  }

  /** WZ-05: o cronômetro começa no ticket recém-criado e a pessoa cai no ticket, já contando. */
  protected async startTimer(): Promise<void> {
    const ticketId = this._ticketId();
    if (ticketId === null) {
      await this.skip();
      return;
    }
    this._saving.set(true);
    const started = await this.timerStore.start({ ticketId });
    this._saving.set(false);
    await this.router.navigate([started ? `/tickets/${ticketId}` : '/dashboard']);
  }

  /** WZ-01/WZ-04: pular leva ao dashboard, que oferece os mesmos caminhos em estado vazio. */
  protected async skip(): Promise<void> {
    await this.router.navigate(['/dashboard']);
  }

  private async run(operation: () => Promise<void>): Promise<void> {
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation();
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._saving.set(false);
    }
  }
}

/** Data local no formato ISO que o backend espera; `toISOString` daria o dia em UTC. */
function today(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}
