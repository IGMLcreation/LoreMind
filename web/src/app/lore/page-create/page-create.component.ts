import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { LucideAngularModule, FileText, Sparkles } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { TemplateService } from '../../services/template.service';
import { PageService } from '../../services/page.service';
import { LayoutService } from '../../services/layout.service';
import { PageTitleService } from '../../services/page-title.service';
import { LoreNode } from '../../services/lore.model';
import { Template } from '../../services/template.model';
import { loadLoreSidebarData, buildLoreSidebarConfig } from '../lore-sidebar.helper';
import { AiChatDrawerComponent, ChatPrimaryAction } from '../../shared/ai-chat-drawer/ai-chat-drawer.component';

/**
 * Écran de création d'une Page.
 *
 * Deux entrées possibles :
 *  - /lore/:loreId/pages/create                     → noeud choisi depuis le template
 *  - /lore/:loreId/nodes/:nodeId/pages/create       → noeud pré-rempli depuis l'URL
 *
 * Le MVP est volontairement simple (maquette "création de page") : titre +
 * choix de template (grille) + noeud de destination. L'édition détaillée des
 * champs dynamiques du template se fait APRÈS création, via l'écran page-edit.
 */
@Component({
  selector: 'app-page-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, LucideAngularModule, AiChatDrawerComponent],
  templateUrl: './page-create.component.html',
  styleUrls: ['./page-create.component.scss']
})
export class PageCreateComponent implements OnInit, OnDestroy {
  readonly FileText = FileText;
  readonly Sparkles = Sparkles;

  form: FormGroup;
  loreId = '';
  /** Pré-rempli si la route contient :nodeId. */
  preselectedNodeId: string | null = null;
  nodes: LoreNode[] = [];
  templates: Template[] = [];
  /** Template actuellement sélectionné dans la grille. */
  selectedTemplateId: string | null = null;

  // --- Mode wizard IA (étape b6) -----------------------------------------

