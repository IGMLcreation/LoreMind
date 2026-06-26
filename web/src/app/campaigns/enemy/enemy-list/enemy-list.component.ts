import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { DestroyRef } from '@angular/core';
import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { DataSyncService } from '../../../services/data-sync.service';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, Skull, Folder, Upload, ChevronDown, ChevronRight } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { EnemyService } from '../../../services/enemy.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { Enemy } from '../../../services/enemy.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { FileDropDirective } from '../../../shared/file-drop.directive';
import { FolderGroup, groupByFolder } from '../../../shared/folder-grouping.util';

/**
 * Liste des ennemis d'une campagne, groupés par dossier (comme les PNJ).
 * Route : /campaigns/:campaignId/enemies
 */
@Component({
    selector: 'app-enemy-list',
    imports: [LucideAngularModule, TranslatePipe, FileDropDirective, CdkDropList, CdkDrag],
    templateUrl: './enemy-list.component.html',
    styleUrls: ['./enemy-list.component.scss']
})
export class EnemyListComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Plus = Plus;
  readonly Trash2 = Trash2;
  readonly Skull = Skull;
  readonly Folder = Folder;
  readonly Upload = Upload;
  readonly ChevronDown = ChevronDown;
  readonly ChevronRight = ChevronRight;

  /** Dossiers repliés (par nom ; '' = « Sans dossier ») — persiste entre rechargements. */
  collapsedFolders = new Set<string>();

  /** Import de monstres Foundry en cours (anti double-clic). */
  importing = false;

  campaignId = '';
  /** Groupes triés par nom de dossier ; les non-classés en dernier (folder = ''). */
  groups: FolderGroup<Enemy>[] = [];
  total = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: EnemyService,
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
    // Synchro temps réel avec l'arbre (et inversement) : recharge la liste ET l'arbre.
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
    this.router.navigate(['/campaigns', this.campaignId, 'enemies', 'create']);
  }

  /** Replie / déplie un dossier. */
  toggleFolder(folder: string): void {
    if (this.collapsedFolders.has(folder)) this.collapsedFolders.delete(folder);
    else this.collapsedFolders.add(folder);
  }

  isCollapsed(folder: string): boolean {
    return this.collapsedFolders.has(folder);
  }

  // --- Glisser-déposer (réordonner + déplacer entre dossiers) --------------

  /** Ids des zones de dépôt (une par groupe/dossier) pour les connecter entre elles. */
  get groupListIds(): string[] {
    return this.groups.map((_, i) => 'enemy-group-' + i);
  }

  /** Drop d'un ennemi : réordonne dans le dossier, ou le déplace vers un autre dossier. */
  drop(target: FolderGroup<Enemy>, event: CdkDragDrop<Enemy[]>): void {
    if (event.previousContainer === event.container) {
      if (event.previousIndex === event.currentIndex) return;
      moveItemInArray(target.items, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(event.previousContainer.data, event.container.data,
        event.previousIndex, event.currentIndex);
    }
    const folder = target.folder || null;
    this.dataSync.persist(this.service.reorder(folder, target.items.map(e => e.id!)), () => this.load());
  }

  /**
   * Importe un catalogue de monstres Foundry (.json exporté par le module) dans
   * le bestiaire. Upsert côté backend (dédup par référence).
   */
  importMonsters(): void {
    if (this.importing) return;
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,application/json';
    input.addEventListener('change', () => {
      const file = input.files?.[0];
      if (file) this.importMonsterFile(file);
    }, { once: true });
    input.click();
  }

  /** Drop d'un catalogue de monstres (.json) sur la barre d'outils. */
  onMonstersDropped(files: File[]): void {
    if (files[0]) this.importMonsterFile(files[0]);
  }

  private importMonsterFile(file: File): void {
    if (this.importing) return;
    file.text().then(txt => {
      let catalog: unknown;
      try {
        catalog = JSON.parse(txt);
      } catch {
        console.error('Catalogue de monstres illisible (JSON invalide)');
        return;
      }
      this.importing = true;
      this.service.importFoundryMonsters(this.campaignId, catalog).subscribe({
        next: () => { this.importing = false; this.load(); },
        error: (err) => { this.importing = false; console.error('Échec import monstres', err); }
      });
    });
  }

  open(e: Enemy): void {
    this.router.navigate(['/campaigns', this.campaignId, 'enemies', e.id]);
  }

  remove(e: Enemy, ev: Event): void {
    ev.stopPropagation();
    this.confirmDialog.confirm({
      title: this.translate.instant('enemyList.deleteTitle'),
      message: this.translate.instant('enemyList.deleteMessage', { name: e.name }),
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.service.delete(e.id!).subscribe(() => this.load());
    });
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }
}
