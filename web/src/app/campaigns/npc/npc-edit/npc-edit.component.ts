import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Save, ArrowLeft, Drama, Trash2, Sparkles } from 'lucide-angular';
import { NpcService } from '../../../services/npc.service';
import { AiChatDrawerComponent } from '../../../shared/ai-chat-drawer/ai-chat-drawer.component';

/**
 * Éditeur plein écran d'une fiche de PNJ.
 * Double rôle création/édition :
 *  - `/campaigns/:campaignId/npcs/create` → POST
 *  - `/campaigns/:campaignId/npcs/:npcId/edit` → PUT
 *
 * MVP : name + markdown libre. L'Assistant IA est branché en mode édition
 * (focus entityType="npc") pour proposer apparence, motivations, secrets...
 */
@Component({
  selector: 'app-npc-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, AiChatDrawerComponent],
  templateUrl: './npc-edit.component.html',
  styleUrls: ['./npc-edit.component.scss']
})
export class NpcEditComponent implements OnInit {
  readonly Save = Save;
  readonly ArrowLeft = ArrowLeft;
  readonly Drama = Drama;
  readonly Trash2 = Trash2;
  readonly Sparkles = Sparkles;

  /** État drawer chat IA focalisé sur ce PNJ. */
  chatOpen = false;
  readonly chatQuickSuggestions = [
    'Propose une apparence et une posture marquantes',
    'Suggère 2 motivations et un secret pour ce PNJ',
    'Imagine 3 répliques signatures qui le caractérisent'
  ];

  toggleChat(): void { this.chatOpen = !this.chatOpen; }

  campaignId: string | null = null;
  npcId: string | null = null;

  name = '';
  markdownContent = '';
  private order = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: NpcService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    this.campaignId = params.get('campaignId');
    this.npcId = params.get('npcId');

    if (this.npcId) {
      this.service.getById(this.npcId).subscribe({
        next: (n) => {
          this.name = n.name;
          this.markdownContent = n.markdownContent ?? '';
          this.order = n.order ?? 0;
        },
        error: () => this.back()
      });
    }
  }

  submit(): void {
    if (!this.name.trim() || !this.campaignId) return;
    const req = this.npcId
      ? this.service.update(this.npcId, {
          id: this.npcId,
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
      error: () => console.error('Erreur sauvegarde Npc')
    });
  }

  deleteNpc(): void {
    if (!this.npcId) return;
    if (!confirm(`Supprimer la fiche de "${this.name}" ? Cette action est irréversible.`)) return;
    this.service.delete(this.npcId).subscribe({
      next: () => this.back(),
      error: () => console.error('Erreur suppression Npc')
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
