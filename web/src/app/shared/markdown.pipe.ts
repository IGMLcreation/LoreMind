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
    // Revue sécurité : le bypass est sûr ICI car le HTML vient d'être passé
    // par DOMPurify juste au-dessus (le sanitizer Angular, moins permissif,
    // casserait le rendu markdown). Ne jamais bypasser sans DOMPurify amont.
    // eslint-disable-next-line sonarjs/no-angular-bypass-sanitization
    return this.sanitizer.bypassSecurityTrustHtml(clean);
  }
}
