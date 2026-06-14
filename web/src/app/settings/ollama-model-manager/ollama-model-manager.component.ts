import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { Subscription } from 'rxjs';

import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Check, AlertCircle, Download, Trash2, X } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { SettingsService, OllamaPullEvent } from '../../services/settings.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';

/**
 * Gestion des modeles Ollama installes (composant standalone).
 *
 * Responsabilite unique : le CYCLE DE VIE des modeles sur le serveur Ollama —
 * liste des modeles installes + suppression, et dialog de telechargement (pull)
 * avec barre de progression streamee (NDJSON).
 *
 * Le parent (SettingsComponent) garde le CHOIX du modele actif (formulaire) ;
 * il ecoute `modelsChanged` / `modelPulled` / `modelDeleted` pour rafraichir sa
 * liste et corriger la selection courante si besoin.
 */
@Component({
    selector: 'app-ollama-model-manager',
    imports: [FormsModule, LucideAngularModule, TranslatePipe],
    templateUrl: './ollama-model-manager.component.html',
    // Reutilise la feuille de style de l'ecran Parametres (modal, suggestions,
    // progress-bar, installed-models) pour un rendu strictement identique.
    styleUrls: ['../settings.component.scss']
})
export class OllamaModelManagerComponent implements OnDestroy {

  readonly Check = Check;
  readonly AlertCircle = AlertCircle;
  readonly Download = Download;
  readonly Trash2 = Trash2;
  readonly X = X;

  /** Modeles actuellement installes (charges par le parent). */
  @Input() models: string[] = [];

  /** La liste des modeles a change (pull termine / suppression) → recharger. */
  @Output() modelsChanged = new EventEmitter<void>();
  /** Un modele vient d'etre telecharge avec succes (nom complet). */
  @Output() modelPulled = new EventEmitter<string>();
  /** Un modele vient d'etre supprime (nom complet). */
  @Output() modelDeleted = new EventEmitter<string>();

  /** Dialog d'ajout de modele ouvert/ferme. */
  pullDialogOpen = false;
  /** Nom saisi par l'utilisateur dans le dialog. */
  pullModelName = '';
  /** Suggestions courantes affichees dans le dialog. */
  readonly pullSuggestions = [
    'gemma4:e4b', 'gemma3:4b', 'gemma3:12b',
    'llama3.2:3b', 'llama3.1:8b',
    'mistral:7b', 'qwen2.5:3b', 'qwen2.5:7b'
  ];
  /** Pull en cours ; null si aucun. */
  pullInProgress = false;
  /** Etape courante affichee a l'utilisateur (ex: "downloading", "verifying"). */
  pullStatus = '';
  /** Bytes telecharges sur le digest courant. */
  pullCompleted = 0;
  /** Bytes totaux du digest courant. */
  pullTotal = 0;
  /** Souscription au flux de pull pour pouvoir l'annuler. */
  private pullSubscription: Subscription | null = null;
  /** True si on a recu un evenement {status:"success"} d'Ollama. Sans ca,
   *  une fermeture de stream (timeout proxy, perte reseau) ne doit PAS etre
   *  interpretee comme une reussite. */
  private pullSucceeded = false;

  /** Modele en cours de suppression (nom) pour disabler son bouton. */
  deletingModel: string | null = null;

  errorMessage = '';
  successMessage = '';

  constructor(
    private settingsService: SettingsService,
    private confirmDialog: ConfirmDialogService,
    private translate: TranslateService
  ) {}

  ngOnDestroy(): void {
    if (this.pullSubscription) {
      this.pullSubscription.unsubscribe();
    }
  }

  openPullDialog(): void {
    this.pullDialogOpen = true;
    this.pullModelName = '';
    this.resetPullState();
  }

  closePullDialog(): void {
    if (this.pullInProgress) return; // empêche fermeture pendant un pull
    this.pullDialogOpen = false;
  }

  selectSuggestion(name: string): void {
    this.pullModelName = name;
  }

