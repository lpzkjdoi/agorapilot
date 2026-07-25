import { TestBed } from '@angular/core/testing';
import { DashboardKpiComponent } from './dashboard-kpi.component';

describe('DashboardKpiComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardKpiComponent],
    }).compileComponents();
  });

  function createWith(title: string, value: number, color?: string) {
    const fixture = TestBed.createComponent(DashboardKpiComponent);
    fixture.componentRef.setInput('title', title);
    fixture.componentRef.setInput('value', value);
    if (color !== undefined) {
      fixture.componentRef.setInput('color', color);
    }
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = createWith('Campagnes actives', 4);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the title and the value', () => {
    const fixture = createWith('Campagnes actives', 4);
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.kpi-card-header .kpi-title')?.textContent?.trim()).toBe(
      'Campagnes actives',
    );
    expect(compiled.querySelector('.kpi-value')?.textContent?.trim()).toBe('4');
  });

  it('should apply the color as the icon badge background', () => {
    const fixture = createWith('Rappels prévus', 48, 'rgb(42, 42, 42)');
    const badge = (fixture.nativeElement as HTMLElement).querySelector(
      '.kpi-icon-badge',
    ) as HTMLElement;

    expect(badge.style.backgroundColor).toBe('rgb(42, 42, 42)');
  });
});
