import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { EditOccurrenceModalComponent, RescheduleIntent } from './edit-occurrence-modal.component';

registerLocaleData(localeFr, 'fr-FR');

function occurrence(overrides: Partial<Occurrence> = {}): Occurrence {
  return {
    id: 12,
    scheduledAt: '2026-10-03T18:30:00',
    pinned: false,
    status: 'SCHEDULED',
    publication: { id: 1, content: 'Marché de Noël samedi', status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'PENDING' }],
    ...overrides,
  };
}

describe('EditOccurrenceModalComponent', () => {
  let fixture: ComponentFixture<EditOccurrenceModalComponent>;
  let component: EditOccurrenceModalComponent;
  let element: HTMLElement;
  let emitted: RescheduleIntent[];

  function render(value: Occurrence, saving = false): void {
    fixture = TestBed.createComponent(EditOccurrenceModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('occurrence', value);
    fixture.componentRef.setInput('saving', saving);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
    emitted = [];
    component.rescheduled.subscribe((intent) => emitted.push(intent));
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [EditOccurrenceModalComponent] }).compileComponents();
  });

  it('should recall the publication and when it is due', () => {
    render(occurrence());

    expect(element.querySelector('.edit-publication')?.textContent).toContain('Marché de Noël samedi');
    expect(element.querySelector('.edit-current')?.textContent).toContain('samedi 3 octobre à 18:30');
    expect(element.querySelector('.edit-current')?.textContent).toContain('heure automatique');
  });

  it('should leave the time empty for an automatic occurrence, and prefill a pinned one', () => {
    render(occurrence());
    expect(component.form.getRawValue()).toEqual({ date: '2026-10-03', time: '' });

    render(occurrence({ pinned: true }));
    expect(component.form.getRawValue()).toEqual({ date: '2026-10-03', time: '18:30' });
  });

  it('should pin the time that was entered', () => {
    render(occurrence());

    component.form.controls.time.setValue('17:45');
    component.onSubmit();

    expect(emitted).toEqual([expect.objectContaining({ date: '2026-10-03', time: '17:45' })]);
  });

  it('should hand a pinned occurrence back to the automatic placement when the time is cleared', () => {
    render(occurrence({ pinned: true }));

    component.form.controls.time.setValue('');
    component.onSubmit();

    expect(emitted).toEqual([expect.objectContaining({ date: '2026-10-03', time: null })]);
  });

  it('should move to another day — the keyboard way to drag and drop', () => {
    render(occurrence());

    component.form.controls.date.setValue('2026-10-05');
    component.onSubmit();

    expect(emitted).toEqual([expect.objectContaining({ date: '2026-10-05', time: null })]);
  });

  it('should simply close when nothing changed, which would reshuffle the day for nothing', () => {
    render(occurrence());
    const closed = vi.fn();
    component.closed.subscribe(closed);

    component.onSubmit();

    expect(emitted).toEqual([]);
    expect(closed).toHaveBeenCalled();
  });

  it('should ask for a confirmation before cancelling', () => {
    render(occurrence());
    const cancelled = vi.fn();
    component.cancelled.subscribe(cancelled);
    const button = () => element.querySelector<HTMLButtonElement>('.edit-cancel')!;

    button().click();
    fixture.detectChanges();
    expect(cancelled).not.toHaveBeenCalled();
    expect(button().textContent).toContain('Confirmer l’annulation');

    button().click();
    expect(cancelled).toHaveBeenCalledWith(expect.objectContaining({ id: 12 }));
  });

  it('should ignore both actions while a change is in flight', () => {
    render(occurrence(), true);
    const cancelled = vi.fn();
    component.cancelled.subscribe(cancelled);

    component.form.controls.date.setValue('2026-10-05');
    component.onSubmit();
    component.onCancel();
    component.onCancel();

    expect(emitted).toEqual([]);
    expect(cancelled).not.toHaveBeenCalled();
  });

  it('should close on Escape and on the backdrop', () => {
    render(occurrence());
    const closed = vi.fn();
    component.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    element.querySelector<HTMLButtonElement>('.modal-backdrop')!.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
