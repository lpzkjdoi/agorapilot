import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CreateCampaignFormValue } from '../../campaign.model';
import { CreateCampaignModalComponent } from './create-campaign-modal.component';

describe('CreateCampaignModalComponent', () => {
  let fixture: ComponentFixture<CreateCampaignModalComponent>;
  let component: CreateCampaignModalComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateCampaignModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateCampaignModalComponent);
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
    } satisfies CreateCampaignFormValue);
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
    fixture.componentRef.setInput('creating', true);
    fixture.detectChanges();

    component.onSubmit();

    expect(submitted).not.toHaveBeenCalled();
  });

  /**
   * Le back valide `startDate` en `@FutureOrPresent` sur un instant : une date
   * du jour envoyée à minuit serait refusée.
   */
  it('should not offer any date before tomorrow', () => {
    const start = (fixture.nativeElement as HTMLElement)
      .querySelector('#campaign-start') as HTMLInputElement;

    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const expected = [
      tomorrow.getFullYear(),
      `${ tomorrow.getMonth() + 1 }`.padStart(2, '0'),
      `${ tomorrow.getDate() }`.padStart(2, '0'),
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
});
