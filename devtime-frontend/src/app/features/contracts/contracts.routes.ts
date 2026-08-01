import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';

/**
 * Rotas de contratos (§21.1 de `specs/011-bank-hours/spec.md`).
 *
 * Apenas P16 existe nesta sprint. A lista (P13), o detalhe (P14) e o formulário (P15) de contrato
 * pertencem a `004`, cujo frontend ainda não foi entregue — declará-las aqui apontando para
 * componentes inexistentes quebraria o build.
 *
 * FR-080: rota de feature usa `loadComponent`. FR-082: rota com permissão específica declara
 * `permissionGuard`.
 */
const contractRoutes: Routes = [
  {
    path: ':id/periods/:periodId',
    title: $localize`:@@period.detail.title:Detalhe do período`,
    canActivate: [permissionGuard(['PERIOD_VIEW'])],
    loadComponent: () =>
      import('./pages/period-detail.page').then((module) => module.PeriodDetailPage),
  },
];

export default contractRoutes;
