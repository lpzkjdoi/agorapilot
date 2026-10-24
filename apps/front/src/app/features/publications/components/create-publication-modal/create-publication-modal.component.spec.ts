import { TestBed } from '@angular/core/testing';
import { CreatePublicationFormValue, Publication } from '../../publication.model';
import { CreatePublicationModalComponent } from './create-publication-modal.component';

describe('CreatePublicationModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreatePublicationModalComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the dialog title and the publication form', () => {
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const dialog = compiled.querySelector('[role="dialog"]');
    expect(dialog?.getAttribute('aria-modal')).toBe('true');
    expect(compiled.querySelector('#create-publication-title')?.textContent).toContain(
      'Créer une publication',
    );
    expect(compiled.querySelector('app-publication-form')).not.toBeNull();
  });

  it('should emit `closed` on the backdrop, the close button and the form cancellation', () => {
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    compiled.querySelector<HTMLButtonElement>('.modal-backdrop')?.click();
    compiled.querySelector<HTMLButtonElement>('.modal-close')?.click();
    compiled.querySelector<HTMLButtonElement>('.publication-form-cancel')?.click();

    expect(closed).toHaveBeenCalledTimes(3);
  });

  it('should emit `closed` when Escape is pressed', () => {
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    fixture.detectChanges();

    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('should hand `creating` down to the form as its submitting state', () => {
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.publication-form-submit app-loader')).toBeNull();

    fixture.componentRef.setInput('creating', true);
    fixture.detectChanges();

    expect(compiled.querySelector('.publication-form-submit app-loader')).not.toBeNull();
  });

  it('should forward the form value on submit', () => {
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    let emitted: CreatePublicationFormValue | undefined;
    fixture.componentInstance.submitted.subscribe((value) => (emitted = value));

    const textarea = compiled.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Marché de producteurs samedi';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    compiled.querySelector('form')?.dispatchEvent(new Event('submit'));

    expect(emitted).toEqual({ content: 'Marché de producteurs samedi', status: true });
  });

  it('should switch to edition when a publication is given, prefilled', () => {
    const publication: Publication = {
      id: 4, content: 'Fermeture de la mairie', status: 'VERIFIED', medias: [], campaign: null,
    };
    const fixture = TestBed.createComponent(CreatePublicationModalComponent);
    fixture.componentRef.setInput('publication', publication);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('#create-publication-title')?.textContent).toContain('Modifier la publication');
    expect(compiled.querySelector('.modal-subtitle')?.textContent).toContain('déjà publiées ne changent pas');
    expect((compiled.querySelector('textarea') as HTMLTextAreaElement).value).toBe('Fermeture de la mairie');
    expect((compiled.querySelector('#status') as HTMLInputElement).checked).toBe(true);
    expect(compiled.querySelector('.publication-form-submit')?.textContent).toContain('Enregistrer');
  });

  describe('sans le droit de vérifier', () => {
    function render(publication: Publication | null) {
      const fixture = TestBed.createComponent(CreatePublicationModalComponent);
      fixture.componentRef.setInput('publication', publication);
      fixture.componentRef.setInput('canVerify', false);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    it('should announce a draft and hide the « verified » box on creation', () => {
      const compiled = render(null);

      expect(compiled.querySelector('.modal-subtitle')?.textContent).toContain('enregistrée en brouillon');
      expect(compiled.querySelector('#status')).toBeNull();
    });

    it('should warn that editing a verified publication sends it back to draft', () => {
      const compiled = render({ id: 4, content: 'Fermeture de la mairie', status: 'VERIFIED', medias: [], campaign: null });

      expect(compiled.querySelector('[role="note"]')?.textContent).toContain('repassera en brouillon');
    });

    it('should not warn about a draft', () => {
      const compiled = render({ id: 4, content: 'Fermeture de la mairie', status: 'DRAFT', medias: [], campaign: null });

      expect(compiled.querySelector('[role="note"]')).toBeNull();
    });
  });
});
