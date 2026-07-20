import { TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../../../occurrences/occurrence.model';
import { UpcomingPostCardComponent } from './upcoming-post-card.component';

const occurrence: Occurrence = {
  id: 7,
  scheduledAt: '2026-07-20T09:00:00Z',
  status: 'SCHEDULED',
  publication: { id: 3, content: 'Réunion publique ce jeudi', status: 'VERIFIED' },
  deliveries: [],
};

describe('UpcomingPostCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UpcomingPostCardComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(UpcomingPostCardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the occurrence id and publication content', () => {
    const fixture = TestBed.createComponent(UpcomingPostCardComponent);
    fixture.componentRef.setInput('occurrence', occurrence);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('7');
    expect(text).toContain('Réunion publique ce jeudi');
  });

  it('should render gracefully without an occurrence', () => {
    const fixture = TestBed.createComponent(UpcomingPostCardComponent);
    expect(() => fixture.detectChanges()).not.toThrow();
  });
});
