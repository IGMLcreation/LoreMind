import { Component, OnInit, DestroyRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, Flag } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { QuestService } from '../../../services/quest.service';
import { Quest } from '../../../services/campaign.model';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { DataSyncService } from '../../../services/data-sync.service';
import { resolveCampaignIcon } from '../../campaign-icons';

/**
 * Liste des quêtes d'une campagne (Niveau 1). Quêtes ORTHOGONALES à l'arbre.
 * Route : /campaigns/:campaignId/quests
 */
@Component({
    selector: 'app-quest-list',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './quest-list.component.html',
    styleUrls: ['./quest-list.component.scss']
})
export class QuestListComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Flag = Flag;
  readonly resolveCampaignIcon = resolveCampaignIcon;

  campaignId = '';
  quests: Quest[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: QuestService,
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
    this.dataSync.onChange(this.destroyRef, () => {
      this.load();
      if (this.campaignId) this.campaignSidebar.show(this.campaignId);
    });
  }

  load(): void {
    this.service.getByCampaign(this.campaignId).subscribe({
      // Seules les quêtes TRANSVERSES (non rattachées à un arc HUB) : celles d'un arc
      // apparaissent sous cet arc dans l'arbre / sur la fiche de l'arc.
      next: list => this.quests = [...list].filter(q => !q.arcId).sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
      error: () => this.quests = []
    });
  }

  create(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'quests', 'create']);
  }

  open(q: Quest): void {
    this.router.navigate(['/campaigns', this.campaignId, 'quests', q.id]);
  }

  remove(q: Quest, ev: Event): void {
    ev.stopPropagation();
    this.confirmDialog.confirm({
      title: this.translate.instant('questList.deleteTitle'),
      message: this.translate.instant('questList.deleteMessage', { name: q.name }),
      details: [this.translate.instant('questList.irreversible')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok || !q.id) return;
      this.service.delete(this.campaignId, q.id).subscribe(() => this.load());
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
