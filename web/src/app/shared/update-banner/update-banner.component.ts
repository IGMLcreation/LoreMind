import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, RefreshCw, X } from 'lucide-angular';
import { VersionCheckerService } from '../../services/version-checker.service';

/**
 * Bandeau global affiche en haut de l'app quand une nouvelle version de
 * LoreMind a ete deployee pendant que l'utilisateur avait deja l'onglet
 * ouvert. Propose un reload en un clic.
 */
@Component({
  selector: 'app-update-banner',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './update-banner.component.html',
  styleUrls: ['./update-banner.component.scss']
})
export class UpdateBannerComponent {
  readonly RefreshCw = RefreshCw;
  readonly X = X;

  /** L'utilisateur a explicitement ferme le bandeau pour cette session. */
  dismissed = false;

  constructor(public versionChecker: VersionCheckerService) {}

  reload(): void {
    this.versionChecker.reload();
  }

  dismiss(): void {
    this.dismissed = true;
  }
}
