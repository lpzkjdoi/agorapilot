import { TestBed } from '@angular/core/testing';
import { CreatePublicationFormValue } from '../../publication.model';
import { PublicationFormComponent } from './publication-form.component';

describe('PublicationFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PublicationFormComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start invalid with an empty content and a checked status', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const form = fixture.componentInstance.form;

    expect(form.invalid).toBe(true);
    expect(form.controls.status.value).toBe(true);
  });

  it('should not emit and should mark controls as touched on an invalid submit', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const component = fixture.componentInstance;

    let emitted: CreatePublicationFormValue | undefined;
    component.submitted.subscribe((value) => (emitted = value));

    component.onSubmit();

    expect(emitted).toBeUndefined();
    expect(component.form.controls.content.touched).toBe(true);
  });

  it('should emit the form value and reset on a valid submit', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const component = fixture.componentInstance;

    let emitted: CreatePublicationFormValue | undefined;
    component.submitted.subscribe((value) => (emitted = value));

    component.form.setValue({ content: 'Nouvelle annonce', status: false });
    component.onSubmit();

    expect(emitted).toEqual({ content: 'Nouvelle annonce', status: false });
    // The form is reset to its defaults after a successful submit.
    expect(component.form.controls.content.value).toBe('');
    expect(component.form.controls.status.value).toBe(true);
  });

  it('should emit `cancelled` from the cancel button without touching the form', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    fixture.detectChanges();

    const cancelled = vi.fn();
    fixture.componentInstance.cancelled.subscribe(cancelled);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.publication-form-cancel')
      ?.click();

    expect(cancelled).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.form.controls.content.touched).toBe(false);
  });

  it('should show the character counter and the error message once touched', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.publication-form-counter')?.textContent).toContain('0 / 2000');
    expect(compiled.querySelector('.publication-form-error')).toBeNull();

    component.onSubmit();
    fixture.detectChanges();

    expect(compiled.querySelector('.publication-form-error')?.textContent).toContain(
      'Le contenu est obligatoire',
    );
  });

  it('should reject content longer than 2000 characters', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const component = fixture.componentInstance;

    component.form.controls.content.setValue('x'.repeat(2001));
    expect(component.form.controls.content.invalid).toBe(true);
  });
});
