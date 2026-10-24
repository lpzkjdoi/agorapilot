import { TestBed } from '@angular/core/testing';
import { ALL_PERMISSIONS } from '../../permissions';
import { Account } from '../../user.model';
import { AccountFormValue, AccountModalComponent } from './account-modal.component';

const camille: Account = {
  id: 2, username: 'camille', status: 'ACTIVE', permissions: ['medias:read'],
  lockedUntil: null, lastLoginAt: null, createdAt: null,
};

describe('AccountModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AccountModalComponent] }).compileComponents();
  });

  function render(account: Account | null = null) {
    const fixture = TestBed.createComponent(AccountModalComponent);
    fixture.componentRef.setInput('account', account);
    fixture.componentRef.setInput('grantable', ALL_PERMISSIONS);
    fixture.detectChanges();
    const submitted: AccountFormValue[] = [];
    fixture.componentInstance.submitted.subscribe((value) => submitted.push(value));
    return { fixture, compiled: fixture.nativeElement as HTMLElement, submitted };
  }

  async function typeUsername(fixture: ReturnType<typeof render>['fixture'], compiled: HTMLElement, value: string) {
    const input = compiled.querySelector('#account-username') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(fixture: ReturnType<typeof render>['fixture'], compiled: HTMLElement) {
    compiled.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('should create an account with a normalised username and the chosen profile', async () => {
    const { fixture, compiled, submitted } = render();

    await typeUsername(fixture, compiled, '  Camille.Durand ');
    Array.from(compiled.querySelectorAll<HTMLButtonElement>('.picker-profile'))
         .find((button) => button.textContent?.trim() === 'Lecteur')!.click();
    fixture.detectChanges();
    submit(fixture, compiled);

    expect(submitted).toEqual([{
      username: 'camille.durand',
      permissions: ['publications:read', 'campaigns:read', 'medias:read'],
    }]);
  });

  it('should refuse a username outside the rule without calling back', async () => {
    const { fixture, compiled, submitted } = render();

    await typeUsername(fixture, compiled, 'camille durand');
    submit(fixture, compiled);

    expect(submitted).toEqual([]);
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('ne respecte pas la règle');
  });

  it('should warn that an account without permission sees nothing', () => {
    const { compiled } = render();

    expect(compiled.textContent).toContain('ne verra rien');
  });

  it('should edit the permissions of an existing account, starting from them', () => {
    const { fixture, compiled, submitted } = render(camille);

    expect(compiled.querySelector('#account-title')?.textContent).toContain('Droits de camille');
    expect(compiled.querySelector('#account-username')).toBeNull();
    expect((compiled.querySelector('input[value="medias:read"]') as HTMLInputElement).checked).toBe(true);

    (compiled.querySelector('input[value="medias:write"]') as HTMLInputElement).click();
    fixture.detectChanges();
    submit(fixture, compiled);

    expect(submitted).toEqual([{ username: 'camille', permissions: ['medias:read', 'medias:write'] }]);
  });

  it('should show the refusal of the back', () => {
    const { fixture, compiled } = render();
    fixture.componentRef.setInput('error', 'Le pseudo « camille » est déjà pris.');
    fixture.detectChanges();

    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('déjà pris');
  });

  it('should close on Escape and on the cancel button', () => {
    const { fixture, compiled } = render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    compiled.querySelector<HTMLButtonElement>('.modal-actions .secondary')!.click();

    expect(closed).toHaveBeenCalledTimes(2);
  });
});
