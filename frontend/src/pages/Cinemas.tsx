import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, Armchair, MapPin } from 'lucide-react';
import { Link } from 'react-router-dom';
import { catalogueApi, errorMessage } from '../lib/api';
import { usePreferences } from '../lib/store';
import { EmptyState, ErrorState } from '../components/States';

export function Cinemas() {
  const city = usePreferences((s) => s.city);
  const theatres = useQuery({ queryKey: ['theatres', city], queryFn: ({ signal }) => catalogueApi.theatres(city, signal) });
  return <div className="page-shell secondary-page"><div className="eyebrow muted">THE SETTING MATTERS</div><h1>Big screens.<br/><span className="accent-text">Better evenings.</span></h1><p className="page-intro">Find your next favourite cinema in {city}. A perfect place for a little escape.</p><div className="section-heading compact"><h2>Cinemas in {city}</h2><span className="location-note"><MapPin size={15}/>Change your city in the header</span></div>{theatres.isPending ? <div className="skeleton shows-skeleton" role="status" aria-label="Loading cinemas"/> : theatres.isError ? <ErrorState message={errorMessage(theatres.error)} retry={() => void theatres.refetch()}/> : !theatres.data.length ? <EmptyState title="No cinemas here yet" message="Choose another city in the header to discover nearby cinemas."/> : <div className="cinema-grid">{theatres.data.map((theatre, index) => <article className="cinema-card" key={theatre.id}><div className={`cinema-illustration variant-${index % 3}`} aria-hidden="true"><div className="architectural-screen"/><div className="architectural-seats">{Array.from({ length: 12 }, (_, i) => <Armchair key={i}/>)}</div><span>CINENEXT VENUES</span></div><div className="cinema-card-body"><span className="eyebrow muted">{theatre.city}</span><h3>{theatre.name}</h3><p><MapPin size={15}/>{theatre.address}</p><div className="amenities">{theatre.amenities.map((amenity) => <span key={amenity}>{amenity}</span>)}</div><Link to="/" className="text-link">Explore movies in {city}<ArrowUpRight size={17}/></Link></div></article>)}</div>}</div>;
}
