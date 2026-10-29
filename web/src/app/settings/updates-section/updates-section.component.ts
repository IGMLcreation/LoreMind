import { Component, OnDestroy, OnInit } from '@angular/core';
import { interval, switchMap, Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, ArrowLeft, RefreshCw, Check, AlertCircle, Download, FlaskConical } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { UpdatesService, UpdateStatus, BetaStatusDTO, ChannelStatusDTO, ChannelName } from '../../services/updates.service';
import { ConfigService } from '../../services/config.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Section « Mises a jour » de l'ecran Parametres (composant standalone).
 *
 * Responsabilite unique : tout ce qui touche au CYCLE DE VIE des conteneurs —
 *  - canal stable : verification + application des mises a jour (Watchtower) ;
 *  - canal beta (libre) : verification des images beta quand on y tourne ;
 *  - bascule de canal stable <-> beta via le sidecar switcher (avec polling).
 *
 * Autonome : injecte ses propres services, aucun @Input/@Output — le parent
 * (SettingsComponent) ne gere plus que le formulaire de configuration LLM.
 */
@Component({
    selector: 'app-settings-updates-section',
    imports: [CommonModule, FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './updates-section.component.html',
    // Reutilise la feuille de style de l'ecran Parametres : les blocs deplaces
    // gardent exactement le meme rendu (cards, alerts, channel-switch, …).
    styleUrls: ['../settings.component.scss']
})
export class UpdatesSectionComponent implements OnInit, OnDestroy {

  readonly ArrowLeft = ArrowLeft;
  readonly RefreshCw = RefreshCw;
  readonly Check = Check;
  readonly AlertCircle = AlertCircle;
  readonly Download = Download;
  readonly FlaskConical = FlaskConical;

  /** Nouvelle beta disponible ? (verifie seulement quand l'instance tourne en beta). */
  betaStatus: BetaStatusDTO | null = null;
  betaChecking = false;

  // --- Bascule de canal stable <-> beta via sidecar switcher ---
  channelStatus: ChannelStatusDTO | null = null;
  /** True pendant le polling apres clic. Bloque les boutons. */
  switchInFlight = false;
  /** ID de la commande de switch en cours, pour ignorer les vieux resultats. */
  private switchCommandId: string | null = null;
  /** Subscription du polling pour pouvoir l'arreter. */
  private switchPollSub: Subscription | null = null;
  /** Erreur affichee si le switch a echoue. */
  switchError = '';

  // --- Mises a jour conteneurs (canal stable) ---
  updateStatus: UpdateStatus | null = null;
  updateChecking = false;
  updateApplying = false;
  updateMessage = '';

  constructor(
    private updatesService: UpdatesService,
    public config: ConfigService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    if (this.config.updateCheckEnabled) {
      this.checkUpdates();
    }
    this.loadChannelStatus();
  }

  ngOnDestroy(): void {
    this.stopSwitchPolling();
  }

  checkBeta(): void {
    this.betaChecking = true;
    this.updatesService.checkBeta().subscribe({
      next: (s) => {
        this.betaStatus = s;
        this.betaChecking = false;
      },
      error: () => { this.betaChecking = false; }
    });
  }

  // --- Bascule de canal stable <-> beta --------------------------------------

  loadChannelStatus(): void {
    this.updatesService.getChannelStatus().subscribe({
      next: (s) => {
        this.channelStatus = s;
        // En beta, on verifie aussi s'il existe une beta plus recente.
        if (s?.currentChannel === 'beta') this.checkBeta();
      },
      error: () => { this.channelStatus = null; }
    });
  }

  /**
   * Declenche un switch de canal. La sequence cote UI :
   *   1. Confirm modal (action destructrice : recreate des containers)
   *   2. POST /api/admin/updates/channel/switch -> 202 avec l'ID de la commande
   *   3. Polling /api/admin/updates/channel toutes les 2s jusqu'a status != IN_PROGRESS
   *   4. Si SUCCESS : la page va se rendre injoignable (Core recree). On affiche
   *      "Recharge la page dans quelques secondes" et on essaie de poll quand
   *      meme — au retour de Core, on detectera SUCCESS et on rechargera auto.
   *   5. Si ERROR : on affiche le message d'erreur et on debloque les boutons.
   */
  requestChannelSwitch(target: ChannelName): void {
    const confirmMessage = target === 'beta'
      ? this.translate.instant('updatesSection.switchToBetaMessage')
      : this.translate.instant('updatesSection.switchToStableMessage');

    this.confirmDialog.confirm({
      title: target === 'beta'
        ? this.translate.instant('updatesSection.switchToBetaTitle')
        : this.translate.instant('updatesSection.switchToStableTitle'),
      message: confirmMessage,
      details: [
        this.translate.instant('updatesSection.switchDetailData'),
        this.translate.instant('updatesSection.switchDetailReversible')
      ],
      confirmLabel: target === 'beta'
        ? this.translate.instant('updatesSection.switchToBetaConfirm')
        : this.translate.instant('updatesSection.switchToStableConfirm'),
      variant: 'warning'
    }).then(ok => {
      if (!ok) return;
      this.doChannelSwitch(target);
    });
  }

  private doChannelSwitch(target: ChannelName): void {
    this.switchInFlight = true;
    this.switchError = '';
    this.updatesService.switchChannel(target).subscribe((res) => {
      if ('error' in res) {
        this.switchError = res.error;
        this.switchInFlight = false;
        return;
      }
      this.switchCommandId = res.id;
      this.startSwitchPolling();
    });
  }

  /**
   * Poll /api/admin/updates/channel toutes les 2s. S'arrete quand on detecte un
   * resultat avec un ID >= a celui qu'on a soumis (le sidecar le met a jour
   * a la fin de son traitement).
   */
  private startSwitchPolling(): void {
    this.stopSwitchPolling();
    this.switchPollSub = interval(2000).pipe(
      switchMap(() => this.updatesService.getChannelStatus())
    ).subscribe((status) => {
      if (!status) return;
      this.channelStatus = status;
      const last = status.lastSwitch;
      if (!last || last.id !== this.switchCommandId) return;
      if (last.status === 'SUCCESS') {
        // La page va se rafraichir auto via l'update-banner qui detecte le
        // restart de Core. On laisse switchInFlight a true pour bloquer
        // toute autre action en attendant.
        this.stopSwitchPolling();
        this.switchInFlight = false;
      } else if (last.status === 'ERROR') {
        this.switchError = last.message || this.translate.instant('updatesSection.switchFailed');
        this.stopSwitchPolling();
        this.switchInFlight = false;
      }
      // IN_PROGRESS : on continue a poll.
    });
  }

  private stopSwitchPolling(): void {
    if (this.switchPollSub) {
      this.switchPollSub.unsubscribe();
      this.switchPollSub = null;
    }
  }

  // --- Mises a jour conteneurs (canal stable) -----------------------------

  checkUpdates(): void {
    this.updateChecking = true;
    this.updateMessage = '';
    this.updatesService.checkNow().subscribe({
      next: (s) => {
        this.updateStatus = s;
        this.updateChecking = false;
      },
      error: () => {
        this.updateChecking = false;
      }
    });
  }

  applyUpdate(): void {
    this.confirmDialog.confirm({
      title: this.translate.instant('updatesSection.applyTitle'),
      message: this.translate.instant('updatesSection.applyMessage'),
      details: [this.translate.instant('updatesSection.applyDetail')],
      confirmLabel: this.translate.instant('updatesSection.applyConfirm'),
      variant: 'warning'
    }).then(ok => {
      if (!ok) return;
      this.updateApplying = true;
      this.updateMessage = '';
      this.updatesService.apply().subscribe({
        next: (r) => {
          this.updateApplying = false;
          // Le redemarrage de core peut couper la connexion avant la reponse —
          // dans ce cas r vaut null (gere par catchError dans le service).
          this.updateMessage = r?.message
            ?? this.translate.instant('updatesSection.applyTriggered');
        },
        error: () => {
          this.updateApplying = false;
          this.updateMessage = this.translate.instant('updatesSection.applyTriggered');
        }
      });
    });
  }
}