  /** Drawer chat ouvert ? */
  chatOpen = false;
  /** Dernière réponse complète de l'assistant — on y cherchera le bloc <values>. */
  private lastWizardReply: string | null = null;
  /** Erreur de parsing du bloc <values> — affichée sous le drawer. */
  wizardError: string | null = null;
  /** Action primaire du wizard : applique les valeurs extraites et crée la page. */
  readonly wizardPrimaryAction: ChatPrimaryAction = { label: 'Appliquer et créer la page' };
  /** Suggestions rapides orientées "affiner le résultat" (mode wizard). */
  readonly wizardSuggestions: string[] = [
    'Rends la description plus courte',
    'Ajoute un trait distinctif marquant',
    'Donne un ton plus sombre'
  ];

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private loreService: LoreService,
    private templateService: TemplateService,
    private pageService: PageService,
    private layoutService: LayoutService,
    private pageTitleService: PageTitleService
  ) {
    this.form = this.fb.group({
      title:  ['', Validators.required],
      nodeId: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.pageTitleService.set('Nouvelle page');
    this.loreId = this.route.snapshot.paramMap.get('loreId')!;
    this.preselectedNodeId = this.route.snapshot.paramMap.get('nodeId');

    loadLoreSidebarData(this.loreId, this.loreService, this.templateService, this.pageService)
      .subscribe(data => {
        this.nodes = data.nodes;
        this.templates = data.templates;
        this.layoutService.show(buildLoreSidebarConfig(data));

        // Si nodeId fourni par l'URL, on fige la valeur ET on désactive le
        // contrôle de formulaire (FormControl.disable, pas attr.disabled qui
        // serait cosmétique). La valeur reste incluse dans les submits.
        if (this.preselectedNodeId) {
          this.form.patchValue({ nodeId: this.preselectedNodeId });
          this.form.get('nodeId')?.disable();
          this.autoSelectTemplateForNode(this.preselectedNodeId);
        } else {
          // Pas de nodeId dans l'URL : le <select> affiche visuellement la
          // première option mais la valeur du FormControl reste ''. On tente
          // l'auto-sélection inverse : si un seul template a un defaultNodeId
          // qui pointe sur un dossier existant, on le sélectionne et on
          // pré-remplit le dossier — sinon on laisse l'utilisateur choisir.
          const validNodeIds = new Set(this.nodes.map(n => n.id));
          const candidates = this.templates.filter(
            t => t.defaultNodeId && validNodeIds.has(t.defaultNodeId)
          );
          if (candidates.length === 1) {
            const tpl = candidates[0];
            this.selectedTemplateId = tpl.id!;
            this.form.patchValue({ nodeId: tpl.defaultNodeId });
          }
        }

        this.form.get('nodeId')?.valueChanges.subscribe(nodeId => {
          this.autoSelectTemplateForNode(nodeId);
        });

        this.restoreDraft();
      });
  }

  /** Clé sessionStorage pour le brouillon — scopée au lore courant. */
  private get draftKey(): string {
    return `page-create-draft:${this.loreId}`;
  }

  /**
   * Sauvegarde le titre et le template sélectionné avant un détour de navigation
   * (création de template ou de dossier), pour pouvoir les restaurer au retour.
   * NodeId volontairement omis : il peut référencer un dossier qui n'existait
   * pas encore et serait invalide après un aller-retour.
   */
  saveDraft(): void {
    const draft = {
      title: this.form.value.title ?? '',
      selectedTemplateId: this.selectedTemplateId
    };
    if (!draft.title && !draft.selectedTemplateId) return;
    try {
      sessionStorage.setItem(this.draftKey, JSON.stringify(draft));
    } catch { /* quota dépassé ou storage indisponible : on ignore */ }
  }

  private restoreDraft(): void {
    let raw: string | null = null;
    try { raw = sessionStorage.getItem(this.draftKey); } catch { return; }
    if (!raw) return;
    sessionStorage.removeItem(this.draftKey);
    try {
      const draft = JSON.parse(raw) as { title?: string; selectedTemplateId?: string | null };
      if (draft.title) this.form.patchValue({ title: draft.title });
      if (draft.selectedTemplateId && this.templates.some(t => t.id === draft.selectedTemplateId)) {
        const tpl = this.templates.find(t => t.id === draft.selectedTemplateId)!;
        this.selectTemplate(tpl);
      }
    } catch { /* JSON corrompu : on ignore */ }
  }

  /**
   * Auto-sélection du template dont defaultNodeId === nodeId courant.
   * Ne fait rien si l'utilisateur a déjà choisi un template manuellement
   * (on ne veut pas écraser un choix explicite).
   */
  private autoSelectTemplateForNode(nodeId: string | null | undefined): void {
    if (!nodeId) return;
    if (this.selectedTemplateId) return;
    const matching = this.templates.find(t => t.defaultNodeId === nodeId);
    if (matching) this.selectedTemplateId = matching.id!;
  }

  selectTemplate(template: Template): void {
    this.selectedTemplateId = template.id!;
    // Si pas de noeud pré-choisi par l'URL, on pré-remplit avec le defaultNodeId du template.
    if (!this.preselectedNodeId && template.defaultNodeId) {
      this.form.patchValue({ nodeId: template.defaultNodeId });
    }
  }

  get canSubmit(): boolean {
    return this.form.valid && !!this.selectedTemplateId;
  }

  get selectedTemplate(): Template | null {
    return this.templates.find(t => t.id === this.selectedTemplateId) ?? null;
  }

  submit(): void {
    if (!this.canSubmit) return;
    const raw = this.form.getRawValue();
    this.pageService.create({
      loreId: this.loreId,
      nodeId: raw.nodeId,
      templateId: this.selectedTemplateId!,
      title: raw.title
    }).subscribe({
      // Après la création classique, la coquille est vide → on redirige
      // vers l'écran d'édition pour que l'utilisateur remplisse les champs
      // dynamiques du template.
      next: created => this.router.navigate(['/lore', this.loreId, 'pages', created.id, 'edit']),
      error: () => console.error('Erreur lors de la création de la page')
    });
  }

  cancel(): void {
    this.router.navigate(['/lore', this.loreId]);
  }

  // --- Mode wizard IA (étape b6) -----------------------------------------

  openWizard(): void {
    if (!this.canSubmit) return;
    this.wizardError = null;
    this.lastWizardReply = null;
    this.chatOpen = true;
  }

  closeWizard(): void {
    this.chatOpen = false;
  }

  /** Mémorise la dernière réponse de l'assistant — on y cherchera le bloc <values>. */
  onWizardReply(reply: string): void {
    this.lastWizardReply = reply;
  }

  /**
   * Clic sur "Appliquer et créer la page" :
   * 1. Extraire le bloc JSON <values>...</values> de la dernière réponse.
   * 2. Créer la page avec titre + template + nodeId + values.
   * 3. Naviguer vers l'édition pour que l'utilisateur finalise.
   */
  applyWizardAndCreate(): void {
    if (!this.canSubmit || !this.lastWizardReply) {
      this.wizardError = "L'assistant n'a pas encore répondu. Décrivez d'abord votre idée.";
      return;
    }
    const values = this.extractValuesBlock(this.lastWizardReply);
    if (!values) {
      this.wizardError = "Impossible d'extraire les valeurs. Demandez à l'assistant de proposer à nouveau.";
      return;
    }
    this.wizardError = null;
    const raw = this.form.getRawValue();
    // Le backend POST /api/pages ne prend pas `values` — on crée d'abord la
    // coquille, puis on PUT immédiatement avec les valeurs extraites.
    // 2 roundtrips, mais zéro modification backend nécessaire.
    this.pageService.create({
      loreId: this.loreId,
      nodeId: raw.nodeId,
      templateId: this.selectedTemplateId!,
      title: raw.title
    }).subscribe({
      next: (created) => {
        const updated = { ...created, values };
        this.pageService.update(created.id!, updated).subscribe({
          next: () => this.router.navigate(['/lore', this.loreId, 'pages', created.id]),
          error: () => this.wizardError = 'Page créée, mais impossible d\'appliquer les valeurs.'
        });
      },
      error: () => this.wizardError = 'Erreur lors de la création de la page.'
    });
  }

  /** Prompt système injecté dans le backend pour le mode wizard. */
  get wizardSystemPrompt(): string | null {
    const tpl = this.selectedTemplate;
    if (!tpl || !this.canSubmit) return null;
    const title = this.form.value.title as string;
    // Seuls les champs TEXT sont proposes a l'IA : l'IA ne genere pas d'images.
    const textFields = (tpl.fields ?? []).filter(f => f.type === 'TEXT');
    const fieldsList = textFields.length ? textFields.map(f => `"${f.name}"`).join(', ') : '(aucun champ)';
    const exampleJson = textFields.length
      ? '{\n  ' + textFields.map(f => `"${f.name}": "valeur proposée"`).join(',\n  ') + '\n}'
      : '{}';

    return `MODE WIZARD — CRÉATION DE PAGE

L'utilisateur crée une nouvelle page intitulée "${title}" à partir du template "${tpl.name}".
Les champs à proposer sont : ${fieldsList}.

Règles de cohérence :
- Tu PEUX inventer des éléments originaux (personnages, lieux, objets, intrigues) — c'est ton rôle.
- Tu ne peux PAS faire référence à un élément comme s'il existait déjà dans l'univers, sauf s'il apparaît EXACTEMENT dans la carte du Lore fournie plus haut.
- Si l'utilisateur évoque un élément absent de la carte, suggère de le créer plutôt que d'inventer des détails fictifs à son sujet.

Format de réponse OBLIGATOIRE :
Après avoir dialogué (1-3 phrases), termine CHAQUE réponse par un bloc JSON entre balises <values>, sans rien ajouter après :

<values>
${exampleJson}
</values>

Les clés du JSON doivent correspondre EXACTEMENT aux noms de champs indiqués. Laisse "" si tu manques d'info pour un champ.`;
  }

  /** Welcome message contextualisé au template choisi. */
  get wizardWelcome(): string {
    const tpl = this.selectedTemplate;
    if (!tpl) return 'Décrivez ce que vous souhaitez créer.';
    return `Super, on va créer une page "${tpl.name}" ! Décrivez-la-moi en quelques mots — contexte, rôle, traits marquants — et je proposerai des valeurs pour chaque champ.`;
  }

  /**
   * Extrait le bloc <values>{...}</values> de la réponse assistant et parse en objet.
   * Retourne null si absent ou JSON invalide.
   */
  private extractValuesBlock(reply: string): Record<string, string> | null {
    const match = reply.match(/<values>\s*([\s\S]*?)\s*<\/values>/i);
    if (!match) return null;
    try {
      const parsed = JSON.parse(match[1]) as Record<string, unknown>;
      // On coerce toute valeur non-string en string (l'IA peut parfois produire des nombres).
      const result: Record<string, string> = {};
      for (const [k, v] of Object.entries(parsed)) {
        result[k] = v == null ? '' : String(v);
      }
      return result;
    } catch {
      return null;
    }
  }

  ngOnDestroy(): void {
    this.layoutService.hide();
  }
}
