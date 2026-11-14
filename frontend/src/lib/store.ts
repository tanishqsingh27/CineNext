import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type Preferences = { city: string; saved: string[]; setCity: (city: string) => void; toggleSaved: (id: string) => void };
export const usePreferences = create<Preferences>()(persist((set) => ({
  city: '', saved: [],
  setCity: (city) => set({ city }),
  toggleSaved: (id) => set((s) => ({ saved: s.saved.includes(id) ? s.saved.filter((item) => item !== id) : [...s.saved, id] })),
}), {
  name: 'cinenext-preferences', version: 2,
  // Older versions selected a seeded city automatically. Keep bookmarks, reset that filter.
  migrate: (previous) => ({ city: '', saved: (previous as Partial<Preferences>)?.saved || [] }),
}));
