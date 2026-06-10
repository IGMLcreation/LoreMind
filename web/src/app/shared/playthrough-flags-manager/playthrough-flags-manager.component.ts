import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { CampaignFlagService } from '../../services/campaign-flag.service';
import { PlaythroughFlagService } from '../../services/playthrough-flag.service';
import { forkJoin } from 'rxjs';

/**
 * Toggle de l'état des faits d'une Partie.
 *
 * <p>La liste des faits est déduite des conditions FLAG_SET référencées par les
 * quêtes de la campagne. Pour chaque fait référencé, on affiche un toggle qui
 * met à jour la valeur du Playthrough courant.</p>
 */
@Component({
    selector: 'app-playthrough-flags-manager',
    imports: [FormsModule, LucideAngularModule],
    templateUrl: './playthrough-flags-manager.component.html',
    styleUrls: ['./playthrough-flags-manager.component.scss']
})
export class PlaythroughFlagsManagerComponent implements OnInit, OnChanges {

  @Input() campaignId!: string;
  @Input() playthroughId!: string;

  rows: { name: string; value: boolean }[] = [];
  loading = false;

  constructor(
      private campaignFlagService: CampaignFlagService,
      private playthroughFlagService: PlaythroughFlagService
  ) {}

  ngOnInit(): void { this.reload(); }
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['campaignId'] || changes['playthroughId']) this.reload();
  }

  reload(): void {
    if (!this.campaignId || !this.playthroughId) {
      this.rows = [];
      return;
    }
    this.loading = true;
    forkJoin({
      referenced: this.campaignFlagService.listReferenced(this.campaignId),
      values: this.playthroughFlagService.list(this.playthroughId)
    }).subscribe({
      next: ({ referenced, values }) => {
        const valueByName: Record<string, boolean> = {};
        for (const v of values) valueByName[v.name] = v.value;
        this.rows = referenced.map(name => ({
          name,
          value: valueByName[name] === true
        }));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  toggle(row: { name: string; value: boolean }): void {
    const next = !row.value;
    this.playthroughFlagService.setFlag(this.playthroughId, row.name, next).subscribe({
      next: () => { row.value = next; }
    });
  }
}
