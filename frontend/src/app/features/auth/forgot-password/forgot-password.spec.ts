import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ForgotPassword } from './forgot-password';
import { AuthService } from '../../../core/auth/auth.service';

describe('ForgotPassword', () => {
  let authStub: { forgotPassword: ReturnType<typeof vi.fn> };

  function setup() {
    TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [provideRouter([]), { provide: AuthService, useValue: authStub }],
    });
    const fixture = TestBed.createComponent(ForgotPassword);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    authStub = { forgotPassword: vi.fn(() => of(undefined)) };
  });

  it('submits the email and shows a confirmation', () => {
    const component = setup().componentInstance as any;
    component.form.setValue({ email: 'a@b.com' });

    component.submit();

    expect(authStub.forgotPassword).toHaveBeenCalledWith('a@b.com');
    expect(component.submitted()).toBe(true);
  });

  it('does not submit an invalid email', () => {
    const component = setup().componentInstance as any;
    component.form.setValue({ email: 'not-an-email' });

    component.submit();

    expect(authStub.forgotPassword).not.toHaveBeenCalled();
  });
});
