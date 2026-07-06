import { Component, Input, OnInit } from '@angular/core';

import { LucideAngularModule, ChevronDown, ChevronUp } from 'lucide-angular';

/**
 * Section repliable avec titre (icône + texte) et contenu projeté via ng-content.
 * Utilisé dans les écrans d'édition de Scene pour structurer les champs narratifs.
 *
 * Usage :
 *   <app-expandable-section title="Contexte et ambiance" icon="📍" [initiallyOpen]="true">
 *     <!-- champs de formulaire -->
 *   </app-expandable-section>
 */
@Component({
    selector: 'app-expandable-section',
    imports: [LucideAngularModule],
    templateUrl: './expandable-section.component.html',
    styleUrls: ['./expandable-section.component.scss']
})
export class ExpandableSectionComponent implements OnInit {
  readonly ChevronDown = ChevronDown;
  readonly ChevronUp = ChevronUp;

  @Input() title = '';
  @Input() icon = '';                      // Emoji ou caractère unicode (ex: '📍', '📖')
  @Input() initiallyOpen = false;
  @Input() variant: 'default' | 'private' = 'default';  // 'private' = notes MJ (couleur différente)
  /**
   * Pastille « contient du contenu » dans l'en-tête (replié comme déplié).
   * Permet de voir d'un coup d'œil quelles sections optionnelles sont remplies
   * sans avoir à les ouvrir une à une. Le parent calcule l'état.
   */
  @Input() filled = false;

  isOpen = false;

  ngOnInit(): void {
    this.isOpen = this.initiallyOpen;
  }

  toggle(): void {
    this.isOpen = !this.isOpen;
  }
}
