import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Swords, Plus, Globe, Pencil, Trash2, User, Dices, Drama } from 'lucide-angular';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, switchMap, filter, map } from 'rxjs/operators';
import { CampaignService } from '../../../services/campaign.service';
import { LoreService } from '../../../services/lore.service';
import { GameSystemService } from '../../../services/game-system.service';
import { GameSystem } from '../../../services/game-system.model';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { Character } from '../../../services/character.model';
import { Npc } from '../../../services/npc.model';
import { LayoutService, GlobalItem } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Campaign, Arc } from '../../../services/campaign.model';
import { Lore } from '../../../services/lore.model';
import { loadCampaignTreeData, buildCampaignTree, CampaignTreeData } from '../../campaign-tree.helper';

@Component({
  selector: 'app-campaign-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, RouterLink],
  templateUrl: './campaign-detail.component.html',
  styleUrls: ['./campaign-detail.component.scss']
})
export class CampaignDetailComponent implements OnInit, OnDestroy {
  readonly Swords = Swords;
  readonly Plus = Plus;
  readonly Globe = Globe;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly User = User;
  readonly Dices = Dices;
  readonly Drama = Drama;

  campaign: Campaign | null = null;
  arcs: Arc[] = [];
  /** Nombre de chapitres par arc — alimente le compteur des cartes. */
  chapterCountByArc: Record<string, number> = {};
  /** Lore associé si `campaign.loreId` est renseigné ; sinon null. */
  linkedLore: Lore | null = null;
  /** Lores disponibles pour changer l'association en mode édition. */
  availableLores: Lore[] = [];
  /** GameSystems disponibles pour changer l'association en mode édition. */
  availableGameSystems: GameSystem[] = [];
  /** GameSystem associé si `campaign.gameSystemId` est renseigné ; sinon null. */
  linkedGameSystem: GameSystem | null = null;
  /** Fiches de personnages (PJ) de la campagne. */
  characters: Character[] = [];
  /** Fiches de personnages non-joueurs (PNJ) de la campagne. */
  npcs: Npc[] = [];

  /** Mode édition inline. */
  editing = false;
  editName = '';
  editDescription = '';
  editLoreId = '';
  editGameSystemId = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private loreService: LoreService,
    private gameSystemService: GameSystemService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {}

  ngOnInit(): void {
    // switchMap annule automatiquement le load précédent si l'utilisateur
    // change de campagne avant que le forkJoin ne réponde — évite qu'une
    // réponse en retard écrase des données plus récentes (race condition).
    this.route.paramMap.pipe(
      map(pm => pm.get('id')),
      filter((id): id is string => !!id && id !== this.campaign?.id),
      switchMap(id => forkJoin({
        campaign: this.campaignService.getCampaignById(id),
        allCampaigns: this.campaignService.getAllCampaigns(),
        treeData: loadCampaignTreeData(this.campaignService, id, this.characterService, this.npcService).pipe(
          catchError(() => of({ arcs: [], chaptersByArc: {}, scenesByChapter: {}, characters: [], npcs: [] } as CampaignTreeData))
        )
      }))
    ).subscribe(({ campaign, allCampaigns, treeData }) => {
      this.campaign = campaign;
      this.editing = false;
      this.loadLinkedLore(campaign);
      this.loadLinkedGameSystem(campaign);
      this.loadCharacters(campaign.id!);
      this.loadNpcs(campaign.id!);
      this.arcs = treeData.arcs;
      this.chapterCountByArc = this.computeChapterCounts(treeData);
      this.showLayout(allCampaigns, treeData);
      this.pageTitleService.set(campaign.name);
    });
  }

  private computeChapterCounts(data: CampaignTreeData): Record<string, number> {
    const counts: Record<string, number> = {};
    for (const arcId of Object.keys(data.chaptersByArc)) {
      counts[arcId] = data.chaptersByArc[arcId].length;
    }
    return counts;
  }

