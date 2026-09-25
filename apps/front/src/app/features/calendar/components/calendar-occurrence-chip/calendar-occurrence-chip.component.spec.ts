import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { OCCURRENCE_DRAG_TYPE } from '../../calendar.utils';
import { CalendarOccurrenceChipComponent } from './calendar-occurrence-chip.component';

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

/** jsdom n'implémente pas `DataTransfer` : un double suffit à observer l'échange. */
function dragStart(host: HTMLElement): { data: Map<string, string>, effectAllowed: string } {
  const data = new Map<string, string>();
  const transfer = { effectAllowed: 'none', setData: (type: string, value: string) => data.set(type, value) };
  const event = new Event('dragstart');
  Object.defineProperty(event, 'dataTransfer', { value: transfer });
  host.dispatchEvent(event);

  return { data, effectAllowed: transfer.effectAllowed };
}

describe('CalendarOccurrenceChipComponent', () => {
  let fixture: ComponentFixture<CalendarOccurrenceChipComponent>;

  function render(value: Occurrence, draggable = false): HTMLElement {
    fixture = TestBed.createComponent(CalendarOccurrenceChipComponent);
    fixture.componentRef.setInput('occurrence', value);
    fixture.componentRef.setInput('draggable', draggable);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CalendarOccurrenceChipComponent] }).compileComponents();
  });

  it('should show the time and the start of the content, in the Facebook tint', () => {
    const host = render(occurrence());
    const chip = host.querySelector('.chip')!;

    expect(chip.textContent).toContain('18:30');
    expect(chip.textContent).toContain('Marché de Noël samedi');
    expect(chip.classList.contains('chip-facebook')).toBe(true);
  });

  it('should use the Intramuros tint for an Intramuros delivery', () => {
    const host = render(occurrence({ deliveries: [{ id: 1, channel: 'INTRAMUROS', status: 'PENDING' }] }));

    expect(host.querySelector('.chip')?.classList.contains('chip-intramuros')).toBe(true);
  });

  it('should mark a pinned time with a pin', () => {
    const host = render(occurrence({ pinned: true }));

    expect(host.querySelector('svg[aria-label="Heure fixe"]')).not.toBeNull();
  });

  it('should flag a failure and give its reason in the tooltip', () => {
    const host = render(occurrence({
      status: 'FAILED',
      deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'FAILED', errorMessage: 'Token expired' }],
    }));
    const chip = host.querySelector('.chip')!;

    expect(chip.classList.contains('chip-failed')).toBe(true);
    expect(chip.getAttribute('title')).toContain('Diffusion en échec');
    expect(chip.getAttribute('title')).toContain('Motif : Token expired');
  });

  it('should fade a published occurrence', () => {
    const host = render(occurrence({ status: 'PUBLISHED' }));

    expect(host.querySelector('.chip')?.classList.contains('chip-published')).toBe(true);
  });

  it('should carry its id when dragged', () => {
    const host = render(occurrence(), true);

    expect(host.getAttribute('draggable')).toBe('true');
    const { data, effectAllowed } = dragStart(host);
    expect(data.get(OCCURRENCE_DRAG_TYPE)).toBe('12');
    expect(effectAllowed).toBe('move');
  });

  it('should neither be draggable nor carry anything when it can no longer move', () => {
    const host = render(occurrence({ status: 'PUBLISHED' }), false);

    expect(host.getAttribute('draggable')).toBeNull();
    expect(dragStart(host).data.size).toBe(0);
  });
});
