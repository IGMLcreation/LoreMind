import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowLeft, ArrowRight, Sparkles } from 'lucide-angular';
import { TranslatePipe } from '@ngx-translate/core';
import { GUIDED_METHODS } from '../guided-methods';
import { GuidedMethod } from '../guided-method.model';

/**
 * Choix d'une méthode guidée (Mentor d'écriture) : une carte par méthode, avec son
 * accroche et le nombre de scènes qu'elle crée. Route : /campaigns/:campaignId/guided
 */
@Component({
  selector: 'app-guided-chooser',
  imports: [RouterLink, TranslatePipe, LucideAngularModule],
  templateUrl: './guided-chooser.component.html',
  styleUrls: ['./guided-chooser.component.scss']
})
export class GuidedChooserComponent {
  readonly ArrowLeft = ArrowLeft;
  readonly ArrowRight = ArrowRight;
  readonly Sparkles = Sparkles;
  readonly methods = GUIDED_METHODS;
  readonly campaignId = inject(ActivatedRoute).snapshot.paramMap.get('campaignId') ?? '';

  /** Nombre de scènes produites (brouillon vide : la structure ne dépend pas des réponses). */
  sceneCount(method: GuidedMethod): number {
    return method.toDraft({}).scenes.length;
  }
}
