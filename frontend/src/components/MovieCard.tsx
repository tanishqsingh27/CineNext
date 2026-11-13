import { Bookmark, Star } from 'lucide-react';
import { Link } from 'react-router-dom';
import { usePreferences } from '../lib/store';
import { duration } from '../lib/format';
import type { Movie } from '../lib/types';

export function MovieCard({ movie }: { movie: Movie }) {
  const saved = usePreferences((s) => s.saved.includes(movie.id));
  const toggle = usePreferences((s) => s.toggleSaved);
  return <article className="movie-card">
    <div className="poster-wrap">
      <Link to={`/movies/${movie.id}`} aria-label={`Explore ${movie.title}`}><img src={movie.posterUrl} alt={`${movie.title} — original illustrated poster`} loading="lazy"/></Link>
      <span className="poster-certificate">{movie.certificate}</span>
      <button className={`save-button ${saved ? 'is-saved' : ''}`} aria-label={`${saved ? 'Remove' : 'Save'} ${movie.title}${saved ? ' from' : ' to'} watchlist`} aria-pressed={saved} onClick={() => toggle(movie.id)}><Bookmark size={17} fill={saved ? 'currentColor' : 'none'}/></button>
      <div className="poster-rating"><Star size={13} fill="currentColor"/>{movie.rating.toFixed(1)}<span>/ 10</span></div>
    </div>
    <div className="movie-meta"><span>{movie.genres.slice(0, 2).join(' · ')}</span><span>{duration(movie.durationMinutes)}</span></div>
    <h3><Link to={`/movies/${movie.id}`}>{movie.title}</Link></h3>
    <p>{movie.language} <span>•</span> In cinemas</p>
  </article>;
}
