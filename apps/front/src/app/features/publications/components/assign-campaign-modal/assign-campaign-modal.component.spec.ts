import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Campaign } from '../../../campaigns/campaign.model';
import { Publication } from '../../publication.model';
import { AssignCampaignModalComponent } from './assign-campaign-modal.component';

function campaign(id: number, name: string): Campaign {
  return {
    id,
    name,
    description: '',
    startDate: null,
    endDate: null,
    status: 'DRAFT',
    publications: [],
  };
}

function publication(campaign: Publication['campaign'] = null): Publication {
  return {
    id: 7,
    content: 'Marché de producteurs samedi',
    status: 'VERIFIED',
    medias: [],
    campaign,
  };
}

const campaigns = [campaign(1, 'Marché de Noël'), campaign(2, 'Budget participatif')];

describe('AssignCampaignModalComponent', () => {
  let fixture: ComponentFixture<AssignCampaignModalComponent>;
  let compiled: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssignCampaignModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AssignCampaignModalComponent);
  });

  function render(value: Publication = publication()): void {
    fixture.componentRef.setInput('publication', value);
    fixture.componentRef.setInput('campaigns', campaigns);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function select(): HTMLSelectElement {
    return compiled.querySelector('#assign-campaign') as HTMLSelectElement;
  }

  function choose(value: string): void {
    select().value = value;
    select().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('should recall the publication being assigned', () => {
    render();

    expect(compiled.querySelector('.assign-publication')?.textContent?.trim())
      .toBe('Marché de producteurs samedi');
  });

  it('should offer every campaign plus a way to detach', () => {
    render();

    const options = Array.from(select().options).map((option) => option.textContent?.trim());
    expect(options).toEqual(['Aucune campagne', 'Marché de Noël', 'Budget participatif']);
  });

  it('should preselect the current campaign', () => {
    render(publication({ id: 2, name: 'Budget participatif' }));

    expect(select().value).toBe('2');
  });

  it('should preselect none when the publication has no campaign', () => {
    render();

    expect(select().value).toBe('NONE');
  });

  it('should emit the chosen campaign id', () => {
    const submitted = vi.fn();
    render();
    fixture.componentInstance.submitted.subscribe(submitted);

    choose('2');
    fixture.componentInstance.onSubmit();

    expect(submitted).toHaveBeenCalledWith(2);
  });

  it('should emit null when detaching', () => {
    const submitted = vi.fn();
    render(publication({ id: 2, name: 'Budget participatif' }));
    fixture.componentInstance.submitted.subscribe(submitted);

    choose('NONE');
    fixture.componentInstance.onSubmit();

    expect(submitted).toHaveBeenCalledWith(null);
  });

  /** Réassigner la même campagne ne ferait qu'un appel pour rien. */
  it('should disable the submit button while the selection has not changed', () => {
    render(publication({ id: 2, name: 'Budget participatif' }));
    const submit = compiled.querySelector('.primary') as HTMLButtonElement;

    expect(submit.disabled).toBe(true);

    choose('1');
    expect(submit.disabled).toBe(false);
  });

  it('should ignore a second submit while the first one is in flight', () => {
    const submitted = vi.fn();
    render();
    fixture.componentInstance.submitted.subscribe(submitted);
    choose('1');
    fixture.componentRef.setInput('saving', true);
    fixture.detectChanges();

    fixture.componentInstance.onSubmit();

    expect(submitted).not.toHaveBeenCalled();
  });

  it('should close on Escape and on the backdrop', () => {
    const closed = vi.fn();
    render();
    fixture.componentInstance.closed.subscribe(closed);

    fixture.componentInstance.onEscape();
    compiled.querySelector<HTMLButtonElement>('.modal-backdrop')!.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
