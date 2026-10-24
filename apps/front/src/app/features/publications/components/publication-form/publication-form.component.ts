import {
  ChangeDetectionStrategy,
  Component,
  input,
  OnInit,
  output,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { CreatePublicationFormValue } from "../../publication.model";

@Component({
  selector: 'app-publication-form',
  imports: [
    ReactiveFormsModule,
    LoaderComponent,
  ],
  templateUrl: './publication-form.component.html',
  styleUrl: './publication-form.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationFormComponent implements OnInit {
  /** Vrai tant que l'envoi est en cours : le formulaire attend la réponse. */
  readonly submitting = input(false);

  /**
   * Valeur de départ en modification. Lue une seule fois, à l'ouverture : la
   * modale est recréée pour chaque publication modifiée.
   */
  readonly initialValue = input<CreatePublicationFormValue | null>(null);

  readonly submitted = output<CreatePublicationFormValue>();
  readonly cancelled = output<void>();

  readonly form = new FormGroup({
    content: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(2000)],
    }),
    status: new FormControl(true, {
      nonNullable: true,
    }),
  });

  /** En modification, le formulaire garde la saisie : la modale se ferme sur le succès. */
  protected get editing(): boolean {
    return this.initialValue() !== null;
  }

  ngOnInit(): void {
    const initial = this.initialValue();

    if (initial) {
      this.form.setValue(initial);
    }
  }

  onSubmit(): void {
    // Un double envoi créerait deux publications : on ignore le second.
    if (this.submitting()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitted.emit(this.form.getRawValue());

    if (!this.editing) {
      this.form.reset({ content: '', status: true });
    }
  }

}
