import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { DestroyRef } from '@angular/core';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { DataSyncService } from '../../../services/data-sync.service';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, Drama, Folder } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { NpcService } from '../../../services/npc.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { Npc } from '../../../services/npc.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { FolderGroup, groupByFolder } from '../../../shared/folder-grouping.util';

/**
 * Vue d'ensemble des PNJ d'une campagne, groupés par dossier — pendant « page »
 * de l'arbre dépliable de la sidebar (les deux modes coexistent).
 * Route : /campaigns/:campaignId/npcs
 */
@Component({
    selector: 'app-npc-list',
    imports: [LucideAngularModule, TranslatePipe, CdkDropList, CdkDrag],
    templateUrl: './npc-list.component.html',
    styleUrls: ['./npc-list.component.scss']
})
export class NpcListComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Drama = Drama;
  readonly Folder = Folder;

  campaignId = '';
  /** Groupes triés par nom de dossier ; les non-classés en dernier (folder = ''). */
  groups: FolderGroup<Npc>[] = [];
  total = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NpcService,
    private campaignSidebar: CampaignSidebarService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService,
    private dataSync: DataSyncService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId') ?? '';
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
      this.load();
    }
    // Recharge la liste ET l'arbre (synchro temps réel).
    this.dataSync.onChange(this.destroyRef, () => {
      this.load();
      if (this.campaignId) this.campaignSidebar.show(this.campaignId);
    });
  }

  load(): void {
    this.service.getByCampaign(this.campaignId).subscribe({
      next: (list) => { this.total = list.length; this.groups = groupByFolder(list); },
      error: () => this.groups = []
    });
  }

  create(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'npcs', 'create']);
  }

  // --- Glisser-déposer (réordonner + déplacer entre dossiers) --------------

  get groupListIds(): string[] {
    return this.groups.map((_, i) => 'npc-group-' + i);
  }

  drop(target: FolderGroup<Npc>, event: CdkDragDrop<Npc[]>): void {
    if (event.previousContainer === event.container) {
      if (event.previousIndex === event.currentIndex) return;
      moveItemInArray(target.items, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(event.previousContainer.data, event.container.data,
        event.previousIndex, event.currentIndex);
    }
    const folder = target.folder || null;
    this.dataSync.persist(this.service.reorder(folder, target.items.map(n => n.id!)), () => this.load());
  }

  open(n: Npc): void {
    this.router.navigate(['/campaigns', this.campaignId, 'npcs', n.id]);
  }

  remove(n: Npc, ev: Event): void {
    ev.stopPropagation();
    this.confirmDialog.confirm({
      title: this.translate.instant('npcList.deleteTitle'),
      message: this.translate.instant('npcList.deleteMessage', { name: n.name }),
      details: [this.translate.instant('npcList.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.service.delete(n.id!).subscribe(() => this.load());
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
