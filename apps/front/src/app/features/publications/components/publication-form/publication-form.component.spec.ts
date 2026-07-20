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

    expect(form.invalid).toBeTrue();
    expect(form.controls.status.value).toBeTrue();
  });

  it('should not emit and should mark controls as touched on an invalid submit', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const component = fixture.componentInstance;

    let emitted: CreatePublicationFormValue | undefined;
    component.submitted.subscribe((value) => (emitted = value));

    component.onSubmit();

    expect(emitted).toBeUndefined();
    expect(component.form.controls.content.touched).toBeTrue();
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
    expect(component.form.controls.status.value).toBeTrue();
  });

  it('should reject content longer than 2000 characters', () => {
    const fixture = TestBed.createComponent(PublicationFormComponent);
    const component = fixture.componentInstance;

    component.form.controls.content.setValue('x'.repeat(2001));
    expect(component.form.controls.content.invalid).toBeTrue();
  });
});
