import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

/**
 * Rotas de tickets (P17 a P20).
 *
 * `board`, `new` e `:id/edit` vêm antes de `:id`: sem essa ordem, o detalhe capturaria os três e
 * pediria ao servidor um ticket de identificador "board".
 */
const ticketRoutes: Routes = [
  {
    path: '',
    title: $localize`:@@tickets.title:Tickets`,
    canActivate: [permissionGuard(['TICKET_VIEW'])],
    loadComponent: () => import('./pages/ticket-list.page').then((module) => module.TicketListPage),
  },
  {
    path: 'board',
    title: $localize`:@@tickets.board.title:Quadro de tickets`,
    canActivate: [permissionGuard(['TICKET_VIEW'])],
    loadComponent: () =>
      import('./pages/ticket-board.page').then((module) => module.TicketBoardPage),
  },
  {
    path: 'new',
    title: $localize`:@@ticket.form.newTitle:Novo ticket`,
    canActivate: [permissionGuard(['TICKET_CREATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./pages/ticket-form.page').then((module) => module.TicketFormPage),
  },
  {
    path: ':id/edit',
    title: $localize`:@@ticket.form.editTitle:Editar ticket`,
    canActivate: [permissionGuard(['TICKET_UPDATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./pages/ticket-form.page').then((module) => module.TicketFormPage),
  },
  {
    path: ':id',
    canActivate: [permissionGuard(['TICKET_VIEW'])],
    loadComponent: () =>
      import('./pages/ticket-detail.page').then((module) => module.TicketDetailPage),
  },
];

export default ticketRoutes;
