import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, X, ChevronUp, ChevronDown, BookOpen, MapPin } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { Chapter, Scene, QuestNodeRef, NodeType } from '../../services/campaign.model';

/**
 * Sélecteur de nœuds narratifs (Chapitres / Scènes) traversés par une quête.
 * Émet une liste ORDONNÉE de QuestNodeRef ; l'ordre = position dans la liste
 * (réindexé à chaque changement). Réutilise les chapitres / scènes déjà chargés
 * par {@code loadCampaignTreeData} — aucun appel réseau ici.
 */
@Component({
    selector: 'app-node-picker',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './node-picker.component.html',
    styleUrls: ['./node-picker.component.scss']
})
export class NodePickerComponent {
  readonly Plus = Plus;
  readonly X = X;
  readonly ChevronUp = ChevronUp;
  readonly ChevronDown = ChevronDown;
  readonly BookOpen = BookOpen;
  readonly MapPin = MapPin;

  @Input() nodes: QuestNodeRef[] = [];
  @Input() chapters: Chapter[] = [];
  @Input() scenes: Scene[] = [];
  @Output() nodesChange = new EventEmitter<QuestNodeRef[]>();

  selectedChapterId = '';
  selectedSceneId = '';

  /** Chapitres pas encore rattachés (un même nœud ne se lie qu'une fois). */
  get availableChapters(): Chapter[] {
    return this.chapters.filter(c => !!c.id && !this.isLinked('CHAPTER', c.id));
  }

  /** Scènes pas encore rattachées. */
  get availableScenes(): Scene[] {
    return this.scenes.filter(s => !!s.id && !this.isLinked('SCENE', s.id));
  }

  private isLinked(type: NodeType, id: string): boolean {
    return this.nodes.some(n => n.nodeType === type && n.nodeId === id);
  }

  addChapter(): void {
    if (!this.selectedChapterId) return;
    this.emit([...this.nodes, { nodeType: 'CHAPTER', nodeId: this.selectedChapterId, order: this.nodes.length }]);
    this.selectedChapterId = '';
  }

  addScene(): void {
    if (!this.selectedSceneId) return;
    this.emit([...this.nodes, { nodeType: 'SCENE', nodeId: this.selectedSceneId, order: this.nodes.length }]);
    this.selectedSceneId = '';
  }

  remove(index: number): void {
    this.emit(this.nodes.filter((_, i) => i !== index));
  }

  moveUp(index: number): void {
    if (index <= 0) return;
    const next = [...this.nodes];
    [next[index - 1], next[index]] = [next[index], next[index - 1]];
    this.emit(next);
  }

  moveDown(index: number): void {
    if (index >= this.nodes.length - 1) return;
    const next = [...this.nodes];
    [next[index + 1], next[index]] = [next[index], next[index + 1]];
    this.emit(next);
  }

  /** Réindexe {@code order} sur la position courante avant de propager. */
  private emit(nodes: QuestNodeRef[]): void {
    this.nodesChange.emit(nodes.map((n, i) => ({ ...n, order: i })));
  }

  // ── Libellés ──────────────────────────────────────────────────────────────
  /** Libellé d'un nœud lié ('' si l'entité a été supprimée → géré dans le template). */
  nodeName(n: QuestNodeRef): string {
    if (n.nodeType === 'CHAPTER') {
      return this.chapters.find(c => c.id === n.nodeId)?.name ?? '';
    }
    const scene = this.scenes.find(s => s.id === n.nodeId);
    if (!scene) return '';
    const chapter = this.chapters.find(c => c.id === scene.chapterId);
    return chapter ? `${chapter.name} › ${scene.name}` : scene.name;
  }

  /** Option du menu déroulant des scènes : préfixée du chapitre pour lever l'ambiguïté. */
  sceneOptionLabel(s: Scene): string {
    const chapter = this.chapters.find(c => c.id === s.chapterId);
    return chapter ? `${chapter.name} › ${s.name}` : s.name;
  }
}
