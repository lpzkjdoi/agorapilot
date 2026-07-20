import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { WeeklyOccurrenceCardComponent } from './weekly-occurrence-card.component';

// The template formats the date with the `fr-FR` locale, whose data must be
// registered before the DatePipe can run under tests.
registerLocaleData(localeFr, 'fr-FR');

describe('WeeklyOccurrenceCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WeeklyOccurrenceCardComponent],
    }).compileComponents();
  });

  function createWith(entry: [string, string]) {
    const fixture = TestBed.createComponent(WeeklyOccurrenceCardComponent);
    fixture.componentRef.setInput('entry', entry);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = createWith(['2026-07-20', '']);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should derive date, post count and presence from the entry', () => {
    const fixture = createWith(['2026-07-20', 'abc']);
    const component = fixture.componentInstance;

    expect(component.date()).toBe('2026-07-20');
    expect(component.numberOfPosts()).toBe(3);
    expect(component.hasPosts()).toBeTrue();
  });

  it('should report no posts for an empty value', () => {
    const fixture = createWith(['2026-07-20', '']);
    const component = fixture.componentInstance;

    expect(component.numberOfPosts()).toBe(0);
    expect(component.hasPosts()).toBeFalse();
  });

  it('should toggle the has-posts class based on the count', () => {
    const withPosts = createWith(['2026-07-20', 'ab']);
    expect(
      (withPosts.nativeElement as HTMLElement)
        .querySelector('.occurrence-card')
        ?.classList.contains('has-posts'),
    ).toBeTrue();

    const withoutPosts = createWith(['2026-07-20', '']);
    expect(
      (withoutPosts.nativeElement as HTMLElement)
        .querySelector('.occurrence-card')
        ?.classList.contains('has-posts'),
    ).toBeFalse();
  });

  it('should pluralize the post label', () => {
    const single = createWith(['2026-07-20', 'a']);
    expect((single.nativeElement as HTMLElement).textContent).toContain('1 post');

    const many = createWith(['2026-07-20', 'abc']);
    expect((many.nativeElement as HTMLElement).textContent).toContain('3 posts');
  });
});
