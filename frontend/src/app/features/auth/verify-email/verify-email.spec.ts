import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { VerifyEmail } from './verify-email';
import { AuthService } from '../../../core/auth/auth.service';

const TOKENS = { accessToken: 't', refreshToken: 'r', tokenType: 'Bearer' };

describe('VerifyEmail', () => {
  let authStub: { verifyEmail: ReturnType<typeof vi.fn>; resendVerification: ReturnType<typeof vi.fn> };

  function setup() {
    TestBed.configureTestingModule({
      imports: [VerifyEmail],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authStub },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ email: 'a@b.com' }) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(VerifyEmail);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    authStub = {
      verifyEmail: vi.fn(() => of(TOKENS)),
      resendVerification: vi.fn(() => of(undefined)),
    };
  });

  it('reads the email from the query params', () => {
    const component = setup().componentInstance as unknown as { email: () => string };
    expect(component.email()).toBe('a@b.com');
  });

  it('verifies the code and navigates home on success', () => {
    const fixture = setup();
    const component = fixture.componentInstance as any;
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    component.form.setValue({ code: '123456' });
    component.submit();

    expect(authStub.verifyEmail).toHaveBeenCalledWith({ email: 'a@b.com', code: '123456' });
    expect(navSpy).toHaveBeenCalledWith('/');
  });

  it('shows a lockout message on 429', () => {
    authStub.verifyEmail = vi.fn(() => throwError(() => ({ status: 429 })));
    const component = setup().componentInstance as any;

    component.form.setValue({ code: '123456' });
    component.submit();

    expect(component.error()).toContain('Too many');
  });

  it('starts a 60s cooldown after resending and counts down', () => {
    vi.useFakeTimers();
    try {
      const component = setup().componentInstance as any;

      component.resend();
      expect(authStub.resendVerification).toHaveBeenCalledWith('a@b.com');
      expect(component.cooldown()).toBe(60);

      vi.advanceTimersByTime(1000);
      expect(component.cooldown()).toBe(59);
    } finally {
      vi.useRealTimers();
    }
  });
});
