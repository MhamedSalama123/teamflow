export interface RegisterRequest {
  email: string;
  username: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

export interface RegistrationResponse {
  email: string;
  message: string;
}

export interface VerifyEmailRequest {
  email: string;
  code: string;
}
