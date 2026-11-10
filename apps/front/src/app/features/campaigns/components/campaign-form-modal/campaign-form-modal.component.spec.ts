import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Campaign, CampaignFormValue } from '../../campaign.model';
import { CampaignFormModalComponent } from './campaign-form-modal.component';

describe('CampaignFormModalComponent', () => {
  let fixture: ComponentFixture<CampaignFormModalComponent>;
  let component: CampaignFormModalComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CampaignFormModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CampaignFormModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should submit the filled form', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.form.setValue({
      name: 'Marché de Noël',
      description: 'Toutes les annonces du marché',
      startDate: '2026-12-01',
      endDate: '2026-12-24',
    });
    component.onSubmit();

    expect(submitted).toHaveBeenCalledWith({
      name: 'Marché de Noël',
      description: 'Toutes les annonces du marché',
      startDate: '2026-12-01',
      endDate: '2026-12-24',
    } satisfies CampaignFormValue);
  });

  it('should refuse a campaign without a name', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.onSubmit();

    expect(submitted).not.toHaveBeenCalled();
    expect(component.form.controls.name.touched).toBe(true);
  });

  it('should show the name error once the field has been touched', () => {
    component.onSubmit();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.campaign-form-error')?.textContent)
      .toContain('Le nom est obligatoire');
  });

  it('should ignore a second submit while the first one is in flight', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);
    component.form.controls.name.setValue('Marché de Noël');
    fixture.componentRef.setInput('saving', true);
    fixture.detectChanges();

    component.onSubmit();

    expect(submitted).not.toHaveBeenCalled();
  });

  /** Le back contrôle les dates à la journée : une campagne peut démarrer aujourd'hui. */
  it('should offer today but no earlier date', () => {
    const start = (fixture.nativeElement as HTMLElement)
      .querySelector('#campaign-start') as HTMLInputElement;

    const today = new Date();
    const expected = [
      today.getFullYear(),
      `${ today.getMonth() + 1 }`.padStart(2, '0'),
      `${ today.getDate() }`.padStart(2, '0'),
    ].join('-');

    // Construit dans le fuseau du navigateur : `toISOString()` renverrait la
    // veille entre minuit et l'aube, heure française.
    expect(start.min).toBe(expected);
  });

  it('should close on Escape and on the backdrop', () => {
    const closed = vi.fn();
    component.closed.subscribe(closed);

    component.onEscape();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.modal-backdrop')!
                                          .click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
  it('should refuse an end date before the start date', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);
    component.form.setValue({ name: 'À l’envers', description: '', startDate: '2026-12-10', endDate: '2026-12-01' });

    component.onSubmit();
    fixture.detectChanges();

    expect(submitted).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('La date de fin doit suivre la date de début');
  });

  it('should offer the creation title and button without a campaign', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h2')?.textContent).toContain('Créer une campagne');
    expect(element.querySelector('button[type="submit"]')?.textContent).toContain('Créer la campagne');
  });

  describe('editing a campaign', () => {
    const campaign: Campaign = {
      id: 3,
      name: 'Marché de Noël',
      description: 'Toutes les annonces du marché',
      startDate: '2026-01-05T00:00:00',
      endDate: null,
      status: 'ACTIVE',
      publications: [],
    };

    beforeEach(() => {
      fixture = TestBed.createComponent(CampaignFormModalComponent);
      component = fixture.componentInstance;
      fixture.componentRef.setInput('campaign', campaign);
      fixture.detectChanges();
    });

    it('should prefill the form with the campaign', () => {
      expect(component.form.getRawValue()).toEqual({
        name: 'Marché de Noël',
        description: 'Toutes les annonces du marché',
        startDate: '2026-01-05',
        endDate: '',
      } satisfies CampaignFormValue);
    });

    it('should show the edition title and button', () => {
      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector('h2')?.textContent).toContain('Modifier la campagne');
      expect(element.querySelector('button[type="submit"]')?.textContent).toContain('Enregistrer');
    });

    /** Une campagne en cours garde sa date de début passée. */
    it('should not bound the start date', () => {
      const start = (fixture.nativeElement as HTMLElement)
        .querySelector('#campaign-start') as HTMLInputElement;

      expect(start.min).toBe('');
    });

    it('should submit the edited values', () => {
      const submitted = vi.fn();
      component.submitted.subscribe(submitted);
      component.form.controls.name.setValue('Marché de Noël 2026');

      component.onSubmit();

      expect(submitted).toHaveBeenCalledWith(expect.objectContaining({ name: 'Marché de Noël 2026', startDate: '2026-01-05' }));
    });
  });
});
