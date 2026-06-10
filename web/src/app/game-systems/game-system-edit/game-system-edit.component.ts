import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, Dices, Plus, Trash2, ChevronDown, ChevronRight, Upload } from 'lucide-angular';
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
  readonly Upload = Upload;

  id: string | null = null;

  /** Import PDF en cours (appel LLM long) → désactive le bouton + spinner. */
  importing = false;
  /** Message de succès post-import (sections ajoutées, pages, OCR). */
  importNote: string | null = null;
  /** Message d'erreur d'import (Brain injoignable, PDF illisible…). */
  importError: string | null = null;
  /** Libellé de l'étape courante (« Extraction… », « Analyse… (3/12) »). */
  importPhase = '';
  /** Avancement de la structuration ; null pendant l'extraction (total inconnu). */
  importProgress: { current: number; total: number } | null = null;
  /** Titres de sections trouvés au fil de l'eau (affichage live). */
  importFound: string[] = [];

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

  // --- Import d'un PDF de règles -------------------------------------------

  /** Déclenché par le <input file> caché : lance l'import du PDF choisi. */
  onPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Reset de la valeur : re-sélectionner le même fichier doit re-déclencher.
    input.value = '';
    if (file) this.importPdf(file);
  }

  private importPdf(file: File): void {
    this.importing = true;
    this.importNote = null;
    this.importError = null;
    this.importPhase = 'Extraction du texte…';
    this.importProgress = null;
    this.importFound = [];

    this.service.importRulesStream(file).subscribe({
      next: (ev) => {
        if (ev.type === 'progress') {
          if (ev.total === 0) {
            // Phase d'extraction (total encore inconnu).
            this.importPhase = 'Extraction du texte…';
            this.importProgress = null;
          } else {
            this.importPhase = `Analyse des règles… (${ev.current}/${ev.total})`;
            this.importProgress = { current: ev.current, total: ev.total };
            for (const t of ev.newSectionTitles) {
              if (!this.importFound.includes(t)) this.importFound.push(t);
            }
          }
        } else if (ev.type === 'done') {
          this.finishImport(ev.sections, ev.pageCount, ev.ocrPageCount);
        }
      },
      error: (err: Error) => {
        this.resetImportProgress();
        this.importError = err?.message
          ? `Échec de l'import : ${err.message}`
          : "Échec de l'import du PDF.";
      }
    });
  }

  private finishImport(sections: Record<string, string>, pageCount: number, ocrPageCount: number): void {
    this.resetImportProgress();
    const added = this.mergeImportedSections(sections);
    if (added === 0) {
      this.importError = "Aucune règle exploitable n'a été extraite de ce PDF "
        + "(scan sans OCR, ou contenu non reconnu).";
      return;
    }
    const ocr = ocrPageCount > 0 ? ` (dont ${ocrPageCount} page(s) via OCR)` : '';
    this.importNote = `${added} section(s) proposée(s) depuis ${pageCount} page(s)${ocr}. `
      + `Relisez et ajustez ci-dessous avant d'enregistrer.`;
  }

  private resetImportProgress(): void {
    this.importing = false;
    this.importPhase = '';
    this.importProgress = null;
  }

  /**
   * Fusionne les sections proposées dans l'éditeur. Section de même titre
   * (insensible à la casse) → contenu ajouté à la suite ; sinon nouvelle carte.
   * Retourne le nombre de sections effectivement intégrées.
   */
  private mergeImportedSections(sections: Record<string, string>): number {
    let count = 0;
    for (const [rawTitle, rawContent] of Object.entries(sections ?? {})) {
      const title = (rawTitle ?? '').trim();
      const content = (rawContent ?? '').trim();
      if (!title || !content) continue;
      const existing = this.sections.find(
        s => s.title.trim().toLowerCase() === title.toLowerCase()
      );
      if (existing) {
        existing.content = `${existing.content.trim()}\n\n${content}`.trim();
        existing.collapsed = false;
      } else {
        this.sections.push({ title, content, collapsed: false });
      }
      count++;
    }
    return count;
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
