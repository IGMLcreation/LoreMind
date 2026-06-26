import { Component, OnInit, OnDestroy } from '@angular/core';

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
import { Scene, SceneBranch, Room } from '../../../services/campaign.model';
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
import { FileDropDirective } from '../../../shared/file-drop.directive';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Écran de détail/modification d'une Scène.
 * Route : /campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId
 */
@Component({
    selector: 'app-scene-edit',
    imports: [ReactiveFormsModule, LucideAngularModule, ExpandableSectionComponent, LoreLinkPickerComponent, EnemyLinkPickerComponent, AiChatDrawerComponent, ImageGalleryComponent, IconPickerComponent, RoomsEditorComponent, FileDropDirective, TranslatePipe],
    templateUrl: './scene-edit.component.html',
    styleUrls: ['./scene-edit.component.scss']
})
export class SceneEditComponent implements OnInit, OnDestroy {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
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

  availablePages: Page[] = [];
  loreId: string | null = null;
  relatedPageIds: string[] = [];
  /** Bestiaire de la campagne + fiches liées à la rencontre. */
  availableEnemies: Enemy[] = [];
  enemyIds: string[] = [];
  illustrationImageIds: string[] = [];

  /** Battlemap Foundry : paire { media + sidecar JSON Universal VTT }. Non affichee. */
  battlemapMediaFileId: string | null = null;
  battlemapDataFileId: string | null = null;
  battlemapMediaName: string | null = null;
  battlemapDataName: string | null = null;
  battlemapUploadingMedia = false;
  battlemapUploadingData = false;

  /**
   * Source de carte choisie pour CETTE scene :
   *  - 'DUNGEON_ALCHEMIST' : export Foundry = image/video + .json (2 fichiers).
   *  - 'DUNGEONDRAFT'      : .dd2vtt unique (Universal VTT, image embarquee) ;
   *    on extrait l'image a l'upload -> media, et on range le .dd2vtt en donnees.
   * Non persistee : deduite au chargement de l'extension du fichier de donnees.
   */
  battlemapSource: 'DUNGEON_ALCHEMIST' | 'DUNGEONDRAFT' = 'DUNGEON_ALCHEMIST';
  /** Le .dd2vtt deposse ne contenait pas d'image embarquee (carte sans fond). */
  battlemapDd2vttNoImage = false;

  /** Scènes du chapitre courant (hors scène éditée) — alimente le dropdown des cibles. */
  siblingScenes: Scene[] = [];
  /** Branches narratives (état local mutable, persisté au submit). */
  branches: SceneBranch[] = [];

  /** Pièces du lieu explorable (état local, persisté au submit). */
  rooms: Room[] = [];

