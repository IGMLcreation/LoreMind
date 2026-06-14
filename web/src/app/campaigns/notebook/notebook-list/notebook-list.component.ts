import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, BookOpen } from 'lucide-angular';
import { NotebookService } from '../../../services/notebook.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { Notebook } from '../../../services/notebook.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

/**
 * Liste des ateliers (notebooks) d'une campagne + création.
 * Route : /campaigns/:campaignId/notebooks
 */
@Component({
    selector: 'app-notebook-list',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './notebook-list.component.html',
    styleUrls: ['./notebook-list.component.scss']
})
export class NotebookListComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly BookOpen = BookOpen;

  campaignId = '';
  notebooks: Notebook[] = [];
  newName = '';
  creating = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NotebookService,
    private campaignSidebar: CampaignSidebarService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId') ?? '';
    if (this.campaignId) {
      this.campaignSidebar.show(this.campaignId);
      this.load();
    }
  }

  load(): void {
    this.service.listByCampaign(this.campaignId).subscribe({
      next: (list) => this.notebooks = list,
      error: () => this.notebooks = []
    });
  }

  create(): void {
    if (this.creating) return;
    this.creating = true;
    this.service.create(this.campaignId, this.newName.trim() || this.translate.instant('notebookList.defaultName')).subscribe({
      next: (nb) => this.router.navigate(['/campaigns', this.campaignId, 'notebooks', nb.id]),
      error: () => this.creating = false
    });
  }

  open(nb: Notebook): void {
    this.router.navigate(['/campaigns', this.campaignId, 'notebooks', nb.id]);
  }

  remove(nb: Notebook, ev: Event): void {
    ev.stopPropagation();
    this.confirmDialog.confirm({
      title: this.translate.instant('notebookList.deleteTitle'),
      message: this.translate.instant('notebookList.deleteMessage', { name: nb.name }),
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.service.delete(nb.id).subscribe(() => this.load());
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
