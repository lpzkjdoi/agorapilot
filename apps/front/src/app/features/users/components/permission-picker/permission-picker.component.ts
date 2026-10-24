import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { Permission } from "../../../../core/auth/auth.model";
import { ALL_PERMISSIONS, matchingProfile, PERMISSION_GROUPS, Profile, PROFILES } from "../../permissions";

/**
 * Choix des droits d'un compte : un profil pré-coche, les cases ajustent.
 *
 * Un droit que l'administrateur ne détient pas reste figé : il ne peut ni le
 * donner ni le retirer, et le back le refuserait de toute façon.
 */
@Component({
  selector: 'app-permission-picker',
  templateUrl: './permission-picker.component.html',
  styleUrl: './permission-picker.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PermissionPickerComponent {
  readonly selected = model<Permission[]>([]);
  /** Les droits de l'administrateur qui coche. */
  readonly grantable = input.required<Permission[]>();

  protected readonly groups = PERMISSION_GROUPS;
  protected readonly profiles = PROFILES;
  protected readonly profile = computed(() => matchingProfile(this.selected()));

  protected isSelected(permission: Permission): boolean {
    return this.selected().includes(permission);
  }

  protected isGrantable(permission: Permission): boolean {
    return this.grantable().includes(permission);
  }

  protected toggle(permission: Permission, checked: boolean): void {
    this.update((current) => checked ? [...current, permission] : current.filter((p) => p !== permission));
  }

  /** Les droits figés gardent leur état ; les autres suivent le profil. */
  protected apply(profile: Profile): void {
    this.update((current) => ALL_PERMISSIONS.filter((permission) =>
      this.isGrantable(permission) ? profile.permissions.includes(permission) : current.includes(permission)));
  }

  /** Toujours dans l'ordre du catalogue : la liste envoyée au back est stable. */
  private update(change: (current: Permission[]) => Permission[]): void {
    const next = new Set(change(this.selected()));
    this.selected.set(ALL_PERMISSIONS.filter((permission) => next.has(permission)));
  }
}
