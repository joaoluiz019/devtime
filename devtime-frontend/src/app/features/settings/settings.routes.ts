import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';
import { SettingsLayoutComponent } from './settings-layout.component';

/**
 * Rotas de configurações (P26 a P33), todas sob o layout L9.
 *
 * Perfil, preferências e notificações não declaram permissão: são do próprio usuário e existem para
 * qualquer papel. As demais declaram a sua (FR-082) — e a navegação lateral oculta o que a pessoa
 * não pode abrir, evitando um `403` que ela não teria como prever.
 */
const settingsRoutes: Routes = [
  {
    path: '',
    component: SettingsLayoutComponent,
    children: [
      { path: '', redirectTo: 'profile', pathMatch: 'full' },
      {
        path: 'profile',
        title: $localize`:@@settings.profile:Perfil`,
        loadComponent: () =>
          import('./pages/profile-settings.page').then((module) => module.ProfileSettingsPage),
      },
      {
        path: 'preferences',
        title: $localize`:@@settings.preferences:Preferências`,
        loadComponent: () =>
          import('./pages/preferences-settings.page').then(
            (module) => module.PreferencesSettingsPage,
          ),
      },
      {
        path: 'notifications',
        title: $localize`:@@settings.notifications:Notificações`,
        loadComponent: () =>
          import('./pages/notification-settings.page').then(
            (module) => module.NotificationSettingsPage,
          ),
      },
      {
        path: 'organization',
        title: $localize`:@@settings.organization:Organização`,
        canActivate: [permissionGuard(['TENANT_UPDATE'])],
        loadComponent: () =>
          import('./pages/organization-settings.page').then(
            (module) => module.OrganizationSettingsPage,
          ),
      },
      {
        path: 'team',
        title: $localize`:@@settings.team:Equipe`,
        canActivate: [permissionGuard(['MEMBER_VIEW'])],
        loadComponent: () =>
          import('./pages/team-settings.page').then((module) => module.TeamSettingsPage),
      },
      {
        path: 'categories',
        title: $localize`:@@settings.categories:Categorias`,
        canActivate: [permissionGuard(['CATEGORY_MANAGE'])],
        loadComponent: () =>
          import('./pages/category-settings.page').then((module) => module.CategorySettingsPage),
      },
      {
        path: 'tags',
        title: $localize`:@@settings.tags:Etiquetas`,
        canActivate: [permissionGuard(['TAG_MANAGE'])],
        loadComponent: () =>
          import('./pages/tag-settings.page').then((module) => module.TagSettingsPage),
      },
      {
        path: 'audit',
        title: $localize`:@@settings.audit:Auditoria`,
        canActivate: [permissionGuard(['TENANT_AUDIT_VIEW'])],
        loadComponent: () =>
          import('./pages/audit-settings.page').then((module) => module.AuditSettingsPage),
      },
    ],
  },
];

export default settingsRoutes;
