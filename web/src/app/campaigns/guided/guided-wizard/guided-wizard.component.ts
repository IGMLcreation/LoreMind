import { Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LucideAngularModule, ArrowLeft, ArrowRight, Check, RotateCcw, Sparkles } from 'lucide-angular';

import { Arc } from '../../../services/campaign.model';
import { CampaignService } from '../../../services/campaign.service';
import { GuidedSkeletonService } from '../../../services/guided-skeleton.service';
import { ConfirmDialogService } from '../../../shared/confirm-dialog/confirm-dialog.service';
import { WritingCoachTriggerComponent } from '../../../shared/writing-coach/writing-coach-trigger.component';
import { GuidedAnswers, GuidedDraft, GuidedField, GuidedMethod, GuidedStep, answer } from '../guided-method.model';
import { findGuidedMethod } from '../guided-methods';
import { IDEA_KEY, applySuggestions, buildSuggestRequest, markTouched } from '../guided-assist';

/** Clés de réponse réservées à l'assistant (placement + chaînage), communes à toutes les méthodes. */
const PLACEMENT_MODE = 'placement.mode';
const PLACEMENT_ARC = 'placement.arcId';
const PLACEMENT_NEW_ARC = 'placement.newArcName';
const LINK_SCENES = 'link';

/**
 * Assistant pas à pas d'une méthode guidée (Mentor d'écriture, phase M3) :
 * une étape par écran (lisible sur téléphone), chacune expliquée (pourquoi + exemple),
 * avec le coach 💡 sur les champs. Les réponses sont celles du MJ : rien n'est généré.
 * Le brouillon est gardé dans le navigateur pour pouvoir quitter et revenir.
 */
