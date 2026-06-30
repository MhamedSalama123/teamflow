export interface UserProfile {
  id: number;
  email: string;
  username: string;
  bio: string | null;
  jobTitle: string | null;
  location: string | null;
  phoneNumber: string | null;
  photoUrl: string | null;
  emailVerified: boolean;
}

export interface UpdateProfileRequest {
  bio: string | null;
  jobTitle: string | null;
  location: string | null;
  phoneNumber: string | null;
}
