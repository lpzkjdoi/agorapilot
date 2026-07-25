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

  it('should paint the header with the dark blue bar of the maquette', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const header = (fixture.nativeElement as HTMLElement).querySelector('.navbar') as HTMLElement;

    expect(getComputedStyle(header).backgroundColor).toBe('rgb(30, 58, 138)');
  });

  it('should render the navigation icon at the 16px size of the maquette', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const icon = (fixture.nativeElement as HTMLElement).querySelector('.navbar-nav-button svg');

    expect(icon?.getAttribute('width')).toBe('16px');
    expect(icon?.getAttribute('height')).toBe('16px');
  });
});
