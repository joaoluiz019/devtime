/**
 * Markdown mínimo com sanitização por allowlist (T-007-25).
 *
 * **A ordem é a segurança:** todo o texto é escapado primeiro e só depois as construções permitidas
 * viram marcação. Assim nenhum HTML do autor sobrevive — `<script>` chega ao DOM como texto, não como
 * elemento. O caminho inverso (renderizar e depois limpar) é o que produz XSS: basta uma construção
 * esquecida na limpeza.
 *
 * A allowlist é deliberadamente curta: negrito, itálico, código, título, lista, citação, link e
 * quebra de linha. Descrições de ticket e comentários não precisam de tabela nem de imagem, e cada
 * construção a mais é uma superfície a mais para escapar do escape.
 *
 * Links só valem com esquema `http`/`https`. `javascript:` é execução disfarçada de navegação.
 */
const ESCAPE_MAP: Readonly<Record<string, string>> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ESCAPE_MAP[character] ?? character);
}

function isSafeHref(href: string): boolean {
  return /^https?:\/\//i.test(href);
}

/** Converte o subconjunto permitido em HTML já escapado. */
export function renderMarkdown(source: string): string {
  if (source.trim() === '') {
    return '';
  }

  const escaped = escapeHtml(source);
  const lines = escaped.split(/\r?\n/);
  const blocks: string[] = [];
  let paragraph: string[] = [];
  let listItems: string[] = [];
  let inCodeBlock = false;
  let codeLines: string[] = [];

  const flushParagraph = (): void => {
    if (paragraph.length > 0) {
      blocks.push(`<p>${inline(paragraph.join('<br>'))}</p>`);
      paragraph = [];
    }
  };

  const flushList = (): void => {
    if (listItems.length > 0) {
      blocks.push(`<ul>${listItems.map((item) => `<li>${inline(item)}</li>`).join('')}</ul>`);
      listItems = [];
    }
  };

  for (const line of lines) {
    if (line.trimStart().startsWith('```')) {
      if (inCodeBlock) {
        blocks.push(`<pre><code>${codeLines.join('\n')}</code></pre>`);
        codeLines = [];
      } else {
        flushParagraph();
        flushList();
      }
      inCodeBlock = !inCodeBlock;
      continue;
    }

    if (inCodeBlock) {
      // Dentro do bloco de código nada é interpretado: é literal por definição.
      codeLines.push(line);
      continue;
    }

    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading !== null) {
      flushParagraph();
      flushList();
      const level = heading[1]?.length ?? 1;
      blocks.push(`<h${level + 2}>${inline(heading[2] ?? '')}</h${level + 2}>`);
      continue;
    }

    const listItem = /^\s*[-*]\s+(.*)$/.exec(line);
    if (listItem !== null) {
      flushParagraph();
      listItems.push(listItem[1] ?? '');
      continue;
    }

    const quote = /^&gt;\s?(.*)$/.exec(line);
    if (quote !== null) {
      flushParagraph();
      flushList();
      blocks.push(`<blockquote>${inline(quote[1] ?? '')}</blockquote>`);
      continue;
    }

    if (line.trim() === '') {
      flushParagraph();
      flushList();
      continue;
    }

    paragraph.push(line);
  }

  if (inCodeBlock && codeLines.length > 0) {
    // Bloco de código não fechado: o conteúdo continua sendo literal, e perdê-lo seria pior.
    blocks.push(`<pre><code>${codeLines.join('\n')}</code></pre>`);
  }
  flushParagraph();
  flushList();

  return blocks.join('');
}

/** Construções de linha: código, negrito, itálico e link. */
function inline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>')
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (match, label: string, href: string) =>
      isSafeHref(href)
        ? `<a href="${href}" target="_blank" rel="noopener noreferrer">${label}</a>`
        : // Esquema não permitido: o rótulo permanece legível, o destino é descartado.
          label,
    );
}
