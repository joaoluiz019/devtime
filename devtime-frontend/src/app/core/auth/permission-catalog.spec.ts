import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Toda permissão exigida pela interface existe no catálogo do servidor.
 *
 * O cliente compara textos: `hasPermission('TICKET_UPDATE')` é `false` quando a permissão se chama
 * `TICKET_UPDATE_ANY` do outro lado — e `false` para **todos** os papéis, inclusive o proprietário.
 * O sintoma é cruel de diagnosticar: o botão some ou a rota cai em "sem permissão", nenhuma
 * requisição sai, e não há registro nenhum no backend porque nada chegou até lá. Foi exatamente
 * assim que `TICKET_UPDATE` passou despercebido.
 *
 * Este teste lê o enum `Permission` do backend, que é a fonte da verdade (TK-03: as permissões são
 * derivadas do papel no servidor, nunca no cliente), e falha se a interface exigir um nome que não
 * existe lá.
 */
describe('catálogo de permissões', () => {
  const ENUM_PATH = '../devtime-backend/src/main/java/com/devtime/shared/security/Permission.java';
  const APP_DIR = 'src/app';

  function backendPermissions(): Set<string> {
    const source = readFileSync(ENUM_PATH, 'utf8');
    const body = source.slice(source.indexOf('enum Permission'));
    return new Set([...body.matchAll(/\b([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\b/g)].map((m) => m[1]));
  }

  function sourceFiles(dir: string, found: string[] = []): string[] {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry);
      if (statSync(full).isDirectory()) {
        sourceFiles(full, found);
      } else if (/\.(ts|html)$/.test(entry) && !/\.spec\.ts$/.test(entry)) {
        found.push(full);
      }
    }
    return found;
  }

  /** Só os três pontos em que um texto é usado **como permissão**; qualquer outro seria ruído. */
  function requiredPermissions(): Map<string, string> {
    const required = new Map<string, string>();
    for (const file of sourceFiles(APP_DIR)) {
      const content = readFileSync(file, 'utf8');
      for (const match of content.matchAll(/hasPermission\(\s*['"]([A-Z0-9_]+)['"]/g)) {
        required.set(match[1], file);
      }
      for (const guard of content.matchAll(/permissionGuard\(\s*\[([^\]]+)\]/g)) {
        for (const name of guard[1].matchAll(/['"]([A-Z0-9_]+)['"]/g)) {
          required.set(name[1], file);
        }
      }
      for (const match of content.matchAll(/permission:\s*['"]([A-Z0-9_]+)['"]/g)) {
        required.set(match[1], file);
      }
    }
    return required;
  }

  it('não exige nenhuma permissão inexistente no backend', () => {
    const backend = backendPermissions();
    expect(backend.size).toBeGreaterThan(20);

    const desconhecidas = [...requiredPermissions().entries()]
      .filter(([name]) => !backend.has(name))
      .map(([name, file]) => `${name} (${file})`);

    expect(desconhecidas).toEqual([]);
  });
});
