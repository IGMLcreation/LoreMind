import {
  ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, Output,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, ChevronLeft, ChevronRight, X, ZoomIn, ZoomOut, Move, Image as ImageIcon,
} from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { ImageService } from '../../services/image.service';
import { Image } from '../../services/image.model';
import { ImageFraming } from '../../services/page.model';
import { ImageUploaderComponent } from '../image-uploader/image-uploader.component';

const DEFAULT_FRAMING: ImageFraming = { x: 50, y: 50, scale: 1 };
// scale 1 = image ENTIÈRE visible (object-fit contain, bordures noires si besoin).
// < 1 : encore plus petite (plus de marge) ; > 1 : on zoome -> remplit puis rogne.
const MIN_SCALE = 0.4;
const MAX_SCALE = 4;

function clamp(v: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, v));
}

/**
 * Bloc image d'une page de lore : une ou plusieurs images, chacune remplissant
 * tout le bloc (object-fit cover). En mode édition, on peut DÉPLACER l'image
 * (glisser pour recadrer, via object-position) et la ZOOMER (molette / curseur),
 * le cadrage étant persisté PAR IMAGE (pan/zoom) côté page.
 *
 * Remplace `app-image-gallery` pour les blocs IMAGE des pages (les variantes
 * grille/mosaïque/héros n'ont plus de sens : l'image occupe le bloc entier).
 *
 * E/S :
 *   [imageIds] (string[])               + (imageIdsChange)
 *   [framing]  (imageId → {x,y,scale})  + (framingChange)
 *   [editable]
 */
@Component({
  selector: 'app-image-block',
  imports: [FormsModule, LucideAngularModule, ImageUploaderComponent, TranslatePipe],
  templateUrl: './image-block.component.html',
  styleUrls: ['./image-block.component.scss'],
})
export class ImageBlockComponent implements OnDestroy {
  readonly ChevronLeft = ChevronLeft;
  readonly ChevronRight = ChevronRight;
  readonly X = X;
  readonly ZoomIn = ZoomIn;
  readonly ZoomOut = ZoomOut;
  readonly Move = Move;
  readonly ImageIcon = ImageIcon;

  @Input() imageIds: string[] = [];
  @Input() framing: Record<string, ImageFraming> = {};
  @Input() editable = false;
  /** Remplit la hauteur du conteneur (bloc à hauteur fixe) au lieu d'un ratio 16/9. */
  @Input() fill = false;

  @Output() imageIdsChange = new EventEmitter<string[]>();
  @Output() framingChange = new EventEmitter<Record<string, ImageFraming>>();

  /** Index de l'image affichée. */
  current = 0;

  private panState:
    | { id: string; startX: number; startY: number; fw: number; fh: number; ox: number; oy: number; scale: number }
    | null = null;

  constructor(private imageService: ImageService, private cdr: ChangeDetectorRef) {}

  // --- Accès aux images ---------------------------------------------------

  get currentId(): string | null {
    const i = clamp(this.current, 0, Math.max(0, this.imageIds.length - 1));
    return this.imageIds[i] ?? null;
  }

  get hasMany(): boolean {
    return this.imageIds.length > 1;
  }

  urlFor(id: string): string {
    return this.imageService.contentUrl(id);
  }

  framingOf(id: string | null): ImageFraming {
    return (id && this.framing?.[id]) || DEFAULT_FRAMING;
  }

  /** object-position CSS du cadrage courant (sert aussi de transform-origin). */
  objectPosition(id: string | null): string {
    const f = this.framingOf(id);
    return `${f.x}% ${f.y}%`;
  }

  /** transform CSS (zoom) du cadrage courant. */
  zoomTransform(id: string | null): string {
    return `scale(${this.framingOf(id).scale})`;
  }

  scaleOf(id: string | null): number {
    return this.framingOf(id).scale;
  }

  // --- Navigation ---------------------------------------------------------

  prev(): void {
    if (!this.imageIds.length) return;
    this.current = (this.current - 1 + this.imageIds.length) % this.imageIds.length;
  }

  next(): void {
    if (!this.imageIds.length) return;
    this.current = (this.current + 1) % this.imageIds.length;
  }

  goTo(i: number): void {
    this.current = clamp(i, 0, this.imageIds.length - 1);
  }

  // --- Ajout / suppression (édition) --------------------------------------

  onUploaded(image: Image): void {
    const ids = [...this.imageIds, image.id];
    this.imageIdsChange.emit(ids);
    this.current = ids.length - 1; // affiche la nouvelle image
  }

  removeCurrent(): void {
    const id = this.currentId;
    if (!id) return;
    // Best-effort côté serveur (pas d'orpheline) ; on n'attend pas la réponse.
    this.imageService.delete(id).subscribe({ error: () => {} });
    const ids = this.imageIds.filter(i => i !== id);
    if (this.framing?.[id]) {
      const next = { ...this.framing };
      delete next[id];
      this.framing = next;
      this.framingChange.emit(next);
    }
    this.imageIdsChange.emit(ids);
    this.current = clamp(this.current, 0, Math.max(0, ids.length - 1));
  }

  // --- Recadrage : déplacement (drag) -------------------------------------

  startPan(event: PointerEvent): void {
    if (!this.editable) return;
    const id = this.currentId;
    if (!id) return;
    event.preventDefault();
    const frame = event.currentTarget as HTMLElement;
    const rect = frame.getBoundingClientRect();
    const f = this.framingOf(id);
    this.panState = {
      id, startX: event.clientX, startY: event.clientY,
      fw: rect.width, fh: rect.height, ox: f.x, oy: f.y, scale: f.scale,
    };
    frame.setPointerCapture?.(event.pointerId);
    window.addEventListener('pointermove', this.onPanMove);
    window.addEventListener('pointerup', this.onPanUp, { once: true });
  }

  private onPanMove = (event: PointerEvent): void => {
    const s = this.panState;
    if (!s) return;
    // Glisser l'image la fait suivre le pointeur (object-position diminue quand
    // on tire vers la droite -> on révèle la partie gauche). Plus on est zoomé,
    // plus le déplacement est fin (/ scale).
    const dxPct = ((event.clientX - s.startX) / s.fw) * 100 / s.scale;
    const dyPct = ((event.clientY - s.startY) / s.fh) * 100 / s.scale;
    const x = clamp(s.ox - dxPct, 0, 100);
    const y = clamp(s.oy - dyPct, 0, 100);
    this.framing[s.id] = { x, y, scale: s.scale }; // mise à jour live (sans émettre)
    this.cdr.detectChanges();
  };

  private onPanUp = (): void => {
    window.removeEventListener('pointermove', this.onPanMove);
    if (!this.panState) return;
    this.panState = null;
    this.framing = { ...this.framing };
    this.framingChange.emit(this.framing);
  };

  // --- Recadrage : zoom ---------------------------------------------------

  setZoom(scale: number): void {
    const id = this.currentId;
    if (!id) return;
    const f = this.framingOf(id);
    this.framing = { ...this.framing, [id]: { ...f, scale: clamp(scale, MIN_SCALE, MAX_SCALE) } };
    this.framingChange.emit(this.framing);
  }

  onWheel(event: WheelEvent): void {
    if (!this.editable) return;
    const id = this.currentId;
    if (!id) return;
    event.preventDefault();
    const delta = event.deltaY < 0 ? 0.12 : -0.12;
    this.setZoom(this.framingOf(id).scale + delta);
  }

  ngOnDestroy(): void {
    window.removeEventListener('pointermove', this.onPanMove);
    window.removeEventListener('pointerup', this.onPanUp);
  }
}
