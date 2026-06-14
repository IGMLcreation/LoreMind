import { Component, OnInit } from '@angular/core';

import { Router } from '@angular/router';
import { LucideAngularModule, Dices, Plus, Pencil, Trash2 } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { GameSystemService } from '../services/game-system.service';
import { LayoutService } from '../services/layout.service';
import { GameSystem } from '../services/game-system.model';
import { ConfirmDialogService } from '../shared/confirm-dialog/confirm-dialog.service';

@Component({
    selector: 'app-game-systems',
    imports: [LucideAngularModule, TranslatePipe],
    templateUrl: './game-systems.component.html',
    styleUrls: ['./game-systems.component.scss']
})
export class GameSystemsComponent implements OnInit {
  readonly Dices = Dices;
  readonly Plus = Plus;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;

  gameSystems: GameSystem[] = [];

  constructor(
    private router: Router,
    private gameSystemService: GameSystemService,
    private confirmDialog: ConfirmDialogService,
    private layoutService: LayoutService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    // Page racine : on s'assure de ne pas heriter de la sidebar d'une
    // section precedente (cf. fix CampaignsComponent / LoreComponent).
    this.layoutService.hide();
    this.load();
  }

  load(): void {
    this.gameSystemService.getAll().subscribe({
      next: (data) => this.gameSystems = data,
      error: () => this.gameSystems = []
    });
  }

  create(): void {
    this.router.navigate(['/game-systems/create']);
  }

  edit(id: string): void {
    this.router.navigate(['/game-systems', id, 'edit']);
  }

  delete(system: GameSystem, event: MouseEvent): void {
    event.stopPropagation();
    if (!system.id) return;
    this.confirmDialog.confirm({
      title: this.translate.instant('gameSystems.deleteTitle'),
      message: this.translate.instant('gameSystems.deleteMessage', { name: system.name }),
      details: [this.translate.instant('gameSystems.deleteDetail')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok || !system.id) return;
      this.gameSystemService.delete(system.id).subscribe({
        next: () => this.load(),
        error: () => console.error('Erreur suppression GameSystem')
      });
    });
  }
}
