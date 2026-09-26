import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { CalendarDayPanelComponent } from './calendar-day-panel.component';

registerLocaleData(localeFr, 'fr-FR');

const NOW = new Date(2026, 9, 1, 12, 0);

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

describe('CalendarDayPanelComponent', () => {
  let fixture: ComponentFixture<CalendarDayPanelComponent>;
  let element: HTMLElement;

  function render(occurrences: Occurrence[]): void {
    fixture = TestBed.createComponent(CalendarDayPanelComponent);
    fixture.componentRef.setInput('date', '2026-10-03');
    fixture.componentRef.setInput('occurrences', occurrences);
    fixture.componentRef.setInput('now', NOW);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CalendarDayPanelComponent] }).compileComponents();
  });

  it('should title the day in French and list its occurrences with their badges', () => {
    render([occurrence()]);

    expect(element.querySelector('h3')?.textContent).toContain('samedi 3 octobre 2026');
    expect(element.querySelector('.day-panel-content')?.textContent).toContain('Marché de Noël samedi');
    const badges = Array.from(element.querySelectorAll('.occurrence-badge')).map((badge) => badge.textContent?.trim());
    expect(badges).toEqual(['Facebook', 'Programmée']);
    expect(element.querySelector('.day-panel-time')?.textContent).toContain('18:30 · heure auto');
  });

  it('should tell a pinned time apart', () => {
    render([occurrence({ pinned: true })]);

    expect(element.querySelector('.day-panel-time')?.textContent).toContain('18:30 · heure fixe');
  });

  it('should say when the day is empty', () => {
    render([]);

    expect(element.textContent).toContain('Aucune diffusion ce jour-là.');
  });

  it('should show why an occurrence failed, and offer to retry it rather than edit it', () => {
    render([occurrence({
      status: 'FAILED',
      deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'FAILED', errorMessage: 'Token expired' }],
    })]);

    expect(element.querySelector('.occurrence-badge-failed')?.textContent).toContain('Échec');
    expect(element.querySelector('.day-panel-reason')?.textContent).toContain('Motif : Token expired');
    expect(element.querySelector('.day-panel-edit')?.textContent).toContain('Reprendre');
    expect(element.textContent).not.toContain('Modifier');
  });

  it('should ask to retry a failed occurrence', () => {
    render([occurrence({
      status: 'FAILED',
      deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'FAILED', errorMessage: 'Token expired' }],
    })]);
    const retry = vi.fn();
    const edit = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);
    fixture.componentInstance.edit.subscribe(edit);

    element.querySelector<HTMLButtonElement>('.day-panel-retry')!.click();

    expect(retry).toHaveBeenCalledWith(expect.objectContaining({ id: 12 }));
    expect(edit).not.toHaveBeenCalled();
  });

  it('should offer nothing for a published occurrence', () => {
    render([occurrence({
      scheduledAt: '2026-09-30T18:30:00',
      status: 'PUBLISHED',
      deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'PUBLISHED' }],
    })]);

    expect(element.querySelector('.day-panel-edit')).toBeNull();
    expect(element.querySelector('.day-panel-note')).toBeNull();
  });

  it('should explain that an imminent occurrence can no longer change', () => {
    render([occurrence({ scheduledAt: '2026-10-01T12:03:00' })]);

    expect(element.querySelector('.day-panel-edit')).toBeNull();
    expect(element.textContent).toContain('elle ne peut plus être modifiée');
  });

  it('should ask to edit a modifiable occurrence', () => {
    render([occurrence()]);
    const edit = vi.fn();
    fixture.componentInstance.edit.subscribe(edit);

    element.querySelector<HTMLButtonElement>('.day-panel-edit')!.click();

    expect(edit).toHaveBeenCalledWith(expect.objectContaining({ id: 12 }));
  });

  it('should close on Escape and on a click outside', () => {
    render([]);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    element.querySelector<HTMLButtonElement>('.day-panel-backdrop')!.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
