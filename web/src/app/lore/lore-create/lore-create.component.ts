import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LucideAngularModule, BookCopy, X } from 'lucide-angular';

@Component({
  selector: 'app-lore-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './lore-create.component.html',
  styleUrls: ['./lore-create.component.scss']
})
export class LoreCreateComponent {
  @Output() close = new EventEmitter<void>();
  @Output() created = new EventEmitter<{ name: string; description: string }>();

  readonly BookCopy = BookCopy;
  readonly X = X;

  form: FormGroup;

  constructor(private fb: FormBuilder) {
    this.form = this.fb.group({
      name: ['', Validators.required],
      description: ['']
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.created.emit(this.form.value);
  }

  onCancel(): void {
    this.close.emit();
  }
}
