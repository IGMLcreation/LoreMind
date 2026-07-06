import { Component, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, firstValueFrom } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2, Sparkles } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../../services/campaign.service';
import { StoredFileService } from '../../../services/stored-file.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Scene, SceneBattlemap, SceneBranch, Room, SceneType, LinkType } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { Enemy } from '../../../services/enemy.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { ExpandableSectionComponent } from '../../../shared/expandable-section/expandable-section.component';
import { LoreLinkPickerComponent } from '../../../shared/lore-link-picker/lore-link-picker.component';
import { EnemyLinkPickerComponent } from '../../../shared/enemy-link-picker/enemy-link-picker.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { RoomsEditorComponent } from '../../../shared/rooms-editor/rooms-editor.component';
import { EntityAssistPanelComponent } from '../../../shared/entity-assist-panel/entity-assist-panel.component';
import { FieldProposal } from '../../../services/entity-assist.model';
import { FileDropDirective } from '../../../shared/file-drop.directive';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * État d'édition d'UNE battlemap de la scène (une carte par variante : Jour, Nuit,
 * étage…). Enveloppe locale de SceneBattlemap + méta d'UI (noms de fichiers résolus,
 * source du fichier, uploads en cours) — seuls label/mediaFileId/dataFileId persistent.
 */
interface BattlemapEdit {
  label: string;
  mediaFileId: string | null;
  dataFileId: string | null;
  mediaName: string | null;
  dataName: string | null;
  /**
   * Source de CETTE carte :
   *  - 'DUNGEON_ALCHEMIST' : export Foundry = image/video + .json (2 fichiers).
   *  - 'DUNGEONDRAFT'      : .dd2vtt unique (Universal VTT, image embarquee) ;
   *    on extrait l'image a l'upload -> media, et on range le .dd2vtt en donnees.
   * Non persistee : deduite au chargement de l'extension du fichier de donnees.
   */
  source: 'DUNGEON_ALCHEMIST' | 'DUNGEONDRAFT';
  /** Le .dd2vtt deposse ne contenait pas d'image embarquee (carte sans fond). */
  dd2vttNoImage: boolean;
  uploadingMedia: boolean;
  uploadingData: boolean;
}

/**
 * Écran de détail/modification d'une Scène.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId
 */
@Component({
    selector: 'app-scene-edit',
    imports: [ReactiveFormsModule, LucideAngularModule, ExpandableSectionComponent, LoreLinkPickerComponent, EnemyLinkPickerComponent, AiChatDrawerComponent, ImageGalleryComponent, IconPickerComponent, RoomsEditorComponent, EntityAssistPanelComponent, FileDropDirective, TranslatePipe],
    templateUrl: './scene-edit.component.html',
    styleUrls: ['./scene-edit.component.scss']
})
export class SceneEditComponent implements OnInit {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
  readonly sceneTypeOptions: SceneType[] = ['GENERIC', 'LOCATION', 'ENCOUNTER', 'NPC', 'EVENT', 'REVELATION'];
  readonly linkTypeOptions: LinkType[] = ['EXIT', 'CLUE', 'LEAD'];
  selectedIcon: string | null = null;

  /** État drawer chat IA (b5.7 — intégration Campagne). */
  chatOpen = false;
  get chatQuickSuggestions(): string[] {
    return [
      this.translate.instant('sceneEdit.chatSuggestion1'),
      this.translate.instant('sceneEdit.chatSuggestion2'),
      this.translate.instant('sceneEdit.chatSuggestion3')
    ];
  }

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  form: FormGroup;
  campaignId = '';
  arcId = '';
  chapterId = '';
  sceneId = '';
  scene: Scene | null = null;

  /**
   * Section à déplier d'office (query param `?focus=combat|branches|rooms`) — posé par
   * le bouton « Corriger » du guidage pour atterrir directement sur l'outil fautif.
   */
  focusSection: string | null = null;

  availablePages: Page[] = [];
  loreId: string | null = null;
  relatedPageIds: string[] = [];
  /** Bestiaire de la campagne + fiches liées à la rencontre. */
  availableEnemies: Enemy[] = [];
  enemyIds: string[] = [];
  illustrationImageIds: string[] = [];

