import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type Preferences = { city: string; saved: string[]; setCity: (city: string) => void; toggleSaved: (id: string) => void };
export const usePreferences = create<Preferences>()(persist((set) => ({
  city: 'Mumbai', saved: [],
  setCity: (city) => set({ city }),
  toggleSaved: (id) => set((s) => ({ saved: s.saved.includes(id) ? s.saved.filter((item) => item !== id) : [...s.saved, id] })),
}), { name: 'cinenext-preferences', version: 1 }));
