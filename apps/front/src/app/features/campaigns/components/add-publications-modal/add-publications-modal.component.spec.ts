import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Publication } from '../../../publications/publication.model';
import { Campaign } from '../../campaign.model';
import { AddPublicationsModalComponent } from './add-publications-modal.component';

function publication(id: number, content: string, campaign: Publication['campaign'] = null): Publication {
  return { id, content, status: 'VERIFIED', medias: [], campaign };
}

const campaign: Campaign = {
  id: 3,
  name: 'Marché de Noël',
  description: '',
  startDate: null,
  endDate: null,
  status: 'DRAFT',
  publications: [],
};

describe('AddPublicationsModalComponent', () => {
  let fixture: ComponentFixture<AddPublicationsModalComponent>;
  let compiled: HTMLElement;

  function render(publications: Publication[] | null): void {
    fixture.componentRef.setInput('campaign', campaign);
    fixture.componentRef.setInput('publications', publications);
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
  }

  function submitButton(): HTMLButtonElement {
    return compiled.querySelector('.modal-actions .primary') as HTMLButtonElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddPublicationsModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AddPublicationsModalComponent);
  });

  it('should show a loader while the publications load', () => {
    render(null);

    expect(compiled.querySelector('app-loader')).not.toBeNull();
    expect(compiled.querySelector('.add-publications-list')).toBeNull();
  });

  it('should leave out the publications already in the campaign and flag those moving', () => {
    render([
      publication(1, 'Déjà dedans', { id: 3, name: 'Marché de Noël' }),
      publication(2, 'Sans campagne'),
      publication(4, 'Ailleurs', { id: 7, name: 'Budget participatif' }),
    ]);

    const items = Array.from(compiled.querySelectorAll('.add-publications-item'));
    expect(items.map((item) => item.querySelector('.add-publications-content')?.textContent?.trim()))
      .toEqual(['Sans campagne', 'Ailleurs']);
    expect(items[1].querySelector('.add-publications-current')?.textContent)
      .toContain('Déplacée depuis « Budget participatif »');
  });

  it('should filter by the search', () => {
    render([publication(2, 'Marché samedi'), publication(4, 'Concert dimanche')]);

    const search = compiled.querySelector('.add-publications-search') as HTMLInputElement;
    search.value = 'CONCERT';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(compiled.querySelectorAll('.add-publications-item').length).toBe(1);
  });

  it('should tell when there is nothing to add', () => {
    render([publication(1, 'Déjà dedans', { id: 3, name: 'Marché de Noël' })]);

    expect(compiled.querySelector('.add-publications-empty')?.textContent).toContain('Aucune autre publication');
  });

  it('should submit the checked publications only once one is checked', () => {
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);
    render([publication(2, 'Un'), publication(4, 'Deux'), publication(5, 'Trois')]);

    expect(submitButton().disabled).toBe(true);

    const boxes = compiled.querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    boxes[0].click();
    boxes[2].click();
    boxes[2].click();
    boxes[1].click();
    fixture.detectChanges();

    expect(submitButton().textContent).toContain('(2)');
    submitButton().click();

    expect(submitted).toHaveBeenCalledWith([2, 4]);
  });

  it('should ignore a submit while saving', () => {
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);
    render([publication(2, 'Un')]);
    fixture.componentInstance.toggle(2);
    fixture.componentRef.setInput('saving', true);
    fixture.detectChanges();

    fixture.componentInstance.onSubmit();

    expect(submitted).not.toHaveBeenCalled();
  });

  it('should close on Escape and on the backdrop', () => {
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    render([]);

    fixture.componentInstance.onEscape();
    compiled.querySelector<HTMLButtonElement>('.modal-backdrop')!.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
