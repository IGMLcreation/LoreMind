import {
  ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, Trash2, Type, Image as ImageIcon,
  ListOrdered, Table as TableIcon, X, GripVertical, Plus,
} from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { FieldType, TemplateField, buildLoreTemplateField } from '../../services/template.model';
import { GRID_COLS, GRID_ROW_HEIGHT, DEFAULT_BLOCK_H } from '../block-layout.helper';

const MAX_H = 40;

function genId(): string {
  try { return 'blk-' + crypto.randomUUID(); }
  catch { return 'blk-' + Math.random().toString(36).slice(2) + Date.now().toString(36); }
}
function clampW(w: number | null | undefined): number {
  return Math.min(GRID_COLS, Math.max(1, Math.round(w ?? GRID_COLS)));
}
function clampH(h: number | null | undefined): number {
  return Math.min(MAX_H, Math.max(1, Math.round(h ?? DEFAULT_BLOCK_H)));
}

interface PaletteType {
  type: FieldType;
  icon: typeof Type;
  labelKey: string;
  nameKey: string;
}

interface CanvasGeom { left: number; top: number; colUnit: number; rowUnit: number; }

interface DragOp {
  kind: 'create' | 'move' | 'resize';
  type?: FieldType;
  block?: TemplateField;
  edge?: 'e' | 's' | 'se';
  g: CanvasGeom;
  grabCol: number;
  grabRow: number;
  // Aperçu (création) : rectangle cible dans la grille.
  px: number; py: number; pw: number; ph: number;
  over: boolean;        // pointeur au-dessus de la toile (création)
  pointerX: number; pointerY: number; // pour le fantôme suiveur
  moved: boolean;
}

/**
 * Builder de blocs en grille 2D LIBRE (inspiré de l'app "Lore" d'Amsel).
 *
 * - PALETTE latérale de types de blocs : on les glisse sur la toile (ou on
 *   clique pour les ajouter en bas).
 * - chaque bloc se PLACE où on veut (x ET y) en glissant sa poignée, et se
 *   REDIMENSIONNE en largeur ET hauteur via les poignées de bord/coin (snap à
 *   la grille 12 colonnes × lignes fixes de {@link GRID_ROW_HEIGHT}px).
 * - le nom est éditable en place (id stable → les valeurs des pages suivent).
 *
 * Le placement {x, y, w, h} est explicite (pas de "flow-packing") : le rendu
 * (page-view / page-edit) le reproduit exactement. Les chevauchements sont
 * permis (placement libre) — c'est à l'utilisateur d'arranger.
 *
 * Composant partagé par template-create et template-edit. E/S : `[(fields)]`.
 */
@Component({
  selector: 'app-block-grid-builder',
  imports: [FormsModule, LucideAngularModule, TranslatePipe],
  templateUrl: './block-grid-builder.component.html',
  styleUrls: ['./block-grid-builder.component.scss'],
})
export class BlockGridBuilderComponent implements OnDestroy {
  readonly Trash2 = Trash2;
  readonly X = X;
  readonly GripVertical = GripVertical;
  readonly Plus = Plus;
  readonly GRID_ROW_HEIGHT = GRID_ROW_HEIGHT;

  readonly paletteTypes: PaletteType[] = [
    { type: 'TEXT', icon: Type, labelKey: 'templateEdit.typeText', nameKey: 'templateEdit.defaultNameText' },
    { type: 'IMAGE', icon: ImageIcon, labelKey: 'templateEdit.typeImage', nameKey: 'templateEdit.defaultNameImage' },
    { type: 'KEY_VALUE_LIST', icon: ListOrdered, labelKey: 'templateEdit.typeKeyValue', nameKey: 'templateEdit.defaultNameKeyValue' },
    { type: 'TABLE', icon: TableIcon, labelKey: 'templateEdit.typeTable', nameKey: 'templateEdit.defaultNameTable' },
  ];

  @Input() existingFieldNames: Set<string> | null = null;
  @Output() fieldsChange = new EventEmitter<TemplateField[]>();

  @ViewChild('canvasEl') private canvasRef?: ElementRef<HTMLElement>;

  private lastEmitted: TemplateField[] | null = null;
  blocks: TemplateField[] = [];

  private renameOrig = '';
  drag: DragOp | null = null;

  constructor(private translate: TranslateService, private cdr: ChangeDetectorRef) {}

  @Input() set fields(value: TemplateField[] | null | undefined) {
    if (value && value === this.lastEmitted) return;
    this.blocks = this.normalize(value ?? []);
    queueMicrotask(() => this.emit());
  }

  iconFor(type: FieldType) {
    switch (type) {
      case 'IMAGE': return ImageIcon;
      case 'KEY_VALUE_LIST': return ListOrdered;
      case 'TABLE': return TableIcon;
      default: return Type;
    }
  }

