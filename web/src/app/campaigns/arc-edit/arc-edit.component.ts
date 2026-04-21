import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, Trash2, Sparkles } from 'lucide-angular';
import { CampaignService } from '../../services/campaign.service';
import { PageService } from '../../services/page.service';
import { LayoutService, GlobalItem } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { Campaign, Arc } from '../../services/campaign.model';
import { Page } from '../../services/page.model';
import { loadCampaignTreeData, buildCampaignTree } from '../campaign-tree.helper';
import { LoreLinkPickerComponent } from '../../shared/lore-link-picker/lore-link-picker.component';
import { AiChatDrawerComponent } from '../../shared/ai-chat-drawer/ai-chat-drawer.component';
import { ImageGalleryComponent } from '../../shared/image-gallery/image-gallery.component';

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
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, LoreLinkPickerComponent, AiChatDrawerComponent, ImageGalleryComponent],
  templateUrl: './arc-edit.component.html',
  styleUrls: ['./arc-edit.component.scss']
})
export class ArcEditComponent implements OnInit, OnDestroy {
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  /** État drawer chat IA (b5.7 — intégration Campagne). */
  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose 3 thèmes majeurs pour cet arc',
    'Imagine des enjeux qui mettent la pression sur les joueurs',
    'Suggère un dénouement en deux actes'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

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
  /** IDs des images utilisees comme cartes / plans (outil de table). */
  mapImageIds: string[] = [];

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {
    this.form = this.fb.group({
      name:        ['', Validators.required],
      description: [''],
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
    this.route.paramMap.subscribe(pm => {
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
      treeData: loadCampaignTreeData(this.campaignService, this.campaignId)
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
      this.illustrationImageIds = [...(arc.illustrationImageIds ?? [])];
      this.mapImageIds = [...(arc.mapImageIds ?? [])];
      this.pageTitleService.set(arc.name);
      this.form.patchValue({
        name:        arc.name,
        description: arc.description ?? '',
        themes:      arc.themes ?? '',
        stakes:      arc.stakes ?? '',
        gmNotes:     arc.gmNotes ?? '',
        rewards:     arc.rewards ?? '',
        resolution:  arc.resolution ?? ''
      });

      const globalItems: GlobalItem[] = allCampaigns.map((c: Campaign) => ({
        id: c.id!, name: c.name, route: `/campaigns/${c.id}`
      }));

      this.layoutService.show({
        title: campaign.name,
        items: buildCampaignTree(this.campaignId, treeData),
        footerLabel: 'Toutes les campagnes',
        createActions: [
          { id: 'create-arc', label: '+ Nouvel arc', variant: 'primary', route: `/campaigns/${this.campaignId}/arcs/create` }
        ],
        globalItems,
        globalBackLabel: 'Toutes les campagnes',
        globalBackRoute: '/campaigns'
      });
    });
  }

  submit(): void {
    if (this.form.invalid || !this.arc) return;
    this.campaignService.updateArc(this.arcId, {
      name:           this.form.value.name,
      description:    this.form.value.description,
      campaignId:     this.campaignId,
      order:          this.arc.order ?? 1,
      themes:         this.form.value.themes,
      stakes:         this.form.value.stakes,
      gmNotes:        this.form.value.gmNotes,
      rewards:        this.form.value.rewards,
      resolution:     this.form.value.resolution,
      relatedPageIds: this.relatedPageIds,
      illustrationImageIds: this.illustrationImageIds,
      mapImageIds:    this.mapImageIds
    }).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId]),
      error: () => console.error('Erreur lors de la sauvegarde')
    });
  }

  delete(): void {
    if (!confirm(`Supprimer l'arc "${this.arc?.name}" ? Cette action est irréversible.`)) return;
    this.campaignService.deleteArc(this.arcId).subscribe({
      next: () => this.router.navigate(['/campaigns', this.campaignId]),
      error: () => console.error('Erreur lors de la suppression')
    });
  }

  cancel(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'arcs', this.arcId]);
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
