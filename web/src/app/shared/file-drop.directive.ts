import { Directive, EventEmitter, HostBinding, HostListener, Input, Output } from '@angular/core';

/**
 * Directive de drag & drop de fichiers réutilisable.
 *
 * Pose la classe `.drag-over` sur l'élément hôte pendant le survol (à styler côté
 * composant) et émet les fichiers déposés.
 *
 * Usage :
 *   <div appFileDrop (filesDropped)="onDrop($event)">…</div>
 *   <div appFileDrop [multiple]="true" (filesDropped)="onDrop($event)">…</div>
 */
@Directive({
  selector: '[appFileDrop]',
  standalone: true
})
export class FileDropDirective {
  /** Autoriser plusieurs fichiers (sinon seul le premier est émis). */
  @Input() multiple = false;

  /** Désactive le drop (ex: pendant un upload en cours). */
  @Input() dropDisabled = false;

  @Output() filesDropped = new EventEmitter<File[]>();

  @HostBinding('class.drag-over') isOver = false;

  @HostListener('dragover', ['$event'])
  onDragOver(event: DragEvent): void {
    if (this.dropDisabled) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy';
    this.isOver = true;
  }

  @HostListener('dragleave', ['$event'])
  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isOver = false;
  }

  @HostListener('drop', ['$event'])
  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isOver = false;
    if (this.dropDisabled) return;
    const list = event.dataTransfer?.files;
    if (!list || list.length === 0) return;
    const files = Array.from(list);
    this.filesDropped.emit(this.multiple ? files : [files[0]]);
  }
}
