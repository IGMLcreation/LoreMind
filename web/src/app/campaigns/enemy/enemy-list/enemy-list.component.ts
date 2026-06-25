import { Component, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, Plus, Trash2, Skull, Folder, Upload } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { EnemyService } from '../../../services/enemy.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { Enemy } from '../../../services/enemy.model';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { FileDropDirective } from '../../../shared/file-drop.directive';

/** Groupe d'affichage : un dossier (« Démons »…) et ses ennemis. */
interface FolderGroup {
  folder: string;
  enemies: Enemy[];
}

/**
 * Liste des ennemis d'une campagne, groupés par dossier (comme les PNJ).
 * Route : /campaigns/:campaignId/enemies
 */
@Component({
    selector: 'app-enemy-list',
    imports: [LucideAngularModule, TranslatePipe, FileDropDirective],
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

  /** Import de monstres Foundry en cours (anti double-clic). */
  importing = false;

  campaignId = '';
  /** Groupes triés par nom de dossier ; les non-classés en dernier (folder = ''). */
  groups: FolderGroup[] = [];
  total = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: EnemyService,
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
    this.service.getByCampaign(this.campaignId).subscribe({
      next: (list) => this.groups = this.groupByFolder(list),
      error: () => this.groups = []
    });
  }

  /** Même logique de groupement que les PNJ de la sidebar : dossiers triés, non-classés à la fin. */
  private groupByFolder(enemies: Enemy[]): FolderGroup[] {
    this.total = enemies.length;
    const sorted = [...enemies].sort((a, b) => a.name.localeCompare(b.name, 'fr'));
    const byFolder = new Map<string, Enemy[]>();
    const ungrouped: Enemy[] = [];
    for (const e of sorted) {
      const f = (e.folder ?? '').trim();
      if (f) {
        if (!byFolder.has(f)) byFolder.set(f, []);
        byFolder.get(f)!.push(e);
      } else {
        ungrouped.push(e);
      }
    }
    const groups: FolderGroup[] = [...byFolder.keys()]
      .sort((a, b) => a.localeCompare(b, 'fr'))
      .map(folder => ({ folder, enemies: byFolder.get(folder)! }));
    if (ungrouped.length) groups.push({ folder: '', enemies: ungrouped });
    return groups;
  }

  create(): void {
    this.router.navigate(['/campaigns', this.campaignId, 'enemies', 'create']);
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
