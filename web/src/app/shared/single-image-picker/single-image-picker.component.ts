import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, X, Image as ImageIcon } from 'lucide-angular';
import { ImageService } from '../../services/image.service';
import { Image } from '../../services/image.model';
import { ImageUploaderComponent } from '../image-uploader/image-uploader.component';

/**
 * Picker d'image unique : preview + upload + suppression.
 *
 * Usage :
 *   <app-single-image-picker [imageId]="portraitId" (imageIdChange)="portraitId = $event">
 *   </app-single-image-picker>
 *
 * Comportements :
 *  - Si imageId est defini : affiche la miniature avec un bouton X pour retirer
 *  - Sinon : affiche le bouton d'upload (compact mode)
 *
 * Le composant ne supprime pas l'image cote backend — il decouple juste le
 * lien (passe imageId a null). L'image reste accessible via d'autres entites.
 */
@Component({
  selector: 'app-single-image-picker',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, ImageUploaderComponent],
  templateUrl: './single-image-picker.component.html',
  styleUrls: ['./single-image-picker.component.scss']
})
export class SingleImagePickerComponent {
  readonly X = X;
  readonly ImageIcon = ImageIcon;

  @Input() imageId: string | null = null;

  /** Texte d'aide affiche sous le picker (ex: "Format conseille : 400×400"). */
  @Input() hint?: string;

  /** Aspect ratio de la preview (CSS aspect-ratio property). */
  @Input() aspectRatio = '1 / 1';

  @Output() imageIdChange = new EventEmitter<string | null>();

  constructor(private imageService: ImageService) {}

  contentUrl(id: string): string {
    return this.imageService.contentUrl(id);
  }

  onUploaded(img: Image): void {
    if (img?.id) {
      this.imageId = img.id;
      this.imageIdChange.emit(this.imageId);
    }
  }

  remove(): void {
    this.imageId = null;
    this.imageIdChange.emit(null);
  }
}
