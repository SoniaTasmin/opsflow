import { TestBed } from '@angular/core/testing';

import { StatusChipComponent } from './status-chip.component';

describe('StatusChipComponent', () => {
  async function render(status: string) {
    const fixture = TestBed.createComponent(StatusChipComponent);
    fixture.componentRef.setInput('status', status);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the status text', async () => {
    expect((await render('OPEN')).textContent).toContain('OPEN');
  });

  it('marks live states as positive', async () => {
    expect((await render('ACTIVE')).querySelector('.chip--positive')).toBeTruthy();
    expect((await render('OPEN')).querySelector('.chip--positive')).toBeTruthy();
  });

  it('mutes ended states', async () => {
    expect((await render('ARCHIVED')).querySelector('.chip--muted')).toBeTruthy();
    expect((await render('CLOSED')).querySelector('.chip--muted')).toBeTruthy();
  });

  it('falls back to neutral', async () => {
    expect((await render('DRAFT')).querySelector('.chip--neutral')).toBeTruthy();
  });
});
