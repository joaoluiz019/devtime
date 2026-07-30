import { computed, effect, Injectable, signal } from '@angular/core';

/** Preferência de tema do usuário (entities.md §6.2.1). */
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';

const STORAGE_KEY = 'dt.theme';

/**
 * Tema claro/escuro (design-system.md §15).
 *
 * DK-01: ativado por preferência do usuário ou do sistema operacional. DS-10: os dois temas são
 * equivalentes, não um secundário.
 *
 * FR-052 proíbe persistir **estado de negócio** em `localStorage`. A preferência de tema não é estado
 * de negócio: é preferência de apresentação, e guardá-la evita o flash de tema errado no carregamento.
 * O valor canônico continua sendo `user.preferences.theme` no servidor, sincronizado pela feature de
 * perfil; este armazenamento é apenas cache local.
 */
@Injectable({ providedIn: 'root' })
export class ThemeStore {
  private readonly _preference = signal<ThemePreference>(readStoredPreference());
  private readonly _systemPrefersDark = signal(systemPrefersDark());

  readonly preference = this._preference.asReadonly();

  readonly isDark = computed(() => {
    const preference = this._preference();
    if (preference === 'SYSTEM') {
      return this._systemPrefersDark();
    }
    return preference === 'DARK';
  });

  constructor() {
    this.watchSystemPreference();
    // Aplicar o atributo no elemento raiz é efeito colateral legítimo: o CSS depende dele, e nenhum
    // componente deve manipular o documento diretamente (FR-034).
    effect(() => this.applyToDocument(this.isDark()));
  }

  setPreference(preference: ThemePreference): void {
    this._preference.set(preference);
    persistPreference(preference);
  }

  /** Alterna entre claro e escuro, saindo de SYSTEM para uma escolha explícita. */
  toggle(): void {
    this.setPreference(this.isDark() ? 'LIGHT' : 'DARK');
  }

  private applyToDocument(isDark: boolean): void {
    document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  }

  private watchSystemPreference(): void {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    query.addEventListener('change', (event) => this._systemPrefersDark.set(event.matches));
  }
}

function systemPrefersDark(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function readStoredPreference(): ThemePreference {
  const stored = readStorage(STORAGE_KEY);
  return stored === 'LIGHT' || stored === 'DARK' || stored === 'SYSTEM' ? stored : 'SYSTEM';
}

/**
 * Acesso a `sessionStorage`/`localStorage` isolado e tolerante a falha.
 *
 * Navegador com armazenamento bloqueado lança ao acessar `localStorage`; deixar a exceção subir
 * impediria a aplicação de inicializar por causa de uma preferência visual.
 */
function readStorage(key: string): string | null {
  try {
    return globalThis.localStorage?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function persistPreference(preference: ThemePreference): void {
  try {
    globalThis.localStorage?.setItem(STORAGE_KEY, preference);
  } catch {
    // Preferência não persistida é degradação aceitável (ER-08): a sessão corrente segue correta.
  }
}
