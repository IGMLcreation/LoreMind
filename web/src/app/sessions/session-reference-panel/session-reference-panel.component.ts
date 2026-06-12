import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';

import { LucideAngularModule, User, Drama, Swords, Dices, ExternalLink, Sparkles, Table2, Package } from 'lucide-angular';
import { catchError, of } from 'rxjs';
import { CampaignService } from '../../services/campaign.service';
import { CharacterService } from '../../services/character.service';
import { NpcService } from '../../services/npc.service';
import { EnemyService } from '../../services/enemy.service';
import { Character } from '../../services/character.model';
import { Npc } from '../../services/npc.model';
import { Arc, Chapter, Scene } from '../../services/campaign.model';
import { loadCampaignTreeData, CampaignTreeData } from '../../campaigns/campaign-tree.helper';
import {
  SessionDicePanelComponent, DiceRollResult
} from '../session-dice-panel/session-dice-panel.component';
import { SessionAiChatPanelComponent } from '../session-ai-chat-panel/session-ai-chat-panel.component';
import { SessionRandomTablesPanelComponent } from '../session-random-tables-panel/session-random-tables-panel.component';
import { SessionItemCatalogsPanelComponent } from '../session-item-catalogs-panel/session-item-catalogs-panel.component';

type TabId = 'dice' | 'tables' | 'objects' | 'characters' | 'scenes' | 'ai';

/**
 * Panneau latéral du mode jeu : référence rapide en lecture seule.
 *
 * <p>Charge à la volée les PJ/PNJ et l'arbre de scènes de la campagne associée
 * à la session. La navigation vers les fiches s'ouvre dans un nouvel onglet
 * pour ne pas casser le flux de la session en cours.</p>
 *
 * <p>Le sous-composant {@link SessionDicePanelComponent} émet un événement
 * de jet qui remonte ici puis vers le parent via {@link rolled}.</p>
 */
@Component({
    selector: 'app-session-reference-panel',
    imports: [LucideAngularModule, SessionDicePanelComponent, SessionAiChatPanelComponent, SessionRandomTablesPanelComponent, SessionItemCatalogsPanelComponent],
    templateUrl: './session-reference-panel.component.html',
    styleUrls: ['./session-reference-panel.component.scss']
})
export class SessionReferencePanelComponent implements OnChanges {
  readonly User = User;
  readonly Drama = Drama;
  readonly Swords = Swords;
  readonly Dices = Dices;
  readonly ExternalLink = ExternalLink;
  readonly Sparkles = Sparkles;
  readonly Table2 = Table2;
  readonly Package = Package;

  @Input() campaignId!: string;
  /** Partie active — nécessaire pour charger les PJ (refonte Playthrough). */
  @Input() playthroughId: string | null = null;
  @Input() sessionId!: string;
  @Input() canAddToJournal = true;
  @Output() rolled = new EventEmitter<DiceRollResult>();
  /** Émis quand l'IA répond et que le MJ veut sauvegarder la réponse comme entrée. */
  @Output() aiReplyToJournal = new EventEmitter<string>();
  /** Émis pour consigner un objet de catalogue au journal (entrée NOTE). */
  @Output() noteToJournal = new EventEmitter<string>();

  activeTab: TabId = 'dice';

  characters: Character[] = [];
  npcs: Npc[] = [];
  treeData: CampaignTreeData | null = null;

  loadingChars = false;
  loadingTree = false;
  /** True dès qu'un tab "lourd" a été chargé pour éviter de rappeler l'API en boucle. */
  private charsLoaded = false;
  private treeLoaded = false;

  constructor(
    private campaignService: CampaignService,
    private characterService: CharacterService,
    private npcService: NpcService,
    private enemyService: EnemyService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['campaignId']) {
      this.charsLoaded = false;
      this.treeLoaded = false;
      this.characters = [];
      this.npcs = [];
      this.treeData = null;
    }
  }

  selectTab(tab: TabId): void {
    this.activeTab = tab;
    if (tab === 'characters') this.ensureCharactersLoaded();
    if (tab === 'scenes')     this.ensureTreeLoaded();
  }

  private ensureCharactersLoaded(): void {
    if (this.charsLoaded || this.loadingChars || !this.campaignId) return;
    this.loadingChars = true;
    // PJ : propres à la Partie. PNJ : campagne-scope.
    const chars$ = this.playthroughId
        ? this.characterService.getByPlaythrough(this.playthroughId)
        : of([] as Character[]);
    chars$.pipe(catchError(() => of([] as Character[])))
      .subscribe(list => { this.characters = list; this.tryFinishCharsLoad(); });
    this.npcService.getByCampaign(this.campaignId).pipe(catchError(() => of([] as Npc[])))
      .subscribe(list => { this.npcs = list; this.tryFinishCharsLoad(); });
  }

  private tryFinishCharsLoad(): void {
    // On considère que le chargement est fini quand au moins une des deux listes
    // a été assignée (vide ou pleine). Le double subscribe ci-dessus garantit
    // qu'on tombe ici deux fois ; idempotent.
    this.loadingChars = false;
    this.charsLoaded = true;
  }

  private ensureTreeLoaded(): void {
    if (this.treeLoaded || this.loadingTree || !this.campaignId) return;
    this.loadingTree = true;
    loadCampaignTreeData(this.campaignService, this.campaignId, this.characterService, this.npcService, undefined, this.enemyService).pipe(
      catchError(() => of({ arcs: [], chaptersByArc: {}, scenesByChapter: {}, characters: [], npcs: [], randomTables: [], enemies: [] } as CampaignTreeData))
    ).subscribe(data => {
      this.treeData = data;
      this.loadingTree = false;
      this.treeLoaded = true;
    });
  }

  /**
   * Ouvre une fiche dans un nouvel onglet pour préserver l'écran de session.
   * Le MJ peut consulter sans perdre son journal ni son historique de dés.
   */
  openInNewTab(path: (string | number)[]): void {
    const url = path.map(p => String(p)).join('/');
    window.open('/' + url, '_blank', 'noopener');
  }

  /** Helpers de typage pour le template (Angular n'infère pas bien sans). */
  chaptersOf(arc: Arc): Chapter[] {
    return this.treeData?.chaptersByArc[arc.id!] ?? [];
  }
  scenesOf(chapter: Chapter): Scene[] {
    return this.treeData?.scenesByChapter[chapter.id!] ?? [];
  }

  onDiceRolled(result: DiceRollResult): void {
    this.rolled.emit(result);
  }

  onAiSaveToJournal(content: string): void {
    this.aiReplyToJournal.emit(content);
  }

  onItemNote(content: string): void {
    this.noteToJournal.emit(content);
  }
}
