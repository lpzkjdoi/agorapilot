import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppNotification, NotificationLevel } from '../../notification.model';
import { NotificationToastComponent } from './notification-toast.component';

function notification(overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id: 1,
    level: 'info',
    message: 'Cette action n’est pas encore disponible.',
    duration: 5_000,
    dismissible: true,
    closing: false,
    ...overrides,
  };
}

describe('NotificationToastComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationToastComponent],
    }).compileComponents();
  });

  function render(
    value: AppNotification = notification(),
  ): ComponentFixture<NotificationToastComponent> {
    const fixture = TestBed.createComponent(NotificationToastComponent);
    fixture.componentRef.setInput('notification', value);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    expect(render().componentInstance).toBeTruthy();
  });

  it('should render the message', () => {
    const compiled = render().nativeElement as HTMLElement;

    expect(compiled.querySelector('.toast-message')?.textContent?.trim()).toBe(
      'Cette action n’est pas encore disponible.',
    );
    expect(compiled.querySelector('.toast-title')).toBeNull();
  });

  it('should render the title above the message when there is one', () => {
    const compiled = render(notification({ title: 'Action indisponible' }))
      .nativeElement as HTMLElement;

    expect(compiled.querySelector('.toast-title')?.textContent?.trim()).toBe('Action indisponible');
    expect(compiled.querySelector('.toast-message')?.textContent?.trim()).toBe(
      'Cette action n’est pas encore disponible.',
    );
  });

  it.each([
    ['success' as NotificationLevel],
    ['info' as NotificationLevel],
    ['warning' as NotificationLevel],
    ['error' as NotificationLevel],
  ])('should carry the %s level as a class and show its icon', (level) => {
    const compiled = render(notification({ level })).nativeElement as HTMLElement;
    const toast = compiled.querySelector('.toast') as HTMLElement;

    expect(toast.classList.contains(`toast-${level}`)).toBe(true);
    expect(toast.querySelector('.toast-icon svg')).not.toBeNull();
  });

  it('should interrupt the screen reader for errors and warnings only', () => {
    const roleOf = (level: NotificationLevel) =>
      (render(notification({ level })).nativeElement as HTMLElement)
        .querySelector('.toast')
        ?.getAttribute('role');

    expect(roleOf('error')).toBe('alert');
    expect(roleOf('warning')).toBe('alert');
    expect(roleOf('success')).toBe('status');
    expect(roleOf('info')).toBe('status');
  });

  it('should emit the id when the close button is pressed', () => {
    const fixture = render(notification({ id: 7 }));
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.toast-close')
      ?.click();

    expect(closed).toHaveBeenCalledWith(7);
  });

  it('should hide the close button when the notification is not dismissible', () => {
    const compiled = render(notification({ dismissible: false })).nativeElement as HTMLElement;

    expect(compiled.querySelector('.toast-close')).toBeNull();
  });

  it('should flag the exit animation while the notification is closing', () => {
    const compiled = render(notification({ closing: true })).nativeElement as HTMLElement;

    expect(compiled.querySelector('.toast')?.classList.contains('is-closing')).toBe(true);
  });
});
