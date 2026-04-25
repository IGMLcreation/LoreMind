import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, RefreshCw, Save, Check, AlertCircle, Download, Trash2, Plus, X } from 'lucide-angular';
import { SettingsService, AppSettings, AppSettingsUpdate, OneMinModelGroup, OllamaPullEvent } from '../services/settings.service';
import { Subscription } from 'rxjs';
import { UpdatesService, UpdateStatus } from '../services/updates.service';
import { ConfigService } from '../services/config.service';

/**
 * Ecran de parametrage du LLM utilise par le Brain.
 *
 * Deux providers au choix :
 *  - Ollama (local) : on liste dynamiquement les modeles installes.
 *  - 1min.ai (cloud) : on fournit une cle API + on choisit dans un catalogue fixe.
 *
 * Les modifications sont persistees cote Brain dans data/settings.json
 * (fichier local, usage mono-utilisateur) et appliquees a la prochaine
 * requete chat / generate — pas besoin de redemarrer.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {

  readonly ArrowLeft = ArrowLeft;
  readonly RefreshCw = RefreshCw;
  readonly Save = Save;
  readonly Check = Check;
  readonly AlertCircle = AlertCircle;
  readonly Download = Download;
  readonly Trash2 = Trash2;
  readonly Plus = Plus;
  readonly X = X;

  // --- Pull / delete de modeles Ollama ---
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

  /** Modele en cours de suppression (nom) pour disabler son bouton. */
  deletingModel: string | null = null;

  // Mises a jour conteneurs
  updateStatus: UpdateStatus | null = null;
  updateChecking = false;
  updateApplying = false;
  updateMessage = '';

  settings: AppSettings | null = null;
  ollamaModels: string[] = [];
  oneminGroups: OneMinModelGroup[] = [];
  /** Fournisseur 1min.ai actuellement selectionne (filtre la liste des modeles). */
  oneminProvider: string = '';

  loadingModels = false;
  saving = false;
  errorMessage = '';
  successMessage = '';

  /**
   * Fenetre de contexte max supportee par le modele Ollama actuellement
   * selectionne (extraite des metadonnees GGUF via /api/show). 0 si inconnue
   * — dans ce cas on laisse un fallback de 131072 cote UI.
   */
  ollamaModelMaxContext = 0;

  /** Minimum raisonnable pour num_ctx (defaut Ollama = 2048). */
  readonly CTX_MIN = 2048;
  /** Fallback si Ollama ne renvoie pas le context_length (modele exotique). */
  readonly CTX_FALLBACK_MAX = 131072;

  /** Cle 1min.ai saisie — vide = on ne touche pas a la cle persistee. */
  oneminApiKeyInput = '';
  /** True si l'utilisateur a coche "effacer la cle". */
  clearApiKey = false;

  constructor(
    private settingsService: SettingsService,
    private router: Router,
    private updatesService: UpdatesService,
    public config: ConfigService
  ) {}

  ngOnInit(): void {
    this.loadSettings();
    if (this.config.updateCheckEnabled) {
      this.checkUpdates();
    }
  }

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
    if (!confirm('Telecharger et redemarrer les conteneurs maintenant ? L\'app sera indisponible quelques secondes.')) {
      return;
    }
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
  }

  loadSettings(): void {
    this.settingsService.getSettings().subscribe({
      next: (s) => {
        this.settings = { ...s };
        this.refreshModels();
        this.fetchOllamaModelInfo();
      },
      error: (err) => this.errorMessage = this.extractError(err, 'Impossible de charger les parametres.')
    });
  }

  refreshModels(): void {
    if (!this.settings) return;
    this.loadingModels = true;

    this.settingsService.listOllamaModels().subscribe({
      next: (r) => this.ollamaModels = r.models,
      error: () => this.ollamaModels = [],
      complete: () => this.loadingModels = false
    });

    this.settingsService.listOneMinModels().subscribe({
      next: (r) => {
        this.oneminGroups = r.groups;
        this.syncOneminProviderFromModel();
      },
      error: () => this.oneminGroups = []
    });
  }

  /** Deduit le fournisseur a partir du modele actuellement configure. */
  private syncOneminProviderFromModel(): void {
    if (!this.settings) return;
    const currentModel = this.settings.onemin_model;
    const found = this.oneminGroups.find(g => g.models.includes(currentModel));
    this.oneminProvider = found ? found.provider : (this.oneminGroups[0]?.provider ?? '');
  }

  /** Retourne la liste des modeles du fournisseur selectionne. */
  get currentProviderModels(): string[] {
    const group = this.oneminGroups.find(g => g.provider === this.oneminProvider);
    return group ? group.models : [];
  }

  /**
   * Recupere la fenetre max supportee par le modele Ollama selectionne.
   * Si la valeur courante de num_ctx depasse ce max, on la clamp.
   */
  fetchOllamaModelInfo(): void {
    if (!this.settings || this.settings.llm_provider !== 'ollama') return;
    const modelName = this.settings.llm_model;
    if (!modelName) return;
    this.settingsService.getOllamaModelInfo(modelName).subscribe({
      next: (info) => {
        this.ollamaModelMaxContext = info.context_length;
        const max = this.effectiveMaxContext;
        if (this.settings && this.settings.llm_num_ctx > max) {
          this.settings.llm_num_ctx = max;
        }
      },
      error: () => this.ollamaModelMaxContext = 0
    });
  }

  /** Max effectif a afficher pour le slider (modele Ollama ou fallback). */
  get effectiveMaxContext(): number {
    return this.ollamaModelMaxContext > 0 ? this.ollamaModelMaxContext : this.CTX_FALLBACK_MAX;
  }

  /** Quand on change de fournisseur, bascule automatiquement sur son premier modele. */
  onProviderChange(): void {
    if (!this.settings) return;
    const models = this.currentProviderModels;
    if (models.length > 0 && !models.includes(this.settings.onemin_model)) {
      this.settings.onemin_model = models[0];
    }
  }

  save(): void {
    if (!this.settings) return;
    this.saving = true;
    this.errorMessage = '';
    this.successMessage = '';

    const patch: AppSettingsUpdate = {
      llm_provider: this.settings.llm_provider,
      ollama_base_url: this.settings.ollama_base_url,
      llm_model: this.settings.llm_model,
      onemin_model: this.settings.onemin_model,
      llm_num_ctx: this.settings.llm_num_ctx
    };
    if (this.clearApiKey) {
      patch.onemin_api_key = '';
    } else if (this.oneminApiKeyInput.trim()) {
      patch.onemin_api_key = this.oneminApiKeyInput.trim();
    }

    this.settingsService.updateSettings(patch).subscribe({
      next: (s) => {
        this.settings = { ...s };
        this.oneminApiKeyInput = '';
        this.clearApiKey = false;
        this.successMessage = 'Parametres sauvegardes.';
        this.saving = false;
      },
      error: (err) => {
        this.errorMessage = this.extractError(err, 'Echec de la sauvegarde.');
        this.saving = false;
      }
    });
  }

  // --- Gestion des modeles Ollama (pull / delete) -------------------------

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
    this.pullStatus = 'connexion...';
    this.errorMessage = '';

    this.pullSubscription = this.settingsService.pullOllamaModel(name).subscribe({
      next: (event: OllamaPullEvent) => {
        if (event.error) {
          this.errorMessage = `Echec : ${event.error}`;
          this.pullInProgress = false;
          return;
        }
        if (event.status) this.pullStatus = event.status;
        if (event.completed != null) this.pullCompleted = event.completed;
        if (event.total != null) this.pullTotal = event.total;
      },
      error: (err) => {
        this.errorMessage = this.extractError(err, `Echec du telechargement de ${name}.`);
        this.pullInProgress = false;
      },
      complete: () => {
        this.pullInProgress = false;
        this.successMessage = `Modele ${name} telecharge.`;
        this.refreshModels();
        // Si l'utilisateur n'avait aucun modele, on selectionne celui-ci.
        if (this.settings && !this.settings.llm_model) {
          this.settings.llm_model = name;
          this.fetchOllamaModelInfo();
        }
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
    this.pullStatus = 'annule';
  }

  private resetPullState(): void {
    this.pullStatus = '';
    this.pullCompleted = 0;
    this.pullTotal = 0;
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
    if (!confirm(`Supprimer le modele '${name}' ? L'espace disque sera libere.`)) return;
    this.deletingModel = name;
    this.errorMessage = '';
    this.settingsService.deleteOllamaModel(name).subscribe({
      next: () => {
        this.deletingModel = null;
        this.successMessage = `Modele ${name} supprime.`;
        // Si l'utilisateur supprime le modele actuellement selectionne,
        // on bascule sur le premier disponible (ou vide).
        this.refreshModels();
        if (this.settings && this.settings.llm_model === name) {
          this.settings.llm_model = '';
          this.ollamaModelMaxContext = 0;
        }
      },
      error: (err) => {
        this.deletingModel = null;
        this.errorMessage = this.extractError(err, `Echec de la suppression de ${name}.`);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/lore']);
  }

  private extractError(err: any, fallback: string): string {
    if (err?.error?.detail) return String(err.error.detail);
    if (err?.message) return err.message;
    return fallback;
  }
}
