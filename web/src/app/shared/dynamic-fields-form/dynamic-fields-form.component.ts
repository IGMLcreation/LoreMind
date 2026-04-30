import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TemplateField } from '../../services/template.model';
import { ImageGalleryComponent } from '../image-gallery/image-gallery.component';

/**
 * Formulaire dynamique pilote par une liste de TemplateField.
 *
 * Inputs :
 *  - fields : structure (provient du GameSystem.characterTemplate / npcTemplate)
 *  - values : Record<champName, string> pour les types TEXT et NUMBER
 *  - imageValues : Record<champName, string[]> pour le type IMAGE
 *
 * Pour les champs IMAGE, delegue au composant <app-image-gallery editable>
 * qui gere l'upload, la suppression et le respect du layout.
 */
@Component({
  selector: 'app-dynamic-fields-form',
  standalone: true,
  imports: [CommonModule, FormsModule, ImageGalleryComponent],
  templateUrl: './dynamic-fields-form.component.html',
  styleUrls: ['./dynamic-fields-form.component.scss']
})
export class DynamicFieldsFormComponent {
  @Input() fields: TemplateField[] = [];
  @Input() values: Record<string, string> = {};
  @Input() imageValues: Record<string, string[]> = {};
  @Input() keyValueValues: Record<string, Record<string, string>> = {};

  @Output() valuesChange = new EventEmitter<Record<string, string>>();
  @Output() imageValuesChange = new EventEmitter<Record<string, string[]>>();
  @Output() keyValueValuesChange = new EventEmitter<Record<string, Record<string, string>>>();

  onTextChange(field: TemplateField, value: string): void {
    this.values = { ...this.values, [field.name]: value };
    this.valuesChange.emit(this.values);
  }

  onImageIdsChange(field: TemplateField, ids: string[]): void {
    this.imageValues = { ...this.imageValues, [field.name]: ids };
    this.imageValuesChange.emit(this.imageValues);
  }

  imagesFor(field: TemplateField): string[] {
    return this.imageValues[field.name] ?? [];
  }

  /** Valeur d'un label particulier dans un champ KEY_VALUE_LIST. */
  kvValue(field: TemplateField, label: string): string {
    return this.keyValueValues?.[field.name]?.[label] ?? '';
  }

  /** Met a jour la valeur d'un label dans un champ KEY_VALUE_LIST. */
  onKvChange(field: TemplateField, label: string, value: string): void {
    const inner = { ...(this.keyValueValues[field.name] ?? {}), [label]: value };
    this.keyValueValues = { ...this.keyValueValues, [field.name]: inner };
    this.keyValueValuesChange.emit(this.keyValueValues);
  }

  trackByName = (_: number, f: TemplateField) => f.name;
  trackByLabel = (_: number, l: string) => l;
}
