import { DOCUMENT } from '@angular/common';
import {
  ApplicationRef,
  ComponentRef,
  createComponent,
  DestroyRef,
  EnvironmentInjector,
  inject,
  Injectable,
} from '@angular/core';
import {
  NotificationContainerComponent,
} from './components/notification-container/notification-container.component';

/**
 * « Portal » de notifications.
 *
 * Angular n'a pas de `createPortal` natif : la notion existe dans le CDK
 * (`@angular/cdk/portal`, `Overlay`), non installé ici. On la reproduit avec les
 * API du framework — `createComponent()` pour instancier le conteneur sur un
 * élément hôte créé à la volée, `ApplicationRef.attachView()` pour le brancher
 * sur la détection de changement. Résultat : le conteneur vit directement dans
 * `<body>`, en dehors de l'arborescence du composant qui déclenche la
 * notification, donc à l'abri des `overflow`, `transform` et `z-index` locaux.
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationPortalService {
  private readonly applicationRef = inject(ApplicationRef);
  private readonly environmentInjector = inject(EnvironmentInjector);
  private readonly document = inject(DOCUMENT);

  private componentRef: ComponentRef<NotificationContainerComponent> | null = null;
  private hostElement: HTMLElement | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detach());
  }

  /** Monte le conteneur dans `<body>`. Sans effet s'il y est déjà. */
  attach(): void {
    if (this.componentRef) {
      return;
    }

    const host = this.document.createElement('aside');
    this.document.body.appendChild(host);

    this.componentRef = createComponent(NotificationContainerComponent, {
      environmentInjector: this.environmentInjector,
      hostElement: host,
    });
    this.applicationRef.attachView(this.componentRef.hostView);
    this.hostElement = host;
  }

  /** Démonte le conteneur et retire son élément hôte du DOM. */
  detach(): void {
    if (!this.componentRef) {
      return;
    }

    this.applicationRef.detachView(this.componentRef.hostView);
    this.componentRef.destroy();
    this.hostElement?.remove();

    this.componentRef = null;
    this.hostElement = null;
  }
}
