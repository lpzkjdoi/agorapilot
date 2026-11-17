import { ComponentFixture, TestBed } from '@angular/core/testing';
import { createRepeatGroup, RepeatFieldsComponent, RepeatGroup, repeatRule } from './repeat-fields.component';

describe('RepeatFieldsComponent', () => {
  let fixture: ComponentFixture<RepeatFieldsComponent>;
  let group: RepeatGroup;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RepeatFieldsComponent] }).compileComponents();

    group = createRepeatGroup();
    fixture = TestBed.createComponent(RepeatFieldsComponent);
    fixture.componentRef.setInput('form', group);
    fixture.componentRef.setInput('minDate', '2026-11-17');
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  });

  function select(selector: string, value: string): void {
    const field = element.querySelector<HTMLSelectElement>(selector)!;
    field.value = value;
    field.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('should start every week, without end, with a live summary', () => {
    expect(repeatRule(group)).toBe('FREQ=WEEKLY');
    expect(element.querySelector('.repeat-summary')?.textContent).toBe('Chaque semaine, heure automatique, sans fin.');
  });

  it('should offer the presets and hide the custom fields until « Personnaliser »', () => {
    expect(element.querySelectorAll('#repeat-preset option')).toHaveLength(10);
    expect(element.querySelector('.repeat-custom')).toBeNull();

    select('#repeat-preset', 'CUSTOM');

    expect(element.querySelector('.repeat-custom')).not.toBeNull();
  });

  it('should toggle week days into a custom weekly rule', () => {
    select('#repeat-preset', 'CUSTOM');
    const days = element.querySelectorAll<HTMLButtonElement>('.repeat-weekday');
    days[3].click();
    days[0].click();
    fixture.detectChanges();

    expect(days[0].getAttribute('aria-pressed')).toBe('true');
    expect(repeatRule(group)).toBe('FREQ=WEEKLY;BYDAY=MO,TH');

    days[0].click();
    expect(repeatRule(group)).toBe('FREQ=WEEKLY;BYDAY=TH');
  });

  it('should pick days of the month, or the last friday, in a monthly rule', () => {
    select('#repeat-preset', 'CUSTOM');
    group.controls.frequency.setValue('MONTHLY');
    fixture.detectChanges();

    const days = element.querySelectorAll<HTMLButtonElement>('.repeat-monthday');
    expect(days).toHaveLength(31);
    days[30].click();
    expect(repeatRule(group)).toBe('FREQ=MONTHLY;BYMONTHDAY=31');

    group.patchValue({ monthMode: 'ordinal', ordinal: -1, ordinalDay: 'FR' });
    fixture.detectChanges();
    expect(element.querySelector('.repeat-monthday')).toBeNull();
    expect(repeatRule(group)).toBe('FREQ=MONTHLY;BYDAY=-1FR');
  });

  it('should pick months in a yearly rule', () => {
    select('#repeat-preset', 'CUSTOM');
    group.controls.frequency.setValue('YEARLY');
    fixture.detectChanges();

    const months = element.querySelectorAll<HTMLButtonElement>('.repeat-month');
    expect(months).toHaveLength(12);
    expect(months[1].textContent?.trim()).toBe('févr.');
    months[5].click();

    expect(repeatRule(group)).toBe('FREQ=YEARLY;BYMONTH=6');
  });

  it('should require a valid end', () => {
    group.patchValue({ endKind: 'until', until: '' });
    expect(group.hasError('untilMissing')).toBe(true);

    group.patchValue({ endKind: 'count', count: 0 });
    expect(group.controls.count.invalid).toBe(true);
  });
});
