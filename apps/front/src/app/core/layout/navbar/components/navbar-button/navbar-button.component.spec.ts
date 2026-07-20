import { TestBed } from '@angular/core/testing';
import { NavbarButtonComponent } from './navbar-button.component';

describe('NavbarButtonComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NavbarButtonComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(NavbarButtonComponent);
    fixture.componentRef.setInput('name', 'Dashboard');
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the provided name as a label', () => {
    const fixture = TestBed.createComponent(NavbarButtonComponent);
    fixture.componentRef.setInput('name', 'Dashboard');
    fixture.detectChanges();

    const label = (fixture.nativeElement as HTMLElement).querySelector('p');
    expect(label?.textContent?.trim()).toBe('Dashboard');
  });

  it('should reflect a name change', () => {
    const fixture = TestBed.createComponent(NavbarButtonComponent);
    fixture.componentRef.setInput('name', 'Dashboard');
    fixture.detectChanges();

    fixture.componentRef.setInput('name', 'Campagnes');
    fixture.detectChanges();

    const label = (fixture.nativeElement as HTMLElement).querySelector('p');
    expect(label?.textContent?.trim()).toBe('Campagnes');
  });
});
