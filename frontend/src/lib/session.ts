import { create } from 'zustand';
export type User = { id: string; name: string; email: string; role: 'USER' | 'ADMIN' };
export type Session = { accessToken: string; user: User; expiresIn: number };
// Access tokens intentionally stay in memory and are never persisted to localStorage.
export const useSession = create<{user:User|null; token:string|null; signIn:(session:Session)=>void; signOut:()=>void}>((set)=>({
  user:null,token:null,signIn:(session)=>set({user:session.user,token:session.accessToken}),signOut:()=>set({user:null,token:null}),
}));
