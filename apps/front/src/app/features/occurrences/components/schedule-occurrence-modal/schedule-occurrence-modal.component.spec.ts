import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Publication } from '../../../publications/publication.model';
import { ScheduleOccurrenceModalComponent } from './schedule-occurrence-modal.component';

const publication: Publication = {
  id: 7,
  content: 'Marché de Noël samedi sur la place',
  status: 'VERIFIED',
  medias: [],
  campaign: null,
};

describe('ScheduleOccurrenceModalComponent', () => {
  let fixture: ComponentFixture<ScheduleOccurrenceModalComponent>;
  let component: ScheduleOccurrenceModalComponent;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScheduleOccurrenceModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ScheduleOccurrenceModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('publication', publication);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  });

  it('should recall which publication is being scheduled', () => {
    expect(element.querySelector('.schedule-publication')?.textContent).toContain('Marché de Noël');
  });

  it('should submit a day without time, leaving the time to the back', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.form.controls.date.setValue('2026-10-03');
    component.onSubmit();

    expect(submitted).toHaveBeenCalledWith({ date: '2026-10-03', time: '', channels: ['FACEBOOK'] });
  });

  it('should submit a precise time when one is entered', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.form.setValue({ date: '2026-10-03', time: '17:45', facebook: true });
    component.onSubmit();

    expect(submitted).toHaveBeenCalledWith({ date: '2026-10-03', time: '17:45', channels: ['FACEBOOK'] });
  });

  it('should refuse to submit without a day, and say so', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.onSubmit();
    fixture.detectChanges();

    expect(submitted).not.toHaveBeenCalled();
    expect(element.querySelector('.schedule-error')?.textContent).toContain('Choisissez un jour');
  });

  it('should refuse to submit without any channel', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.form.setValue({ date: '2026-10-03', time: '', facebook: false });
    component.onSubmit();
    fixture.detectChanges();

    expect(submitted).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Choisissez au moins un canal');
  });

  it('should offer Intramuros as a disabled, upcoming channel', () => {
    const soon = element.querySelector<HTMLInputElement>('.schedule-channel-soon input');

    expect(soon?.disabled).toBe(true);
  });

  it('should not allow picking a past day', () => {
    const date = element.querySelector<HTMLInputElement>('#schedule-date');

    expect(date?.min).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('should ignore a second submit while the first one is in flight', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);
    fixture.componentRef.setInput('saving', true);

    component.form.controls.date.setValue('2026-10-03');
    component.onSubmit();

    expect(submitted).not.toHaveBeenCalled();
  });

  it('should close on Escape and on the backdrop', () => {
    const closed = vi.fn();
    component.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    element.querySelector<HTMLButtonElement>('.modal-backdrop')?.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
