import { escapeHtml, renderMarkdown } from './markdown';

/**
 * Sanitização por allowlist (T-007-25).
 *
 * Os testes de segurança vêm primeiro porque são a razão de o renderizador existir: um `<script>` na
 * descrição de um ticket roda no navegador de quem abrir o ticket, com a sessão dessa pessoa.
 */
describe('renderMarkdown — segurança', () => {
  it('escapa HTML do autor em vez de renderizá-lo', () => {
    const html = renderMarkdown('<script>alert(1)</script>');

    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('recusa link com esquema javascript, preservando o texto', () => {
    const html = renderMarkdown('[clique](javascript:alert(1))');

    expect(html).not.toContain('javascript:');
    expect(html).toContain('clique');
  });

  it('não cria elemento a partir de tag do autor', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)">');

    // O texto "onerror" sobrevive como conteúdo legível; o que não pode existir é a tag.
    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img');
  });

  it('escapa aspas e apóstrofos, que fechariam um atributo', () => {
    expect(escapeHtml(`" '`)).toBe('&quot; &#39;');
  });
});

describe('renderMarkdown — construções permitidas', () => {
  it('converte negrito, itálico e código', () => {
    const html = renderMarkdown('**forte** *ênfase* `codigo`');

    expect(html).toContain('<strong>forte</strong>');
    expect(html).toContain('<em>ênfase</em>');
    expect(html).toContain('<code>codigo</code>');
  });

  it('converte lista e título', () => {
    const html = renderMarkdown('## Passos\n- um\n- dois');

    expect(html).toContain('<h4>Passos</h4>');
    expect(html).toContain('<li>um</li>');
    expect(html).toContain('<li>dois</li>');
  });

  it('mantém o bloco de código literal', () => {
    const html = renderMarkdown('```\n**não** vira negrito\n```');

    expect(html).toContain('<pre><code>**não** vira negrito</code></pre>');
  });

  it('abre link permitido em nova aba, sem vazar o referenciador', () => {
    const html = renderMarkdown('[docs](https://exemplo.com)');

    expect(html).toContain('href="https://exemplo.com"');
    expect(html).toContain('rel="noopener noreferrer"');
  });

  it('texto vazio não produz marcação', () => {
    expect(renderMarkdown('   ')).toBe('');
  });
});
