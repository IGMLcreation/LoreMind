import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, Drama, Folder } from 'lucide-angular';
import { NpcService } from '../../../services/npc.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { Npc } from '../../../services/npc.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/** Groupe d'affichage : un dossier (« Bard's Gate »…) et ses PNJ. */
interface FolderGroup {
  folder: string;
  npcs: Npc[];
}

/**
 * Vue d'ensemble des PNJ d'une campagne, groupés par dossier — pendant « page »
 * de l'arbre dépliable de la sidebar (les deux modes coexistent).
 * Route : /campaigns/:campaignId/npcs
 */
@Component({
    selector: 'app-npc-list',
    imports: [LucideAngularModule],
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
  groups: FolderGroup[] = [];
  total = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NpcService,
    private campaignSidebar: CampaignSidebarService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId') ?? '';
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
      this.load();
    }
  }

  load(): void {
    this.service.getByCampaign(this.campaignId).subscribe({
      next: (list) => this.groups = this.groupByFolder(list),
      error: () => this.groups = []
    });
  }

  /** Même logique de groupement que la sidebar : dossiers triés, non-classés à la fin. */
  private groupByFolder(npcs: Npc[]): FolderGroup[] {
    this.total = npcs.length;
    const sorted = [...npcs].sort((a, b) => a.name.localeCompare(b.name, 'fr'));
    const byFolder = new Map<string, Npc[]>();
    const ungrouped: Npc[] = [];
    for (const n of sorted) {
      const f = (n.folder ?? '').trim();
      if (f) {
        if (!byFolder.has(f)) byFolder.set(f, []);
        byFolder.get(f)!.push(n);
      } else {
        ungrouped.push(n);
      }
    }
    const groups: FolderGroup[] = [...byFolder.keys()]
      .sort((a, b) => a.localeCompare(b, 'fr'))
      .map(folder => ({ folder, npcs: byFolder.get(folder)! }));
    if (ungrouped.length) groups.push({ folder: '', npcs: ungrouped });
    return groups;
  }

  create(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'npcs', 'create']);
  }

  open(n: Npc): void {
    this.router.navigate(['/campaigns', this.campaignId, 'npcs', n.id]);
  }

  remove(n: Npc, ev: Event): void {
    ev.stopPropagation();
    this.confirmDialog.confirm({
      title: 'Supprimer la fiche',
      message: `Supprimer la fiche de « ${n.name} » ?`,
      details: ['Cette action est irréversible.'],
      confirmLabel: 'Supprimer',
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
