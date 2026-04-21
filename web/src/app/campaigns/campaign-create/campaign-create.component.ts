import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LucideAngularModule, BookCopy, X } from 'lucide-angular';
import { LoreService } from '../../services/lore.service';
import { Lore } from '../../services/lore.model';

/**
 * Payload émis vers le parent à la création d'une campagne.
 * `loreId` est optionnel (null = campagne sans univers associé).
 */
export interface CampaignCreatePayload {
  name: string;
  description: string;
  playerCount: number;
  loreId: string | null;
}

@Component({
  selector: 'app-campaign-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './campaign-create.component.html',
  styleUrls: ['./campaign-create.component.scss']
})
export class CampaignCreateComponent implements OnInit {
  @Output() close = new EventEmitter<void>();
  @Output() created = new EventEmitter<CampaignCreatePayload>();

  readonly BookCopy = BookCopy;
  readonly X = X;

  form: FormGroup;
  /** Lores disponibles pour association. Chargés à l'ouverture de la modal. */
  availableLores: Lore[] = [];

  constructor(private fb: FormBuilder, private loreService: LoreService) {
    this.form = this.fb.group({
      name:        ['', Validators.required],
      description: [''],
      playerCount: [4, [Validators.required, Validators.min(1)]],
      // Valeur par défaut : chaîne vide = "— Aucun lore associé —".
      // Le service normalise ensuite ""/null en null côté backend.
      loreId:      ['']
    });
  }

  ngOnInit(): void {
    this.loreService.getAllLores().subscribe({
      next: (lores) => this.availableLores = lores,
      error: () => this.availableLores = []
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.value;
    this.created.emit({
      name: raw.name,
      description: raw.description,
      playerCount: raw.playerCount,
      loreId: raw.loreId ? raw.loreId : null
    });
  }

  onCancel(): void {
    this.close.emit();
  }
}
