import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NOTIFICATION_EXIT_DURATION_MS } from '../../notification.model';
import { NotificationService } from '../../notification.service';
import { NotificationContainerComponent } from './notification-container.component';

describe('NotificationContainerComponent', () => {
  let fixture: ComponentFixture<NotificationContainerComponent>;
  let compiled: HTMLElement;
  let service: NotificationService;

  beforeEach(async () => {
    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [NotificationContainerComponent],
    }).compileComponents();

    service = TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(NotificationContainerComponent);
    compiled = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should render nothing when the stack is empty', () => {
    expect(compiled.querySelectorAll('app-notification-toast').length).toBe(0);
  });

  it('should expose the stack as a labelled region', () => {
    expect(fixture.debugElement.nativeElement.getAttribute('role')).toBe('region');
    expect(fixture.debugElement.nativeElement.getAttribute('aria-label')).toBe('Notifications');
  });

  it('should render one toast per notification, oldest first', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'error', message: 'Seconde' });
    fixture.detectChanges();

    const messages = Array.from(compiled.querySelectorAll('.toast-message')).map((element) =>
      element.textContent?.trim(),
    );
    expect(messages).toEqual(['Première', 'Seconde']);
  });

  it('should close the notification when its toast asks for it', () => {
    service.sendNotification({ level: 'info', message: 'À fermer' });
    fixture.detectChanges();

    compiled.querySelector<HTMLButtonElement>('.toast-close')?.click();
    fixture.detectChanges();

    // Le temps de l'animation, le toast reste affiché en état de sortie.
    expect(compiled.querySelector('.toast')?.classList.contains('is-closing')).toBe(true);

    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    fixture.detectChanges();
    expect(compiled.querySelectorAll('app-notification-toast').length).toBe(0);
  });

  it('should empty itself on closeAll()', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'info', message: 'Seconde' });
    fixture.detectChanges();

    service.closeAll();
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    fixture.detectChanges();

    expect(compiled.querySelectorAll('app-notification-toast').length).toBe(0);
  });

  it('should float above the page, anchored to the bottom right of the maquette', () => {
    const styles = getComputedStyle(fixture.debugElement.nativeElement as HTMLElement);

    expect(styles.position).toBe('fixed');
    expect(styles.pointerEvents).toBe('none');
  });
});
