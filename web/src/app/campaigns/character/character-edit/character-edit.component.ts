import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, User, Trash2, Sparkles } from 'lucide-angular';
import { CharacterService } from '../../../services/character.service';
import { Character } from '../../../services/character.model';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';

/**
 * Éditeur plein écran d'une fiche de personnage (PJ).
 * Double rôle création/édition :
 *  - `/campaigns/:campaignId/characters/create` → POST
 *  - `/campaigns/:campaignId/characters/:characterId/edit` → PUT
 *
 * MVP : name + markdown libre. Évolution prévue vers un template dérivé
 * du GameSystem de la campagne (stats structurées).
 */
@Component({
  selector: 'app-character-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, AiChatDrawerComponent],
  templateUrl: './character-edit.component.html',
  styleUrls: ['./character-edit.component.scss']
})
export class CharacterEditComponent implements OnInit {
  readonly Save = Save;
  readonly ArrowLeft = ArrowLeft;
  readonly User = User;
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  /** État drawer chat IA focalisé sur ce PJ. */
  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose une backstory cohérente avec l\'univers',
    'Suggère 3 objectifs personnels pour ce personnage',
    'Aide-moi à équilibrer les stats de combat'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  campaignId: string | null = null;
  characterId: string | null = null;

  name = '';
  markdownContent = '';
  private order = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: CharacterService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.characterId = params.get('characterId');

    if (this.characterId) {
      this.service.getById(this.characterId).subscribe({
        next: (c) => {
          this.name = c.name;
          this.markdownContent = c.markdownContent ?? '';
          this.order = c.order ?? 0;
        },
        error: () => this.back()
      });
    }
  }

  submit(): void {
    if (!this.name.trim() || !this.campaignId) return;
    const req = this.characterId
      ? this.service.update(this.characterId, {
          id: this.characterId,
          name: this.name.trim(),
          markdownContent: this.markdownContent || null,
          campaignId: this.campaignId,
          order: this.order
        })
      : this.service.create({
          name: this.name.trim(),
          markdownContent: this.markdownContent || null,
          campaignId: this.campaignId
        });
    req.subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur sauvegarde Character')
    });
  }

  deleteCharacter(): void {
    if (!this.characterId) return;
    if (!confirm(`Supprimer la fiche de "${this.name}" ? Cette action est irréversible.`)) return;
    this.service.delete(this.characterId).subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur suppression Character')
    });
  }

  back(): void {
    if (this.campaignId) {
      this.router.navigate(['/campaigns', this.campaignId]);
    } else {
      this.router.navigate(['/campaigns']);
    }
  }
}
