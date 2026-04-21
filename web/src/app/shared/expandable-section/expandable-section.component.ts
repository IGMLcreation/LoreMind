import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './expandable-section.component.html',
  styleUrls: ['./expandable-section.component.scss']
})
export class ExpandableSectionComponent {
  readonly ChevronDown = ChevronDown;
  readonly ChevronUp = ChevronUp;

  @Input() title = '';
  @Input() icon = '';                      // Emoji ou caractère unicode (ex: '📍', '📖')
  @Input() initiallyOpen = false;
  @Input() variant: 'default' | 'private' = 'default';  // 'private' = notes MJ (couleur différente)

  isOpen = false;

  ngOnInit(): void {
    this.isOpen = this.initiallyOpen;
  }

  toggle(): void {
    this.isOpen = !this.isOpen;
  }
}
