import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, Dices, Plus, Trash2, ChevronDown, ChevronRight } from 'lucide-angular';
import { GameSystemService } from '../../services/game-system.service';
import { TemplateField } from '../../services/template.model';
import { TemplateFieldsEditorComponent } from '../../shared/template-fields-editor/template-fields-editor.component';

/**
 * Éditeur plein écran d'un GameSystem. Rôle double création/édition :
 *  - `/game-systems/create` → POST au submit
 *  - `/game-systems/:id/edit` → PUT au submit
 *
 * UI par sections : au lieu d'un gros textarea de markdown, on présente
 * une liste de cartes repliables (une par section H2). Au load on parse
 * le markdown existant, au submit on le reconstruit. Les noms suggérés
 * (Combat, Classes…) guident sans imposer — c'est le même mapping que
 * le backend utilise pour filtrer les sections à injecter dans l'IA.
 */
interface RuleSection {
  title: string;
  content: string;
  collapsed: boolean;
}

/**
 * Sections suggérées : mécaniques de jeu uniquement. Lore/factions/PNJ
 * appartiennent au module Lore de LoreMind (décrit l'univers de l'utilisateur),
 * pas au GameSystem (décrit les règles). Séparation volontaire.
 */
const SUGGESTED_SECTIONS = [
  'Combat', 'Classes', 'Stats', 'Magie', 'Monstres', 'Progression'
];

/** Suggestions de champs pour la fiche PJ — generiques (extension par template). */
const CHARACTER_FIELD_SUGGESTIONS = ['Histoire', 'Personnalite', 'Apparence', 'Notes'];

/** Suggestions de champs pour la fiche PNJ — focus sur les besoins MJ. */
const NPC_FIELD_SUGGESTIONS = ['Motivation', 'Apparence', 'Faction', 'Notes MJ'];

@Component({
  selector: 'app-game-system-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, TemplateFieldsEditorComponent],
  templateUrl: './game-system-edit.component.html',
  styleUrls: ['./game-system-edit.component.scss']
})
export class GameSystemEditComponent implements OnInit {
  readonly Save = Save;
  readonly ArrowLeft = ArrowLeft;
  readonly Dices = Dices;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;

  id: string | null = null;

  name = '';
  description = '';
  author = '';
  sections: RuleSection[] = [];
  characterTemplate: TemplateField[] = [];
  npcTemplate: TemplateField[] = [];

  readonly suggestedSections = SUGGESTED_SECTIONS;
  readonly characterFieldSuggestions = CHARACTER_FIELD_SUGGESTIONS;
  readonly npcFieldSuggestions = NPC_FIELD_SUGGESTIONS;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: GameSystemService
  ) {}

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id');
    if (this.id) {
      this.service.getById(this.id).subscribe({
        next: (gs) => {
          this.name = gs.name;
          this.description = gs.description ?? '';
          this.author = gs.author ?? '';
          this.sections = this.parseMarkdown(gs.rulesMarkdown ?? '');
          this.characterTemplate = gs.characterTemplate ? [...gs.characterTemplate] : [];
          this.npcTemplate = gs.npcTemplate ? [...gs.npcTemplate] : [];
        },
        error: () => this.back()
      });
    }
  }

  /** Noms de section déjà utilisés — pour désactiver les suggestions en doublon. */
  isSectionUsed(title: string): boolean {
    const lower = title.toLowerCase();
    return this.sections.some(s => s.title.toLowerCase() === lower);
  }

  /** Ajoute une section avec un nom suggéré. */
  addSuggested(title: string): void {
    if (this.isSectionUsed(title)) return;
    this.sections.push({ title, content: '', collapsed: false });
  }

  /** Ajoute une section vierge (titre à remplir). */
  addBlank(): void {
    this.sections.push({ title: '', content: '', collapsed: false });
  }

  removeSection(index: number): void {
    this.sections.splice(index, 1);
  }

  toggleCollapse(section: RuleSection): void {
    section.collapsed = !section.collapsed;
  }

  submit(): void {
    if (!this.name.trim()) return;
    if (this.hasInvalidTemplateFields()) {
      console.warn('Champs templates invalides (noms vides ou doublons) — sauvegarde bloquee.');
      return;
    }
    const payload = {
      name: this.name.trim(),
      description: this.description.trim() || null,
      author: this.author.trim() || null,
      rulesMarkdown: this.serializeMarkdown(),
      characterTemplate: this.characterTemplate,
      npcTemplate: this.npcTemplate,
      isPublic: false
    };
    const req = this.id
      ? this.service.update(this.id, payload)
      : this.service.create(payload);
    req.subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur sauvegarde GameSystem')
    });
  }

  back(): void {
    this.router.navigate(['/game-systems']);
  }

  /** Validation cote front : nom vide ou doublons (case-insensitive). */
  private hasInvalidTemplateFields(): boolean {
    return this.hasInvalidList(this.characterTemplate) || this.hasInvalidList(this.npcTemplate);
  }

  private hasInvalidList(fields: TemplateField[]): boolean {
    const seen = new Set<string>();
    for (const f of fields) {
      const name = f.name?.trim().toLowerCase();
      if (!name) return true;
      if (seen.has(name)) return true;
      seen.add(name);
    }
    return false;
  }

  // --- Parse / Serialize markdown ------------------------------------------

  /**
   * Découpe un markdown par titres H2 en sections. Doit rester cohérent avec
   * le parser Java côté backend (regex `^##\s+…$`). Le texte avant le premier
   * H2 est volontairement perdu — le backend l'ignore aussi pour l'injection IA.
   */
  private parseMarkdown(markdown: string): RuleSection[] {
    if (!markdown) return [];
    const sections: RuleSection[] = [];
    const lines = markdown.split('\n');
    let current: RuleSection | null = null;
    const buffer: string[] = [];

    const flush = () => {
      if (current) {
        current.content = buffer.join('\n').trim();
        sections.push(current);
      }
      buffer.length = 0;
    };

    for (const line of lines) {
      const match = line.match(/^##\s+(.+?)\s*$/);
      if (match) {
        flush();
        current = { title: match[1].trim(), content: '', collapsed: false };
      } else if (current) {
        buffer.push(line);
      }
    }
    flush();
    return sections;
  }

  /** Reconstruit le markdown à partir des sections (ignore les sections vides). */
  private serializeMarkdown(): string | null {
    const valid = this.sections.filter(s => s.title.trim() || s.content.trim());
    if (valid.length === 0) return null;
    return valid
      .map(s => `## ${s.title.trim() || 'Sans titre'}\n${s.content.trim()}`)
      .join('\n\n');
  }
}
