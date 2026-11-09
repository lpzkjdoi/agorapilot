import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Publication } from '../../../publications/publication.model';
import { MAX_SLOTS, ScheduleOccurrenceModalComponent } from './schedule-occurrence-modal.component';

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

    component.slots.at(0).controls.date.setValue('2026-10-03');
    component.onSubmit();

    expect(submitted).toHaveBeenCalledWith({ slots: [{ date: '2026-10-03', time: '' }], channels: ['FACEBOOK'] });
  });

  it('should submit a precise time when one is entered', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.slots.at(0).setValue({ date: '2026-10-03', time: '17:45' });
    component.onSubmit();

    expect(submitted).toHaveBeenCalledWith({ slots: [{ date: '2026-10-03', time: '17:45' }], channels: ['FACEBOOK'] });
  });

  it('should add dates and submit them in calendar order', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    element.querySelector<HTMLButtonElement>('.schedule-add')!.click();
    element.querySelector<HTMLButtonElement>('.schedule-add')!.click();
    fixture.detectChanges();

    expect(element.querySelectorAll('.schedule-when').length).toBe(3);

    component.slots.at(0).setValue({ date: '2027-02-05', time: '' });
    component.slots.at(1).setValue({ date: '2026-11-11', time: '' });
    component.slots.at(2).setValue({ date: '2026-12-04', time: '17:45' });
    component.onSubmit();

    expect(submitted).toHaveBeenCalledWith({
      slots: [
        { date: '2026-11-11', time: '' },
        { date: '2026-12-04', time: '17:45' },
        { date: '2027-02-05', time: '' },
      ],
      channels: ['FACEBOOK'],
    });
  });

  it('should remove a date, but never the last one', () => {
    expect(element.querySelector('.schedule-remove')).toBeNull();

    element.querySelector<HTMLButtonElement>('.schedule-add')!.click();
    fixture.detectChanges();
    component.slots.at(1).controls.date.setValue('2026-12-04');
    element.querySelectorAll<HTMLButtonElement>('.schedule-remove')[0].click();
    fixture.detectChanges();

    expect(component.slots.length).toBe(1);
    expect(component.slots.at(0).controls.date.value).toBe('2026-12-04');
    expect(element.querySelector('.schedule-remove')).toBeNull();

    component.removeSlot(0);
    expect(component.slots.length).toBe(1);
  });

  it('should refuse the same date twice, and say so', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);

    component.addSlot();
    component.slots.at(0).setValue({ date: '2026-11-11', time: '' });
    component.slots.at(1).setValue({ date: '2026-11-11', time: '' });
    component.onSubmit();
    fixture.detectChanges();

    expect(submitted).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Une même date figure deux fois');

    // Le même jour, à une heure fixée, est une autre diffusion.
    component.slots.at(1).controls.time.setValue('17:45');
    component.onSubmit();

    expect(submitted).toHaveBeenCalled();
  });

  it('should stop offering new dates at the cap of the back', () => {
    for (let i = 1; i < MAX_SLOTS; i++) {
      element.querySelector<HTMLButtonElement>('.schedule-add')!.click();
      fixture.detectChanges();
    }

    component.addSlot();

    expect(component.slots.length).toBe(MAX_SLOTS);
    expect(element.querySelector('.schedule-add')).toBeNull();
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

    component.slots.at(0).controls.date.setValue('2026-10-03');
    component.form.controls.facebook.setValue(false);
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
    const date = element.querySelector<HTMLInputElement>('#schedule-date-0');

    expect(date?.min).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('should ignore a second submit while the first one is in flight', () => {
    const submitted = vi.fn();
    component.submitted.subscribe(submitted);
    fixture.componentRef.setInput('saving', true);

    component.slots.at(0).controls.date.setValue('2026-10-03');
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
