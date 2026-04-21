import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, RefreshCw, Save, Check, AlertCircle } from 'lucide-angular';
import { SettingsService, AppSettings, AppSettingsUpdate, OneMinModelGroup } from '../services/settings.service';

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

  settings: AppSettings | null = null;
  ollamaModels: string[] = [];
  oneminGroups: OneMinModelGroup[] = [];
  /** Fournisseur 1min.ai actuellement selectionne (filtre la liste des modeles). */
  oneminProvider: string = '';

  loadingModels = false;
  saving = false;
  errorMessage = '';
  successMessage = '';

  /** Cle 1min.ai saisie — vide = on ne touche pas a la cle persistee. */
  oneminApiKeyInput = '';
  /** True si l'utilisateur a coche "effacer la cle". */
  clearApiKey = false;

  constructor(
    private settingsService: SettingsService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadSettings();
  }

  loadSettings(): void {
    this.settingsService.getSettings().subscribe({
      next: (s) => {
        this.settings = { ...s };
        this.refreshModels();
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
      onemin_model: this.settings.onemin_model
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

  goBack(): void {
    this.router.navigate(['/lore']);
  }

  private extractError(err: any, fallback: string): string {
    if (err?.error?.detail) return String(err.error.detail);
    if (err?.message) return err.message;
    return fallback;
  }
}
