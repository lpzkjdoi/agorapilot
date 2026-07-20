import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NavbarComponent } from './navbar.component';

describe('NavbarComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the AgoraPilot logo (badge + wordmark)', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.navbar-logo-badge')?.textContent?.trim()).toBe('AP');
    expect(compiled.querySelector('.navbar-logo-text')?.textContent?.trim()).toBe('AgoraPilot');
  });

  it('should render the Dashboard navigation entry linking to /dashboard', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const button = compiled.querySelector('.navbar-nav-button');
    expect(button).not.toBeNull();
    expect(button?.tagName.toLowerCase()).toBe('button');
    expect(button?.textContent).toContain('Dashboard');
    // The Dashboard entry is wired to the router (routerLink="/dashboard").
    expect(button?.getAttribute('routerlink')).toBe('/dashboard');
  });
});
