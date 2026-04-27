import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LucideAngularModule, BookCopy, X } from 'lucide-angular';
import { LoreService } from '../../../services/lore.service';
import { Lore } from '../../../services/lore.model';
import { GameSystemService } from '../../../services/game-system.service';
import { GameSystem } from '../../../services/game-system.model';

/**
 * Payload émis vers le parent à la création d'une campagne.
 * `loreId` et `gameSystemId` sont optionnels (null = non associé).
 */
export interface CampaignCreatePayload {
  name: string;
  description: string;
  playerCount: number;
  loreId: string | null;
  gameSystemId: string | null;
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
  /** GameSystems disponibles pour association. */
  availableGameSystems: GameSystem[] = [];

  constructor(
    private fb: FormBuilder,
    private loreService: LoreService,
    private gameSystemService: GameSystemService
  ) {
    this.form = this.fb.group({
      name:         ['', Validators.required],
      description:  [''],
      playerCount:  [4, [Validators.required, Validators.min(1)]],
      loreId:       [''],
      gameSystemId: ['']
    });
  }

  ngOnInit(): void {
    this.loreService.getAllLores().subscribe({
      next: (lores) => this.availableLores = lores,
      error: () => this.availableLores = []
    });
    this.gameSystemService.getAll().subscribe({
      next: (gs) => this.availableGameSystems = gs,
      error: () => this.availableGameSystems = []
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.value;
    this.created.emit({
      name: raw.name,
      description: raw.description,
      playerCount: raw.playerCount,
      loreId: raw.loreId ? raw.loreId : null,
      gameSystemId: raw.gameSystemId ? raw.gameSystemId : null
    });
  }

  onCancel(): void {
    this.close.emit();
  }
}
