import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

/**
 * Rotas de clientes (P10 a P12).
 *
 * FR-082: a permissão da área é verificada na rota; FR-083 lembra que isso é ergonomia — a decisão
 * real é do backend, que responde `403` de qualquer forma.
 *
 * `new` vem **antes** de `:id`: sem essa ordem, `/clients/new` casaria com a rota de detalhe e
 * pediria ao servidor um cliente de identificador "new".
 */
const clientRoutes: Routes = [
  {
    path: '',
    title: $localize`:@@clients.title:Clientes`,
    canActivate: [permissionGuard(['CLIENT_VIEW'])],
    loadComponent: () => import('./pages/client-list.page').then((module) => module.ClientListPage),
  },
  {
    path: 'new',
    title: $localize`:@@client.form.newTitle:Novo cliente`,
    canActivate: [permissionGuard(['CLIENT_CREATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./pages/client-form.page').then((module) => module.ClientFormPage),
  },
  {
    path: ':id/edit',
    title: $localize`:@@client.form.editTitle:Editar cliente`,
    canActivate: [permissionGuard(['CLIENT_UPDATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./pages/client-form.page').then((module) => module.ClientFormPage),
  },
  {
    path: ':id',
    canActivate: [permissionGuard(['CLIENT_VIEW'])],
    loadComponent: () =>
      import('./pages/client-detail.page').then((module) => module.ClientDetailPage),
  },
];

export default clientRoutes;
