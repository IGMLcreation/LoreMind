import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, BookOpen } from 'lucide-angular';
import { TemplateField, ImageLayout } from '../../services/template.model';

/** Section rendue dans la vue, dans l'ordre du template. Discriminee par `kind`. */
export type RenderedSection =
  | { kind: 'TEXT'; name: string; value: string }
  | { kind: 'NUMBER_GROUP'; entries: { label: string; value: string }[] }
  | { kind: 'KEY_VALUE_LIST'; name: string; entries: { label: string; value: string }[] }
  | { kind: 'IMAGE'; name: string; ids: string[]; layout: ImageLayout };
import { ImageService } from '../../services/image.service';
import { ImageGalleryComponent } from '../image-gallery/image-gallery.component';

/**
 * Affichage type "WorldAnvil" d'une fiche PJ ou PNJ.
 *
 * Layout :
 *  - Bandeau (headerImageId) en haut, pleine largeur
 *  - Bloc 2 colonnes : portrait a gauche, infos textuelles a droite
 *  - Sections suivantes pour chaque champ template TEXT/NUMBER/IMAGE
 *  - Drop cap sur la 1re lettre du 1er paragraphe TEXT
 *
 * Composant pur de presentation : ne fetche rien, recoit (persona, templateFields).
 */
export interface PersonaLike {
  name: string;
  portraitImageId?: string | null;
  headerImageId?: string | null;
  values?: Record<string, string>;
  imageValues?: Record<string, string[]>;
  keyValueValues?: Record<string, Record<string, string>>;
}

@Component({
  selector: 'app-persona-view',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, ImageGalleryComponent],
  templateUrl: './persona-view.component.html',
  styleUrls: ['./persona-view.component.scss']
})
export class PersonaViewComponent {
  readonly BookOpen = BookOpen;

  @Input() persona: PersonaLike | null = null;
  @Input() templateFields: TemplateField[] = [];

  /** Sous-titre optionnel sous le nom (ex: "Champion d'Aerimor"). */
  @Input() subtitle?: string;

  constructor(private imageService: ImageService) {}

  contentUrl(id: string): string {
    return this.imageService.contentUrl(id);
  }

  /**
   * Decompose la fiche en (heroBadges, sections) en un seul passage :
   *  - NUMBER consecutifs groupes : 2+ → NUMBER_GROUP en section ; 1 isole → badge hero.
   *  - TEXT / KEY_VALUE_LIST / IMAGE : sections dans l'ordre du template.
   * Le calcul est fait par rendered() et cache via le get pour eviter les
   * recalculs multiples par cycle de change detection.
   */
  private rendered(): { heroBadges: { label: string; value: string }[]; sections: RenderedSection[] } {
    const sections: RenderedSection[] = [];
    const heroBadges: { label: string; value: string }[] = [];
    let numberBuffer: { label: string; value: string }[] = [];

    const flushNumberBuffer = () => {
      if (numberBuffer.length === 1) {
        heroBadges.push(numberBuffer[0]);
      } else if (numberBuffer.length > 1) {
        sections.push({ kind: 'NUMBER_GROUP', entries: numberBuffer });
      }
      numberBuffer = [];
    };

    for (const f of this.templateFields) {
      if (f.type === 'NUMBER') {
        const value = this.persona?.values?.[f.name] ?? '';
        if (value.trim()) numberBuffer.push({ label: f.name, value });
        continue;
      }
      flushNumberBuffer();
      if (f.type === 'TEXT') {
        const value = this.persona?.values?.[f.name] ?? '';
        if (value.trim()) sections.push({ kind: 'TEXT', name: f.name, value });
      } else if (f.type === 'KEY_VALUE_LIST') {
        const inner = this.persona?.keyValueValues?.[f.name] ?? {};
        const labels = f.labels ?? [];
        const entries = labels.map(label => ({ label, value: inner[label] ?? '' }));
        if (entries.some(e => e.value.trim())) {
          sections.push({ kind: 'KEY_VALUE_LIST', name: f.name, entries });
        }
      } else if (f.type === 'IMAGE') {
        const ids = this.persona?.imageValues?.[f.name] ?? [];
        if (ids.length > 0) {
          sections.push({ kind: 'IMAGE', name: f.name, ids, layout: f.layout ?? 'GALLERY' });
        }
      }
    }
    flushNumberBuffer();
    return { heroBadges, sections };
  }

  get heroBadges(): { label: string; value: string }[] {
    return this.rendered().heroBadges;
  }

  get orderedSections(): RenderedSection[] {
    return this.rendered().sections;
  }

  /** Pour la drop cap : seul le 1er TEXT la recoit. */
  get firstTextSectionName(): string | null {
    for (const s of this.orderedSections) {
      if (s.kind === 'TEXT') return s.name;
    }
    return null;
  }

  /** Premier paragraphe d'un texte (utilise pour la drop cap). */
  firstParagraph(text: string): string {
    if (!text) return '';
    const paragraphs = text.split(/\n\s*\n/);
    return paragraphs[0]?.trim() ?? '';
  }

  /** Reste du texte apres le 1er paragraphe. */
  restAfterFirstParagraph(text: string): string {
    if (!text) return '';
    const paragraphs = text.split(/\n\s*\n/);
    return paragraphs.slice(1).join('\n\n').trim();
  }
}
