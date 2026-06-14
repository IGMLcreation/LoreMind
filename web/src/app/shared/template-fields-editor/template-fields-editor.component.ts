import { Component, EventEmitter, Input, Output } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Trash2, ArrowUp, ArrowDown, Type, Image as ImageIcon, Hash, ListOrdered, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TemplateField, FieldType, ImageLayout } from '../../services/template.model';

/**
 * Editeur reutilisable d'une liste de TemplateField.
 * Pilote l'ajout / suppression / reordonnancement / changement de type / renommage.
 *
 * Emet `fieldsChange` a chaque modification pour permettre un binding 2-way :
 *   <app-template-fields-editor [fields]="myFields" (fieldsChange)="myFields = $event">
 *
 * Validation locale : duplicats de noms (case-insensitive) marques visuellement,
 * mais c'est le parent qui decide du blocage du submit.
 */
@Component({
    selector: 'app-template-fields-editor',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './template-fields-editor.component.html',
    styleUrls: ['./template-fields-editor.component.scss']
})
export class TemplateFieldsEditorComponent {
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly ArrowUp = ArrowUp;
  readonly ArrowDown = ArrowDown;
  readonly Type = Type;
  readonly ImageIcon = ImageIcon;
  readonly Hash = Hash;
  readonly ListOrdered = ListOrdered;
  readonly X = X;

  /** Liste des champs (binding parent). */
  @Input() fields: TemplateField[] = [];

  /** Suggestions de noms de champs (chips ajout rapide). */
  @Input() suggestions: string[] = [];

  /** Label de la section (ex: "Champs de la fiche PJ"). Si vide, un libellé par défaut traduit est affiché. */
  @Input() label = '';

  /** Hint affichee sous le label. */
  @Input() hint?: string;

  @Output() fieldsChange = new EventEmitter<TemplateField[]>();

  constructor(private translate: TranslateService) {}

  get typeOptions(): { value: FieldType; label: string }[] {
    return [
      { value: 'TEXT', label: this.translate.instant('templateFieldsEditor.typeText') },
      { value: 'NUMBER', label: this.translate.instant('templateFieldsEditor.typeNumber') },
      { value: 'IMAGE', label: this.translate.instant('templateFieldsEditor.typeImage') },
      { value: 'KEY_VALUE_LIST', label: this.translate.instant('templateFieldsEditor.typeKeyValue') }
    ];
  }

  get layoutOptions(): { value: ImageLayout; label: string }[] {
    return [
      { value: 'GALLERY', label: this.translate.instant('templateFieldsEditor.layoutGallery') },
      { value: 'HERO', label: this.translate.instant('templateFieldsEditor.layoutHero') },
      { value: 'MASONRY', label: this.translate.instant('templateFieldsEditor.layoutMasonry') },
      { value: 'CAROUSEL', label: this.translate.instant('templateFieldsEditor.layoutCarousel') }
    ];
  }

  isDuplicate(field: TemplateField, index: number): boolean {
    if (!field.name?.trim()) return false;
    const lower = field.name.trim().toLowerCase();
    return this.fields.some((f, i) => i !== index && f.name?.trim().toLowerCase() === lower);
  }

  isSuggestionUsed(name: string): boolean {
    const lower = name.toLowerCase();
    return this.fields.some(f => f.name?.trim().toLowerCase() === lower);
  }

  addSuggestion(name: string): void {
    if (this.isSuggestionUsed(name)) return;
    this.emit([...this.fields, { name, type: 'TEXT', layout: null }]);
  }

  addBlank(type: FieldType): void {
    const layout: ImageLayout | null = type === 'IMAGE' ? 'GALLERY' : null;
    const labels: string[] | null = type === 'KEY_VALUE_LIST' ? [] : null;
    this.emit([...this.fields, { name: '', type, layout, labels }]);
  }

  // --- Sous-editeur labels (KEY_VALUE_LIST) ---

  addLabel(field: TemplateField): void {
    const labels = field.labels ? [...field.labels] : [];
    labels.push('');
    field.labels = labels;
    this.onFieldChanged();
  }

  removeLabel(field: TemplateField, index: number): void {
    if (!field.labels) return;
    const labels = [...field.labels];
    labels.splice(index, 1);
    field.labels = labels;
    this.onFieldChanged();
  }

  updateLabelAt(field: TemplateField, index: number, value: string): void {
    if (!field.labels) return;
    const labels = [...field.labels];
    labels[index] = value;
    field.labels = labels;
    this.onFieldChanged();
  }

  moveLabelUp(field: TemplateField, index: number): void {
    if (!field.labels || index <= 0) return;
    const labels = [...field.labels];
    [labels[index - 1], labels[index]] = [labels[index], labels[index - 1]];
    field.labels = labels;
    this.onFieldChanged();
  }

  moveLabelDown(field: TemplateField, index: number): void {
    if (!field.labels || index >= field.labels.length - 1) return;
    const labels = [...field.labels];
    [labels[index], labels[index + 1]] = [labels[index + 1], labels[index]];
    field.labels = labels;
    this.onFieldChanged();
  }


  remove(index: number): void {
    const next = [...this.fields];
    next.splice(index, 1);
    this.emit(next);
  }

  moveUp(index: number): void {
    if (index <= 0) return;
    const next = [...this.fields];
    [next[index - 1], next[index]] = [next[index], next[index - 1]];
    this.emit(next);
  }

  moveDown(index: number): void {
    if (index >= this.fields.length - 1) return;
    const next = [...this.fields];
    [next[index], next[index + 1]] = [next[index + 1], next[index]];
    this.emit(next);
  }

  /** Notifie les changements internes (input/select sur un champ existant). */
  onFieldChanged(): void {
    for (const f of this.fields) {
      if (f.type === 'IMAGE' && !f.layout) f.layout = 'GALLERY';
      if (f.type !== 'IMAGE') f.layout = null;
      if (f.type === 'KEY_VALUE_LIST' && !f.labels) f.labels = [];
      if (f.type !== 'KEY_VALUE_LIST') f.labels = null;
    }
    this.emit([...this.fields]);
  }

  private emit(fields: TemplateField[]): void {
    this.fields = fields;
    this.fieldsChange.emit(fields);
  }
}
