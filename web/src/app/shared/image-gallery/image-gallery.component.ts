import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, X, Image as ImageIcon } from 'lucide-angular';
import { ImageService } from '../../services/image.service';
import { Image } from '../../services/image.model';
import { ImageUploaderComponent } from '../image-uploader/image-uploader.component';

/**
 * Composant reutilisable de galerie d'images.
 *
 * Deux modes :
 *  - editable=false (defaut) : affichage en grille, clic sur une image ouvre
 *    un lightbox plein ecran pour zoomer.
 *  - editable=true : bouton "+ ajouter" en fin de grille (via app-image-uploader),
 *    chaque vignette a un bouton "X" pour la retirer.
 *
 * La galerie raisonne sur une liste d'IDs d'images (string[]). Elle ne stocke
 * pas les objets Image eux-memes : les thumbs utilisent `imageService.contentUrl(id)`
 * directement comme src. Le navigateur cache les binaires via Cache-Control immutable
 * pose par le backend, donc aucune requete redondante.
 *
 * Usage :
 *   <app-image-gallery [imageIds]="scene.illustrationImageIds"></app-image-gallery>
 *   <app-image-gallery [imageIds]="tempIds" [editable]="true"
 *                      (imageIdsChange)="tempIds = $event"></app-image-gallery>
 */
@Component({
  selector: 'app-image-gallery',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, ImageUploaderComponent],
  templateUrl: './image-gallery.component.html',
  styleUrls: ['./image-gallery.component.scss']
})
export class ImageGalleryComponent {
  readonly X = X;
  readonly ImageIcon = ImageIcon;

  /** IDs d'images a afficher. */
  @Input() imageIds: string[] = [];

  /** Mode edition : afficher le bouton d'ajout + les boutons de suppression. */
  @Input() editable = false;

  /** Emet la nouvelle liste quand l'utilisateur ajoute/retire une image. */
  @Output() imageIdsChange = new EventEmitter<string[]>();

  /** ID de l'image actuellement ouverte en lightbox (null = ferme). */
  lightboxId: string | null = null;

  constructor(private imageService: ImageService) {}

  /** URL absolue du binaire d'une image. */
  urlFor(id: string): string {
    return this.imageService.contentUrl(id);
  }

  onUploaded(image: Image): void {
    this.imageIdsChange.emit([...this.imageIds, image.id]);
  }

  remove(id: string, event: MouseEvent): void {
    event.stopPropagation(); // Evite d'ouvrir le lightbox en cliquant sur X.
    // On supprime aussi cote serveur pour ne pas laisser d'image orpheline.
    // Best-effort : on n'attend pas le retour pour emettre la nouvelle liste.
    this.imageService.delete(id).subscribe({ error: () => {} });
    this.imageIdsChange.emit(this.imageIds.filter(i => i !== id));
  }

  openLightbox(id: string): void {
    this.lightboxId = id;
  }

  closeLightbox(): void {
    this.lightboxId = null;
  }
}
