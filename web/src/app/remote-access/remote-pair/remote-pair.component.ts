import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, Tablet, Check } from 'lucide-angular';

import { RemoteAccessService } from '../../services/remote-access.service';
import { guessDeviceKey, normalizeCode, safeReturnUrl } from './remote-pair.helpers';

/**
 * Écran d'appairage d'une tablette (accès distant, mode bureau). Recouvre toute l'appli :
 * tant que l'appareil n'est pas reconnu, rien d'autre n'est accessible depuis le réseau.
 * Le QR code affiché sur le PC ouvre `/pair?code=123456` : le code est alors pré-rempli.
 */
@Component({
  selector: 'app-remote-pair',
  imports: [FormsModule, RouterLink, TranslatePipe, LucideAngularModule],
  templateUrl: './remote-pair.component.html',
  styleUrls: ['./remote-pair.component.scss']
})
export class RemotePairComponent implements OnInit {
  readonly Tablet = Tablet;
  readonly Check = Check;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly remoteAccess = inject(RemoteAccessService);
  private readonly translate = inject(TranslateService);

  code = '';
  deviceName = '';
  submitting = false;
  error: string | null = null;
  /** Ouvert sur le PC lui-même : rien à appairer. */
  onHost = false;
  private returnUrl = '/';

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.code = normalizeCode(params.get('code'));
    this.returnUrl = safeReturnUrl(params.get('returnUrl'));
    this.deviceName = this.translate.instant(guessDeviceKey(navigator.userAgent));

    this.remoteAccess.session().subscribe(session => {
      if (!session.remote) this.onHost = true;
      else if (session.paired) this.router.navigateByUrl(this.returnUrl);
    });
  }

  get canSubmit(): boolean {
    return /^\d{6}$/.test(this.code) && !this.submitting;
  }

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.code = normalizeCode(input.value);
    input.value = this.code;
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.submitting = true;
    this.error = null;
    this.remoteAccess.pair(this.code, this.deviceName).subscribe({
      next: () => {
        this.remoteAccess.resetSession();
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.error = this.translate.instant(err.status === 403 ? 'remoteAccess.pair.rejected' : 'remoteAccess.pair.failed');
      }
    });
  }
}
