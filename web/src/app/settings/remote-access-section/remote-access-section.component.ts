import { Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Tablet, Trash2, RefreshCw, AlertCircle, ShieldCheck, CheckCircle2, Settings2 } from 'lucide-angular';
import QRCode from 'qrcode';

import {
  NetworkReadiness, PairedDevice, PairingCode, RemoteAccessService, RemoteAccessStatus
} from '../../services/remote-access.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Paramètres > Accès depuis une tablette (mode bureau). Active le connecteur réseau local,
 * affiche l'adresse + un code d'appairage (et son QR code), liste les appareils appairés.
 * Masquée quand le Core ne connaît pas ces routes (Docker : 404).
 */
@Component({
  selector: 'app-remote-access-section',
  imports: [FormsModule, TranslatePipe, LucideAngularModule],
  templateUrl: './remote-access-section.component.html',
  styleUrls: ['../settings.component.scss', './remote-access-section.component.scss']
})
export class RemoteAccessSectionComponent implements OnInit, OnDestroy {
  readonly Tablet = Tablet;
  readonly Trash2 = Trash2;
  readonly RefreshCw = RefreshCw;
  readonly AlertCircle = AlertCircle;
  readonly ShieldCheck = ShieldCheck;
  readonly CheckCircle2 = CheckCircle2;
  readonly Settings2 = Settings2;

  private readonly remoteAccess = inject(RemoteAccessService);
  private readonly translate = inject(TranslateService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Null tant que non chargé, ou si la fonctionnalité n'existe pas (Docker). */
  status: RemoteAccessStatus | null = null;
  port = 0;
  busy = false;
  error: string | null = null;

  /** Pare-feu et type de réseau (Windows) : null tant que non vérifié ou accès fermé. */
  network: NetworkReadiness | null = null;
  checking = false;
  fixing = false;
  fixMessage: string | null = null;

  pairing: PairingCode | null = null;
  qrDataUrl: string | null = null;
  secondsLeft = 0;
  private countdown?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.remoteAccess.status().subscribe({
      next: status => {
        this.apply(status);
        if (this.route.snapshot.queryParamMap.get('section') === 'remote-access') {
          setTimeout(() => this.host.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' }));
        }
      },
      error: () => { this.status = null; }
    });
  }

  ngOnDestroy(): void {
    this.stopCountdown();
  }

  get primaryUrl(): string | null {
    return this.status?.urls[0] ?? null;
  }

  toggle(enabled: boolean): void {
    this.save(enabled);
  }

  applyPort(): void {
    this.save(this.status?.enabled ?? false);
  }

  private save(enabled: boolean): void {
    if (!this.status || this.busy) return;
    this.busy = true;
    this.error = null;
    this.remoteAccess.configure(enabled, Number(this.port)).subscribe({
      next: status => {
        this.busy = false;
        this.apply(status);
        if (!status.running) this.clearPairing();
      },
      error: (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = err.error?.error ?? this.translate.instant('remoteAccess.settings.saveError');
      }
    });
  }

  newCode(): void {
    if (this.busy) return;
    this.busy = true;
    this.error = null;
    this.remoteAccess.newPairingCode().subscribe({
      next: async code => {
        this.busy = false;
        this.pairing = code;
        this.startCountdown();
        await this.renderQr(code.code);
      },
      error: (err: HttpErrorResponse) => {
        this.busy = false;
        this.error = err.error?.error ?? this.translate.instant('remoteAccess.settings.codeError');
      }
    });
  }

  async revoke(device: PairedDevice): Promise<void> {
    const ok = await this.confirmDialog.confirm({
      title: this.translate.instant('remoteAccess.settings.revokeTitle'),
      message: this.translate.instant('remoteAccess.settings.revokeMessage', { name: device.name }),
      confirmLabel: this.translate.instant('remoteAccess.settings.revoke'),
      variant: 'danger'
    });
    if (!ok) return;
    this.remoteAccess.revoke(device.id).subscribe({
      next: () => {
        if (this.status) this.status = { ...this.status, devices: this.status.devices.filter(d => d.id !== device.id) };
      },
      error: () => { this.error = this.translate.instant('remoteAccess.settings.revokeError'); }
    });
  }

  refresh(): void {
    this.remoteAccess.status().subscribe({ next: s => this.apply(s), error: () => undefined });
  }

  formatDate(value?: string | null): string {
    if (!value) return '—';
    const lang = this.translate.currentLang;
    return new Date(value).toLocaleString(lang ? String(lang) : undefined, { dateStyle: 'medium', timeStyle: 'short' });
  }

  private apply(status: RemoteAccessStatus): void {
    this.status = status;
    this.port = status.port;
    if (status.running) {
      this.checkNetwork();
    } else {
      this.network = null;
      this.fixMessage = null;
    }
  }

  // --- Vérification du réseau (Windows) ----------------------------------------------

  /** Sans règle de pare-feu, ou sur un réseau « Public », la tablette charge sans fin : on le dit. */
  checkNetwork(): void {
    if (this.checking) return;
    this.checking = true;
    this.remoteAccess.networkCheck().subscribe({
      next: r => { this.checking = false; this.network = r; },
      error: () => { this.checking = false; this.network = null; }
    });
  }

  /** Windows affiche sa confirmation administrateur : la requête attend la réponse. */
  allowFirewall(): void {
    if (this.fixing) return;
    this.fixing = true;
    this.fixMessage = null;
    this.remoteAccess.allowThroughFirewall().subscribe({
      next: res => {
        this.fixing = false;
        this.network = res.readiness;
        this.fixMessage = this.translate.instant(`remoteAccess.network.fix.${res.outcome}`);
      },
      error: () => {
        this.fixing = false;
        this.fixMessage = this.translate.instant('remoteAccess.network.fix.FAILED');
      }
    });
  }

  openNetworkSettings(): void {
    this.remoteAccess.openNetworkSettings().subscribe({ error: () => undefined });
  }

  get firewallNeedsFix(): boolean {
    return !!this.network && (!this.network.firewallAllowed || this.network.firewallBlocked);
  }

  /** Le QR code ouvre directement l'écran d'appairage avec le code pré-rempli. */
  private async renderQr(code: string): Promise<void> {
    this.qrDataUrl = null;
    if (!this.primaryUrl) return;
    try {
      this.qrDataUrl = await QRCode.toDataURL(`${this.primaryUrl}/pair?code=${code}`,
        { margin: 1, width: 220, color: { dark: '#111827', light: '#ffffff' } });
    } catch {
      this.qrDataUrl = null; // le code et l'adresse restent affichés
    }
  }

  private startCountdown(): void {
    this.stopCountdown();
    const tick = () => {
      const left = this.pairing ? Math.max(0, Math.round((Date.parse(this.pairing.expiresAt) - Date.now()) / 1000)) : 0;
      this.secondsLeft = left;
      if (left === 0) {
        this.clearPairing();
        this.refresh(); // l'appareil vient peut-être de s'appairer
      }
    };
    tick();
    this.countdown = setInterval(tick, 1000);
  }

  private stopCountdown(): void {
    if (this.countdown) clearInterval(this.countdown);
    this.countdown = undefined;
  }

  private clearPairing(): void {
    this.stopCountdown();
    this.pairing = null;
    this.qrDataUrl = null;
  }

  get countdownLabel(): string {
    const m = Math.floor(this.secondsLeft / 60);
    const s = String(this.secondsLeft % 60).padStart(2, '0');
    return `${m}:${s}`;
  }
}
