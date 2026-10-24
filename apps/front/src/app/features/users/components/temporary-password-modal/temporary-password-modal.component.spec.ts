import { TestBed } from '@angular/core/testing';
import { TemporaryPasswordModalComponent } from './temporary-password-modal.component';

describe('TemporaryPasswordModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TemporaryPasswordModalComponent] }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(TemporaryPasswordModalComponent);
    fixture.componentRef.setInput('username', 'camille');
    fixture.componentRef.setInput('password', 'abcd-efgh-jkmn-pqrs');
    fixture.detectChanges();
    return { fixture, compiled: fixture.nativeElement as HTMLElement };
  }

  it('should show the password once, with whom it belongs to', () => {
    const { compiled } = render();

    expect(compiled.querySelector('#temporary-title')?.textContent).toContain('camille');
    expect(compiled.querySelector('.temporary-value')?.textContent).toBe('abcd-efgh-jkmn-pqrs');
    expect(compiled.textContent).toContain('Il ne sera plus affiché');
  });

  it('should copy the password to the clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const { fixture, compiled } = render();

    compiled.querySelector<HTMLButtonElement>('.temporary-copy')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledWith('abcd-efgh-jkmn-pqrs');
    expect(compiled.querySelector('.temporary-copy')?.textContent).toContain('Copié');
  });

  it('should close only on the explicit button, not on Escape', () => {
    const { fixture, compiled } = render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).not.toHaveBeenCalled();

    compiled.querySelector<HTMLButtonElement>('.modal-actions .primary')!.click();
    expect(closed).toHaveBeenCalledTimes(1);
  });
});
