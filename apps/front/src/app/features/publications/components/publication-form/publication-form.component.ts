import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
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
export class PublicationFormComponent {
  /** Vrai tant que la création est en cours : le formulaire attend la réponse. */
  readonly submitting = input(false);

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
    this.form.reset({ content: '', status: true });
  }

}