@Component({
  selector: 'app-guided-wizard',
  imports: [FormsModule, RouterLink, TranslatePipe, LucideAngularModule, WritingCoachTriggerComponent],
  templateUrl: './guided-wizard.component.html',
  styleUrls: ['./guided-wizard.component.scss']
})
export class GuidedWizardComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly ArrowRight = ArrowRight;
  readonly Check = Check;
  readonly RotateCcw = RotateCcw;
  readonly Sparkles = Sparkles;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly campaignService = inject(CampaignService);
  private readonly skeletonService = inject(GuidedSkeletonService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('stepTitle') stepTitle?: ElementRef<HTMLHeadingElement>;

  campaignId = '';
  method: GuidedMethod | null = null;
  answers: GuidedAnswers = {};
  stepIndex = 0;
  /** Arcs linéaires de la campagne : seuls candidats pour y ranger l'aventure. */
  linearArcs: Arc[] = [];
  /** Le placement par défaut dépend des arcs : on attend leur chargement pour le choisir. */
  private arcsLoaded = false;
  submitting = false;
  error: string | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(pm => {
      this.campaignId = pm.get('campaignId') ?? '';
      this.method = findGuidedMethod(pm.get('method')) ?? null;
      if (!this.method) return;
      this.restoreDraft();
      this.arcsLoaded = false;
      this.campaignService.getArcs(this.campaignId).subscribe(arcs => {
        this.linearArcs = arcs
          .filter(a => (a.type ?? 'LINEAR') === 'LINEAR')
          .sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
        this.arcsLoaded = true;
        this.applyDefaults();
      });
      this.applyDefaults();
    });
  }

  // --- Navigation ------------------------------------------------------------------

  get step(): GuidedStep | null {
    return this.method?.steps[this.stepIndex] ?? null;
  }

  get total(): number {
    return this.method?.steps.length ?? 0;
  }

  get progress(): number {
    return this.total ? Math.round(((this.stepIndex + 1) / this.total) * 100) : 0;
  }

  get isLast(): boolean {
    return this.stepIndex === this.total - 1;
  }

  next(): void {
    if (!this.canProceed() || this.isLast) return;
    this.goTo(this.stepIndex + 1);
  }

  previous(): void {
    if (this.stepIndex > 0) this.goTo(this.stepIndex - 1);
  }

  private goTo(index: number): void {
    this.stepIndex = index;
    this.error = null;
    this.applyDefaults();
    this.saveDraft();
    // Nouvelle étape = nouvel écran : on remonte et on place le focus sur son titre.
    setTimeout(() => {
      this.stepTitle?.nativeElement.focus();
      this.stepTitle?.nativeElement.scrollIntoView({ block: 'start' });
    });
  }

  canProceed(): boolean {
    const step = this.step;
    if (!step) return false;
    if (step.kind === 'placement') {
      return this.answers[PLACEMENT_MODE] === 'existing'
        ? !!this.answers[PLACEMENT_ARC]
        : !!answer(this.answers, PLACEMENT_NEW_ARC);
    }
    return (step.fields ?? []).every(f => !f.required || !!answer(this.answers, f.key));
  }

  // --- Réponses ----------------------------------------------------------------------

  /** Clé i18n de la méthode courante. */
  k(path: string): string {
    return `guided.${this.method?.i18nKey}.${path}`;
  }

  fieldId(field: GuidedField): string {
    return `guided-${field.key.replace(/\./g, '-')}`;
  }

  setAnswer(key: string, value: string): void {
    // Un champ modifié par le MJ ne sera plus jamais remplacé par une proposition de l'IA.
    this.answers = markTouched({ ...this.answers, [key]: value }, key);
    this.saveDraft();
  }

  // --- IA : propositions à partir de l'idée de départ ------------------------------------

  /** Proposition en cours (anti double-clic) ; `all` = toute la méthode, sinon l'étape. */
  suggesting: 'all' | 'step' | null = null;
  suggestError: string | null = null;
  /** Nombre de champs remplis par la dernière proposition (message de confirmation). */
  suggestFilled: number | null = null;
  /** Réponses avant la dernière proposition : « Annuler » les restaure. */
  private beforeSuggest: GuidedAnswers | null = null;

  get idea(): string {
    return this.answers[IDEA_KEY] ?? '';
  }

  set idea(value: string) {
    this.answers = { ...this.answers, [IDEA_KEY]: value };
    this.saveDraft();
  }

  get canUndoSuggest(): boolean {
    return this.beforeSuggest !== null;
  }

  /** Propose des réponses pour tous les champs encore libres de la méthode. */
  suggestAll(): void {
    this.suggest('all');
  }

  /** Propose des réponses pour les champs encore libres de l'étape affichée. */
  suggestStep(): void {
    this.suggest('step');
  }

  undoSuggest(): void {
    if (!this.beforeSuggest) return;
    this.answers = this.beforeSuggest;
    this.beforeSuggest = null;
    this.suggestFilled = null;
    this.saveDraft();
  }

  private suggest(scope: 'all' | 'step'): void {
    const method = this.method;
    if (!method || this.suggesting) return;
    const t = (key: string) => this.translate.instant(key) as string;
    const stepIds = scope === 'step' && this.step ? [this.step.id] : undefined;
    const request = buildSuggestRequest(method, this.answers, t, stepIds);
    this.suggestError = null;
    this.suggestFilled = null;
    if (request.questions.length === 0) {
      this.suggestFilled = 0;
      return;
    }
    this.suggesting = scope;
    this.skeletonService.suggest(this.campaignId, request).subscribe({
      next: ({ answers }) => {
        const before = this.answers;
        const result = applySuggestions(method, this.answers, answers ?? {}, t);
        this.answers = result.answers;
        this.beforeSuggest = result.filled > 0 ? before : this.beforeSuggest;
        this.suggestFilled = result.filled;
        this.suggesting = null;
        this.saveDraft();
      },
      error: () => {
        this.suggesting = null;
        this.suggestError = this.translate.instant('guided.ai.error');
      }
    });
  }

  /** Placement par défaut + valeurs pré-remplies + premier choix des champs `choice`. */
  private applyDefaults(): void {
    const a = { ...this.answers };
    if (this.arcsLoaded) this.applyPlacementDefaults(a);
    a[LINK_SCENES] ??= 'true';
    for (const field of this.step?.fields ?? []) {
      if (a[field.key] !== undefined) continue;
      if (field.kind === 'choice' && field.options?.length) a[field.key] = field.options[0];
      else if (field.prefilled) a[field.key] = this.translate.instant(this.k(`defaults.${field.key}`));
    }
    this.answers = a;
  }

  /** Arc existant par défaut s'il y en a ; sinon (ou arc disparu) nouvel arc. */
  private applyPlacementDefaults(a: GuidedAnswers): void {
    a[PLACEMENT_MODE] ??= this.linearArcs.length > 0 ? 'existing' : 'new';
    if (a[PLACEMENT_MODE] === 'existing' && !this.linearArcs.some(arc => arc.id === a[PLACEMENT_ARC])) {
      a[PLACEMENT_ARC] = this.linearArcs[0]?.id ?? '';
      if (!a[PLACEMENT_ARC]) a[PLACEMENT_MODE] = 'new';
    }
  }

  // --- Récapitulatif & création --------------------------------------------------------

  get draft(): GuidedDraft | null {
    return this.method ? this.method.toDraft(this.answers) : null;
  }

  get placementLabel(): string {
    if (this.answers[PLACEMENT_MODE] === 'existing') {
      return this.linearArcs.find(a => a.id === this.answers[PLACEMENT_ARC])?.name ?? '';
    }
    return answer(this.answers, PLACEMENT_NEW_ARC) ?? '';
  }

  get linkScenes(): boolean {
    return this.answers[LINK_SCENES] !== 'false';
  }

  set linkScenes(value: boolean) {
    this.setAnswer(LINK_SCENES, String(value));
  }

  /** Chaînage proposé seulement quand la méthode ne relie pas elle-même ses scènes. */
  offersChain(draft: GuidedDraft): boolean {
    return !draft.links?.length && !draft.noChain;
  }

  create(): void {
    const draft = this.draft;
    if (!draft || this.submitting) return;
    const existing = this.answers[PLACEMENT_MODE] === 'existing';
    this.submitting = true;
    this.error = null;
    this.skeletonService.create(this.campaignId, {
      arcId: existing ? this.answers[PLACEMENT_ARC] : null,
      newArcName: existing ? null : answer(this.answers, PLACEMENT_NEW_ARC),
      chapter: draft.chapter,
      scenes: draft.scenes,
      linkSequentially: this.offersChain(draft) && this.linkScenes,
      linkLabel: this.translate.instant('guided.linkLabel'),
      links: (draft.links ?? []).map(l => ({
        from: l.from, to: l.to, kind: l.kind, label: this.translate.instant(this.k(`links.${l.labelKey}`))
      })),
      clock: draft.clock ?? null
    }).subscribe({
      next: result => {
        this.clearDraft();
        this.router.navigate(['/campaigns', this.campaignId, 'arcs', result.arcId, 'chapters', result.chapterId]);
      },
      error: () => {
        this.submitting = false;
        this.error = this.translate.instant('guided.createError');
      }
    });
  }

  async restart(): Promise<void> {
    const ok = await this.confirmDialog.confirm({
      title: this.translate.instant('guided.restartTitle'),
      message: this.translate.instant('guided.restartMessage'),
      confirmLabel: this.translate.instant('guided.restart')
    });
    if (!ok) return;
    this.clearDraft();
    this.answers = {};
    this.goTo(0);
  }

  // --- Brouillon (confort par navigateur : jamais indispensable) -------------------------

  private get storageKey(): string {
    return `loremind.guided.${this.campaignId}.${this.method?.id}`;
  }

  private restoreDraft(): void {
    this.answers = {};
    this.stepIndex = 0;
    try {
      const raw = localStorage.getItem(this.storageKey);
      if (!raw) return;
      const saved = JSON.parse(raw) as { answers?: GuidedAnswers; step?: number };
      this.answers = saved.answers ?? {};
      this.stepIndex = Math.min(Math.max(saved.step ?? 0, 0), this.total - 1);
    } catch { /* brouillon illisible ou stockage bloqué : on repart de zéro */ }
  }

  private saveDraft(): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify({ answers: this.answers, step: this.stepIndex }));
    } catch { /* stockage indisponible : le brouillon n'est simplement pas gardé */ }
  }

  private clearDraft(): void {
    try {
      localStorage.removeItem(this.storageKey);
    } catch { /* rien à nettoyer */ }
  }
}
