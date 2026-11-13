import axios from 'axios';
import type { Filters, Movie, Page, SeatMap, Showtime, Theatre } from './types';
import { useSession, type Session } from './session';

export const api = axios.create({ baseURL: `${import.meta.env.VITE_API_BASE_URL || ''}/api/v1`, timeout: 12000 });
api.interceptors.request.use((config) => {
  const token=useSession.getState().token;
  if(token) config.headers.Authorization=`Bearer ${token}`;
  return config;
});
api.interceptors.response.use((response)=>response,(error:unknown)=>{
  if(axios.isAxiosError(error)&&error.response?.status===401&&!error.config?.url?.startsWith('/auth/')) useSession.getState().signOut();
  return Promise.reject(error);
});
export type MovieQuery = { q?: string; genre?: string; language?: string; city?: string; page?: number; size?: number };
export const catalogueApi = {
  movies: async (params: MovieQuery, signal?: AbortSignal) => (await api.get<Page<Movie>>('/movies', { params, signal })).data,
  movie: async (id: string, signal?: AbortSignal) => (await api.get<Movie>(`/movies/${encodeURIComponent(id)}`, { signal })).data,
  filters: async (signal?: AbortSignal) => (await api.get<Filters>('/catalogue/filters', { signal })).data,
  theatres: async (city: string, signal?: AbortSignal) => (await api.get<Theatre[]>('/theatres', { params: { city }, signal })).data,
  showtimes: async (movieId: string, city: string, date: string, signal?: AbortSignal) => (await api.get<Showtime[]>('/showtimes', { params: { movieId, city, date }, signal })).data,
  seats: async (id: string, signal?: AbortSignal) => (await api.get<SeatMap>(`/showtimes/${encodeURIComponent(id)}/seats`, { signal })).data,
};
export function errorMessage(error: unknown) {
  if(axios.isAxiosError(error) && typeof error.response?.data?.detail==='string') return error.response.data.detail;
  if (axios.isAxiosError(error) && error.response?.status === 404) return 'This title or showtime is no longer available.';
  return 'We couldn’t load the cinema catalogue. Please try again in a moment.';
}
export type Hold = {id:string;showtimeId:string;seatIds:string[];seatLabels:string[];totalPaise:number;currency:string;expiresAt:string;status:string;movieTitle:string;theatreName:string;screenName:string;startsAt:string};
export type Booking = {id:string;showtimeId:string;movieTitle:string;theatreName:string;screenName:string;startsAt:string;seatLabels:string[];totalPaise:number;currency:string;status:'CONFIRMED'|'CANCELLED';ticketCode:string|null;createdAt:string;canCancel:boolean};
export const authApi = {
  login:async(email:string,password:string)=>(await api.post<Session>('/auth/login',{email,password})).data,
  register:async(name:string,email:string,password:string)=>(await api.post<Session>('/auth/register',{name,email,password})).data,
};
export const bookingApi = {
  hold:async(showId:string,seatIds:string[],key:string)=>(await api.post<Hold>(`/showtimes/${showId}/holds`,{seatIds},{headers:{'Idempotency-Key':key}})).data,
  getHold:async(id:string)=>(await api.get<Hold>(`/holds/${id}`)).data,
  release:async(id:string)=>api.delete(`/holds/${id}`),
  pay:async(id:string,outcome:string,key:string)=>(await api.post<{status:string;booking:Booking|null;message:string}>(`/holds/${id}/payments`,{outcome},{headers:{'Idempotency-Key':key}})).data,
  history:async()=>(await api.get<Booking[]>('/bookings')).data,
  booking:async(id:string)=>(await api.get<Booking>(`/bookings/${id}`)).data,
  cancel:async(id:string)=>(await api.post<Booking>(`/bookings/${id}/cancel`)).data,
};
