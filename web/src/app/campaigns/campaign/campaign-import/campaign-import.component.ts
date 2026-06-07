import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule, ArrowLeft, Upload, Plus, Trash2,
  ChevronDown, ChevronRight, Swords, BookOpen, MapPin, Check
} from 'lucide-angular';
import { CampaignImportService } from '../../../services/campaign-import.service';
import { CampaignService } from '../../../services/campaign.service';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { PageTitleService } from '../../../services/page-title.service';
import { ArcKind, ArcProposal, ChapterProposal, SceneProposal } from '../../../services/campaign-import.model';
import { CampaignImportProposal } from '../../../services/campaign-import.model';
import { loadCampaignTreeData, CampaignTreeData } from '../../campaign-tree.helper';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Nœuds éditables (= proposition + état d'UI). `existing` = déjà présent dans la
 * campagne (chargé pour la revue) : lecture seule, sert de parent aux ajouts.
 * `existingId` = l'ID de l'entité existante (envoyé à l'apply pour s'y rattacher).
 */
interface RoomNode { name: string; description: string; enemies: string; loot: string; }
interface SceneNode {
  name: string; description: string; playerNarration: string; gmNotes: string;
  rooms: RoomNode[]; detailsOpen: boolean; existing: boolean; existingId?: string;
}
interface ChapterNode {
  name: string; description: string; scenes: SceneNode[]; collapsed: boolean;
  existing: boolean; existingId?: string;
}
interface ArcNode {
  name: string; description: string; type: ArcKind; chapters: ChapterNode[]; collapsed: boolean;
  existing: boolean; existingId?: string;
}

/**
 * Page d'import d'un PDF de campagne → arbre arc/chapitre/scène.
 * Route : /campaigns/:campaignId/import
 *
 * Flux : upload → progression streamée → arbre éditable (revue) → création.
 * Rien n'est créé tant que l'utilisateur n'a pas validé « Créer dans la campagne ».
 */
@Component({
  selector: 'app-campaign-import',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './campaign-import.component.html',
  styleUrls: ['./campaign-import.component.scss']
})
export class CampaignImportComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Upload = Upload;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;
  readonly Swords = Swords;
  readonly BookOpen = BookOpen;
  readonly MapPin = MapPin;
  readonly Check = Check;

  campaignId = '';

  // --- État import (streaming) ---
  importing = false;
  importPhase = '';
  importProgress: { current: number; total: number } | null = null;
  importCounts: { arcs: number; chapters: number; scenes: number } | null = null;
  importError: string | null = null;
  /** Vrai une fois la proposition reçue (on affiche l'arbre éditable). */
  reviewing = false;

  // --- Arbre éditable ---
  tree: ArcNode[] = [];

  /** Structure actuelle de la campagne (chargée pour la fusion à la revue). */
  private existingData: CampaignTreeData | null = null;

  // --- État application (création) ---
  applying = false;
  applyError: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: CampaignImportService,
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private campaignSidebar: CampaignSidebarService,
    private pageTitle: PageTitleService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId')!;
    this.pageTitle.set('Importer une campagne');
    this.campaignSidebar.show(this.campaignId);

    // Pré-chargement de l'arborescence existante (pour fusionner à la revue).
    // En cas d'échec on dégrade : tout sera considéré comme nouveau.
    loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, this.randomTableService)
      .pipe(catchError(() => of(null)))
      .subscribe(data => this.existingData = data);
  }

  // --- Upload + streaming --------------------------------------------------

  onPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) this.importPdf(file);
  }

  private importPdf(file: File): void {
    this.importing = true;
    this.reviewing = false;
    this.importError = null;
    this.applyError = null;
    this.importPhase = 'Extraction du texte…';
    this.importProgress = null;
    this.importCounts = null;
    this.tree = [];

    this.service.importStructureStream(this.campaignId, file).subscribe({
      next: (ev) => {
        if (ev.type === 'progress') {
          if (ev.total === 0) {
            this.importPhase = 'Extraction du texte…';
            this.importProgress = null;
          } else {
            this.importPhase = `Analyse de la campagne… (${ev.current}/${ev.total})`;
            this.importProgress = { current: ev.current, total: ev.total };
            this.importCounts = { arcs: ev.arcCount, chapters: ev.chapterCount, scenes: ev.sceneCount };
          }
        } else if (ev.type === 'done') {
          this.importing = false;
          this.importPhase = '';
          this.importProgress = null;
          if ((ev.arcs ?? []).length === 0) {
            this.importError = "Aucune structure narrative détectée dans ce PDF.";
            this.reviewing = false;
          } else {
            this.tree = this.buildMergedTree(ev.arcs);
            this.reviewing = true;
          }
        }
      },
      error: (err: Error) => {
        this.importing = false;
        this.importPhase = '';
        this.importProgress = null;
        this.importError = err?.message ? `Échec de l'import : ${err.message}` : "Échec de l'import du PDF.";
      }
    });
  }

  // --- Construction de l'arbre fusionné (existant + proposition) -----------

  /**
   * Construit l'arbre de revue : d'abord l'arborescence ACTUELLE de la campagne
   * (nœuds `existing`, lecture seule), puis on y fusionne la proposition par NOM
   * (insensible à la casse). Ce qui matche un nœud existant est rattaché ; ce qui
   * ne matche pas devient un nouveau nœud éditable.
   */
  private buildMergedTree(proposalArcs: ArcProposal[]): ArcNode[] {
    const byOrder = (a: { order?: number }, b: { order?: number }) => (a.order ?? 0) - (b.order ?? 0);
    const arcs: ArcNode[] = [];

    // 1. Arbre existant.
    const data = this.existingData;
    if (data) {
      for (const arc of [...data.arcs].sort(byOrder)) {
        const chapters: ChapterNode[] = [];
        for (const ch of [...(data.chaptersByArc[arc.id!] ?? [])].sort(byOrder)) {
          const scenes: SceneNode[] = [];
          for (const sc of [...(data.scenesByChapter[ch.id!] ?? [])].sort(byOrder)) {
            scenes.push(this.existingSceneNode(sc.id!, sc.name, sc.description));
          }
          chapters.push({
            name: ch.name, description: ch.description ?? '', scenes,
            collapsed: true, existing: true, existingId: ch.id
          });
        }
        arcs.push({
          name: arc.name, description: arc.description ?? '',
          type: (arc.type === 'HUB' ? 'HUB' : 'LINEAR'), chapters,
          collapsed: true, existing: true, existingId: arc.id
        });
      }
    }

    // 2. Fusion de la proposition.
    for (const pa of proposalArcs ?? []) {
      const match = arcs.find(a => a.existing && this.sameName(a.name, pa.name));
      if (match) {
        this.mergeChaptersInto(match, pa.chapters ?? []);
        match.collapsed = false;
      } else {
        arcs.push(this.newArcNode(pa));
      }
    }
    return arcs;
  }

  private mergeChaptersInto(arc: ArcNode, propChapters: ChapterProposal[]): void {
    for (const pc of propChapters) {
      const match = arc.chapters.find(c => c.existing && this.sameName(c.name, pc.name));
      if (match) {
        this.mergeScenesInto(match, pc.scenes ?? []);
        match.collapsed = false;
      } else {
        arc.chapters.push(this.newChapterNode(pc));
      }
    }
  }

  private mergeScenesInto(chapter: ChapterNode, propScenes: SceneProposal[]): void {
    for (const ps of propScenes) {
      // Scène de même nom déjà présente → on ne duplique pas (dédup).
      if (chapter.scenes.some(s => this.sameName(s.name, ps.name))) continue;
      chapter.scenes.push(this.newSceneNode(ps));
    }
  }

  private sameName(a: string, b: string): boolean {
    return (a ?? '').trim().toLowerCase() === (b ?? '').trim().toLowerCase();
  }

  // --- Mappers proposition → nœud NEUF -------------------------------------

  private newArcNode(a: ArcProposal): ArcNode {
    return {
      name: a.name ?? '', description: a.description ?? '',
      type: (a.type === 'HUB' ? 'HUB' : 'LINEAR'),
      collapsed: false, existing: false,
      chapters: (a.chapters ?? []).map(c => this.newChapterNode(c))
    };
  }

  private newChapterNode(c: ChapterProposal): ChapterNode {
    return {
      name: c.name ?? '', description: c.description ?? '',
      collapsed: false, existing: false,
      scenes: (c.scenes ?? []).map(s => this.newSceneNode(s))
    };
  }

  private newSceneNode(s: SceneProposal): SceneNode {
    return {
      name: s.name ?? '', description: s.description ?? '',
      playerNarration: s.playerNarration ?? '', gmNotes: s.gmNotes ?? '',
      detailsOpen: false, existing: false,
      rooms: (s.rooms ?? []).map(r => ({
        name: r.name ?? '', description: r.description ?? '',
        enemies: r.enemies ?? '', loot: r.loot ?? ''
      }))
    };
  }

  private existingSceneNode(id: string, name: string, description?: string): SceneNode {
    return {
      name, description: description ?? '', playerNarration: '', gmNotes: '',
      detailsOpen: false, existing: true, existingId: id, rooms: []
    };
  }

  setArcType(arc: ArcNode, type: ArcKind): void { arc.type = type; }
  toggleDetails(scene: SceneNode): void { scene.detailsOpen = !scene.detailsOpen; }
  addRoom(scene: SceneNode): void {
    scene.rooms.push({ name: '', description: '', enemies: '', loot: '' });
    scene.detailsOpen = true;
  }
  removeRoom(scene: SceneNode, index: number): void { scene.rooms.splice(index, 1); }

  // --- Édition de l'arbre --------------------------------------------------

  toggleArc(arc: ArcNode): void { arc.collapsed = !arc.collapsed; }
  toggleChapter(chapter: ChapterNode): void { chapter.collapsed = !chapter.collapsed; }

  addArc(): void {
    this.tree.push({ name: '', description: '', type: 'LINEAR', chapters: [], collapsed: false, existing: false });
  }
  removeArc(index: number): void { this.tree.splice(index, 1); }

  addChapter(arc: ArcNode): void {
    arc.chapters.push({ name: '', description: '', scenes: [], collapsed: false, existing: false });
  }
  removeChapter(arc: ArcNode, index: number): void { arc.chapters.splice(index, 1); }

  addScene(chapter: ChapterNode): void {
    chapter.scenes.push({
      name: '', description: '', playerNarration: '', gmNotes: '', rooms: [], detailsOpen: true, existing: false
    });
  }
  removeScene(chapter: ChapterNode, index: number): void { chapter.scenes.splice(index, 1); }

  /** Compteurs des nœuds NOUVEAUX (= ce qui sera réellement créé). */
  get arcCount(): number { return this.tree.filter(a => !a.existing && a.name.trim()).length; }
  get chapterCount(): number {
    return this.tree.reduce((n, a) => n + a.chapters.filter(c => !c.existing && c.name.trim()).length, 0);
  }
  get sceneCount(): number {
    return this.tree.reduce((n, a) =>
      n + a.chapters.reduce((m, c) => m + c.scenes.filter(s => !s.existing && s.name.trim()).length, 0), 0);
  }

  /** Vrai s'il y a au moins un nœud nouveau à créer (sinon « Créer » désactivé). */
  get hasNewContent(): boolean {
    return this.arcCount > 0 || this.chapterCount > 0 || this.sceneCount > 0;
  }

  // --- Application (création) ----------------------------------------------

  apply(): void {
    if (this.applying || !this.hasNewContent) return;
    this.applying = true;
    this.applyError = null;

    // On envoie l'arbre fusionné COMPLET (existants + nouveaux) : les nœuds
    // `existing` portent leur existingId et servent de parents — l'apply ne
    // recrée que les nœuds sans existingId.
    const proposal: CampaignImportProposal = {
      arcs: this.tree
        .filter(a => a.name.trim())
        .map(a => ({
          name: a.name.trim(),
          description: a.description.trim(),
          type: a.type,
          existingId: a.existingId ?? null,
          chapters: a.chapters
            .filter(c => c.name.trim())
            .map(c => ({
              name: c.name.trim(),
              description: c.description.trim(),
              existingId: c.existingId ?? null,
              scenes: c.scenes
                .filter(s => s.name.trim())
                .map(s => ({
                  name: s.name.trim(),
                  description: s.description.trim(),
                  playerNarration: s.playerNarration.trim(),
                  gmNotes: s.gmNotes.trim(),
                  existingId: s.existingId ?? null,
                  rooms: s.rooms
                    .filter(r => r.name.trim())
                    .map(r => ({
                      name: r.name.trim(),
                      description: r.description.trim(),
                      enemies: r.enemies.trim(),
                      loot: r.loot.trim()
                    }))
                }))
            }))
        }))
    };

    this.service.applyStructure(this.campaignId, proposal).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId]),
      error: () => {
        this.applying = false;
        this.applyError = "Échec de la création. La campagne existe-t-elle toujours ?";
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
