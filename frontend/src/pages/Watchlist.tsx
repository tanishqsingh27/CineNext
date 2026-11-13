import { useQueries } from '@tanstack/react-query';
import { Bookmark } from 'lucide-react';
import { catalogueApi, errorMessage } from '../lib/api';
import { usePreferences } from '../lib/store';
import { MovieCard } from '../components/MovieCard';
import { EmptyState, ErrorState, MovieSkeletons } from '../components/States';

export function Watchlist() {
  const ids = usePreferences((s) => s.saved);
  const movies = useQueries({ queries: ids.map((id) => ({ queryKey: ['movie', id], queryFn: ({ signal }: { signal: AbortSignal }) => catalogueApi.movie(id, signal) })) });
  const pending = movies.some((movie) => movie.isPending);
  const failed = movies.find((movie) => movie.isError);
  return <div className="page-shell secondary-page"><div className="eyebrow muted"><Bookmark size={15}/>THE STORIES YOU’RE SAVING</div><h1>Your next<br/><span className="accent-text">movie nights.</span></h1><p className="page-intro">A personal shortlist, saved in this browser. No account needed.</p><div className="section-heading compact"><h2>My watchlist <span className="count-badge">{ids.length}</span></h2></div>{!ids.length ? <EmptyState title="Start a little collection" message="Tap the bookmark on any movie to save it here for a future movie night."/> : pending ? <MovieSkeletons/> : failed ? <ErrorState message={errorMessage(failed.error)} retry={() => movies.forEach((movie) => void movie.refetch())}/> : <div className="movie-grid">{movies.map((movie) => movie.data && <MovieCard key={movie.data.id} movie={movie.data}/>)}</div>}</div>;
}
