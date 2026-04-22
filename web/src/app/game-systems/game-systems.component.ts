import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, Dices, Plus, Pencil, Trash2 } from 'lucide-angular';
import { GameSystemService } from '../services/game-system.service';
import { GameSystem } from '../services/game-system.model';

@Component({
  selector: 'app-game-systems',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './game-systems.component.html',
  styleUrls: ['./game-systems.component.scss']
})
export class GameSystemsComponent implements OnInit {
  readonly Dices = Dices;
  readonly Plus = Plus;
  readonly Pencil = Pencil;
  readonly Trash2 = Trash2;

  gameSystems: GameSystem[] = [];

  constructor(
    private router: Router,
    private gameSystemService: GameSystemService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.gameSystemService.getAll().subscribe({
      next: (data) => this.gameSystems = data,
      error: () => this.gameSystems = []
    });
  }

  create(): void {
    this.router.navigate(['/game-systems/create']);
  }

  edit(id: string): void {
    this.router.navigate(['/game-systems', id, 'edit']);
  }

  delete(system: GameSystem, event: MouseEvent): void {
    event.stopPropagation();
    if (!system.id) return;
    if (!confirm(`Supprimer le système "${system.name}" ? Les campagnes qui l'utilisent ne seront plus associées à aucun système.`)) return;
    this.gameSystemService.delete(system.id).subscribe({
      next: () => this.load(),
      error: () => console.error('Erreur suppression GameSystem')
    });
  }
}
