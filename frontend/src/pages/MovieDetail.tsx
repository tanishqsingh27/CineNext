import { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Armchair, Bookmark, Clock3, MapPin, Star } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { catalogueApi, errorMessage } from '../lib/api';
import { cinemaDates, duration, money, showtimeLabel } from '../lib/format';
import { usePreferences } from '../lib/store';
import type { Showtime } from '../lib/types';
import { ErrorState } from '../components/States';
import { SeatPreview } from '../components/SeatPreview';

export function MovieDetail() {
  const { id = '' } = useParams();
  const dates = useMemo(() => cinemaDates(), []);
  const [date, setDate] = useState(dates[0].value);
  const [preview, setPreview] = useState<Showtime | null>(null);
  const close = useCallback(() => setPreview(null), []);
  const city = usePreferences((s) => s.city);
  const saved = usePreferences((s) => s.saved.includes(id));
  const toggle = usePreferences((s) => s.toggleSaved);
  const movie = useQuery({ queryKey: ['movie', id], queryFn: ({ signal }) => catalogueApi.movie(id, signal) });
  const shows = useQuery({ queryKey: ['showtimes', id, city, date], queryFn: ({ signal }) => catalogueApi.showtimes(id, city, date, signal) });
  const grouped = (shows.data || []).reduce<Record<string, Showtime[]>>((groups, show) => {
    (groups[show.theatreId] ??= []).push(show);
    return groups;
  }, {});
  if (movie.isPending) return <div className="page-shell detail-loading" role="status"><div className="skeleton detail-skeleton"/><span>Setting the scene…</span></div>;
  if (movie.isError) return <div className="page-shell"><ErrorState message={errorMessage(movie.error)} retry={() => void movie.refetch()}/></div>;
  const film = movie.data;
  return <div className="page-shell detail-page"><Link to="/" className="back-link"><ArrowLeft size={16}/>All movies</Link><section className="movie-detail-hero"><img className="detail-poster" src={film.posterUrl} alt={`${film.title} original illustrated poster`}/><div className="detail-info"><div className="eyebrow"><span className="live-dot"/>NOW SHOWING</div><div className="detail-genres">{film.genres.join(' / ')}</div><h1>{film.title}</h1><p className="movie-tagline">{film.tagline}</p><div className="detail-specs"><span><Star size={16} fill="currentColor"/>{film.rating.toFixed(1)} <small>/10</small></span><span>{film.certificate}</span><span><Clock3 size={15}/>{duration(film.durationMinutes)}</span><span>{film.language}</span></div><p className="synopsis">{film.synopsis}</p><div className="detail-actions"><a href="#showtimes" className="button primary">Find a showtime <Armchair size={17}/></a><button className={`button secondary ${saved ? 'saved' : ''}`} aria-pressed={saved} onClick={() => toggle(id)}><Bookmark size={17} fill={saved ? 'currentColor' : 'none'}/>{saved ? 'Saved to watchlist' : 'Add to watchlist'}</button></div><p className="fictional-note">An original fictional title from the CineNext demo collection.</p></div><span className="detail-watermark" aria-hidden="true">CN</span></section>
    <section id="showtimes" className="showtime-section"><div className="section-heading"><div><div className="eyebrow muted">MAKE A NIGHT OF IT</div><h2>Find your show<span className="heading-dot">.</span></h2></div><span className="location-note"><MapPin size={15}/>{city} · All times IST</span></div><div className="dates-row" aria-label="Choose show date">{dates.map((item) => <button key={item.value} className={`date-button ${date === item.value ? 'active' : ''}`} onClick={() => setDate(item.value)} aria-pressed={date === item.value}><span>{item.day}</span><strong>{item.date}</strong></button>)}</div><div className="showtime-info"><Armchair size={16}/><span>Select a showtime, choose up to eight seats, and continue to secure checkout.</span></div>
      {shows.isPending ? <div className="skeleton shows-skeleton" role="status" aria-label="Loading showtimes"/> : shows.isError ? <ErrorState message={errorMessage(shows.error)} retry={() => void shows.refetch()}/> : !shows.data.length ? <div className="state-card"><Armchair size={30}/><h3>No shows on this date</h3><p>Try another day or change your city in the header.</p></div> : <div className="theatre-shows">{Object.entries(grouped).map(([theatreId, items]) => { if (!items?.length) return null; return <article className="theatre-show-card" key={theatreId}><div className="theatre-show-title"><div className="theatre-icon"><Armchair size={22}/></div><div><h3>{items[0].theatreName}</h3><p><MapPin size={13}/>{items[0].city}<span>•</span>Original demo cinema</p></div></div><div className="showtime-buttons">{items.map((show) => <button className="showtime-button" key={show.id} onClick={() => setPreview(show)}><strong>{showtimeLabel(show.startsAt)}</strong><span>{show.format} · {show.screenName}</span><small>from {money(show.priceFromPaise)}</small><span className="show-availability">{show.availableSeats} seats available</span></button>)}</div></article>; })}</div>}
    </section>{preview && <SeatPreview showtime={preview} close={close}/>}</div>;
}
