import { Component, OnDestroy, OnInit } from '@angular/core';

import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { LucideAngularModule, ArrowLeft, Edit3 } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { EnemyService } from '../../../services/enemy.service';
import { CampaignService } from '../../../services/campaign.service';
import { GameSystemService } from '../../../services/game-system.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { TemplateField } from '../../../services/template.model';
import { Enemy } from '../../../services/enemy.model';
import { PersonaViewComponent } from '../../../shared/persona-view/persona-view.component';

/**
 * Vue lecture seule d'une fiche d'ennemi (même rendu « persona » que les PNJ).
 * Route : /campaigns/:campaignId/enemies/:enemyId
 */
@Component({
    selector: 'app-enemy-view',
    imports: [LucideAngularModule, TranslatePipe, PersonaViewComponent],
    templateUrl: './enemy-view.component.html',
    styleUrls: ['./enemy-view.component.scss']
})
export class EnemyViewComponent implements OnInit, OnDestroy {
  readonly ArrowLeft = ArrowLeft;
  readonly Edit3 = Edit3;

  campaignId: string | null = null;
  enemyId: string | null = null;

  enemy: Enemy | null = null;
  templateFields: TemplateField[] = [];

  private paramsSub?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: EnemyService,
    private campaignService: CampaignService,
    private gameSystemService: GameSystemService,
    private campaignSidebar: CampaignSidebarService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    // paramMap (pas snapshot) : Angular réutilise le composant entre 2 ennemis.
    this.paramsSub = this.route.paramMap.subscribe(params => {
      const newCampaignId = params.get('campaignId');
      this.enemyId = params.get('enemyId');

      if (this.enemyId) {
        this.service.getById(this.enemyId).subscribe({
          next: e => { this.enemy = e; },
          error: () => this.back()
        });
      }

      if (newCampaignId && newCampaignId !== this.campaignId) {
        this.campaignId = newCampaignId;
        this.campaignSidebar.show(this.campaignId);
        this.campaignService.getCampaignById(this.campaignId).subscribe(camp => {
          if (camp.gameSystemId) {
            this.gameSystemService.getById(camp.gameSystemId).subscribe(gs => {
              this.templateFields = gs.enemyTemplate ?? [];
            });
          }
        });
      } else if (newCampaignId) {
        this.campaignId = newCampaignId;
      }
    });
  }

  ngOnDestroy(): void {
    this.paramsSub?.unsubscribe();
  }

  /** Sous-titre de la fiche : niveau + dossier (« Niveau 8 · Démons »). */
  get subtitle(): string {
    const parts: string[] = [];
    if (this.enemy?.level) parts.push(this.translate.instant('enemyView.levelLong', { level: this.enemy.level }));
    if (this.enemy?.folder) parts.push(this.enemy.folder);
    return parts.join(' · ');
  }

  edit(): void {
    if (this.campaignId && this.enemyId) {
      this.router.navigate(['/campaigns', this.campaignId, 'enemies', this.enemyId, 'edit']);
    }
  }

  back(): void {
    if (this.campaignId) {
      this.router.navigate(['/campaigns', this.campaignId, 'enemies']);
    } else {
      this.router.navigate(['/campaigns']);
    }
  }
}
