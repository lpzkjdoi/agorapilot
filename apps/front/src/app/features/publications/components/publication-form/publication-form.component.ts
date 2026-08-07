import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { CreatePublicationFormValue } from "../../publication.model";

@Component({
  selector: 'app-publication-form',
  imports: [
    ReactiveFormsModule,
  ],
  templateUrl: './publication-form.component.html',
  styleUrl: './publication-form.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicationFormComponent {
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
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitted.emit(this.form.getRawValue());
    this.form.reset({ content: '', status: true });
  }

}
