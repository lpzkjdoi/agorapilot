import { TestBed } from '@angular/core/testing';
import { DashboardKpiComponent } from './dashboard-kpi.component';

describe('DashboardKpiComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardKpiComponent],
    }).compileComponents();
  });

  function createWith(title: string, value: number | null, options: { color?: string, alert?: boolean } = {}) {
    const fixture = TestBed.createComponent(DashboardKpiComponent);
    fixture.componentRef.setInput('title', title);
    fixture.componentRef.setInput('value', value);
    if (options.color !== undefined) {
      fixture.componentRef.setInput('color', options.color);
    }
    if (options.alert !== undefined) {
      fixture.componentRef.setInput('alert', options.alert);
    }
    fixture.detectChanges();
    return fixture;
  }

  function valueOf(fixture: ReturnType<typeof createWith>): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.kpi-value') as HTMLElement;
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
    expect(valueOf(fixture).textContent?.trim()).toBe('4');
  });

  it('should render zero rather than the placeholder', () => {
    expect(valueOf(createWith('Diffusions en échec', 0)).textContent?.trim()).toBe('0');
  });

  it('should render a dash while the value is unknown', () => {
    expect(valueOf(createWith('Campagnes actives', null)).textContent?.trim()).toBe('–');
  });

  it('should apply the color as the icon badge background', () => {
    const fixture = createWith('Diffusions en échec', 2, { color: 'rgb(42, 42, 42)' });
    const badge = (fixture.nativeElement as HTMLElement).querySelector(
      '.kpi-icon-badge',
    ) as HTMLElement;

    expect(badge.style.backgroundColor).toBe('rgb(42, 42, 42)');
  });

  it('should flag the value only when alert is set', () => {
    expect(valueOf(createWith('Diffusions en échec', 2, { alert: true })).classList).toContain('kpi-value-alert');
    expect(valueOf(createWith('Diffusions en échec', 0)).classList).not.toContain('kpi-value-alert');
  });
});
