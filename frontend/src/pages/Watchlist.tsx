import { useQueries } from '@tanstack/react-query';
import axios from 'axios';
import { Bookmark } from 'lucide-react';
import { catalogueApi, errorMessage } from '../lib/api';
import { usePreferences } from '../lib/store';
import { MovieCard } from '../components/MovieCard';
import { EmptyState, ErrorState, MovieSkeletons } from '../components/States';
import { catalogueRefresh } from '../lib/catalogue-refresh';

const unavailable = (error: unknown) => axios.isAxiosError(error) && error.response?.status === 404;

export function Watchlist() {
  const ids = usePreferences((s) => s.saved);
  const movies = useQueries({ queries: ids.map((id) => ({
    queryKey: ['movie', id], queryFn: ({ signal }: { signal: AbortSignal }) => catalogueApi.movie(id, signal),
    ...catalogueRefresh, retry: (count: number, error: unknown) => !unavailable(error) && count < 1,
  })) });
  const pending = movies.some((movie) => movie.isPending);
  const failed = movies.find((movie) => movie.isError && !unavailable(movie.error));
  // An archived catalogue record must not keep appearing from a stale cached response.
  const available = movies.filter((movie) => movie.isSuccess && movie.data);
  return <div className="page-shell secondary-page">
    <div className="eyebrow muted"><Bookmark size={15}/>THE STORIES YOU’RE SAVING</div><h1>Your next<br/><span className="accent-text">movie nights.</span></h1>
    <p className="page-intro">A personal shortlist, saved in this browser. No account needed.</p>
    <div className="section-heading compact"><h2>My watchlist <span className="count-badge">{available.length}</span></h2></div>
    {!ids.length ? <EmptyState title="Start a little collection" message="Tap the bookmark on any movie to save it here for a future movie night."/>
      : pending ? <MovieSkeletons/>
      : failed ? <ErrorState message={errorMessage(failed.error)} retry={() => movies.forEach((movie) => void movie.refetch())}/>
      : !available.length ? <EmptyState title="No saved movies available" message="Your saved movies are no longer in the current programme. Explore the latest movies to find your next story."/>
      : <div className="movie-grid">{available.map((movie) => movie.data && <MovieCard key={movie.data.id} movie={movie.data}/>)}</div>}
  </div>;
}
