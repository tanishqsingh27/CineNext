import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion, useReducedMotion } from 'framer-motion';
import { ArrowDown, ArrowRight, ArrowUpRight, Check, ChevronDown, Search, SlidersHorizontal, Sparkles, Star, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { catalogueApi, errorMessage } from '../lib/api';
import { usePreferences } from '../lib/store';
import { duration } from '../lib/format';
import { MovieCard } from '../components/MovieCard';
import { EmptyState, ErrorState, MovieSkeletons } from '../components/States';

export function Discover() {
  const city = usePreferences((s) => s.city);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [genre, setGenre] = useState('');
  const [language, setLanguage] = useState('');
  const [page, setPage] = useState(0);
  const reducedMotion = useReducedMotion();
  useEffect(() => { const timer = setTimeout(() => { setQuery(search.trim()); setPage(0); }, 300); return () => clearTimeout(timer); }, [search]);
  useEffect(() => { setPage(0); }, [city, genre, language]);
  const movies = useQuery({ queryKey: ['movies', city, query, genre, language, page], queryFn: ({ signal }) => catalogueApi.movies({ city, q: query || undefined, genre: genre || undefined, language: language || undefined, page, size: 8 }, signal) });
  const featured = useQuery({ queryKey: ['featured', city], queryFn: ({ signal }) => catalogueApi.movies({ city, size: 12 }, signal), staleTime: 300_000 });
  const filters = useQuery({ queryKey: ['filters'], queryFn: ({ signal }) => catalogueApi.filters(signal), staleTime: 300_000 });
  const hero = featured.data?.items.find((movie) => movie.featured) || featured.data?.items[0];
  const clear = () => { setSearch(''); setQuery(''); setGenre(''); setLanguage(''); setPage(0); };
  const hasFilters = Boolean(search || genre || language);
  return <>
    <section className="hero page-shell" aria-label="Featured movie">
      <div className="hero-frame">
        <img className="hero-art" src="/posters/orbit-nine-wide.svg" alt="An original illustration of a lone explorer beneath a monumental ringed planet"/>
        <div className="hero-shade"/>
        <motion.div className="hero-content" initial={reducedMotion ? false : { opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .65 }}>
          <div className="eyebrow"><span className="live-dot"/>THE BIG SCREEN IS CALLING</div>
          <div className="hero-small">Go somewhere</div><h1>extraordinary.</h1>
          <p className="hero-intro">New worlds. Bigger feelings. Your next great<br className="desktop-break"/> story starts with a seat.</p>
          {hero && <><div className="hero-movie-info"><span className="hero-title">{hero.title}</span><span className="hero-rating"><Star size={13} fill="currentColor"/>{hero.rating.toFixed(1)}</span><span>{hero.language}</span><span>{duration(hero.durationMinutes)}</span></div><Link to={`/movies/${hero.id}`} className="button primary">Explore showtimes <ArrowUpRight size={18}/></Link></>}
          {!hero && <a className="button primary" href="#now-showing">Explore the catalogue <ArrowDown size={18}/></a>}
        </motion.div>
        <div className="hero-film-label"><span>THE CINENEXT ORIGINAL COLLECTION</span><strong>ORBIT NINE</strong><span>BEYOND THE KNOWN.</span></div>
        <div className="hero-bottom"><span><Sparkles size={14}/> A different kind of movie night</span><span>01 <i/> FEATURED</span></div>
      </div>
    </section>
    <section className="discovery-strip page-shell" aria-label="CineNext introduction"><span className="strip-label">MORE THAN A MOVIE.</span><p>A little escape. A shared moment. <em>A story that stays.</em></p><a href="#now-showing" aria-label="Explore now showing movies"><ArrowDown size={20}/></a></section>
    <section id="now-showing" className="catalogue page-shell">
      <div className="section-heading"><div><div className="eyebrow muted">FIND YOUR NEXT FAVOURITE</div><h2>Now showing<span className="heading-dot">.</span></h2></div><span className="location-note"><span className="live-dot"/> In cinemas across {city}</span></div>
      <div className="catalogue-toolbar"><label className="search-box"><Search size={19}/><input type="search" placeholder="Search for a story…" value={search} onChange={(e) => setSearch(e.target.value)} maxLength={100} aria-label="Search movies by title"/>{search && <button onClick={() => setSearch('')} aria-label="Clear search"><X size={16}/></button>}</label><div className="language-control"><SlidersHorizontal size={16}/><select aria-label="Filter by language" value={language} onChange={(e) => setLanguage(e.target.value)}><option value="">All languages</option>{filters.data?.languages.map((item) => <option key={item}>{item}</option>)}</select><ChevronDown size={14}/></div></div>
      <div className="genre-row" aria-label="Movie genres"><button className={`genre-chip ${!genre ? 'active' : ''}`} onClick={() => setGenre('')} aria-pressed={!genre}>All stories</button>{filters.data?.genres.map((item) => <button key={item} className={`genre-chip ${genre === item ? 'active' : ''}`} onClick={() => setGenre(genre === item ? '' : item)} aria-pressed={genre === item}>{item}</button>)}</div>
      <div className="catalogue-result-line"><span aria-live="polite">{movies.data ? `${movies.data.totalElements} ${movies.data.totalElements === 1 ? 'movie' : 'movies'} to make a night of` : 'Finding the stories…'}</span>{hasFilters && <button className="text-button" onClick={clear}>Clear filters <X size={12}/></button>}</div>
      {movies.isPending ? <MovieSkeletons/> : movies.isError ? <ErrorState message={errorMessage(movies.error)} retry={() => void movies.refetch()}/> : !movies.data.items.length ? <EmptyState title="A different story awaits" message="No movies match these filters. Try another genre, language or title." reset={clear}/> : <div className="movie-grid">{movies.data.items.map((movie) => <MovieCard movie={movie} key={movie.id}/>)}</div>}
      {movies.data && movies.data.totalPages > 1 && <nav className="pagination" aria-label="Catalogue pages"><button className="button secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</button><span>Page {page + 1} of {movies.data.totalPages}</span><button className="button secondary" disabled={page + 1 >= movies.data.totalPages} onClick={() => setPage((p) => p + 1)}>Next <ArrowRight size={16}/></button></nav>}
    </section>
    <section className="cinema-callout page-shell"><div className="callout-visual" aria-hidden="true"><div className="mini-screen"/>{[0, 1, 2].map((row) => <div className="mini-seat-row" key={row}>{Array.from({ length: 7 }, (_, col) => <span key={col} className={row === 1 && col === 3 ? 'chosen' : ''}/>)}</div>)}</div><div className="callout-copy"><div className="eyebrow muted">EVERY DETAIL. A BETTER EXPERIENCE.</div><h2>Find your kind of cinema.</h2><p>Explore nearby screens, compare showtimes, and take a look inside the auditorium before your next movie night.</p><Link to="/cinemas" className="text-link">Discover cinemas <ArrowUpRight size={18}/></Link></div><div className="callout-proof"><span><Check size={14}/> Real showtime catalogue</span><span><Check size={14}/> Interactive seat previews</span><span><Check size={14}/> Your own watchlist</span></div></section>
  </>;
}
