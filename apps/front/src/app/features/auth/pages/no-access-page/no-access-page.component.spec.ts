import { TestBed } from '@angular/core/testing';
import { NoAccessPageComponent } from './no-access-page.component';

describe('NoAccessPageComponent', () => {
  it('should tell the account it has no permission yet and whom to ask', async () => {
    await TestBed.configureTestingModule({ imports: [NoAccessPageComponent] }).compileComponents();
    const fixture = TestBed.createComponent(NoAccessPageComponent);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Aucun droit pour le moment');
    expect(text).toContain('administrateur');
  });
});
