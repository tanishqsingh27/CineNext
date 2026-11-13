export type Movie = {
  id: string; slug: string; title: string; tagline: string; synopsis: string;
  genres: string[]; language: string; durationMinutes: number; certificate: string;
  releaseDate: string; rating: number; posterUrl: string; backdropUrl?: string; featured: boolean;
};
export type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type Filters = { cities: string[]; genres: string[]; languages: string[] };
export type Theatre = { id: string; name: string; city: string; address: string; amenities: string[] };
export type Showtime = {
  id: string; movieId: string; theatreId: string; theatreName: string; screenName: string;
  city: string; startsAt: string; format: string; language: string; priceFromPaise: number;
  currency: string; availableSeats: number; totalSeats: number;
};
export type Seat = {
  id: string; label: string; row: string; number: number; category: string;
  pricePaise: number; status: 'AVAILABLE' | 'HELD' | 'BOOKED' | 'BLOCKED'; accessible: boolean;
};
export type SeatMap = { showtimeId: string; rows: { label: string; seats: Seat[] }[]; screenLabel: string; currency: string };