  /**
   * Battlemaps Foundry de la scene : une entree par variante (Jour/Nuit, etage…).
   * Non affichees dans l'appli — transportees a l'export Foundry.
   */
  battlemaps: BattlemapEdit[] = [];

  /** Scènes du chapitre courant (hors scène éditée) — alimente le dropdown des cibles. */
  siblingScenes: Scene[] = [];
  /** Branches narratives (état local mutable, persisté au submit). */
  branches: SceneBranch[] = [];

  /** Pièces du lieu explorable (état local, persisté au submit). */
  rooms: Room[] = [];

  onRoomsChange(next: Room[]): void { this.rooms = next; }

  /**
   * Applique au FORMULAIRE les champs étoffés retenus par l'utilisateur (Pilier A).
   * Non destructif : ne touche que les contrôles proposés ; l'utilisateur enregistre
   * ensuite normalement (rien n'est persisté par ce geste).
   */
  onAssistApplied(fields: FieldProposal[]): void {
    const patch: Record<string, string> = {};
    for (const f of fields) {
      if (this.form.get(f.key)) patch[f.key] = f.proposedValue;
    }
    this.form.patchValue(patch);
  }

  // ─────────────── État « rempli » par section (pastille de l'en-tête) ───────────────
  // Tout est optionnel sauf le titre : ces getters signalent visuellement ce qui
  // contient déjà du contenu, pour ne pas donner l'impression qu'il faut tout remplir.
  get illustrationsFilled(): boolean { return this.illustrationImageIds.length > 0; }
  get battlemapFilled(): boolean { return this.battlemaps.some(b => !!b.mediaFileId || !!b.dataFileId); }
  get contextFilled(): boolean {
    const v = this.form.value;
    return !!(v.location || v.timing || v.atmosphere);
  }
  get narrationFilled(): boolean { return !!this.form.value.playerNarration; }
  get gmNotesFilled(): boolean { return !!this.form.value.gmSecretNotes; }
  get choicesFilled(): boolean { return !!this.form.value.choicesConsequences; }
  get branchesFilled(): boolean { return this.branches.length > 0; }
  get combatFilled(): boolean {
    const v = this.form.value;
    return !!(v.combatDifficulty || v.enemies) || this.enemyIds.length > 0;
  }
  get loreFilled(): boolean { return this.relatedPageIds.length > 0; }
  get dungeonFilled(): boolean { return this.rooms.length > 0; }

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private storedFileService: StoredFileService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private enemyService: EnemyService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService,
    private destroyRef: DestroyRef
  ) {
    this.form = this.fb.group({
      name:                 ['', Validators.required],
      type:                 ['GENERIC'],
      description:          [''],
      // Contexte et ambiance
      location:             [''],
      timing:               [''],
      atmosphere:           [''],
      // Narration
      playerNarration:      [''],
      // Secrets MJ
      gmSecretNotes:        [''],
      // Choix
      choicesConsequences:  [''],
      // Combat
      combatDifficulty:     [''],
      enemies:              ['']
    });
  }

  ngOnInit(): void {
    // On s'abonne à paramMap plutôt que de lire snapshot une fois : Angular
    // réutilise le composant quand on navigue entre scènes frères via l'arbre
    // (même route pattern), et ngOnInit ne se relance pas.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
      const newCampaignId = pm.get('campaignId')!;
      const newArcId = pm.get('arcId')!;
      const newChapterId = pm.get('chapterId')!;
      const newSceneId = pm.get('sceneId')!;
      if (newSceneId !== this.sceneId ||
          newChapterId !== this.chapterId ||
          newArcId !== this.arcId ||
          newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.arcId = newArcId;
        this.chapterId = newChapterId;
        this.sceneId = newSceneId;
        this.focusSection = this.route.snapshot.queryParamMap.get('focus');
        this.loadAll();
      }
    });
  }

  private loadAll(): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      scene: this.campaignService.getSceneById(this.sceneId),
      chapterScenes: this.campaignService.getScenes(this.chapterId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(switchMap(pages => of({ ...data, pages, loreId: lid })));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ campaign, allCampaigns, scene, chapterScenes, treeData, pages, loreId }) => {
      this.scene = scene;
      this.pageTitleService.set(scene.name);
      this.loreId = loreId;
      this.availablePages = pages;
      this.relatedPageIds = [...(scene.relatedPageIds ?? [])];
      this.availableEnemies = treeData.enemies ?? [];
      this.enemyIds = [...(scene.enemyIds ?? [])];
      this.selectedIcon = scene.icon ?? null;
      this.illustrationImageIds = [...(scene.illustrationImageIds ?? [])];
      this.battlemaps = (scene.battlemaps ?? []).map(bm => {
        const entry: BattlemapEdit = {
          label: bm.label ?? '',
          mediaFileId: bm.mediaFileId ?? null,
          dataFileId: bm.dataFileId ?? null,
          mediaName: null, dataName: null,
          source: 'DUNGEON_ALCHEMIST', dd2vttNoImage: false,
          uploadingMedia: false, uploadingData: false
        };
        if (entry.mediaFileId) {
          this.storedFileService.getById(entry.mediaFileId)
            .subscribe({ next: f => entry.mediaName = f.filename, error: () => { /* best-effort : erreur ignorée volontairement */ } });
        }
        if (entry.dataFileId) {
          this.storedFileService.getById(entry.dataFileId).subscribe({
            next: f => {
              entry.dataName = f.filename;
              // Deduit la source : un .dd2vtt/.uvtt => DungeonDraft.
              if (/\.(dd2vtt|uvtt)$/i.test(f.filename)) entry.source = 'DUNGEONDRAFT';
            },
            error: () => { /* best-effort : erreur ignorée volontairement */ }
          });
        }
        return entry;
      });
      this.siblingScenes = chapterScenes.filter(s => s.id !== this.sceneId);
      this.branches = (scene.branches ?? []).map(b => ({ ...b }));
      this.rooms = (scene.rooms ?? []).map(r => ({ ...r, branches: [...(r.branches ?? [])] }));
      this.form.patchValue({
        name:                 scene.name,
        type:                 scene.type ?? 'GENERIC',
        description:          scene.description ?? '',
        location:             scene.location ?? '',
        timing:               scene.timing ?? '',
        atmosphere:           scene.atmosphere ?? '',
        playerNarration:      scene.playerNarration ?? '',
        gmSecretNotes:        scene.gmSecretNotes ?? '',
        choicesConsequences:  scene.choicesConsequences ?? '',
        combatDifficulty:     scene.combatDifficulty ?? '',
        enemies:              scene.enemies ?? ''
      });

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  submit(): void {
    if (this.form.invalid || !this.scene) return;
    this.campaignService.updateScene(this.sceneId, {
      name:                 this.form.value.name,
      type:                 this.form.value.type,
      description:          this.form.value.description,
      chapterId:            this.chapterId,
      order:                this.scene.order ?? 1,
      location:             this.form.value.location,
      timing:               this.form.value.timing,
      atmosphere:           this.form.value.atmosphere,
      playerNarration:      this.form.value.playerNarration,
      gmSecretNotes:        this.form.value.gmSecretNotes,
      choicesConsequences:  this.form.value.choicesConsequences,
      combatDifficulty:     this.form.value.combatDifficulty,
      enemies:              this.form.value.enemies,
      enemyIds:             this.enemyIds,
      relatedPageIds:       this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds,
      // Seules les cartes portant au moins un fichier sont persistees (une entree
      // ajoutee puis laissee vide n'est pas une carte).
      battlemaps: this.battlemaps
        .filter(b => b.mediaFileId || b.dataFileId)
        .map(b => ({ label: b.label.trim(), mediaFileId: b.mediaFileId, dataFileId: b.dataFileId } as SceneBattlemap)),
      branches:             this.branches,
      rooms:                this.rooms,
      icon:                 this.selectedIcon
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', this.sceneId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    this.confirmDialog.confirm({
      title: this.translate.instant('sceneEdit.deleteTitle'),
      message: this.translate.instant('sceneEdit.deleteMessage', { name: this.scene?.name }),
      details: [this.translate.instant('sceneEdit.deleteIrreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.campaignService.deleteScene(this.sceneId).subscribe({
        next: () => this.router.navigate(['/campaigns', this.campaignId]),
        error: () => console.error('Erreur lors de la suppression')
      });
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId, 'chapters', this.chapterId, 'scenes', this.sceneId]);
  }

  // ─────────────── Gestion des branches narratives ───────────────


  /**
   * Destination de branche morte : renseignée mais ne résolvant vers aucune scène sœur
   * (scène supprimée). Sans ce signal, la branche est invisible (le <select> affiche le
   * placeholder) alors que le guidage la signale « cassée » ET que la sauvegarde serait
   * refusée par le backend — incompréhensible pour l'utilisateur.
   */
  isBranchTargetBroken(branch: SceneBranch): boolean {
    return !!branch.targetSceneId && !this.siblingScenes.some(s => s.id === branch.targetSceneId);
  }

  addBranch(): void {
    this.branches.push({ label: '', targetSceneId: '', condition: '', kind: 'EXIT' });
  }

  removeBranch(index: number): void {
    this.branches.splice(index, 1);
  }

  updateBranchLabel(index: number, value: string): void {
    this.branches[index].label = value;
  }

  updateBranchTarget(index: number, value: string): void {
    this.branches[index].targetSceneId = value;
  }

  updateBranchCondition(index: number, value: string): void {
    this.branches[index].condition = value;
  }

  updateBranchKind(index: number, value: string): void {
    this.branches[index].kind = value as LinkType;
  }

  // ─────────────── Battlemaps Foundry (liste de variantes) ───────────────
  // On NE supprime PAS le binaire au "retirer" (juste la reference locale) :
  // si l'utilisateur annule le formulaire, la scene garde son fichier intact.
  // Le binaire orphelin eventuel est inoffensif (nettoyage ulterieur possible).

  /** Ajoute une carte vierge (variante Jour/Nuit, etage…). */
  addBattlemap(): void {
    this.battlemaps.push({
      label: '', mediaFileId: null, dataFileId: null, mediaName: null, dataName: null,
      source: 'DUNGEON_ALCHEMIST', dd2vttNoImage: false,
      uploadingMedia: false, uploadingData: false
    });
  }

  /** Retire la carte entiere (references locales seulement, binaires conserves). */
  removeBattlemap(index: number): void {
    this.battlemaps.splice(index, 1);
  }

  updateBattlemapLabel(index: number, value: string): void {
    this.battlemaps[index].label = value;
  }

  onBattlemapMediaSelected(bm: BattlemapEdit, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadBattlemapMedia(bm, file);
    input.value = '';
  }

  onBattlemapMediaDropped(bm: BattlemapEdit, files: File[]): void {
    if (files[0]) this.uploadBattlemapMedia(bm, files[0]);
  }

  private uploadBattlemapMedia(bm: BattlemapEdit, file: File): void {
    bm.uploadingMedia = true;
    this.storedFileService.upload(file).subscribe({
      next: f => {
        bm.mediaFileId = f.id;
        bm.mediaName = f.filename;
        bm.uploadingMedia = false;
      },
      error: () => { bm.uploadingMedia = false; }
    });
  }

  onBattlemapDataSelected(bm: BattlemapEdit, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadBattlemapData(bm, file);
    input.value = '';
  }

  onBattlemapDataDropped(bm: BattlemapEdit, files: File[]): void {
    if (files[0]) this.uploadBattlemapData(bm, files[0]);
  }

  private uploadBattlemapData(bm: BattlemapEdit, file: File): void {
    bm.uploadingData = true;
    this.storedFileService.upload(file).subscribe({
      next: f => {
        bm.dataFileId = f.id;
        bm.dataName = f.filename;
        bm.uploadingData = false;
      },
      error: () => { bm.uploadingData = false; }
    });
  }

  removeBattlemapMedia(bm: BattlemapEdit): void {
    bm.mediaFileId = null;
    bm.mediaName = null;
  }

  removeBattlemapData(bm: BattlemapEdit): void {
    bm.dataFileId = null;
    bm.dataName = null;
  }

  // ─────────────── Source de carte (Dungeon Alchemist / DungeonDraft) ──────────

  /** Change la source de CETTE carte ; repart d'une carte vierge (les deux formats diffèrent). */
  setBattlemapSource(bm: BattlemapEdit, src: 'DUNGEON_ALCHEMIST' | 'DUNGEONDRAFT'): void {
    if (src === bm.source) return;
    bm.source = src;
    bm.mediaFileId = null;
    bm.mediaName = null;
    bm.dataFileId = null;
    bm.dataName = null;
    bm.dd2vttNoImage = false;
  }

  /** Retire la carte DungeonDraft (data + media dérivé). */
  removeDd2vtt(bm: BattlemapEdit): void {
    bm.mediaFileId = null;
    bm.mediaName = null;
    bm.dataFileId = null;
    bm.dataName = null;
    bm.dd2vttNoImage = false;
  }

  onDd2vttSelected(bm: BattlemapEdit, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Fire-and-forget : uploadDd2vtt gère lui-même ses erreurs.
    if (file) this.uploadDd2vtt(bm, file);
    input.value = '';
  }

  onDd2vttDropped(bm: BattlemapEdit, files: File[]): void {
    // Fire-and-forget : uploadDd2vtt gère lui-même ses erreurs.
    if (files[0]) this.uploadDd2vtt(bm, files[0]);
  }

  /**
   * Upload d'un .dd2vtt (Universal VTT) : on en EXTRAIT l'image embarquee (base64)
   * pour en faire le media (fond de carte pour l'export Foundry), et on range le
   * sidecar (murs/lumieres/grille, SANS l'image pour ne pas dupliquer le binaire)
   * comme fichier de donnees.
   */
  private async uploadDd2vtt(bm: BattlemapEdit, file: File): Promise<void> {
    bm.uploadingData = true;
    bm.dd2vttNoImage = false;
    try {
      const json = JSON.parse(await file.text());
      const { image, ...sidecar } = json ?? {};
      // Sidecar allégé (sans l'image embarquée) ; on garde le nom .dd2vtt.
      const dataFile = new File([JSON.stringify(sidecar)], file.name, { type: 'application/json' });
      const storedData = await firstValueFrom(this.storedFileService.upload(dataFile));
      bm.dataFileId = storedData.id;
      bm.dataName = storedData.filename;

      if (typeof image === 'string' && image.length > 0) {
        const blob = this.base64ToImageBlob(image);
        const imgName = this.baseName(file.name) + this.extForType(blob.type);
        const imgFile = new File([blob], imgName, { type: blob.type });
        const storedMedia = await firstValueFrom(this.storedFileService.upload(imgFile));
        bm.mediaFileId = storedMedia.id;
        bm.mediaName = storedMedia.filename;
      } else {
        bm.mediaFileId = null;
        bm.mediaName = null;
        bm.dd2vttNoImage = true;
      }
    } catch {
      // JSON illisible : on stocke le fichier brut comme données, sans image.
      try {
        const stored = await firstValueFrom(this.storedFileService.upload(file));
        bm.dataFileId = stored.id;
        bm.dataName = stored.filename;
        bm.dd2vttNoImage = true;
      } catch { /* upload échoué : on ignore */ }
    } finally {
      bm.uploadingData = false;
    }
  }

  /** Décode une image base64 (sans préfixe data:) en Blob, type sniffé. */
  private base64ToImageBlob(b64: string): Blob {
    const clean = b64.includes(',') ? b64.slice(b64.indexOf(',') + 1) : b64.trim();
    const bin = atob(clean);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new Blob([bytes], { type: this.sniffImageType(bytes) });
  }

  private sniffImageType(b: Uint8Array): string {
    if (b[0] === 0x89 && b[1] === 0x50) return 'image/png';
    if (b[0] === 0xff && b[1] === 0xd8) return 'image/jpeg';
    if (b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46) return 'image/webp'; // RIFF
    return 'image/png';
  }

  private baseName(name: string): string {
    return name.replace(/\.[^./\\]+$/, '');
  }

  private extForType(type: string): string {
    if (type === 'image/jpeg') return '.jpg';
    return type === 'image/webp' ? '.webp' : '.png';
  }
}
