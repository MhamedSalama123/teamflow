export interface UserProfile {
  id: number;
  email: string;
  username: string;
  fullName: string | null;
  bio: string | null;
  jobTitle: string | null;
  location: string | null;
  phoneNumber: string | null;
  photoUrl: string | null;
  emailVerified: boolean;
}

export interface UpdateProfileRequest {
  fullName: string | null;
  bio: string | null;
  jobTitle: string | null;
  location: string | null;
  phoneNumber: string | null;
}
