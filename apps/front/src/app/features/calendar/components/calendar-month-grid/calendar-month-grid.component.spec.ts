import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { monthGrid, OCCURRENCE_DRAG_TYPE } from '../../calendar.utils';
import { CalendarMonthGridComponent, OccurrenceMove } from './calendar-month-grid.component';

const NOW = new Date(2026, 9, 10, 12, 0);

function occurrence(id: number, scheduledAt: string, overrides: Partial<Occurrence> = {}): Occurrence {
  return {
    id,
    scheduledAt,
    pinned: false,
    status: 'SCHEDULED',
    publication: { id, content: `Publication ${ id }`, status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [{ id: id * 10, channel: 'FACEBOOK', status: 'PENDING' }],
    ...overrides,
  };
}

/** Hôte minimal : fournit le gabarit de bulle, comme le fait la page. */
@Component({
  imports: [CalendarMonthGridComponent],
  template: `
    <ng-template #panel let-day let-end="end" let-up="up">
      <p class="test-panel" [attr.data-end]="end" [attr.data-up]="up">bulle {{ day }}</p>
    </ng-template>
    <app-calendar-month-grid
      [days]="days"
      [occurrencesByDay]="byDay"
      [today]="'2026-10-10'"
      [now]="now"
      [selectedDay]="selectedDay"
      [panel]="panel"
      (daySelected)="selected.push($event)"
      (moved)="moves.push($event)"
    />
  `,
})
class HostComponent {
  days = monthGrid({ year: 2026, month: 9 });
  now = NOW;
  byDay = new Map<string, Occurrence[]>();
  selectedDay: string | null = null;
  selected: string[] = [];
  moves: OccurrenceMove[] = [];
}

/** jsdom n'implémente pas `DataTransfer`. */
function dragEvent(type: string, id: number | null): Event {
  const event = new Event(type, { cancelable: true, bubbles: true });
  const transfer = {
    types: id === null ? [] : [OCCURRENCE_DRAG_TYPE],
    dropEffect: 'none',
    getData: (format: string) => (format === OCCURRENCE_DRAG_TYPE && id !== null ? String(id) : ''),
  };
  Object.defineProperty(event, 'dataTransfer', { value: transfer });
  return event;
}

describe('CalendarMonthGridComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  let element: HTMLElement;

  /** La case du `day` d'octobre 2026. */
  function cell(day: number): HTMLElement {
    const index = host.days.findIndex((candidate) => candidate.inMonth && candidate.dayOfMonth === day);
    return element.querySelectorAll<HTMLElement>('.month-cell')[index];
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    element = fixture.nativeElement as HTMLElement;
  });

  it('should head the columns from Monday to Sunday', () => {
    fixture.detectChanges();

    const weekdays = Array.from(element.querySelectorAll('.month-weekday')).map((weekday) => weekday.textContent);
    expect(weekdays).toEqual(['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim']);
  });

  it('should grey out the days outside the month, without number', () => {
    fixture.detectChanges();

    const first = element.querySelector('.month-cell')!;
    expect(first.classList.contains('month-cell-outside')).toBe(true);
    expect(first.querySelector('.month-day')).toBeNull();
  });

  it('should circle today', () => {
    fixture.detectChanges();

    expect(cell(10).querySelector('.month-day')?.classList.contains('month-day-today')).toBe(true);
  });

  it('should show at most three chips, then a count of the others', () => {
    host.byDay = new Map([['2026-10-12', [1, 2, 3, 4, 5].map((id) => occurrence(id, '2026-10-12T16:00:00'))]]);
    fixture.detectChanges();

    expect(cell(12).querySelectorAll('app-calendar-occurrence-chip')).toHaveLength(3);
    expect(cell(12).querySelector('.month-more')?.textContent).toContain('+2 autres');
  });

  it('should only let modifiable occurrences be dragged', () => {
    host.byDay = new Map([['2026-10-12', [
      occurrence(1, '2026-10-12T16:00:00'),
      occurrence(2, '2026-10-12T18:00:00', { status: 'PUBLISHED' }),
    ]]]);
    fixture.detectChanges();

    const chips = cell(12).querySelectorAll('app-calendar-occurrence-chip');
    expect(chips[0].getAttribute('draggable')).toBe('true');
    expect(chips[1].getAttribute('draggable')).toBeNull();
  });

  it('should select a day of the month when clicked', () => {
    fixture.detectChanges();

    cell(12).click();

    expect(host.selected).toEqual(['2026-10-12']);
  });

  it('should select a day from the keyboard, through its day button', () => {
    fixture.detectChanges();

    const button = cell(12).querySelector<HTMLButtonElement>('.month-day')!;
    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(host.selected).toContain('2026-10-12');
  });

  it('should render the panel in the selected day, opening leftwards at the end of the week', () => {
    host.selectedDay = '2026-10-17'; // un samedi, troisième semaine sur cinq
    fixture.detectChanges();

    const panel = cell(17).querySelector('.test-panel');
    expect(panel?.textContent).toContain('bulle 2026-10-17');
    expect(panel?.getAttribute('data-end')).toBe('true');
    expect(panel?.getAttribute('data-up')).toBe('false');
  });

  it('should open the panel upwards on the last two weeks, where it would overflow the page', () => {
    host.selectedDay = '2026-10-20'; // un mardi, quatrième semaine sur cinq
    fixture.detectChanges();

    const panel = cell(20).querySelector('.test-panel');
    expect(panel?.getAttribute('data-up')).toBe('true');
    expect(panel?.getAttribute('data-end')).toBe('false');
  });

  describe('glisser-déposer', () => {
    it('should accept a drop on a coming day and emit the move', () => {
      fixture.detectChanges();

      const over = dragEvent('dragover', 1);
      cell(14).dispatchEvent(over);
      fixture.detectChanges();
      expect(over.defaultPrevented).toBe(true);
      expect(cell(14).classList.contains('month-cell-drop')).toBe(true);

      cell(14).dispatchEvent(dragEvent('drop', 1));
      fixture.detectChanges();

      expect(host.moves).toEqual([{ occurrenceId: 1, date: '2026-10-14' }]);
      expect(cell(14).classList.contains('month-cell-drop')).toBe(false);
    });

    it('should refuse a past day', () => {
      fixture.detectChanges();

      const over = dragEvent('dragover', 1);
      cell(9).dispatchEvent(over);
      cell(9).dispatchEvent(dragEvent('drop', 1));

      expect(over.defaultPrevented).toBe(false);
      expect(host.moves).toEqual([]);
    });

    it('should accept today, whose window the back will check', () => {
      fixture.detectChanges();

      cell(10).dispatchEvent(dragEvent('drop', 1));

      expect(host.moves).toEqual([{ occurrenceId: 1, date: '2026-10-10' }]);
    });

    it('should ignore anything else being dragged', () => {
      fixture.detectChanges();

      const over = dragEvent('dragover', null);
      cell(14).dispatchEvent(over);

      expect(over.defaultPrevented).toBe(false);
    });
  });
});