  /**
   * Recharge explicitement après une mise à jour locale (ex: saveEdit).
   * Contrairement au flux ngOnInit, on bypass le filter sur l'ID puisqu'on
   * veut rafraîchir même si l'ID n'a pas changé.
   */
  private reload(id: string): void {
    forkJoin({
      campaign: this.campaignService.getCampaignById(id),
      allCampaigns: this.campaignService.getAllCampaigns(),
      treeData: loadCampaignTreeData(this.campaignService, id, this.characterService, this.npcService).pipe(
        catchError(() => of({ arcs: [], chaptersByArc: {}, scenesByChapter: {}, characters: [], npcs: [] } as CampaignTreeData))
      )
    }).subscribe(({ campaign, allCampaigns, treeData }) => {
      this.campaign = campaign;
      this.editing = false;
      this.loadLinkedLore(campaign);
      this.loadLinkedGameSystem(campaign);
      this.loadCharacters(campaign.id!);
      this.loadNpcs(campaign.id!);
      this.arcs = treeData.arcs;
      this.chapterCountByArc = this.computeChapterCounts(treeData);
      this.showLayout(allCampaigns, treeData);
      this.pageTitleService.set(campaign.name);
    });
  }

  /**
   * Charge le Lore associé (si loreId présent). On swallow l'erreur :
   * si le Lore a été supprimé entre-temps, on affiche simplement "Univers introuvable".
   */
  private loadLinkedLore(campaign: Campaign): void {
    if (!campaign.loreId) {
      this.linkedLore = null;
      return;
    }
    this.loreService.getLoreById(campaign.loreId).pipe(
      catchError(() => of(null))
    ).subscribe(lore => this.linkedLore = lore);
  }

  /** Même logique pour le GameSystem associé : dégradation si supprimé. */
  private loadLinkedGameSystem(campaign: Campaign): void {
    if (!campaign.gameSystemId) {
      this.linkedGameSystem = null;
      return;
    }
    this.gameSystemService.getById(campaign.gameSystemId).pipe(
      catchError(() => of(null))
    ).subscribe(gs => this.linkedGameSystem = gs);
  }

  /** Charge les fiches de personnages (PJ) de la campagne. */
  private loadCharacters(campaignId: string): void {
    this.characterService.getByCampaign(campaignId).pipe(
      catchError(() => of([] as Character[]))
    ).subscribe(list => this.characters = list);
  }

  /** Symétrique pour les PNJ. */
  private loadNpcs(campaignId: string): void {
    this.npcService.getByCampaign(campaignId).pipe(
      catchError(() => of([] as Npc[]))
    ).subscribe(list => this.npcs = list);
  }

