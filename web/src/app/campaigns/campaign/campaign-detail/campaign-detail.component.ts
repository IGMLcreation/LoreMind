import { Component, OnInit, OnDestroy } from '@angular/core';

import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Swords, Plus, Globe, Pencil, Trash2, Dices, Drama, Check, Play, Upload, Sparkles } from 'lucide-angular';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, switchMap, filter, map } from 'rxjs/operators';
import { CampaignService } from '../../../services/campaign.service';
import { LoreService } from '../../../services/lore.service';
import { GameSystemService } from '../../../services/game-system.service';
import { GameSystem } from '../../../services/game-system.model';
import { CharacterService } from '../../../services/character.service';
import { NpcService } from '../../../services/npc.service';
import { RandomTableService } from '../../../services/random-table.service';
import { SessionService } from '../../../services/session.service';
import { PlaythroughService } from '../../../services/playthrough.service';
import { Playthrough } from '../../../services/campaign.model';
import { Session } from '../../../services/session.model';
import { Npc } from '../../../services/npc.model';
import { LayoutService } from '../../../services/layout.service';
import { PageTitleService } from '../../../services/page-title.service';
import { Campaign, Arc } from '../../../services/campaign.model';
import { Lore } from '../../../services/lore.model';
import { loadCampaignTreeData, buildCampaignSidebarConfig, CampaignTreeData } from '../../campaign-tree.helper';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

@Component({
    selector: 'app-campaign-detail',
    imports: [FormsModule, LucideAngularModule, RouterLink],
    templateUrl: './campaign-detail.component.html',
    styleUrls: ['./campaign-detail.component.scss']
})
export class CampaignDetailComponent implements OnInit, OnDestroy {
  readonly Swords = Swords;
  readonly Plus = Plus;
  readonly Globe = Globe;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;
  readonly Dices = Dices;
  readonly Drama = Drama;
  readonly Check = Check;
  readonly Play = Play;
  readonly Upload = Upload;
  readonly Sparkles = Sparkles;

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
  /** Fiches de personnages non-joueurs (PNJ) de la campagne. */
  npcs: Npc[] = [];
  /** Sessions de jeu (passées et en cours) liées à cette campagne. */
  sessions: Session[] = [];
  /**
   * Session active globale (toutes campagnes confondues).
   * Sert à désactiver le bouton "Lancer" si une session tourne déjà ailleurs.
   * Null si aucune session active dans l'app.
   */
  activeSessionGlobal: Session | null = null;
  /** Indicateur de lancement en cours pour éviter les double-clics. */
  startingSession = false;

  /** Parties (Playthroughs) de cette campagne. */
  playthroughs: Playthrough[] = [];

  /** Mode édition inline. */
  editing = false;
  editName = '';
  editDescription = '';
  editLoreId = '';
  editGameSystemId = '';

