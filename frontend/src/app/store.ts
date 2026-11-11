import { create } from "zustand";
import { loadAuth, saveAuth, type StoredAuth } from "../shared/api/client";
import type { TokenResponse, UserDto } from "../shared/api/types";

interface AuthStore {
  user: UserDto | null;
  accessToken: string | null;
  refreshToken: string | null;
  setAuth: (tokens: TokenResponse) => void;
  setUser: (user: UserDto | null) => void;
  logout: () => void;
}

const initial: StoredAuth | null = loadAuth();

export const useAuthStore = create<AuthStore>((set) => ({
  user: initial?.user ?? null,
  accessToken: initial?.accessToken ?? null,
  refreshToken: initial?.refreshToken ?? null,
  setAuth: (tokens) => {
    saveAuth({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, user: tokens.user });
    set({ user: tokens.user, accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
  },
  setUser: (user) => {
    const current = loadAuth();
    if (!user) {
      saveAuth(null);
      set({ user: null, accessToken: null, refreshToken: null });
      return;
    }
    if (current) {
      saveAuth({ ...current, user });
    }
    set({ user });
  },
  logout: () => {
    saveAuth(null);
    set({ user: null, accessToken: null, refreshToken: null });
  },
}));

export function isAdmin(user: UserDto | null): boolean {
  return user?.role === "ADMIN";
}