  createCharacter(): void {
    if (!this.campaign) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'characters', 'create']);
  }

  createNpc(): void {
    if (!this.campaign) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'npcs', 'create']);
  }

  editNpc(npc: Npc): void {
    if (!this.campaign || !npc.id) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'npcs', npc.id, 'edit']);
  }

  editCharacter(character: Character): void {
    if (!this.campaign || !character.id) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'characters', character.id, 'edit']);
  }

  /** Ouvre la vue lecture seule (style WorldAnvil) — clic sur la carte. */
  viewCharacter(character: Character): void {
    if (!this.campaign || !character.id) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'characters', character.id]);
  }

  viewNpc(npc: Npc): void {
    if (!this.campaign || !npc.id) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'npcs', npc.id]);
  }

  createArc(): void {
    if (!this.campaign) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'arcs', 'create']);
  }

  openArc(arc: Arc): void {
    if (!this.campaign || !arc.id) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'arcs', arc.id]);
  }

  /**
   * Extrait une ligne de resume pour la fiche PJ/PNJ — 1re valeur de template
   * non-vide (apres refonte 2026-04-30, remplace l'ancien parsing markdown).
   */
  personaSnippet(p: { values?: Record<string, string> }): string {
    const values = p.values ?? {};
    for (const v of Object.values(values)) {
      if (!v) continue;
      const firstMeaningful = v
        .split('\n')
        .map(l => l.trim())
        .find(l => l && !l.startsWith('#'));
      if (!firstMeaningful) continue;
      return firstMeaningful.length > 80
        ? firstMeaningful.substring(0, 77) + '…'
        : firstMeaningful;
    }
    return '(Fiche vide)';
  }

  /** Alias gardé pour compatibilité avec les anciens templates. */
  characterSnippet(c: Character): string {
    return this.personaSnippet(c);
  }

  private showLayout(allCampaigns: Campaign[], data: CampaignTreeData): void {
    const campaignId = this.campaign!.id!;
    const globalItems: GlobalItem[] = allCampaigns.map(c => ({
      id: c.id!,
      name: c.name,
      route: `/campaigns/${c.id}`
    }));

    this.layoutService.show({
      title: this.campaign!.name,
      items: buildCampaignTree(campaignId, data),
      footerLabel: 'Toutes les campagnes',
      createActions: [
        { id: 'create-arc', label: '+ Nouvel arc', variant: 'primary', route: `/campaigns/${campaignId}/arcs/create` }
      ],
      globalItems,
      globalBackLabel: 'Toutes les campagnes',
      globalBackRoute: '/campaigns'
    });
  }

  // ─────────────── Édition / suppression de la Campagne ───────────────

  startEdit(): void {
    if (!this.campaign) return;
    this.editName = this.campaign.name;
    this.editDescription = this.campaign.description ?? '';
    this.editLoreId = this.campaign.loreId ?? '';
    this.editGameSystemId = this.campaign.gameSystemId ?? '';
    // On charge les Lores et GameSystems disponibles uniquement à l'entrée en mode édition.
    this.loreService.getAllLores().subscribe({
      next: (lores) => this.availableLores = lores,
      error: () => this.availableLores = []
    });
    this.gameSystemService.getAll().subscribe({
      next: (gs) => this.availableGameSystems = gs,
      error: () => this.availableGameSystems = []
    });
    this.editing = true;
  }

  cancelEdit(): void {
    this.editing = false;
  }

  saveEdit(): void {
    if (!this.campaign || !this.editName.trim()) return;
    this.campaignService.updateCampaign(this.campaign.id!, {
      name: this.editName.trim(),
      description: this.editDescription,
      playerCount: this.campaign.playerCount ?? 0,
      loreId: this.editLoreId ? this.editLoreId : null,
      gameSystemId: this.editGameSystemId ? this.editGameSystemId : null
    }).subscribe({
      next: (updated) => {
        this.campaign = updated;
        this.editing = false;
        // Recharge pour actualiser le badge "Univers" et le titre sidebar.
        this.reload(updated.id!);
      },
      error: () => console.error('Erreur lors de la mise à jour de la campagne')
    });
  }

  /**
   * Suppression en cascade : récupère d'abord le détail de ce qui sera effacé
   * (arcs / chapitres / scènes / personnages), affiche le récapitulatif dans
   * la confirmation, puis supprime. Le cascade est orchestré côté backend dans
   * une seule transaction.
   */
  deleteCampaign(): void {
    if (!this.campaign) return;
    const campaign = this.campaign;
    this.campaignService.getCampaignDeletionImpact(campaign.id!).subscribe({
      next: impact => {
        const parts: string[] = [];
        if (impact.arcs > 0) parts.push(`${impact.arcs} arc${impact.arcs > 1 ? 's' : ''}`);
        if (impact.chapters > 0) parts.push(`${impact.chapters} chapitre${impact.chapters > 1 ? 's' : ''}`);
        if (impact.scenes > 0) parts.push(`${impact.scenes} scène${impact.scenes > 1 ? 's' : ''}`);
        if (impact.characters > 0) parts.push(`${impact.characters} personnage${impact.characters > 1 ? 's' : ''}`);

        const lines = [`Supprimer définitivement la campagne "${campaign.name}" ?`];
        if (parts.length) {
          lines.push('');
          lines.push(`Cette action supprimera aussi : ${parts.join(', ')}.`);
        }
        lines.push('');
        lines.push('Cette action est irréversible.');

        if (!confirm(lines.join('\n'))) return;
        this.campaignService.deleteCampaign(campaign.id!).subscribe({
          next: () => this.router.navigate(['/campaigns']),
          error: () => console.error('Erreur lors de la suppression de la campagne')
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances de la campagne')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