  isExisting(block: TemplateField): boolean {
    return !!this.existingFieldNames?.has(block.name);
  }

  /** Nombre de lignes visibles de la toile (au moins 12, + marge sous le contenu). */
  get gridRows(): number {
    return Math.max(12, this.maxBottom() + 3);
  }

  gridColumn(block: TemplateField): string {
    return `${(block.pos?.x ?? 0) + 1} / span ${clampW(block.pos?.w)}`;
  }
  gridRow(block: TemplateField): string {
    return `${(block.pos?.y ?? 0) + 1} / span ${clampH(block.pos?.h)}`;
  }

  // --- Ajout / suppression ------------------------------------------------

  /** Clic palette : ajoute un bloc pleine largeur sous le contenu existant. */
  addType(type: FieldType): void {
    const block = this.makeBlock(type, 0, this.maxBottom(), GRID_COLS, DEFAULT_BLOCK_H);
    this.blocks = [...this.blocks, block];
    this.emit();
  }

  remove(block: TemplateField): void {
    this.blocks = this.blocks.filter(b => b !== block);
    this.emit();
  }

  // --- Renommage ----------------------------------------------------------

  onNameFocus(block: TemplateField): void { this.renameOrig = block.name; }
  rename(block: TemplateField, value: string): void { block.name = value; this.emit(); }
  commitRename(block: TemplateField): void {
    block.name = block.name.trim() || this.renameOrig;
    this.emit();
  }

  // --- Libellés (KEY_VALUE_LIST / TABLE) ----------------------------------

  addLabel(block: TemplateField): void { block.labels = [...(block.labels ?? []), '']; this.emit(); }
  updateLabel(block: TemplateField, i: number, value: string): void {
    if (!block.labels) return;
    block.labels[i] = value;
    this.emit();
  }
  removeLabel(block: TemplateField, i: number): void {
    if (!block.labels) return;
    block.labels = block.labels.filter((_, k) => k !== i);
    this.emit();
  }

  // --- Glisser : création / déplacement / redimensionnement ---------------

  /** Démarre la création d'un bloc en glissant un type depuis la palette. */
  startCreate(event: PointerEvent, type: FieldType): void {
    event.preventDefault();
    const g = this.geom();
    if (!g) return;
    this.drag = {
      kind: 'create', type, g, grabCol: 0, grabRow: 0,
      px: 0, py: 0, pw: 6, ph: DEFAULT_BLOCK_H, over: false,
      pointerX: event.clientX, pointerY: event.clientY, moved: false,
    };
    this.listen();
  }

  /** Démarre le déplacement d'un bloc (poignée). */
  startMove(event: PointerEvent, block: TemplateField): void {
    event.preventDefault();
    event.stopPropagation();
    const g = this.geom();
    if (!g) return;
    const cell = this.cellAt(event.clientX, event.clientY, g);
    this.drag = {
      kind: 'move', block, g,
      grabCol: cell.col - (block.pos?.x ?? 0),
      grabRow: cell.row - (block.pos?.y ?? 0),
      px: 0, py: 0, pw: 0, ph: 0, over: true,
      pointerX: event.clientX, pointerY: event.clientY, moved: false,
    };
    this.listen();
  }

  /** Démarre le redimensionnement d'un bloc (bord/coin). */
  startResize(event: PointerEvent, block: TemplateField, edge: 'e' | 's' | 'se'): void {
    event.preventDefault();
    event.stopPropagation();
    const g = this.geom();
    if (!g) return;
    this.drag = {
      kind: 'resize', block, edge, g, grabCol: 0, grabRow: 0,
      px: 0, py: 0, pw: 0, ph: 0, over: true,
      pointerX: event.clientX, pointerY: event.clientY, moved: false,
    };
    this.listen();
  }

  private onPointerMove = (event: PointerEvent): void => {
    const d = this.drag;
    if (!d) return;
    d.moved = true;
    d.pointerX = event.clientX;
    d.pointerY = event.clientY;
    const cell = this.cellAt(event.clientX, event.clientY, d.g);

    if (d.kind === 'move' && d.block) {
      const w = clampW(d.block.pos?.w);
      const x = Math.min(GRID_COLS - w, Math.max(0, cell.col - d.grabCol));
      const y = Math.max(0, cell.row - d.grabRow);
      d.block.pos = { ...(d.block.pos ?? {}), x, y, w, h: clampH(d.block.pos?.h) };
      this.cdr.detectChanges();
    } else if (d.kind === 'resize' && d.block) {
      const x = d.block.pos?.x ?? 0;
      const y = d.block.pos?.y ?? 0;
      let w = clampW(d.block.pos?.w);
      let h = clampH(d.block.pos?.h);
      if (d.edge === 'e' || d.edge === 'se') w = Math.min(GRID_COLS - x, Math.max(1, cell.col - x + 1));
      if (d.edge === 's' || d.edge === 'se') h = Math.min(MAX_H, Math.max(1, cell.row - y + 1));
      d.block.pos = { x, y, w, h };
      this.cdr.detectChanges();
    } else if (d.kind === 'create') {
      d.over = this.isOverCanvas(event.clientX, event.clientY);
      if (d.over) {
        d.px = Math.min(GRID_COLS - d.pw, Math.max(0, cell.col));
        d.py = Math.max(0, cell.row);
      }
      this.cdr.detectChanges();
    }
  };

