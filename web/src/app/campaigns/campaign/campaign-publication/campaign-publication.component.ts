import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule, ArrowLeft, Save, FileText } from 'lucide-angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CampaignPublication, CampaignService, PdfExportOptions } from '../../../services/campaign.service';
import { CampaignSidebarService } from '../../../services/campaign-sidebar.service';
import { PageTitleService } from '../../../services/page-title.service';
import { ExpandableSectionComponent } from '../../../shared/expandable-section/expandable-section.component';
import { SingleImagePickerComponent } from '../../../shared/single-image-picker/single-image-picker.component';
import { WritingCoachTriggerComponent } from '../../../shared/writing-coach/writing-coach-trigger.component';
import { PdfExportDialogComponent } from '../pdf-export-dialog/pdf-export-dialog.component';
import { downloadBlob, safeFileName } from '../../../shared/download.util';

type PublicationField = Exclude<keyof CampaignPublication, 'coverImageId'>;

/**
 * Fiche « Publication » d'une campagne : ce que le PDF affiche quand l'aventure est
 * vendue ou partagée — couverture (sous-titre, auteur, illustration), page technique,
 * présentation, introduction pour le MJ, crédits et licence. Tout est facultatif ; une
 * fiche vide garde le livret de campagne habituel.
 * Route : /campaigns/:campaignId/publication
 */
@Component({
  selector: 'app-campaign-publication',
  imports: [FormsModule, LucideAngularModule, TranslatePipe, ExpandableSectionComponent,
    SingleImagePickerComponent, WritingCoachTriggerComponent, PdfExportDialogComponent],
  templateUrl: './campaign-publication.component.html',
  styleUrls: ['./campaign-publication.component.scss']
})
export class CampaignPublicationComponent implements OnInit {
  readonly ArrowLeft = ArrowLeft;
  readonly Save = Save;
  readonly FileText = FileText;

  campaignId = '';
  campaignName = '';
  pub: CampaignPublication = {};
  /** Dernier état enregistré (JSON) : détecte les modifications non sauvegardées. */
  private savedSnapshot = '{}';

  loading = true;
  saving = false;
  savedFlash = false;
  errorMessage = '';

  pdfDialogOpen = false;
  exportingPdf = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private campaignService: CampaignService,
    private campaignSidebar: CampaignSidebarService,
    private pageTitle: PageTitleService,
    private translate: TranslateService,
  ) {}

  ngOnInit(): void {
    this.campaignId = this.route.snapshot.paramMap.get('campaignId') ?? '';
    if (!this.campaignId) return;
    this.campaignSidebar.show(this.campaignId);
    forkJoin({
      campaign: this.campaignService.getCampaignById(this.campaignId),
      publication: this.campaignService.getPublication(this.campaignId),
    }).subscribe({
      next: ({ campaign, publication }) => {
        this.campaignName = campaign.name;
        this.pageTitle.set(this.translate.instant('publication.pageTitle', { name: campaign.name }));
        this.pub = { ...publication };
        this.savedSnapshot = this.snapshot();
        this.loading = false;
      },
      error: () => {
        this.errorMessage = this.translate.instant('publication.loadError');
        this.loading = false;
      }
    });
  }

  get dirty(): boolean { return this.snapshot() !== this.savedSnapshot; }

  /** Au moins un champ renseigné (le PDF passe alors en présentation « module »). */
  get filled(): boolean {
    return Object.values(this.pub).some(v => typeof v === 'string' && v.trim().length > 0);
  }

  filledAny(...fields: (keyof CampaignPublication)[]): boolean {
    return fields.some(f => !!this.pub[f]?.trim());
  }

  set(field: PublicationField, value: string): void {
    this.pub = { ...this.pub, [field]: value };
  }

  setCover(id: string | null): void {
    this.pub = { ...this.pub, coverImageId: id };
  }

  back(): void {
    this.router.navigate(['/campaigns', this.campaignId]);
  }

  save(): void {
    this.persist().subscribe();
  }

  /** Enregistre si besoin, puis émet la fiche à jour (ou rien en cas d'erreur). */
  private persist(): Observable<CampaignPublication | null> {
    if (!this.dirty) return of(this.pub);
    this.saving = true;
    this.errorMessage = '';
    return new Observable(observer => {
      this.campaignService.updatePublication(this.campaignId, this.pub).subscribe({
        next: saved => {
          this.pub = { ...saved };
          this.savedSnapshot = this.snapshot();
          this.saving = false;
          this.savedFlash = true;
          setTimeout(() => this.savedFlash = false, 1800);
          observer.next(saved);
          observer.complete();
        },
        error: () => {
          this.saving = false;
          this.errorMessage = this.translate.instant('publication.saveError');
          observer.next(null);
          observer.complete();
        }
      });
    });
  }

  /** Export PDF : la fiche est d'abord enregistrée pour que le livret reflète l'écran. */
  onPdfConfirmed(opts: PdfExportOptions): void {
    if (this.exportingPdf) return;
    this.exportingPdf = true;
    this.persist().pipe(
      switchMap(saved => saved ? this.campaignService.exportPdf(this.campaignId, opts) : of(null))
    ).subscribe({
      next: blob => {
        if (blob) {
          const suffix = opts.edition === 'player' ? '_joueurs' : '';
          downloadBlob(blob, `${safeFileName(this.campaignName, 'campagne')}${suffix}.pdf`);
          this.pdfDialogOpen = false;
        }
        this.exportingPdf = false;
      },
      error: () => {
        this.errorMessage = this.translate.instant('publication.exportError');
        this.exportingPdf = false;
      }
    });
  }

  private snapshot(): string {
    const clean: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.pub)) {
      if (typeof v === 'string' && v.trim()) clean[k] = v.trim();
    }
    return JSON.stringify(clean, Object.keys(clean).sort());
  }
}
