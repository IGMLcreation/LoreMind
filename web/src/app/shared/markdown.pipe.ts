import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

/**
 * Pipe markdown → HTML sanitise. Utilise pour le rendu des reponses IA.
 * Combine marked (parser) + DOMPurify (anti-XSS) puis bypass la sanitization
 * Angular puisque le contenu est deja nettoye.
 *
 * Configure en mode synchrone (`async: false`) pour eviter une Promise.
 */
@Pipe({ name: 'markdown', standalone: true })
export class MarkdownPipe implements PipeTransform {
  constructor(private readonly sanitizer: DomSanitizer) {}

  transform(value: string | null | undefined): SafeHtml {
    if (!value) return '';
    const html = marked.parse(value, { async: false, gfm: true, breaks: true }) as string;
    const clean = DOMPurify.sanitize(html);
    return this.sanitizer.bypassSecurityTrustHtml(clean);
  }
}
