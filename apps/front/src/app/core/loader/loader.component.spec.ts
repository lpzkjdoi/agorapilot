import { TestBed } from '@angular/core/testing';
import { LoaderComponent } from './loader.component';

describe('LoaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoaderComponent],
    }).compileComponents();
  });

  function createLoader() {
    const fixture = TestBed.createComponent(LoaderComponent);
    fixture.detectChanges();

    return fixture;
  }

  it('should create', () => {
    expect(createLoader().componentInstance).toBeTruthy();
  });

  it('should default to the medium size and the light variant of the maquette', () => {
    const host = createLoader().nativeElement as HTMLElement;

    expect(host.classList.contains('loader-md')).toBe(true);
    expect(host.classList.contains('loader-light')).toBe(true);
  });

  it('should expose the three sizes of the maquette', () => {
    const fixture = createLoader();
    const host = fixture.nativeElement as HTMLElement;

    for (const size of ['sm', 'md', 'lg'] as const) {
      fixture.componentRef.setInput('size', size);
      fixture.detectChanges();

      expect(host.classList.contains(`loader-${size}`)).toBe(true);
    }
  });

  it('should switch to the dark variant, celle du bandeau bleu nuit', () => {
    const fixture = createLoader();
    const host = fixture.nativeElement as HTMLElement;

    fixture.componentRef.setInput('variant', 'dark');
    fixture.detectChanges();

    expect(host.classList.contains('loader-dark')).toBe(true);
    expect(host.classList.contains('loader-light')).toBe(false);
  });

  it('should draw a track and an arc covering 72% of the ring', () => {
    const host = createLoader().nativeElement as HTMLElement;

    const svg = host.querySelector('svg');
    expect(svg?.getAttribute('viewBox')).toBe('0 0 44 44');
    // L'anneau est décoratif : le texte est porté par le libellé, pas par le SVG.
    expect(svg?.getAttribute('aria-hidden')).toBe('true');

    expect(host.querySelector('.loader-track')).not.toBeNull();

    // `pathLength="100"` fait du `stroke-dasharray: 72 28` du CSS un pourcentage.
    const arc = host.querySelector('.loader-arc');
    expect(arc?.getAttribute('pathLength')).toBe('100');
    expect(arc?.getAttribute('r')).toBe('18');
  });

  it('should announce the wait to screen readers', () => {
    const fixture = createLoader();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.getAttribute('role')).toBe('status');
    expect(host.querySelector('.loader-label')?.textContent?.trim())
      .toBe('Chargement en cours…');

    fixture.componentRef.setInput('label', 'Chargement des publications…');
    fixture.detectChanges();

    expect(host.querySelector('.loader-label')?.textContent?.trim())
      .toBe('Chargement des publications…');
  });
});
