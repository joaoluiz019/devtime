import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Category } from '../data/settings.model';
import { CategorySettingsPage } from './category-settings.page';

const CATEGORIES_URL = `${environment.apiBaseUrl}/categories`;

const SYSTEM: Category = {
  id: 'cat-system',
  name: 'Desenvolvimento',
  color: '#4f46e5',
  billableByDefault: true,
  active: true,
  sortOrder: 0,
  isSystem: true,
  version: 1,
};

const CUSTOM: Category = {
  ...SYSTEM,
  id: 'cat-custom',
  name: 'Pesquisa',
  isSystem: false,
  version: 2,
};

/** P30 — categorias. */
describe('CategorySettingsPage', () => {
  async function setup() {
    const result = await render(CategorySettingsPage, {
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
      ],
    });
    return {
      ...result,
      http: result.fixture.debugElement.injector.get(HttpTestingController),
      user: userEvent.setup({ advanceTimers: jest.advanceTimersByTime }),
    };
  }

  async function settle(): Promise<void> {
    for (let index = 0; index < 5; index += 1) {
      await Promise.resolve();
    }
  }

  function flush(http: HttpTestingController, categories: readonly Category[] = [SYSTEM, CUSTOM]) {
    http.expectOne((request) => request.url === CATEGORIES_URL).flush(categories);
  }

  it('RN-503: categoria de sistema não oferece exclusão', async () => {
    const { http, container } = await setup();
    flush(http);

    await screen.findByText('Desenvolvimento');
    // A de sistema traz o selo e apenas duas ações; a personalizada traz três.
    expect(screen.getByText('Padrão')).toBeVisible();
    const deleteButtons = container.querySelectorAll('[aria-label="Excluir"]');
    expect(deleteButtons).toHaveLength(1);
  });

  it('inativar usa PUT com a version corrente, sem excluir', async () => {
    const { http, user } = await setup();
    flush(http);

    const buttons = await screen.findAllByRole('button', { name: 'Inativar' });
    await user.click(buttons[0]!);
    await settle();

    const request = http.expectOne(`${CATEGORIES_URL}/cat-system`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ active: false, version: 1 });
    request.flush({ ...SYSTEM, active: false, version: 2 });
    await settle();
    flush(http, [{ ...SYSTEM, active: false, version: 2 }, CUSTOM]);
  });

  it('RN-505: a exclusão envia a categoria substituta escolhida', async () => {
    const { http, user, fixture } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: 'Excluir' }));
    await settle();

    const page = fixture.componentInstance as unknown as {
      replacementId: { set: (value: string) => void };
    };
    page.replacementId.set('cat-system');
    fixture.detectChanges();

    await user.click(screen.getByRole('button', { name: 'Excluir categoria' }));
    await settle();

    const request = http.expectOne((candidate) => candidate.url === `${CATEGORIES_URL}/cat-custom`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.params.get('replacementCategoryId')).toBe('cat-system');
    request.flush({ migratedWorkLogs: 12, migratedTo: 'cat-system' });
    await settle();
    flush(http, [SYSTEM]);
  });

  it('DEVTIME-2603 é traduzido para a orientação de escolher substituta', async () => {
    const { http, user } = await setup();
    flush(http);

    await user.click(await screen.findByRole('button', { name: 'Excluir' }));
    await settle();
    await user.click(screen.getByRole('button', { name: 'Excluir categoria' }));
    await settle();

    http
      .expectOne((candidate) => candidate.url === `${CATEGORIES_URL}/cat-custom`)
      .flush(
        {
          type: 'about:blank',
          title: 'Conflito',
          status: 409,
          code: 'DEVTIME-2603',
          detail: 'registros vinculados',
          traceId: 'abc',
        },
        { status: 409, statusText: 'Conflict' },
      );

    expect(
      await screen.findByText(
        'Existem registros nesta categoria. Escolha uma categoria substituta.',
      ),
    ).toBeVisible();
  });
});
