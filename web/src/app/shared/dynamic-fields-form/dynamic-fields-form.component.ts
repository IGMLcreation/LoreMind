import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TemplateField } from '../../services/template-field.model';

/**
 * Formulaire dynamique pilote par une liste de TemplateField.
 *
 * Inputs :
 *  - fields : structure (provient du GameSystem.characterTemplate / npcTemplate)
 *  - values : Record<champName, string> pour les types TEXT et NUMBER
 *  - imageValues : Record<champName, string[]> pour le type IMAGE
 *
 * Emet `valuesChange` et `imageValuesChange` a chaque modification.
 *
 * Pour les champs IMAGE le MVP affiche une simple textarea CSV d'IDs (le picker
 * d'images dedie sera branche plus tard, hors scope de la refonte template).
 */
@Component({
  selector: 'app-dynamic-fields-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dynamic-fields-form.component.html',
  styleUrls: ['./dynamic-fields-form.component.scss']
})
export class DynamicFieldsFormComponent implements OnChanges {
  @Input() fields: TemplateField[] = [];
  @Input() values: Record<string, string> = {};
  @Input() imageValues: Record<string, string[]> = {};

  @Output() valuesChange = new EventEmitter<Record<string, string>>();
  @Output() imageValuesChange = new EventEmitter<Record<string, string[]>>();

  /** Cache de strings CSV pour edition d'imageValues sans serialisation continue. */
  imageCsvCache: Record<string, string> = {};

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['imageValues'] || changes['fields']) {
      this.imageCsvCache = {};
      for (const f of this.fields) {
        if (f.type === 'IMAGE') {
          const list = this.imageValues[f.name] ?? [];
          this.imageCsvCache[f.name] = list.join(', ');
        }
      }
    }
  }

  onTextChange(field: TemplateField, value: string): void {
    this.values = { ...this.values, [field.name]: value };
    this.valuesChange.emit(this.values);
  }

  onImageCsvChange(field: TemplateField, csv: string): void {
    this.imageCsvCache[field.name] = csv;
    const ids = csv.split(',').map(s => s.trim()).filter(s => s.length > 0);
    this.imageValues = { ...this.imageValues, [field.name]: ids };
    this.imageValuesChange.emit(this.imageValues);
  }

  trackByName = (_: number, f: TemplateField) => f.name;
}
