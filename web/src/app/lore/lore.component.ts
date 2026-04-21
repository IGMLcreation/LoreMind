import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, BookOpen, Folder, Plus } from 'lucide-angular';
import { LoreService } from '../services/lore.service';
import { Lore } from '../services/lore.model';
import { LoreCreateComponent } from './lore-create/lore-create.component';

@Component({
  selector: 'app-lore',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, LoreCreateComponent],
  templateUrl: './lore.component.html',
  styleUrls: ['./lore.component.scss']
})
export class LoreComponent implements OnInit {
  lores: Lore[] = [];
  loading = true;
  error = false;

  readonly BookOpen = BookOpen;
  readonly Folder = Folder;
  readonly Plus = Plus;

  showCreateModal = false;

  constructor(
    private loreService: LoreService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadLores();
  }

  loadLores(): void {
    this.loreService.getAllLores().subscribe({
      next: (data) => {
        this.lores = data;
        this.loading = false;
      },
      error: () => {
        this.error = true;
        this.loading = false;
      }
    });
  }

  openCreateModal(): void {
    this.showCreateModal = true;
  }

  onModalClose(): void {
    this.showCreateModal = false;
  }

  onLoreCreated(data: { name: string; description: string }): void {
    this.loreService.createLore(data).subscribe({
      next: () => {
        this.showCreateModal = false;
        this.loadLores();
      },
      error: () => {
        console.error('Erreur lors de la création du Lore');
      }
    });
  }

  navigateToDetail(id: string): void {
    this.router.navigate(['/lore', id]);
  }
}
