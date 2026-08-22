import { TestBed } from '@angular/core/testing';
import { EnvironmentBadgeComponent } from './environment-badge.component';
import { buildInfo } from '../../../../../../environments/build-info';

describe('EnvironmentBadgeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentBadgeComponent],
    }).compileComponents();
  });

  const render = (build?: { environment: 'dev' | 'preprod' | 'prod'; version: string }) => {
    const fixture = TestBed.createComponent(EnvironmentBadgeComponent);
    if (build) {
      fixture.componentRef.setInput('build', build);
    }
    fixture.detectChanges();
    return fixture;
  };

  it('should create', () => {
    expect(render().componentInstance).toBeTruthy();
  });

  it('should fall back to the identity of the current build', () => {
    const fixture = render();

    expect(fixture.componentInstance.build()).toBe(buildInfo);
  });

  it('should show the environment name in dev', () => {
    const fixture = render({ environment: 'dev', version: 'dev' });

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('dev');
  });

  it('should show the environment name in preprod', () => {
    const fixture = render({ environment: 'preprod', version: '9f2c1ab' });

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('preprod');
  });

  it('should show the version number in prod', () => {
    const fixture = render({ environment: 'prod', version: '1.2.0' });

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('1.2.0');
  });

  it('should reflect a change of build identity', () => {
    const fixture = render({ environment: 'preprod', version: '1.2.0' });

    fixture.componentRef.setInput('build', { environment: 'prod', version: '1.2.0' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('1.2.0');
  });

  it('should apply the discreet monospace styling of the maquette', () => {
    const styles = getComputedStyle(render().nativeElement as HTMLElement);

    expect(styles.fontSize).toBe('10px');
    expect(styles.color).toBe('rgba(255, 255, 255, 0.3)');
    expect(styles.borderColor).toBe('rgba(255, 255, 255, 0.1)');
    expect(styles.fontFamily).toContain('monospace');
  });
});