  /** Valeur sentinelle de l'option "Creer un systeme" dans le <select>. */
  readonly CREATE_GAMESYSTEM_SENTINEL = '__create__';
  /** Mode creation inline d'un GameSystem depuis le dropdown d'edition. */
  creatingGameSystem = false;
  newGameSystemName = '';
  creatingGameSystemInFlight = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private loreService: LoreService,
    private gameSystemService: GameSystemService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private randomTableService: RandomTableService,
    private sessionService: SessionService,
    private playthroughService: PlaythroughService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService,
    private confirmDialog: ConfirmDialogService
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
        treeData: loadCampaignTreeData(this.campaignService, id, this.characterService, this.npcService, this.randomTableService).pipe(
          catchError(() => of({ arcs: [], chaptersByArc: {}, scenesByChapter: {}, characters: [], npcs: [], randomTables: [] } as CampaignTreeData))
        ),
        playthroughs: this.playthroughService.listByCampaign(id).pipe(catchError(() => of([] as Playthrough[])))
      }))
    ).subscribe(({ campaign, allCampaigns, treeData, playthroughs }) => {
      this.campaign = campaign;
      this.editing = false;
      this.playthroughs = playthroughs;
      this.loadLinkedLore(campaign);
      this.loadLinkedGameSystem(campaign);
      this.loadNpcs(campaign.id!);
      this.loadSessions(campaign.id!);
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
      treeData: loadCampaignTreeData(this.campaignService, id, this.characterService, this.npcService, this.randomTableService).pipe(
        catchError(() => of({ arcs: [], chaptersByArc: {}, scenesByChapter: {}, characters: [], npcs: [], randomTables: [] } as CampaignTreeData))
      ),
      playthroughs: this.playthroughService.listByCampaign(id).pipe(catchError(() => of([] as Playthrough[])))
    }).subscribe(({ campaign, allCampaigns, treeData, playthroughs }) => {
      this.campaign = campaign;
      this.editing = false;
      this.playthroughs = playthroughs;
      this.loadLinkedLore(campaign);
      this.loadLinkedGameSystem(campaign);
      this.loadNpcs(campaign.id!);
      this.loadSessions(campaign.id!);
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

  /** Charge les PNJ de la campagne (les PJ vivent désormais dans une Partie). */
  private loadNpcs(campaignId: string): void {
    this.npcService.getByCampaign(campaignId).pipe(
      catchError(() => of([] as Npc[]))
    ).subscribe(list => this.npcs = list);
  }

  // Sessions retirées de cette vue (vivent dans Playthrough Detail).
  private loadSessions(_campaignId: string): void { this.sessions = []; }

  // ─────────────── Playthroughs (Parties) ───────────────

  openPlaythrough(p: Playthrough): void {
    this.router.navigate(['/campaigns', this.campaign?.id, 'playthroughs', p.id]);
  }

  /** Crée une nouvelle Partie avec un nom par défaut, puis y navigue. */
  newPlaythroughInFlight = false;
  createPlaythrough(): void {
    if (!this.campaign || this.newPlaythroughInFlight) return;
    this.newPlaythroughInFlight = true;
    const defaultName = `Nouvelle partie ${this.playthroughs.length + 1}`;
    this.playthroughService.create({
      campaignId: this.campaign.id!,
      name: defaultName
    }).subscribe({
      next: created => {
        this.newPlaythroughInFlight = false;
        this.router.navigate(['/campaigns', this.campaign?.id, 'playthroughs', created.id]);
      },
      error: () => { this.newPlaythroughInFlight = false; }
    });
  }

  createNpc(): void {
    if (!this.campaign) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'npcs', 'create']);
  }

  editNpc(npc: Npc): void {
    if (!this.campaign || !npc.id) return;
    this.router.navigate(['/campaigns', this.campaign.id, 'npcs', npc.id, 'edit']);
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

  private showLayout(allCampaigns: Campaign[], data: CampaignTreeData): void {
    this.layoutService.show(buildCampaignSidebarConfig(this.campaign!, allCampaigns, data, this.campaign!.id!));
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
    this.creatingGameSystem = false;
    this.newGameSystemName = '';
  }

  /** Detecte la selection de l'option sentinelle dans le <select> GameSystem. */
  onEditGameSystemChange(value: string): void {
    if (value === this.CREATE_GAMESYSTEM_SENTINEL) {
      this.editGameSystemId = '';
      this.startCreateGameSystem();
    }
  }

  startCreateGameSystem(): void {
    this.creatingGameSystem = true;
    this.newGameSystemName = '';
  }

  cancelCreateGameSystem(): void {
    this.creatingGameSystem = false;
    this.newGameSystemName = '';
  }

  submitCreateGameSystem(): void {
    const name = this.newGameSystemName.trim();
    if (!name || this.creatingGameSystemInFlight) return;
    this.creatingGameSystemInFlight = true;
    this.gameSystemService.create({ name, isPublic: false }).subscribe({
      next: (created) => {
        this.creatingGameSystemInFlight = false;
        this.availableGameSystems = [...this.availableGameSystems, created];
        if (created.id) {
          this.editGameSystemId = created.id;
        }
        this.creatingGameSystem = false;
        this.newGameSystemName = '';
      },
      error: () => {
        this.creatingGameSystemInFlight = false;
        console.error('Erreur lors de la creation du systeme de jeu');
      }
    });
  }

  saveEdit(): void {
    if (!this.campaign || !this.editName.trim()) return;
    const newGameSystemId = this.editGameSystemId ? this.editGameSystemId : null;
    const currentGameSystemId = this.campaign.gameSystemId ?? null;
    const gameSystemChanged = newGameSystemId !== currentGameSystemId;
    const hasSheets = this.npcs.length > 0;
    if (gameSystemChanged && hasSheets) {
      const count = this.npcs.length;
      this.confirmDialog.confirm({
        title: 'Changer le systeme de jeu ?',
        message:
          `Vous etes sur le point de changer le systeme de jeu de cette campagne. ` +
          `Cela change egalement le template des fiches de PJ et PNJ.`,
        details: [
          `${count} fiche(s) existante(s) sont liees au template du systeme actuel.`,
          `Leurs champs ne s'afficheront plus avec le nouveau systeme.`,
          `Les donnees restent stockees : revenir a l'ancien systeme les rendra a nouveau visibles.`
        ],
        confirmLabel: 'Changer quand meme',
        variant: 'warning'
      }).then(ok => { if (ok) this.persistEdit(newGameSystemId); });
      return;
    }
    this.persistEdit(newGameSystemId);
  }

  private persistEdit(newGameSystemId: string | null): void {
    if (!this.campaign) return;
    this.campaignService.updateCampaign(this.campaign.id!, {
      name: this.editName.trim(),
      description: this.editDescription,
      playerCount: this.campaign.playerCount ?? 0,
      loreId: this.editLoreId ? this.editLoreId : null,
      gameSystemId: newGameSystemId
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
        if (impact.playthroughs > 0) parts.push(`${impact.playthroughs} partie${impact.playthroughs > 1 ? 's' : ''} (avec ses PJ, sessions, faits)`);

        const details: string[] = [];
        if (parts.length) details.push(`Sera aussi supprime : ${parts.join(', ')}.`);
        details.push('Cette action est irreversible.');

        this.confirmDialog.confirm({
          title: 'Supprimer la campagne ?',
          message: `Supprimer definitivement la campagne "${campaign.name}" ?`,
          details,
          confirmLabel: 'Supprimer',
          variant: 'danger'
        }).then(ok => {
          if (!ok) return;
          this.campaignService.deleteCampaign(campaign.id!).subscribe({
            next: () => this.router.navigate(['/campaigns']),
            error: () => console.error('Erreur lors de la suppression de la campagne')
          });
        });
      },
      error: () => console.error('Impossible de récupérer les dépendances de la campagne')
    });
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
