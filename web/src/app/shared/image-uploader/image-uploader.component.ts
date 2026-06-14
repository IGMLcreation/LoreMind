import { Component, EventEmitter, Input, Output } from '@angular/core';

import { LucideAngularModule, Upload, AlertCircle } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ImageService } from '../../services/image.service';
import { Image } from '../../services/image.model';

/**
 * Composant reutilisable d'upload d'image (drop-zone + bouton file).
 *
 * Usage :
 *   <app-image-uploader (uploaded)="onImageUploaded($event)"></app-image-uploader>
 *
 * Responsabilites :
 *  - Accepter un fichier via drag&drop OU clic sur la zone
 *  - Valider cote client (MIME + taille) pour eviter un aller-retour inutile
 *  - POSTer vers /api/images (service ImageService)
 *  - Emettre (uploaded) avec l'objet Image recu
 *  - Afficher l'etat loading et les erreurs
 */
@Component({
    selector: 'app-image-uploader',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './image-uploader.component.html',
    styleUrls: ['./image-uploader.component.scss']
})
export class ImageUploaderComponent {
  readonly Upload = Upload;
  readonly AlertCircle = AlertCircle;

  /** Compact mode : bouton "+ ajouter" plutot que grande drop-zone. */
  @Input() compact = false;

  /** Emit quand l'image est uploadee avec succes. */
  @Output() uploaded = new EventEmitter<Image>();

  uploading = false;
  errorMessage: string | null = null;
  dragOver = false;

  /** MIME types alignes avec le backend (ImageService.java). */
  private readonly ALLOWED_MIMES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
  private readonly MAX_BYTES = 10 * 1024 * 1024;

  constructor(private imageService: ImageService, private translate: TranslateService) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFile(input.files[0]);
      // Reset pour permettre de re-uploader la meme image si besoin.
      input.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave(): void {
    this.dragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver = false;
    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.handleFile(event.dataTransfer.files[0]);
    }
  }

  private handleFile(file: File): void {
    this.errorMessage = null;

    // Validation cote client (premier filet de securite).
    if (!this.ALLOWED_MIMES.includes(file.type)) {
      this.errorMessage = this.translate.instant('imageUploader.unsupportedFormat');
      return;
    }
    if (file.size > this.MAX_BYTES) {
      this.errorMessage = this.translate.instant('imageUploader.tooLarge', { max: this.MAX_BYTES / 1024 / 1024 });
      return;
    }

    this.uploading = true;
    this.imageService.upload(file).subscribe({
      next: (image) => {
        this.uploading = false;
        this.uploaded.emit(image);
      },
      error: (err) => {
        this.uploading = false;
        this.errorMessage = err?.status === 413
          ? this.translate.instant('imageUploader.serverRejected')
          : this.translate.instant('imageUploader.uploadFailed');
      }
    });
  }
}
