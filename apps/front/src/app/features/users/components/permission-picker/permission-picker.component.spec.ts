import { TestBed } from '@angular/core/testing';
import { Permission } from '../../../../core/auth/auth.model';
import { ALL_PERMISSIONS } from '../../permissions';
import { PermissionPickerComponent } from './permission-picker.component';

describe('PermissionPickerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PermissionPickerComponent] }).compileComponents();
  });

  function render(selected: Permission[], grantable: Permission[] = ALL_PERMISSIONS) {
    const fixture = TestBed.createComponent(PermissionPickerComponent);
    fixture.componentRef.setInput('selected', selected);
    fixture.componentRef.setInput('grantable', grantable);
    fixture.detectChanges();
    const emitted: Permission[][] = [];
    fixture.componentInstance.selected.subscribe((value) => emitted.push(value));
    return { fixture, compiled: fixture.nativeElement as HTMLElement, emitted };
  }

  function checkbox(compiled: HTMLElement, code: Permission): HTMLInputElement {
    return compiled.querySelector(`input[value="${ code }"]`) as HTMLInputElement;
  }

  function profileButton(compiled: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(compiled.querySelectorAll<HTMLButtonElement>('.picker-profile'))
                .find((button) => button.textContent?.trim() === label)!;
  }

  it('should render one checkbox per permission, checked when selected', () => {
    const { compiled } = render(['medias:read']);

    expect(compiled.querySelectorAll('input[type="checkbox"]').length).toBe(13);
    expect(checkbox(compiled, 'medias:read').checked).toBe(true);
    expect(checkbox(compiled, 'medias:write').checked).toBe(false);
  });

  it('should add and remove a permission, in the catalogue order', () => {
    const { compiled, emitted } = render(['medias:read']);

    checkbox(compiled, 'publications:read').click();
    expect(emitted.at(-1)).toEqual(['publications:read', 'medias:read']);

    checkbox(compiled, 'medias:read').click();
    expect(emitted.at(-1)).toEqual(['publications:read']);
  });

  it('should tick the permissions of a profile and show it as chosen', () => {
    const { fixture, compiled, emitted } = render([]);

    profileButton(compiled, 'Lecteur').click();
    fixture.detectChanges();

    expect(emitted.at(-1)).toEqual(['publications:read', 'campaigns:read', 'medias:read']);
    expect(profileButton(compiled, 'Lecteur').classList).toContain('picker-profile-active');
  });

  it('should call a set matching no profile « sur mesure »', () => {
    const { compiled } = render(['medias:read']);

    expect(compiled.querySelector('.picker-profiles')?.textContent).toContain('sur mesure');
  });

  it('should freeze the permissions the administrator does not hold, even when applying a profile', () => {
    const { fixture, compiled, emitted } = render(['users:manage'], ['publications:read', 'campaigns:read', 'medias:read']);

    expect(checkbox(compiled, 'users:manage').disabled).toBe(true);
    expect(checkbox(compiled, 'publications:read').disabled).toBe(false);

    profileButton(compiled, 'Administrateur').click();
    fixture.detectChanges();

    // users:manage reste coché (il l'était), medias:write reste vide (pas détenu).
    expect(emitted.at(-1)).toEqual(['publications:read', 'campaigns:read', 'medias:read', 'users:manage']);
  });
});
