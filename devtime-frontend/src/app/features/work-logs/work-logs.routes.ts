import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

/**
 * Rotas de registros de horas (P21 a P23).
 *
 * `calendar` e `new` vêm antes de `:id/edit` para não serem capturados como identificador.
 * Não há rota de detalhe: um registro de horas se lê na lista e se altera no formulário; uma página
 * só para exibir cinco campos custaria uma navegação sem acrescentar nada.
 */
const workLogRoutes: Routes = [
  {
    path: '',
    title: $localize`:@@workLogs.title:Registros de horas`,
    canActivate: [permissionGuard(['WORKLOG_VIEW_OWN', 'WORKLOG_VIEW_ANY'])],
    loadComponent: () =>
      import('./pages/work-log-list.page').then((module) => module.WorkLogListPage),
  },
  {
    path: 'calendar',
    title: $localize`:@@workLogs.calendar.title:Calendário de horas`,
    canActivate: [permissionGuard(['WORKLOG_VIEW_OWN', 'WORKLOG_VIEW_ANY'])],
    loadComponent: () =>
      import('./pages/work-log-calendar.page').then((module) => module.WorkLogCalendarPage),
  },
  {
    path: 'new',
    title: $localize`:@@workLog.form.newTitle:Lançar horas`,
    canActivate: [permissionGuard(['WORKLOG_CREATE'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./pages/work-log-form.page').then((module) => module.WorkLogFormPage),
  },
  {
    path: ':id/edit',
    title: $localize`:@@workLog.form.editTitle:Editar registro de horas`,
    canActivate: [permissionGuard(['WORKLOG_UPDATE_OWN', 'WORKLOG_UPDATE_ANY'])],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () =>
      import('./pages/work-log-form.page').then((module) => module.WorkLogFormPage),
  },
];

export default workLogRoutes;
