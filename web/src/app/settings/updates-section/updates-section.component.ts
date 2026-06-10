import { Component, OnDestroy, OnInit } from '@angular/core';
import { interval, switchMap, Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, ArrowLeft, RefreshCw, Check, AlertCircle, Download, Heart, Link2, Unlink } from 'lucide-angular';
import { UpdatesService, UpdateStatus } from '../../services/updates.service';
import { ConfigService } from '../../services/config.service';
import { LicenseService, LicenseStatusDTO, BetaStatusDTO, ChannelStatusDTO, ChannelName } from '../../services/license.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Section « Mises a jour » de l'ecran Parametres (composant standalone).
 *
 * Responsabilite unique : tout ce qui touche au CYCLE DE VIE des conteneurs —
 *  - canal stable : verification + application des mises a jour (Watchtower) ;
 *  - licence Patreon : connexion OAuth, installation du JWT, refresh, deconnexion ;
 *  - canal beta : activation + verification des images beta ;
 *  - bascule de canal stable <-> beta via le sidecar switcher (avec polling).
 *
 * Autonome : injecte ses propres services, aucun @Input/@Output — le parent
 * (SettingsComponent) ne gere plus que le formulaire de configuration LLM.
 */
@Component({
  selector: 'app-settings-updates-section',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
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
  readonly Heart = Heart;
  readonly Link2 = Link2;
  readonly Unlink = Unlink;

  // --- Licence Patreon (canal beta) ---
  licenseStatus: LicenseStatusDTO | null = null;
  licenseLoading = false;
  licenseError = '';
  /** Message de succes local a la section (connexion/deconnexion Patreon). */
  licenseSuccess = '';
  /** Token JWT colle par l'utilisateur apres OAuth. */
  licenseJwtInput = '';
  /** Etat du canal beta (digests des images privees). */
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
    private licenseService: LicenseService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    if (this.config.updateCheckEnabled) {
      this.checkUpdates();
    }
    this.loadLicense();
    this.loadChannelStatus();
  }

  ngOnDestroy(): void {
    this.stopSwitchPolling();
  }

  // --- Licence Patreon ---------------------------------------------------

  loadLicense(): void {
    this.licenseLoading = true;
    this.licenseService.getStatus().subscribe({
      next: (s) => {
        this.licenseStatus = s;
        this.licenseLoading = false;
        if (s?.enabled && (s.status === 'VALID' || s.status === 'GRACE') && s.betaChannelEnabled) {
          this.checkBeta();
        }
      },
      error: () => { this.licenseLoading = false; }
    });
  }

  /**
   * Ouvre la page OAuth Patreon dans une nouvelle fenetre.
   * L'utilisateur copie ensuite le JWT et le colle dans l'input ci-dessous.
   */
  connectPatreon(): void {
    this.licenseError = '';
    this.licenseService.getConnectUrl().subscribe({
      next: (r) => {
        if (!r?.url) {
          this.licenseError = 'Impossible de generer l\'URL de connexion. Verifie ta config.';
          return;
        }
        window.open(r.url, '_blank', 'noopener');
      }
    });
  }

  installLicense(): void {
    const jwt = this.licenseJwtInput.trim();
    if (!jwt) {
      this.licenseError = 'Colle d\'abord le token recu apres connexion Patreon.';
      return;
    }
    this.licenseError = '';
    this.licenseService.install(jwt).subscribe((res) => {
      if ((res as any)?.error) {
        this.licenseError = (res as any).error;
        return;
      }
      this.licenseStatus = res as LicenseStatusDTO;
      this.licenseJwtInput = '';
      this.licenseSuccess = 'Compte Patreon connecte. L\'acces beta est actif.';
      if (this.licenseStatus.betaChannelEnabled) {
        this.checkBeta();
      }
    });
  }

  refreshLicense(): void {
    this.licenseLoading = true;
    this.licenseService.refresh().subscribe({
      next: (s) => {
        this.licenseStatus = s;
        this.licenseLoading = false;
      },
      error: () => { this.licenseLoading = false; }
    });
  }

  disconnectPatreon(): void {
    this.confirmDialog.confirm({
      title: 'Deconnecter Patreon',
      message: 'Deconnecter ton compte Patreon ?',
      details: ['Tu perdras l\'acces au canal beta.'],
      confirmLabel: 'Deconnecter',
      variant: 'warning'
    }).then(ok => {
      if (!ok) return;
      this.licenseService.disconnect().subscribe(() => {
        this.licenseStatus = null;
        this.betaStatus = null;
        this.licenseSuccess = 'Compte Patreon deconnecte.';
        this.loadLicense();
      });
    });
  }

  toggleBetaChannel(enabled: boolean): void {
    this.licenseService.setBetaChannel(enabled).subscribe({
      next: (s) => {
        if (s) this.licenseStatus = s;
        if (enabled) this.checkBeta();
        else this.betaStatus = null;
      }
    });
  }

  checkBeta(): void {
    this.betaChecking = true;
    this.licenseService.checkBeta().subscribe({
      next: (s) => {
        this.betaStatus = s;
        this.betaChecking = false;
      },
      error: () => { this.betaChecking = false; }
    });
  }

  // --- Bascule de canal stable <-> beta --------------------------------------

  loadChannelStatus(): void {
    this.licenseService.getChannelStatus().subscribe({
      next: (s) => {
        this.channelStatus = s;
        // Si on revient sur l'ecran apres un reload (post-switch reussi),
        // on affiche le dernier resultat eventuel jusqu'a interaction utilisateur.
      },
      error: () => { this.channelStatus = null; }
    });
  }

  /**
   * Declenche un switch de canal. La sequence cote UI :
   *   1. Confirm modal (action destructrice : recreate des containers)
   *   2. POST /api/license/channel/switch -> 202 avec l'ID de la commande
   *   3. Polling /api/license/channel toutes les 2s jusqu'a status != IN_PROGRESS
   *   4. Si SUCCESS : la page va se rendre injoignable (Core recree). On affiche
   *      "Recharge la page dans quelques secondes" et on essaie de poll quand
   *      meme — au retour de Core, on detectera SUCCESS et on rechargera auto.
   *   5. Si ERROR : on affiche le message d'erreur et on debloque les boutons.
   */
  requestChannelSwitch(target: ChannelName): void {
    const confirmMessage = target === 'beta'
      ? 'Basculer LoreMind sur le canal beta ? Les containers core/brain/web vont etre recrees avec les images beta. L\'application sera indisponible 10-30 secondes.'
      : 'Repasser LoreMind sur le canal stable ? Les containers core/brain/web vont etre recrees avec les images stables. L\'application sera indisponible 10-30 secondes.';

    this.confirmDialog.confirm({
      title: target === 'beta' ? 'Passer en beta ?' : 'Repasser en stable ?',
      message: confirmMessage,
      details: [
        'Les donnees (DB, images) sont preservees.',
        'Tu pourras refaire le chemin inverse a tout moment depuis cet ecran.'
      ],
      confirmLabel: target === 'beta' ? 'Passer en beta' : 'Repasser en stable',
      variant: 'warning'
    }).then(ok => {
      if (!ok) return;
      this.doChannelSwitch(target);
    });
  }

  private doChannelSwitch(target: ChannelName): void {
    this.switchInFlight = true;
    this.switchError = '';
    this.licenseService.switchChannel(target).subscribe((res) => {
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
   * Poll /api/license/channel toutes les 2s. S'arrete quand on detecte un
   * resultat avec un ID >= a celui qu'on a soumis (le sidecar le met a jour
   * a la fin de son traitement).
   */
  private startSwitchPolling(): void {
    this.stopSwitchPolling();
    this.switchPollSub = interval(2000).pipe(
      switchMap(() => this.licenseService.getChannelStatus())
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
        this.switchError = last.message || 'Echec du switch';
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

  /**
   * Mapping tier_id Patreon → nom lisible. Les IDs viennent du dashboard
   * Patreon de LoreMind (Settings -> Tiers). Sans entree dans la map, on
   * affiche l'ID brut pour rester debuggable.
   *
   * Si tu ajoutes un nouveau tier Patreon, complete cette map et redeploie.
   * (Pas besoin de toucher au backend — c'est juste un libelle d'UI.)
   */
  private static readonly TIER_LABELS: Record<string, string> = {
    '28448887': 'Compagnon',
    // '0000000': 'Aventurier',
    // '0000000': 'Heros',
  };

  /** Libelle lisible d'un tier Patreon, fallback sur l'ID brut. */
  tierLabel(tierId: string | null | undefined): string {
    if (!tierId) return '';
    return UpdatesSectionComponent.TIER_LABELS[tierId] ?? tierId;
  }

  /** Format human-readable des dates renvoyees par le backend. */
  formatDate(iso: string | null | undefined): string {
    if (!iso) return '';
    try { return new Date(iso).toLocaleString(); } catch { return iso; }
  }

  /** Nombre de jours restants avant expiration JWT (peut etre negatif). */
  get daysUntilExpiry(): number | null {
    if (!this.licenseStatus?.expiresAt) return null;
    const exp = new Date(this.licenseStatus.expiresAt).getTime();
    const now = Date.now();
    return Math.ceil((exp - now) / (1000 * 60 * 60 * 24));
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
      title: 'Mettre a jour',
      message: 'Telecharger et redemarrer les conteneurs maintenant ?',
      details: ['L\'app sera indisponible quelques secondes.'],
      confirmLabel: 'Mettre à jour',
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
            ?? 'Mise a jour declenchee. Rechargez la page dans 30s.';
        },
        error: () => {
          this.updateApplying = false;
          this.updateMessage = 'Mise a jour declenchee. Rechargez la page dans 30s.';
        }
      });
    });
  }
}
