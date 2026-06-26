import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { CampaignService } from './campaign.service';
import { NpcService } from './npc.service';
import { EnemyService } from './enemy.service';
import { RandomTableService } from './random-table.service';
import { PageService } from './page.service';
import { LoreService } from './lore.service';
import { TemplateService } from './template.service';
import { LayoutService, ReorderKind, SidebarReorderContext } from './layout.service';
import { CampaignSidebarService } from './campaign-sidebar.service';
import { DataSyncService } from './data-sync.service';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore/lore-sidebar.helper';

/**
 * Persiste un réordonnancement déclenché par glisser-déposer DANS L'ARBRE de la
 * sidebar, puis recharge la sidebar depuis le backend (source de vérité).
 *
 * Le `kind` détermine l'endpoint (et le sens du `parentId` : arcId / chapterId /
 * nodeId / dossier…). On recharge systématiquement après la réponse — succès comme
 * erreur — pour resynchroniser l'affichage sur l'état persisté (zéro dérive).
 */
@Injectable({ providedIn: 'root' })
export class SidebarReorderService {
  constructor(
    private campaignService: CampaignService,
    private npcService: NpcService,
    private enemyService: EnemyService,
    private randomTableService: RandomTableService,
    private pageService: PageService,
    private loreService: LoreService,
    private templateService: TemplateService,
    private layoutService: LayoutService,
    private campaignSidebar: CampaignSidebarService,
    private dataSync: DataSyncService
  ) {}

  reorder(context: SidebarReorderContext, kind: ReorderKind, parentId: string | null, orderedIds: string[]): void {
    this.persist(kind, parentId, orderedIds).subscribe({
      next: () => { this.reload(context); this.dataSync.notify(); },
      error: () => { this.reload(context); this.dataSync.notify(); }
    });
  }

  private persist(kind: ReorderKind, parentId: string | null, orderedIds: string[]): Observable<void> {
    switch (kind) {
      case 'arc':     return this.campaignService.reorderArcs(orderedIds);
      case 'chapter': return this.campaignService.reorderChapters(parentId ?? '', orderedIds);
      case 'scene':   return this.campaignService.reorderScenes(parentId ?? '', orderedIds);
      case 'npc':     return this.npcService.reorder(parentId || null, orderedIds);
      case 'enemy':   return this.enemyService.reorder(parentId || null, orderedIds);
      case 'table':   return this.randomTableService.reorder(orderedIds);
      case 'folder':  return this.loreService.reorderNodes(parentId || null, orderedIds);
      case 'page':    return this.pageService.reorder(parentId ?? '', orderedIds);
      default:        return of(void 0);
    }
  }

  private reload(context: SidebarReorderContext): void {
    if (context.scope === 'campaign') {
      this.campaignSidebar.show(context.id);
    } else {
      loadLoreSidebarData(context.id, this.loreService, this.templateService, this.pageService)
        .subscribe(data => this.layoutService.show(buildLoreSidebarConfig(data)));
    }
  }
}
