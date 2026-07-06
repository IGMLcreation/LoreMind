import { Component, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2, Sparkles } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignService } from '../../../services/campaign.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { EnemyService } from '../../../services/enemy.service';
import { PageService } from '../../../services/page.service';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Arc } from '../../../services/campaign.model';
import { Page } from '../../../services/page.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig } from '../../campaign-tree.helper';
import { LoreLinkPickerComponent } from '../../../shared/lore-link-picker/lore-link-picker.component';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../../shared/image-gallery/image-gallery.component';
import { IconPickerComponent } from '../../../shared/icon-picker/icon-picker.component';
import { ExpandableSectionComponent } from '../../../shared/expandable-section/expandable-section.component';
import { CAMPAIGN_ICON_OPTIONS } from '../../campaign-icons';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { EntityAssistPanelComponent } from '../../../shared/entity-assist-panel/entity-assist-panel.component';
import { FieldProposal } from '../../../services/entity-assist.model';

/**
 * Écran de détail/modification d'un Arc.
 * Route : /campaigns/:campaignId/arcs/:arcId
 *
 * Intègre le picker de pages Lore (phase B2 cross-context) :
 * si la campagne parente est associée à un Lore (`campaign.loreId`), les pages
 * de ce Lore sont proposées dans un autocomplete pour lier cet arc à des
 * personnages / lieux / objets du Lore.
 */
@Component({
    selector: 'app-arc-edit',
    imports: [ReactiveFormsModule, LucideAngularModule, LoreLinkPickerComponent, AiChatDrawerComponent, ImageGalleryComponent, IconPickerComponent, ExpandableSectionComponent, EntityAssistPanelComponent, TranslatePipe],
    templateUrl: './arc-edit.component.html',
    styleUrls: ['./arc-edit.component.scss']
})
export class ArcEditComponent implements OnInit {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;
  readonly campaignIconOptions = CAMPAIGN_ICON_OPTIONS;
  selectedIcon: string | null = null;

  /** État drawer chat IA (b5.7 — intégration Campagne). */
  chatOpen = false;
  get chatQuickSuggestions(): string[] {
    return [
      this.translate.instant('arcEdit.chatSuggestion1'),
      this.translate.instant('arcEdit.chatSuggestion2'),
      this.translate.instant('arcEdit.chatSuggestion3')
    ];
  }

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  /** Applique au FORMULAIRE les champs étoffés retenus (Pilier A). Non destructif. */
  onAssistApplied(fields: FieldProposal[]): void {
    const patch: Record<string, string> = {};
    for (const f of fields) {
      if (this.form.get(f.key)) patch[f.key] = f.proposedValue;
    }
    this.form.patchValue(patch);
  }

  form: FormGroup;
  campaignId = '';
  arcId = '';
  arc: Arc | null = null;

  /** Pages disponibles pour le picker (vide si la campagne n'a pas de loreId). */
  availablePages: Page[] = [];
  /** ID du Lore associé à la campagne (null si campagne sans univers). */
  loreId: string | null = null;
  /** IDs des pages liées à cet arc (bind sur app-lore-link-picker). */
  relatedPageIds: string[] = [];

  /** IDs des images illustrant cet arc (bind sur app-image-gallery editable). */
  illustrationImageIds: string[] = [];

  // ─────────────── État « rempli » par section (pastille de l'en-tête) ───────────────
  // Seul le titre est requis ; ces getters signalent ce qui contient déjà du contenu.
  get illustrationsFilled(): boolean { return this.illustrationImageIds.length > 0; }
  get themesStakesFilled(): boolean {
    const v = this.form.value;
    return !!(v.themes || v.stakes);
  }
  get gmNotesFilled(): boolean { return !!this.form.value.gmNotes; }
  get rewardsResolutionFilled(): boolean {
    const v = this.form.value;
    return !!(v.rewards || v.resolution);
  }
  get loreFilled(): boolean { return this.relatedPageIds.length > 0; }

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
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
      name:        ['', Validators.required],
      description: [''],
      type:        ['LINEAR', Validators.required],
      themes:      [''],
      stakes:      [''],
      gmNotes:     [''],
      rewards:     [''],
      resolution:  ['']
    });
  }

  ngOnInit(): void {
    // On s'abonne à paramMap plutôt que de lire snapshot une fois : Angular
    // réutilise le composant quand on navigue entre arcs frères via l'arbre
    // (même route pattern), et ngOnInit ne se relance pas.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
      const newCampaignId = pm.get('campaignId')!;
      const newArcId = pm.get('arcId')!;
      if (newArcId !== this.arcId || newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.arcId = newArcId;
        this.loadAll();
      }
    });
  }

  private loadAll(): void {
    // On déclenche d'abord les 4 appels indépendants, puis on charge les pages
    // du Lore associé UNIQUEMENT si la campagne en a un (switchMap conditionnel).
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      allCampaigns: this.campaignService.getAllCampaigns(),
      arc: this.campaignService.getArcById(this.arcId),
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId, this.npcService, this.randomTableService, this.enemyService)
    }).pipe(
      switchMap(data => {
        const lid = data.campaign.loreId ?? null;
        // Pas de loreId → pas de picker, on retourne une liste vide.
        const pages$ = lid ? this.pageService.getByLoreId(lid) : of([] as Page[]);
        return pages$.pipe(
          switchMap(pages => of({ ...data, pages, loreId: lid }))
        );
      })
    ).subscribe(({ campaign, allCampaigns, arc, treeData, pages, loreId }) => {
      this.arc = arc;
      this.loreId = loreId;
      this.availablePages = pages;
      this.relatedPageIds = [...(arc.relatedPageIds ?? [])];
      this.selectedIcon = arc.icon ?? null;
      this.illustrationImageIds = [...(arc.illustrationImageIds ?? [])];
      this.pageTitleService.set(arc.name);
      this.form.patchValue({
        name:        arc.name,
        description: arc.description ?? '',
        type:        arc.type ?? 'LINEAR',
        themes:      arc.themes ?? '',
        stakes:      arc.stakes ?? '',
        gmNotes:     arc.gmNotes ?? '',
        rewards:     arc.rewards ?? '',
        resolution:  arc.resolution ?? ''
      });

      this.layoutService.show(buildCampaignSidebarConfig(campaign, allCampaigns, treeData, this.campaignId, this.translate));
    });
  }

  submit(): void {
    if (this.form.invalid || !this.arc) return;
    this.campaignService.updateArc(this.arcId, {
      name:           this.form.value.name,
      description:    this.form.value.description,
      campaignId:     this.campaignId,
      order:          this.arc.order ?? 1,
      type:           this.form.value.type,
      themes:         this.form.value.themes,
      stakes:         this.form.value.stakes,
      gmNotes:        this.form.value.gmNotes,
      rewards:        this.form.value.rewards,
      resolution:     this.form.value.resolution,
      relatedPageIds: this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds,
      icon:           this.selectedIcon
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    this.confirmDialog.confirm({
      title: this.translate.instant('arcEdit.deleteTitle'),
      message: this.translate.instant('arcEdit.deleteMessage', { name: this.arc?.name }),
      details: [this.translate.instant('arcEdit.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.campaignService.deleteArc(this.arcId).subscribe({
        next: () => this.router.navigate(['/campaigns', this.campaignId]),
        error: () => console.error('Erreur lors de la suppression')
      });
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId]);
  }
}
