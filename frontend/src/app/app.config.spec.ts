import { TestBed } from '@angular/core/testing';
import { DateAdapter } from '@angular/material/core';

import { appConfig } from './app.config';

describe('appConfig', () => {
  // The period form uses mat-datepicker, which throws at runtime without an adapter. Nothing
  // else in the build catches a missing one: the component compiles perfectly well.
  it('provides a date adapter for the date pickers', () => {
    TestBed.configureTestingModule({ providers: [...appConfig.providers] });

    expect(TestBed.inject(DateAdapter)).toBeTruthy();
  });
});
