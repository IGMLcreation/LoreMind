import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, BookOpen } from 'lucide-angular';
import { TemplateField } from '../../services/template.model';
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

  /** Champs TEXT/NUMBER non vides, dans l'ordre du template. */
  get textFields(): { name: string; value: string; isNumber: boolean }[] {
    if (!this.persona?.values) return [];
    return this.templateFields
      .filter(f => (f.type === 'TEXT' || f.type === 'NUMBER'))
      .map(f => ({
        name: f.name,
        value: this.persona!.values?.[f.name] ?? '',
        isNumber: f.type === 'NUMBER'
      }))
      .filter(x => x.value && x.value.trim().length > 0);
  }

  /** Champs IMAGE non vides, dans l'ordre du template. */
  get imageFields(): { field: TemplateField; ids: string[] }[] {
    if (!this.persona?.imageValues) return [];
    return this.templateFields
      .filter(f => f.type === 'IMAGE')
      .map(f => ({ field: f, ids: this.persona!.imageValues?.[f.name] ?? [] }))
      .filter(x => x.ids.length > 0);
  }

  hasAnyNumber(fields: { isNumber: boolean }[]): boolean {
    return fields.some(f => f.isNumber);
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
