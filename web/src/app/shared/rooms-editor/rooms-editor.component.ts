import { Component, EventEmitter, Input, Output } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Trash2, ChevronDown, ChevronUp, GitBranch, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Room, RoomBranch } from '../../services/campaign.model';
import { Enemy } from '../../services/enemy.model';
import { EnemyLinkPickerComponent } from '../enemy-link-picker/enemy-link-picker.component';

/**
 * Éditeur de pièces (Rooms) d'une Scene explorable.
 *
 * Contrôlé : reçoit la liste, émet la nouvelle liste à chaque mutation.
 * Une pièce est repliée par défaut (juste le nom visible) ; un clic l'expand
 * pour éditer narration / ennemis / loot / pièges / notes MJ / étage.
 *
 * Les branches (graphe inter-pièces) sont éditables dans la card expand.
 */
@Component({
    selector: 'app-rooms-editor',
    imports: [FormsModule, LucideAngularModule, EnemyLinkPickerComponent, TranslatePipe],
    templateUrl: './rooms-editor.component.html',
    styleUrls: ['./rooms-editor.component.scss']
})
export class RoomsEditorComponent {

  @Input() rooms: Room[] = [];
  /** Bestiaire de la campagne (pour référencer des fiches dans une pièce). */
  @Input() availableEnemies: Enemy[] = [];
  /** ID de la campagne (URLs des chips du picker d'ennemis). */
  @Input() campaignId = '';
  @Output() roomsChange = new EventEmitter<Room[]>();

  constructor(private translate: TranslateService) {}

  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly ChevronDown = ChevronDown;
  readonly ChevronUp = ChevronUp;
  readonly GitBranch = GitBranch;
  readonly X = X;

  /** Set des IDs de pièces expandées (UI state local). */
  private expanded = new Set<string>();

  isExpanded(id: string): boolean { return this.expanded.has(id); }
  toggleExpanded(id: string): void {
    if (this.expanded.has(id)) this.expanded.delete(id);
    else this.expanded.add(id);
  }

  /** Génère un UUID v4 simplifié pour l'ID stable d'une pièce. */
  private newId(): string {
    return 'room-' + Math.random().toString(36).slice(2) + '-' + Date.now().toString(36);
  }

  addRoom(): void {
    const nextOrder = this.rooms.length > 0
      ? Math.max(...this.rooms.map(r => r.order ?? 0)) + 1
      : 0;
    const newRoom: Room = {
      id: this.newId(),
      name: this.translate.instant('roomsEditor.defaultRoomName', { n: this.rooms.length + 1 }),
      order: nextOrder,
      branches: []
    };
    this.rooms = [...this.rooms, newRoom];
    this.expanded.add(newRoom.id);
    this.emit();
  }

  removeRoom(room: Room): void {
    this.rooms = this.rooms
      .filter(r => r.id !== room.id)
      .map(r => ({
        ...r,
        // Nettoyer les branches qui pointaient vers la pièce supprimée
        branches: (r.branches ?? []).filter(b => b.targetRoomId !== room.id)
      }));
    this.expanded.delete(room.id);
    this.emit();
  }

  /** Patch un champ d'une pièce et émet. */
  patch(room: Room, patch: Partial<Room>): void {
    this.rooms = this.rooms.map(r => r.id === room.id ? { ...r, ...patch } : r);
    this.emit();
  }

  // === Branches inter-pièces ===

  addBranch(room: Room): void {
    const others = this.rooms.filter(r => r.id !== room.id);
    const branch: RoomBranch = {
      label: this.translate.instant('roomsEditor.defaultBranchLabel'),
      targetRoomId: others[0]?.id ?? '',
      condition: ''
    };
    this.patch(room, { branches: [...(room.branches ?? []), branch] });
  }

  removeBranch(room: Room, index: number): void {
    const next = (room.branches ?? []).filter((_, i) => i !== index);
    this.patch(room, { branches: next });
  }

  updateBranch(room: Room, index: number, patch: Partial<RoomBranch>): void {
    const next = (room.branches ?? []).map((b, i) => i === index ? { ...b, ...patch } : b);
    this.patch(room, { branches: next });
  }

  /** Pièces candidates pour cibler une branche (toutes sauf celle d'origine). */
  otherRooms(room: Room): Room[] {
    return this.rooms.filter(r => r.id !== room.id);
  }

  /** Réorganise l'ordre via boutons up/down (simple : swap avec voisin). */
  moveUp(room: Room): void {
    const idx = this.rooms.findIndex(r => r.id === room.id);
    if (idx <= 0) return;
    const arr = [...this.rooms];
    [arr[idx - 1], arr[idx]] = [arr[idx], arr[idx - 1]];
    this.rooms = arr.map((r, i) => ({ ...r, order: i }));
    this.emit();
  }

  moveDown(room: Room): void {
    const idx = this.rooms.findIndex(r => r.id === room.id);
    if (idx === -1 || idx >= this.rooms.length - 1) return;
    const arr = [...this.rooms];
    [arr[idx], arr[idx + 1]] = [arr[idx + 1], arr[idx]];
    this.rooms = arr.map((r, i) => ({ ...r, order: i }));
    this.emit();
  }

  trackById(_: number, r: Room): string { return r.id; }

  private emit(): void {
    this.roomsChange.emit(this.rooms);
  }
}
