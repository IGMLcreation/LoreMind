import { Component, EventEmitter, OnInit, Output } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, BookCopy, X, Plus, Check } from 'lucide-angular';
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
    imports: [ReactiveFormsModule, FormsModule, LucideAngularModule],
    templateUrl: './campaign-create.component.html',
    styleUrls: ['./campaign-create.component.scss']
})
export class CampaignCreateComponent implements OnInit {
  @Output() close = new EventEmitter<void>();
  @Output() created = new EventEmitter<CampaignCreatePayload>();

  readonly BookCopy = BookCopy;
  readonly X = X;
  readonly Plus = Plus;
  readonly Check = Check;

  /** Valeur sentinelle de l'option "Creer un systeme" dans le <select>. */
  readonly CREATE_GAMESYSTEM_SENTINEL = '__create__';

  form: FormGroup;
  /** Lores disponibles pour association. Chargés à l'ouverture de la modal. */
  availableLores: Lore[] = [];
  /** GameSystems disponibles pour association. */
  availableGameSystems: GameSystem[] = [];

  /** Mode creation inline d'un GameSystem depuis le dropdown. */
  creatingGameSystem = false;
  newGameSystemName = '';
  creatingGameSystemInFlight = false;

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

    // Detecte la selection de l'option sentinelle "Creer un systeme" et bascule
    // en mode creation inline. On reinitialise immediatement le control a ''
    // pour que la sentinelle ne reste pas en valeur reelle du form.
    this.form.get('gameSystemId')?.valueChanges.subscribe(value => {
      if (value === this.CREATE_GAMESYSTEM_SENTINEL) {
        this.form.get('gameSystemId')?.setValue('', { emitEvent: false });
        this.startCreateGameSystem();
      }
    });
  }

  startCreateGameSystem(): void {
    this.creatingGameSystem = true;
    this.newGameSystemName = '';
  }

  cancelCreateGameSystem(): void {
    this.creatingGameSystem = false;
    this.newGameSystemName = '';
  }

  submitCreateGameSystem(): void {
    const name = this.newGameSystemName.trim();
    if (!name || this.creatingGameSystemInFlight) return;
    this.creatingGameSystemInFlight = true;
    this.gameSystemService.create({ name, isPublic: false }).subscribe({
      next: (created) => {
        this.creatingGameSystemInFlight = false;
        this.availableGameSystems = [...this.availableGameSystems, created];
        if (created.id) {
          this.form.get('gameSystemId')?.setValue(created.id);
        }
        this.creatingGameSystem = false;
        this.newGameSystemName = '';
      },
      error: () => {
        this.creatingGameSystemInFlight = false;
        console.error('Erreur lors de la creation du systeme de jeu');
      }
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
