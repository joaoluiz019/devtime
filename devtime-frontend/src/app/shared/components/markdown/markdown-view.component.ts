import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { renderMarkdown } from '../../utils/markdown';

/**
 * Exibição de texto em Markdown — `dt-markdown-view` (T-007-25).
 *
 * `bypassSecurityTrustHtml` só é aceitável porque o HTML **não vem do autor**: ele é produzido por
 * `renderMarkdown`, que escapa tudo antes de aplicar a allowlist. Marcar como confiável um texto
 * qualquer do usuário seria entregar XSS.
 */
@Component({
  selector: 'dt-markdown-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="dt-markdown" [innerHTML]="html()"></div>`,
  styles: `
    .dt-markdown {
      font-size: var(--dt-text-sm);
      line-height: 1.6;
      overflow-wrap: anywhere;
    }

    .dt-markdown :is(h3, h4, h5) {
      margin: var(--dt-space-3) 0 var(--dt-space-1);
      font-size: var(--dt-text-base);
    }

    .dt-markdown p {
      margin: 0 0 var(--dt-space-2);
    }

    .dt-markdown ul {
      margin: 0 0 var(--dt-space-2);
      padding-left: var(--dt-space-5);
    }

    .dt-markdown blockquote {
      margin: 0 0 var(--dt-space-2);
      padding-left: var(--dt-space-3);
      border-left: 3px solid var(--dt-border);
      color: var(--dt-text-secondary);
    }

    .dt-markdown code {
      padding: 0 4px;
      border-radius: var(--dt-radius-sm);
      background-color: var(--dt-surface-raised);
      font-family: var(--dt-font-mono, monospace);
      font-size: var(--dt-text-xs);
    }

    .dt-markdown pre {
      margin: 0 0 var(--dt-space-2);
      padding: var(--dt-space-3);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-raised);
      overflow-x: auto;
    }

    .dt-markdown a {
      color: var(--dt-color-primary);
    }
  `,
})
export class MarkdownViewComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly source = input.required<string>();

  protected readonly html = computed<SafeHtml>(() =>
    this.sanitizer.bypassSecurityTrustHtml(renderMarkdown(this.source())),
  );
}
