import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ResetPassword } from './reset-password';
import { AuthService } from '../../../core/auth/auth.service';

describe('ResetPassword', () => {
  let authStub: { resetPassword: ReturnType<typeof vi.fn> };

  function setup() {
    TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ token: 'reset-token' }) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(ResetPassword);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    authStub = { resetPassword: vi.fn(() => of(undefined)) };
  });

  it('reads the token from the query params', () => {
    const component = setup().componentInstance as unknown as { token: () => string };
    expect(component.token()).toBe('reset-token');
  });

  it('does not submit when the passwords do not match', () => {
    const component = setup().componentInstance as any;
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'different1' });

    component.submit();

    expect(authStub.resetPassword).not.toHaveBeenCalled();
  });

  it('resets the password and navigates to login on success', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    component.form.setValue({ newPassword: 'password123', confirmPassword: 'password123' });
    component.submit();

    expect(authStub.resetPassword).toHaveBeenCalledWith('reset-token', 'password123');
    expect(navSpy).toHaveBeenCalledWith('/login');
  });

  it('shows an error for an invalid or expired token', () => {
    authStub.resetPassword = vi.fn(() => throwError(() => ({ status: 400 })));
    const component = setup().componentInstance as any;

    component.form.setValue({ newPassword: 'password123', confirmPassword: 'password123' });
    component.submit();

    expect(component.error()).toContain('invalid or has expired');
  });
});
