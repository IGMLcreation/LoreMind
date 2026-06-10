import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, BookOpen } from 'lucide-angular';
import { NotebookService } from '../../../services/notebook.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { Notebook } from '../../../services/notebook.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Liste des ateliers (notebooks) d'une campagne + création.
 * Route : /campaigns/:campaignId/notebooks
 */
@Component({
    selector: 'app-notebook-list',
    imports: [FormsModule, LucideAngularModule],
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
    this.service.listByCampaign(this.campaignId).subscribe({
      next: (list) => this.notebooks = list,
      error: () => this.notebooks = []
    });
  }

  create(): void {
    if (this.creating) return;
    this.creating = true;
    this.service.create(this.campaignId, this.newName.trim() || 'Nouvel atelier').subscribe({
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
      title: 'Supprimer l\'atelier',
      message: `Supprimer « ${nb.name} » et ses sources indexées ?`,
      confirmLabel: 'Supprimer',
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
