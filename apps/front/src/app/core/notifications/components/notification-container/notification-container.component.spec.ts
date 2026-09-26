import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  NOTIFICATION_EXIT_DURATION_MS,
  NOTIFICATION_STACK_GAP_PX,
} from '../../notification.model';
import { NotificationService } from '../../notification.service';
import { NotificationContainerComponent } from './notification-container.component';

describe('NotificationContainerComponent', () => {
  let fixture: ComponentFixture<NotificationContainerComponent>;
  let host: HTMLElement;
  let service: NotificationService;

  beforeEach(async () => {
    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [NotificationContainerComponent],
    }).compileComponents();

    service = TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(NotificationContainerComponent);
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Les hôtes de toast, dans l'ordre du DOM : du plus ancien au plus récent. */
  function toasts(): HTMLElement[] {
    return Array.from(host.querySelectorAll<HTMLElement>('app-notification-toast'));
  }

  function hover(entering: boolean): void {
    host.dispatchEvent(new MouseEvent(entering ? 'mouseenter' : 'mouseleave'));
    fixture.detectChanges();
  }

  it('should render nothing when the stack is empty', () => {
    expect(toasts().length).toBe(0);
  });

  it('should expose the stack as a labelled region', () => {
    expect(host.getAttribute('role')).toBe('region');
    expect(host.getAttribute('aria-label')).toBe('Notifications');
  });

  it('should render one toast per notification, oldest first in the DOM', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'error', message: 'Seconde' });
    fixture.detectChanges();

    const messages = Array.from(host.querySelectorAll('.toast-message')).map((element) =>
      element.textContent?.trim(),
    );
    expect(messages).toEqual(['Première', 'Seconde']);
  });

  it('should float above the page, anchored top right like the maquette', () => {
    const styles = getComputedStyle(host);

    expect(styles.position).toBe('fixed');
    expect(styles.top).toBe('2rem');
    expect(styles.right).toBe('2rem');
  });

  it('should keep the newest toast in front and tuck the previous ones behind', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'info', message: 'Seconde' });
    fixture.detectChanges();

    const [older, newest] = toasts();

    // La plus récente est en tête : ni décalage, ni réduction, et au-dessus.
    expect(newest.style.transform).toBe('translateY(0px) scale(1)');
    expect(newest.style.zIndex).toBe('2');

    // Celle de derrière dépasse d'un cran et perd 5 %.
    expect(older.style.transform).toBe(`translateY(${NOTIFICATION_STACK_GAP_PX}px) scale(0.95)`);
    expect(older.style.zIndex).toBe('1');
  });

  it('should unfold the stack while it is hovered, and fold it back after', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'info', message: 'Seconde' });
    fixture.detectChanges();

    hover(true);
    // Dépliée, chacune reprend sa taille : plus aucune réduction.
    for (const toast of toasts()) {
      expect(toast.style.transform).not.toContain('scale');
    }

    hover(false);
    expect(toasts()[0].style.transform).toContain('scale(0.95)');
  });

  it('should unfold the stack when a close button takes the focus', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'info', message: 'Seconde' });
    fixture.detectChanges();

    host.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    fixture.detectChanges();

    expect(toasts()[0].style.transform).not.toContain('scale');
  });

  it('should close the notification when its toast asks for it', () => {
    service.sendNotification({ level: 'info', message: 'À fermer' });
    fixture.detectChanges();

    host.querySelector<HTMLButtonElement>('.toast-close')?.click();
    fixture.detectChanges();

    // Le temps de l'animation, le toast reste affiché en état de sortie.
    expect(host.querySelector('.toast')?.classList.contains('is-closing')).toBe(true);

    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    fixture.detectChanges();
    expect(toasts().length).toBe(0);
  });

  it('should empty itself on closeAll()', () => {
    service.sendNotification({ level: 'info', message: 'Première' });
    service.sendNotification({ level: 'info', message: 'Seconde' });
    fixture.detectChanges();

    service.closeAll();
    vi.advanceTimersByTime(NOTIFICATION_EXIT_DURATION_MS);
    fixture.detectChanges();

    expect(toasts().length).toBe(0);
  });
});