  private onPointerUp = (): void => {
    const d = this.drag;
    this.unlisten();
    this.drag = null;
    if (!d) return;
    if (d.kind === 'create' && d.over) {
      const block = this.makeBlock(d.type!, d.px, d.py, d.pw, d.ph);
      this.blocks = [...this.blocks, block];
      this.emit();
    } else if ((d.kind === 'move' || d.kind === 'resize') && d.moved) {
      this.emit();
    }
    this.cdr.detectChanges();
  };

  private listen(): void {
    window.addEventListener('pointermove', this.onPointerMove);
    window.addEventListener('pointerup', this.onPointerUp, { once: true });
  }
  private unlisten(): void {
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('pointerup', this.onPointerUp);
  }

  ngOnDestroy(): void { this.unlisten(); }

  // --- Helpers ------------------------------------------------------------

  private geom(): CanvasGeom | null {
    const el = this.canvasRef?.nativeElement;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    const style = getComputedStyle(el);
    const padL = parseFloat(style.paddingLeft) || 0;
    const padT = parseFloat(style.paddingTop) || 0;
    const padR = parseFloat(style.paddingRight) || 0;
    const colGap = parseFloat(style.columnGap || style.gap) || 0;
    const rowGap = parseFloat(style.rowGap || style.gap) || 0;
    const innerW = rect.width - padL - padR;
    return {
      left: rect.left + padL,
      top: rect.top + padT,
      colUnit: (innerW + colGap) / GRID_COLS,
      rowUnit: GRID_ROW_HEIGHT + rowGap,
    };
  }

  private cellAt(px: number, py: number, g: CanvasGeom): { col: number; row: number } {
    return {
      col: Math.min(GRID_COLS - 1, Math.max(0, Math.floor((px - g.left) / g.colUnit))),
      row: Math.max(0, Math.floor((py - g.top) / g.rowUnit)),
    };
  }

  private isOverCanvas(px: number, py: number): boolean {
    const el = this.canvasRef?.nativeElement;
    if (!el) return false;
    const r = el.getBoundingClientRect();
    return px >= r.left && px <= r.right && py >= r.top && py <= r.bottom;
  }

  private maxBottom(blocks: TemplateField[] = this.blocks): number {
    return blocks.reduce((m, b) => Math.max(m, (b.pos?.y ?? 0) + clampH(b.pos?.h)), 0);
  }

  private makeBlock(type: FieldType, x: number, y: number, w: number, h: number): TemplateField {
    return {
      ...buildLoreTemplateField(this.uniqueDefaultName(type), type),
      id: genId(),
      pos: { x, y, w: clampW(w), h: clampH(h) },
    };
  }

  private uniqueDefaultName(type: FieldType): string {
    const pt = this.paletteTypes.find(p => p.type === type);
    const base = this.translate.instant(pt?.nameKey ?? 'templateEdit.defaultNameText');
    let name = base;
    let i = 2;
    while (this.blocks.some(b => b.name === name)) name = `${base} ${i++}`;
    return name;
  }

  /**
   * Normalise les champs entrants : id stable + position {x,y,w,h} complète.
   * Les blocs sans aucune position (legacy) sont empilés pleine largeur ; les
   * blocs partiels (largeur seule, ancien format) reçoivent une hauteur par défaut.
   */
  private normalize(fields: TemplateField[]): TemplateField[] {
    let nextY = 0;
    return fields.map(f => {
      const id = f.id && f.id.trim() ? f.id : (f.name || genId());
      const labels = f.labels ? [...f.labels] : f.labels;
      const p = f.pos;
      let pos;
      if (p && (p.x != null || p.y != null || p.w != null || p.h != null)) {
        pos = { x: p.x ?? 0, y: p.y ?? nextY, w: clampW(p.w), h: clampH(p.h) };
      } else {
        pos = { x: 0, y: nextY, w: GRID_COLS, h: DEFAULT_BLOCK_H };
      }
      nextY = Math.max(nextY, pos.y + pos.h);
      return { ...f, id, labels, pos };
    });
  }

  private emit(): void {
    this.lastEmitted = this.blocks;
    this.fieldsChange.emit(this.blocks);
  }
}
