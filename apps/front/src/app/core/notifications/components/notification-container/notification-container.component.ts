import {
  afterEveryRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChildren,
} from '@angular/core';
import {
  NOTIFICATION_STACK_GAP_PX,
  NOTIFICATION_STACK_SCALE_STEP,
} from '../../notification.model';
import { NotificationService } from '../../notification.service';
import { NotificationToastComponent } from '../notification-toast/notification-toast.component';

/**
 * Pile de toasts. Ce composant n'est jamais déclaré dans un template : il est
 * instancié dans `<body>` par `NotificationPortalService`, de sorte que les
 * notifications flottent au-dessus de la page quel que soit le contexte
 * d'empilement (`overflow`, `transform`, `z-index`) de la vue courante.
 *
 * L'empilement reprend celui de la maquette (`sonner`, `expand` par défaut à
 * `false`) : au repos, seule la notification de tête est entièrement visible,
 * les précédentes dépassent derrière, réduites d'un cran par niveau de
 * profondeur ; au survol ou dès qu'une croix prend le focus, la pile se déplie
 * et chacune reprend sa hauteur. Les positions sont calculées ici plutôt que
 * laissées à un flux normal, car passer de l'un à l'autre doit s'animer.
 */
@Component({
  selector: 'app-notification-container',
  imports: [NotificationToastComponent],
  templateUrl: './notification-container.component.html',
  styleUrl: './notification-container.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'region',
    'aria-label': 'Notifications',
    '[style.height]': 'stackHeight()',
    '(mouseenter)': 'expanded.set(true)',
    '(mouseleave)': 'expanded.set(false)',
    '(focusin)': 'expanded.set(true)',
    '(focusout)': 'expanded.set(false)',
    '(window:resize)': 'forgetHeights()',
  },
})
export class NotificationContainerComponent {
  private readonly notificationService = inject(NotificationService);

  protected readonly notifications = this.notificationService.notifications;

  /** La pile est dépliée tant qu'elle est survolée ou qu'elle a le focus. */
  protected readonly expanded = signal(false);

  private readonly toastHosts = viewChildren('toastHost', { read: ElementRef<HTMLElement> });

  /**
   * Hauteur naturelle de chaque notification, par identifiant.
   *
   * Mesurée une fois, à l'insertion, tant que la notification n'a pas encore de
   * hauteur imposée — c'est cette hauteur qui sert ensuite à aligner la pile
   * dépliée et à raboter les notifications de derrière sur celle de tête.
   */
  private readonly heights = signal<ReadonlyMap<number, number>>(new Map());

  /** Hauteur de la pile : celle de la notification de tête, ou la somme dépliée. */
  protected readonly stackHeight = computed<string | null>(() => {
    const stack = this.notifications();
    if (stack.length === 0) {
      return '0px';
    }

    const heights = this.heights();

    if (!this.expanded()) {
      const front = heights.get(stack[stack.length - 1].id);
      return front === undefined ? null : `${front}px`;
    }

    let total = NOTIFICATION_STACK_GAP_PX * (stack.length - 1);
    for (const notification of stack) {
      const height = heights.get(notification.id);
      if (height === undefined) {
        return null;
      }
      total += height;
    }

    return `${total}px`;
  });

  constructor() {
    afterEveryRender(() => this.measure());
  }

  /**
   * Position d'une notification dans la pile. `index` suit l'ordre du DOM, du
   * plus ancien au plus récent ; la profondeur s'en déduit à l'envers, la plus
   * récente étant en tête (profondeur 0).
   */
  protected toastStyle(index: number): Record<string, string> {
    const stack = this.notifications();
    const depth = stack.length - 1 - index;
    const heights = this.heights();

    const style: Record<string, string> = { 'z-index': String(stack.length - depth) };

    if (this.expanded()) {
      style['transform'] = `translateY(${this.offsetAt(depth)}px)`;
      const own = heights.get(stack[index].id);
      if (own !== undefined) {
        style['height'] = `${own}px`;
      }
      return style;
    }

    const scale = 1 - depth * NOTIFICATION_STACK_SCALE_STEP;
    style['transform'] = `translateY(${depth * NOTIFICATION_STACK_GAP_PX}px) scale(${scale})`;

    // Repliées, celles de derrière sont rabotées sur la notification de tête :
    // sans quoi une notification plus haute dépasserait par le bas.
    const height = depth === 0
      ? heights.get(stack[index].id)
      : heights.get(stack[stack.length - 1].id);
    if (height !== undefined) {
      style['height'] = `${height}px`;
    }

    return style;
  }

  protected close(id: number): void {
    this.notificationService.closeNotification(id);
  }

  /** La largeur a changé : les textes se replient, les hauteurs sont à refaire. */
  protected forgetHeights(): void {
    this.heights.set(new Map());
  }

  /** Décalage d'une notification dépliée : ce que celles devant elle occupent. */
  private offsetAt(depth: number): number {
    const stack = this.notifications();
    const heights = this.heights();

    let offset = 0;
    for (let ahead = 0; ahead < depth; ahead++) {
      const height = heights.get(stack[stack.length - 1 - ahead].id) ?? 0;
      offset += height + NOTIFICATION_STACK_GAP_PX;
    }

    return offset;
  }

  private measure(): void {
    const stack = this.notifications();
    const hosts = this.toastHosts();
    if (hosts.length !== stack.length) {
      return;
    }

    const measured = new Map(this.heights());
    let changed = false;

    hosts.forEach((host, index) => {
      const id = stack[index].id;
      if (measured.has(id)) {
        return;
      }

      // `offsetHeight` ignore les transformations : c'est bien la hauteur de
      // mise en page, pas celle du toast réduit à l'écran. Zéro signifie
      // « pas encore de mise en page » (jsdom, notification masquée…).
      const height = host.nativeElement.offsetHeight;
      if (height > 0) {
        measured.set(id, height);
        changed = true;
      }
    });

    if (changed) {
      this.heights.set(measured);
    }
  }
}