  startPull(): void {
    const name = this.pullModelName.trim();
    if (!name || this.pullInProgress) return;
    this.resetPullState();
    this.pullInProgress = true;
    this.pullStatus = this.translate.instant('ollamaModelManager.connecting');
    this.errorMessage = '';

    this.pullSubscription = this.settingsService.pullOllamaModel(name).subscribe({
      next: (event: OllamaPullEvent) => {
        if (event.error) {
          this.errorMessage = this.translate.instant('ollamaModelManager.pullEventError', { error: event.error });
          this.pullInProgress = false;
          return;
        }
        if (event.status) this.pullStatus = event.status;
        if (event.completed != null) this.pullCompleted = event.completed;
        if (event.total != null) this.pullTotal = event.total;
        // Marqueur explicite : Ollama emet "success" en derniere ligne quand
        // le pull est reellement complet (manifest + layers + verify).
        if (event.status === 'success') this.pullSucceeded = true;
      },
      error: (err) => {
        this.errorMessage = this.extractError(err, this.translate.instant('ollamaModelManager.pullFailed', { name }));
        this.pullInProgress = false;
      },
      complete: () => {
        this.pullInProgress = false;
        if (!this.pullSucceeded) {
          // Stream ferme sans 'success' final = connexion coupee
          // (timeout proxy, perte reseau, ...). Le modele est probablement
          // partiellement telecharge ; Ollama gardera les couches deja DL.
          this.errorMessage = this.translate.instant('ollamaModelManager.pullInterrupted', { name });
          this.modelsChanged.emit();
          return;
        }
        this.successMessage = this.translate.instant('ollamaModelManager.pullDone', { name });
        this.modelsChanged.emit();
        this.modelPulled.emit(name);
        // Petite tempo avant de fermer pour que le user voie "success".
        setTimeout(() => this.closePullDialog(), 1200);
      }
    });
  }

  cancelPull(): void {
    if (this.pullSubscription) {
      this.pullSubscription.unsubscribe();
      this.pullSubscription = null;
    }
    this.pullInProgress = false;
    this.pullStatus = this.translate.instant('ollamaModelManager.cancelled');
  }

  private resetPullState(): void {
    this.pullStatus = '';
    this.pullCompleted = 0;
    this.pullTotal = 0;
    this.pullSucceeded = false;
    if (this.pullSubscription) {
      this.pullSubscription.unsubscribe();
      this.pullSubscription = null;
    }
  }

  /** Pourcentage du digest courant pour la barre de progression. */
  get pullPercent(): number {
    if (this.pullTotal <= 0) return 0;
    return Math.min(100, Math.round((this.pullCompleted / this.pullTotal) * 100));
  }

  /** Affichage humain des octets ('1.2 GB' / '450 MB'). */
  formatBytes(b: number): string {
    if (!b) return '0';
    const u = ['B', 'KB', 'MB', 'GB', 'TB'];
    let i = 0;
    let v = b;
    while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
    return `${v.toFixed(v < 10 && i > 0 ? 1 : 0)} ${u[i]}`;
  }

  deleteModel(name: string): void {
    this.confirmDialog.confirm({
      title: this.translate.instant('ollamaModelManager.deleteTitle'),
      message: this.translate.instant('ollamaModelManager.deleteMessage', { name }),
      details: [this.translate.instant('ollamaModelManager.deleteDetail')],
      confirmLabel: this.translate.instant('common.delete'),
      variant: 'danger'
    }).then(ok => {
      if (!ok) return;
      this.deletingModel = name;
      this.errorMessage = '';
      this.settingsService.deleteOllamaModel(name).subscribe({
        next: () => {
          this.deletingModel = null;
          this.successMessage = this.translate.instant('ollamaModelManager.deleteDone', { name });
          this.modelsChanged.emit();
          this.modelDeleted.emit(name);
        },
        error: (err) => {
          this.deletingModel = null;
          this.errorMessage = this.extractError(err, this.translate.instant('ollamaModelManager.deleteFailed', { name }));
        }
      });
    });
  }

  private extractError(err: any, fallback: string): string {
    if (err?.error?.detail) return String(err.error.detail);
    if (err?.message) return err.message;
    return fallback;
  }
}
