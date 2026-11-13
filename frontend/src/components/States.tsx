import { AlertCircle, ArrowRight, Film, RotateCcw } from 'lucide-react';
import { Link } from 'react-router-dom';

export function ErrorState({ message, retry }: { message: string; retry: () => void }) {
  return <div className="state-card" role="alert"><AlertCircle size={30}/><h2>A little intermission</h2><p>{message}</p><button className="button secondary" onClick={retry}><RotateCcw size={16}/>Try again</button></div>;
}
export function EmptyState({ title, message, reset }: { title: string; message: string; reset?: () => void }) {
  return <div className="state-card"><Film size={32}/><h2>{title}</h2><p>{message}</p>{reset ? <button className="button secondary" onClick={reset}>Clear filters</button> : <Link to="/" className="button secondary">Explore movies <ArrowRight size={16}/></Link>}</div>;
}
export function MovieSkeletons() {
  return <div className="movie-grid" role="status" aria-label="Loading movies">{Array.from({ length: 4 }, (_, i) => <div className="skeleton-card" key={i}><div className="skeleton poster-skeleton"/><div className="skeleton line-skeleton"/><div className="skeleton small-skeleton"/></div>)}</div>;
}