  onRoomsChange(next: Room[]): void { this.rooms = next; }

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
    private translate: TranslateService
  ) {
    this.form = this.fb.group({
      name:                 ['', Validators.required],
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
    this.route.paramMap.subscribe(pm => {
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
      })
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
      this.battlemapMediaFileId = scene.battlemapMediaFileId ?? null;
      this.battlemapDataFileId = scene.battlemapDataFileId ?? null;
      this.battlemapMediaName = null;
      this.battlemapDataName = null;
      this.battlemapSource = 'DUNGEON_ALCHEMIST';
      this.battlemapDd2vttNoImage = false;
      if (this.battlemapMediaFileId) {
        this.storedFileService.getById(this.battlemapMediaFileId)
          .subscribe({ next: f => this.battlemapMediaName = f.filename, error: () => {} });
      }
      if (this.battlemapDataFileId) {
        this.storedFileService.getById(this.battlemapDataFileId).subscribe({
          next: f => {
            this.battlemapDataName = f.filename;
            // Deduit la source : un .dd2vtt/.uvtt => DungeonDraft.
            if (/\.(dd2vtt|uvtt)$/i.test(f.filename)) this.battlemapSource = 'DUNGEONDRAFT';
          },
          error: () => {}
        });
      }
      this.siblingScenes = chapterScenes.filter(s => s.id !== this.sceneId);
      this.branches = (scene.branches ?? []).map(b => ({ ...b }));
      this.rooms = (scene.rooms ?? []).map(r => ({ ...r, branches: [...(r.branches ?? [])] }));
      this.form.patchValue({
        name:                 scene.name,
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
      battlemapMediaFileId: this.battlemapMediaFileId,
      battlemapDataFileId:  this.battlemapDataFileId,
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


  addBranch(): void {
    this.branches.push({ label: '', targetSceneId: '', condition: '' });
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

  // ─────────────── Battlemap Foundry (media + sidecar JSON) ───────────────
  // On NE supprime PAS le binaire au "retirer" (juste la reference locale) :
  // si l'utilisateur annule le formulaire, la scene garde son fichier intact.
  // Le binaire orphelin eventuel est inoffensif (nettoyage ulterieur possible).

  onBattlemapMediaSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadBattlemapMedia(file);
    input.value = '';
  }

  onBattlemapMediaDropped(files: File[]): void {
    if (files[0]) this.uploadBattlemapMedia(files[0]);
  }

  private uploadBattlemapMedia(file: File): void {
    this.battlemapUploadingMedia = true;
    this.storedFileService.upload(file).subscribe({
      next: f => {
        this.battlemapMediaFileId = f.id;
        this.battlemapMediaName = f.filename;
        this.battlemapUploadingMedia = false;
      },
      error: () => { this.battlemapUploadingMedia = false; }
    });
  }

  onBattlemapDataSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.uploadBattlemapData(file);
    input.value = '';
  }

  onBattlemapDataDropped(files: File[]): void {
    if (files[0]) this.uploadBattlemapData(files[0]);
  }

  private uploadBattlemapData(file: File): void {
    this.battlemapUploadingData = true;
    this.storedFileService.upload(file).subscribe({
      next: f => {
        this.battlemapDataFileId = f.id;
        this.battlemapDataName = f.filename;
        this.battlemapUploadingData = false;
      },
      error: () => { this.battlemapUploadingData = false; }
    });
  }

  removeBattlemapMedia(): void {
    this.battlemapMediaFileId = null;
    this.battlemapMediaName = null;
  }

  removeBattlemapData(): void {
    this.battlemapDataFileId = null;
    this.battlemapDataName = null;
  }

  // ─────────────── Source de carte (Dungeon Alchemist / DungeonDraft) ──────────

  /** Change la source ; repart d'une carte vierge (les deux formats diffèrent). */
  setBattlemapSource(src: 'DUNGEON_ALCHEMIST' | 'DUNGEONDRAFT'): void {
    if (src === this.battlemapSource) return;
    this.battlemapSource = src;
    this.battlemapMediaFileId = null;
    this.battlemapMediaName = null;
    this.battlemapDataFileId = null;
    this.battlemapDataName = null;
    this.battlemapDd2vttNoImage = false;
  }

  /** Retire la carte DungeonDraft (data + media dérivé). */
  removeDd2vtt(): void {
    this.battlemapMediaFileId = null;
    this.battlemapMediaName = null;
    this.battlemapDataFileId = null;
    this.battlemapDataName = null;
    this.battlemapDd2vttNoImage = false;
  }

  onDd2vttSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) void this.uploadDd2vtt(file);
    input.value = '';
  }

  onDd2vttDropped(files: File[]): void {
    if (files[0]) void this.uploadDd2vtt(files[0]);
  }

  /**
   * Upload d'un .dd2vtt (Universal VTT) : on en EXTRAIT l'image embarquee (base64)
   * pour en faire le media (fond de carte pour l'export Foundry), et on range le
   * sidecar (murs/lumieres/grille, SANS l'image pour ne pas dupliquer le binaire)
   * comme fichier de donnees.
   */
  private async uploadDd2vtt(file: File): Promise<void> {
    this.battlemapUploadingData = true;
    this.battlemapDd2vttNoImage = false;
    try {
      const json = JSON.parse(await file.text());
      const { image, ...sidecar } = json ?? {};
      // Sidecar allégé (sans l'image embarquée) ; on garde le nom .dd2vtt.
      const dataFile = new File([JSON.stringify(sidecar)], file.name, { type: 'application/json' });
      const storedData = await firstValueFrom(this.storedFileService.upload(dataFile));
      this.battlemapDataFileId = storedData.id;
      this.battlemapDataName = storedData.filename;

      if (typeof image === 'string' && image.length > 0) {
        const blob = this.base64ToImageBlob(image);
        const imgName = this.baseName(file.name) + this.extForType(blob.type);
        const imgFile = new File([blob], imgName, { type: blob.type });
        const storedMedia = await firstValueFrom(this.storedFileService.upload(imgFile));
        this.battlemapMediaFileId = storedMedia.id;
        this.battlemapMediaName = storedMedia.filename;
      } else {
        this.battlemapMediaFileId = null;
        this.battlemapMediaName = null;
        this.battlemapDd2vttNoImage = true;
      }
    } catch {
      // JSON illisible : on stocke le fichier brut comme données, sans image.
      try {
        const stored = await firstValueFrom(this.storedFileService.upload(file));
        this.battlemapDataFileId = stored.id;
        this.battlemapDataName = stored.filename;
        this.battlemapDd2vttNoImage = true;
      } catch { /* upload échoué : on ignore */ }
    } finally {
      this.battlemapUploadingData = false;
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
    return type === 'image/jpeg' ? '.jpg' : type === 'image/webp' ? '.webp' : '.png';
  }

  ngOnDestroy(): void {
    // Volontairement vide : la sidebar reste prise en charge par le composant
    // suivant (autre sous-route ou le composant detail parent) qui appellera
    // show(). Eviter d'appeler hide() ici previent le clignotement / la
    // disparition de la sidebar lors des navigations internes a la section.
  }
}
