import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

/**
 * Rotas de contratos (P13 a P16).
 *
 * A ordem importa: `new` antes de `:id`, e `:id/periods/:periodId` antes de `:id`, para que os
 * caminhos mais específicos não sejam capturados pelo detalhe.
 *
 * FR-080: `loadComponent` em toda rota de feature. FR-082: permissão declarada na rota.
 */
const contractRoutes: Routes = [
  {
    path: '',
    title: $localize`:@@contracts.title:Contratos`,
    canActivate: [permissionGuard(['CONTRACT_VIEW'])],
    loadComponent: () =>
      import('./pages/contract-list.page').then((module) => module.ContractListPage),
  },
  {
    path: 'new',
    title: $localize`:@@contract.form.newTitle:Novo contrato`,
    canActivate: [permissionGuard(['CONTRACT_CREATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./pages/contract-form.page').then((module) => module.ContractFormPage),
  },
  {
    path: ':id/edit',
    title: $localize`:@@contract.form.editTitle:Editar contrato`,
    canActivate: [permissionGuard(['CONTRACT_UPDATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./pages/contract-form.page').then((module) => module.ContractFormPage),
  },
  {
    path: ':id/periods/:periodId',
    title: $localize`:@@period.detail.title:Detalhe do período`,
    canActivate: [permissionGuard(['PERIOD_VIEW'])],
    loadComponent: () =>
      import('./pages/period-detail.page').then((module) => module.PeriodDetailPage),
  },
  {
    path: ':id',
    canActivate: [permissionGuard(['CONTRACT_VIEW'])],
    loadComponent: () =>
      import('./pages/contract-detail.page').then((module) => module.ContractDetailPage),
  },
];

export default contractRoutes;
