import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { toIsoDate } from '../../../occurrences/occurrence.utils';
import { RescheduleIntent } from '../edit-occurrence-modal/edit-occurrence-modal.component';
import { RetryOccurrenceModalComponent } from './retry-occurrence-modal.component';

registerLocaleData(localeFr, 'fr-FR');

const REFUSAL = 'Confirmez votre identité avant de pouvoir publier au nom de cette Page. (Facebook, code 368, sous-code 4854002)';

function failed(overrides: Partial<Occurrence> = {}): Occurrence {
  return {
    id: 12,
    scheduledAt: '2026-09-26T17:05:00',
    pinned: false,
    status: 'FAILED',
    publication: { id: 1, content: 'Marché de Noël samedi', status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'FAILED', errorMessage: REFUSAL }],
    ...overrides,
  };
}

describe('RetryOccurrenceModalComponent', () => {
  let fixture: ComponentFixture<RetryOccurrenceModalComponent>;
  let component: RetryOccurrenceModalComponent;
  let element: HTMLElement;
  let emitted: RescheduleIntent[];

  function render(value: Occurrence, saving = false): void {
    fixture = TestBed.createComponent(RetryOccurrenceModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('occurrence', value);
    fixture.componentRef.setInput('saving', saving);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
    emitted = [];
    component.retried.subscribe((intent) => emitted.push(intent));
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RetryOccurrenceModalComponent] }).compileComponents();
  });

  it('should recall the publication, when it failed and why', () => {
    render(failed());

    expect(element.querySelector('.retry-publication')?.textContent).toContain('Marché de Noël samedi');
    expect(element.querySelector('.retry-current')?.textContent).toContain('samedi 26 septembre à 17:05');
    expect(element.querySelector('.retry-reason')?.textContent).toContain(`Motif : ${ REFUSAL }`);
  });

  it('should warn that the post may already be online', () => {
    render(failed());

    expect(element.querySelector('.retry-warning')?.textContent).toContain('n’y figure pas déjà');
  });

  it('should propose today, with an automatic time', () => {
    render(failed());

    expect(component.form.getRawValue()).toEqual({ date: toIsoDate(new Date()), time: '' });
  });

  it('should retry on the chosen day with an automatic time', () => {
    render(failed());

    component.form.controls.date.setValue('2026-09-28');
    component.onSubmit();

    expect(emitted).toEqual([expect.objectContaining({ date: '2026-09-28', time: null })]);
    expect(emitted[0].occurrence.id).toBe(12);
  });

  it('should pin the time that was entered', () => {
    render(failed());

    component.form.controls.time.setValue('19:30');
    component.onSubmit();

    expect(emitted).toEqual([expect.objectContaining({ time: '19:30' })]);
  });

  it('should require a day', () => {
    render(failed());

    component.form.controls.date.setValue('');
    component.onSubmit();
    fixture.detectChanges();

    expect(emitted).toEqual([]);
    expect(element.querySelector('.retry-error')?.textContent).toContain('Choisissez un jour.');
  });

  it('should not retry twice while the first attempt is in flight', () => {
    render(failed(), true);

    component.onSubmit();

    expect(emitted).toEqual([]);
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
  });

  it('should close on Escape and on a click outside', () => {
    render(failed());
    const closed = vi.fn();
    component.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    element.querySelector<HTMLButtonElement>('.modal-backdrop')!.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
