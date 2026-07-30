import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import {
  PreloadAllModules,
  provideRouter,
  withInMemoryScrolling,
  withPreloading,
} from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { MessageService } from 'primeng/api';
import Aura from '@primeuix/themes/aura';
import { routes } from './app.routes';
import { provideGlobalErrorHandler } from './core/error/global-error.handler';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';
import { retryInterceptor } from './core/http/retry.interceptor';
import { tenantInterceptor } from './core/http/tenant.interceptor';

/**
 * Providers da aplicação.
 *
 * FR-003 / FR-01: `core` é provido uma única vez, aqui.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(
      routes,
      // PF-09: pré-carregamento das rotas prováveis após a carga inicial.
      withPreloading(PreloadAllModules),
      // FR-088: a posição de rolagem é restaurada ao navegar de volta.
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    provideHttpClient(
      withFetch(),
      // O backend emite o cookie XSRF-TOKEN (CookieCsrfTokenRepository.withHttpOnlyFalse) e espera
      // o valor de volta no header — security.md §7.1.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      // Ordem obrigatória de frontend.md §7.2: loading → auth → tenant → retry → error.
      withInterceptors([
        loadingInterceptor,
        authInterceptor,
        tenantInterceptor,
        retryInterceptor,
        errorInterceptor,
      ]),
    ),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          // O tema escuro é controlado por data-theme no elemento raiz, o mesmo seletor usado pelos
          // tokens --dt-* — uma única fonte de verdade para o tema (DK-01).
          darkModeSelector: '[data-theme="dark"]',
          cssLayer: {
            name: 'primeng',
            // Ordem de camadas: os tokens do design system precisam vencer os do PrimeNG sem
            // recorrer a !important, que FR-131 proíbe.
            order: 'primeng, dt-tokens',
          },
        },
      },
      ripple: false,
    }),
    MessageService,
    provideGlobalErrorHandler(),
  ],
};
