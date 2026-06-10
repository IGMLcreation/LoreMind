import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, RefreshCw, Save, Check, AlertCircle, Plus } from 'lucide-angular';
import { SettingsService, AppSettings, AppSettingsUpdate, OneMinModelGroup, OpenRouterModel, MistralModel, GeminiModel } from '../services/settings.service';
import { LayoutService } from '../services/layout.service';
import { UpdatesSectionComponent } from './updates-section/updates-section.component';
import { OllamaModelManagerComponent } from './ollama-model-manager/ollama-model-manager.component';

/**
 * Ecran de parametrage du LLM utilise par le Brain.
 *
 * Responsabilite : le FORMULAIRE de configuration (fournisseur LLM, modeles,
 * cles API, embeddings RAG, fenetre de contexte, import PDF) + sa sauvegarde.
 *
 * Les responsabilites voisines sont des sous-composants standalone :
 *  - UpdatesSectionComponent : mises a jour conteneurs + licence Patreon + canal beta ;
 *  - OllamaModelManagerComponent : pull/suppression des modeles Ollama.
 *
 * Les modifications sont persistees cote Brain dans data/settings.json
 * (fichier local, usage mono-utilisateur) et appliquees a la prochaine
 * requete chat / generate — pas besoin de redemarrer.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, UpdatesSectionComponent, OllamaModelManagerComponent],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {

  readonly ArrowLeft = ArrowLeft;
  readonly RefreshCw = RefreshCw;
  readonly Save = Save;
  readonly Check = Check;
  readonly AlertCircle = AlertCircle;
  readonly Plus = Plus;

  settings: AppSettings | null = null;
  ollamaModels: string[] = [];
  oneminGroups: OneMinModelGroup[] = [];

  /** Catalogue OpenRouter (chargé dynamiquement) + filtre "gratuits seulement". */
  openrouterModels: OpenRouterModel[] = [];
  openrouterFreeOnly = true;

  /** Catalogue Mistral (dynamique si cle configuree, repli statique sinon). */
  mistralModels: MistralModel[] = [];

  /** Catalogue Gemini (dynamique si cle configuree, repli statique sinon). */
  geminiModels: GeminiModel[] = [];
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

  /** Cle OpenRouter saisie — vide = on ne touche pas a la cle persistee. */
  openrouterApiKeyInput = '';
  /** True si l'utilisateur a coche "effacer la cle OpenRouter". */
  clearOpenrouterKey = false;

  /** Cle Mistral saisie — vide = on ne touche pas a la cle persistee. */
  mistralApiKeyInput = '';
  /** True si l'utilisateur a coche "effacer la cle Mistral". */
  clearMistralKey = false;

  /** Cle Gemini saisie — vide = on ne touche pas a la cle persistee. */
  geminiApiKeyInput = '';
  /** True si l'utilisateur a coche "effacer la cle Gemini". */
  clearGeminiKey = false;

  constructor(
    private settingsService: SettingsService,
    private router: Router,
    private layoutService: LayoutService
  ) {}

  ngOnInit(): void {
    // Page racine : on s'assure de ne pas heriter de la sidebar d'une
    // section precedente (cf. fix CampaignsComponent / LoreComponent).
    this.layoutService.hide();
    this.loadSettings();
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

    this.settingsService.listOpenRouterModels().subscribe({
      next: (r) => this.openrouterModels = r.models,
      error: () => this.openrouterModels = []
    });

    this.settingsService.listMistralModels().subscribe({
      next: (r) => this.mistralModels = r.models,
      error: () => this.mistralModels = []
    });

    this.settingsService.listGeminiModels().subscribe({
      next: (r) => this.geminiModels = r.models,
      error: () => this.geminiModels = []
    });
  }

  // --- Evenements du gestionnaire de modeles Ollama (sous-composant) -------

  /** Un modele vient d'etre telecharge : si aucun modele n'etait selectionne, on le prend. */
  onModelPulled(name: string): void {
    if (this.settings && !this.settings.llm_model) {
      this.settings.llm_model = name;
      this.fetchOllamaModelInfo();
    }
  }

  /** Le modele selectionne a ete supprime : on vide la selection. */
  onModelDeleted(name: string): void {
    if (this.settings && this.settings.llm_model === name) {
      this.settings.llm_model = '';
      this.ollamaModelMaxContext = 0;
    }
  }

  /** Options du select Mistral : liste + valeur courante garantie selectionnable. */
  get mistralSelectOptions(): { id: string; label: string }[] {
    const options: { id: string; label: string }[] =
      this.mistralModels.map(m => ({ id: m.id, label: m.id }));
    const cur = this.settings?.mistral_model;
    if (cur && !options.some(o => o.id === cur)) {
      options.unshift({ id: cur, label: `${cur} (actuel)` });
    }
    return options;
  }

  /** Options du select Gemini : liste + valeur courante garantie selectionnable. */
  get geminiSelectOptions(): { id: string; label: string }[] {
    const options: { id: string; label: string }[] =
      this.geminiModels.map(m => ({ id: m.id, label: m.id }));
    const cur = this.settings?.gemini_model;
    if (cur && !options.some(o => o.id === cur)) {
      options.unshift({ id: cur, label: `${cur} (actuel)` });
    }
    return options;
  }

  /** Modeles OpenRouter, filtres (gratuits seulement par defaut). */
  get filteredOpenrouterModels(): OpenRouterModel[] {
    return this.openrouterFreeOnly
      ? this.openrouterModels.filter(m => m.free)
      : this.openrouterModels;
  }

  /** Options du select OpenRouter : routeur auto + liste filtree + valeur courante. */
  get openrouterSelectOptions(): { id: string; label: string }[] {
    const options: { id: string; label: string }[] = [
      { id: 'openrouter/free', label: 'openrouter/free — routeur auto (gratuit)' }
    ];
    for (const m of this.filteredOpenrouterModels) {
      const ctx = m.context_length ? ` — ${m.context_length.toLocaleString()} ctx` : '';
      options.push({ id: m.id, label: `${m.id}${ctx}${m.free ? ' · gratuit' : ''}` });
    }
    // Garantit que le modele actuellement configure reste selectionnable.
    const cur = this.settings?.openrouter_model;
    if (cur && !options.some(o => o.id === cur)) {
      options.splice(1, 0, { id: cur, label: `${cur} (actuel)` });
    }
    return options;
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
      openrouter_model: this.settings.openrouter_model,
      mistral_model: this.settings.mistral_model,
      gemini_model: this.settings.gemini_model,
      embedding_provider: this.settings.embedding_provider,
      ollama_embedding_model: this.settings.ollama_embedding_model,
      mistral_embedding_model: this.settings.mistral_embedding_model,
      auto_pull_embedding_model: this.settings.auto_pull_embedding_model,
      rag_top_k: this.settings.rag_top_k,
      llm_num_ctx: this.settings.llm_num_ctx,
      import_chunk_tokens: this.settings.import_chunk_tokens,
      llm_timeout_seconds: this.settings.llm_timeout_seconds
    };
    if (this.clearApiKey) {
      patch.onemin_api_key = '';
    } else if (this.oneminApiKeyInput.trim()) {
      patch.onemin_api_key = this.oneminApiKeyInput.trim();
    }
    if (this.clearOpenrouterKey) {
      patch.openrouter_api_key = '';
    } else if (this.openrouterApiKeyInput.trim()) {
      patch.openrouter_api_key = this.openrouterApiKeyInput.trim();
    }
    if (this.clearMistralKey) {
      patch.mistral_api_key = '';
    } else if (this.mistralApiKeyInput.trim()) {
      patch.mistral_api_key = this.mistralApiKeyInput.trim();
    }
    if (this.clearGeminiKey) {
      patch.gemini_api_key = '';
    } else if (this.geminiApiKeyInput.trim()) {
      patch.gemini_api_key = this.geminiApiKeyInput.trim();
    }

    this.settingsService.updateSettings(patch).subscribe({
      next: (s) => {
        this.settings = { ...s };
        this.oneminApiKeyInput = '';
        this.clearApiKey = false;
        this.openrouterApiKeyInput = '';
        this.clearOpenrouterKey = false;
        this.mistralApiKeyInput = '';
        this.clearMistralKey = false;
        this.geminiApiKeyInput = '';
        this.clearGeminiKey = false;
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
