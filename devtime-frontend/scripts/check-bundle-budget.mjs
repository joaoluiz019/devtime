/**
 * FR-167: o pacote inicial não passa de 500 kB comprimidos.
 *
 * O orçamento de `angular.json` mede bytes **crus**, que é outra grandeza: o build acusava 1,15 MB
 * de "initial" enquanto o que o navegador realmente baixa são 90 kB. Um aviso que dispara sem que
 * o requisito esteja violado ensina a equipe a ignorá-lo, e o requisito passa a não ter guardião.
 * Este script mede o que FR-167 escreve — a transferência comprimida do grafo inicial, o que o
 * `index.html` carrega antes de qualquer rota preguiçosa.
 */
import { gzipSync } from 'node:zlib';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const LIMITE_KB = 500;
const RAIZ = 'dist/devtime-frontend/browser';

/** Localiza o diretório do locale emitido (`localize: true` gera um nível por locale). */
function diretorioDoBuild() {
  const entradas = readdirSync(RAIZ).filter((nome) => statSync(join(RAIZ, nome)).isDirectory());
  const comIndex = entradas.find((nome) => {
    try {
      readFileSync(join(RAIZ, nome, 'index.html'));
      return true;
    } catch {
      return false;
    }
  });
  return comIndex ? join(RAIZ, comIndex) : RAIZ;
}

const diretorio = diretorioDoBuild();
const indice = readFileSync(join(diretorio, 'index.html'), 'utf8');

// `src`, `href` e `modulepreload` cobrem os três modos pelos quais o Angular declara o que é
// carregado na primeira tela.
const referencias = new Set(
  [...indice.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)].map((achado) =>
    achado[1].replace(/^\.?\//, ''),
  ),
);

if (referencias.size === 0) {
  console.error('Nenhum arquivo inicial encontrado em index.html — o build mudou de formato?');
  process.exit(1);
}

let cru = 0;
let comprimido = 0;
for (const arquivo of referencias) {
  const conteudo = readFileSync(join(diretorio, arquivo));
  cru += conteudo.length;
  comprimido += gzipSync(conteudo).length;
}

const kb = comprimido / 1024;
console.log(
  `FR-167 — pacote inicial: ${kb.toFixed(1)} kB gzip ` +
    `(${(cru / 1024).toFixed(1)} kB crus, ${referencias.size} arquivos), limite ${LIMITE_KB} kB`,
);

if (kb > LIMITE_KB) {
  console.error(`FR-167 violado: ${kb.toFixed(1)} kB gzip acima do limite de ${LIMITE_KB} kB.`);
  process.exit(1);
}
