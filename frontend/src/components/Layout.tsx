import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowUpRight, Bookmark, ChevronDown, MapPin } from 'lucide-react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { catalogueApi } from '../lib/api';
import { usePreferences } from '../lib/store';
import { useSession } from '../lib/session';

export function Layout() {
  const user=useSession(s=>s.user),signOut=useSession(s=>s.signOut),client=useQueryClient(),navigate=useNavigate();
  const city = usePreferences((s) => s.city);
  const setCity = usePreferences((s) => s.setCity);
  const saved = usePreferences((s) => s.saved.length);
  const filters = useQuery({ queryKey: ['filters'], queryFn: ({ signal }) => catalogueApi.filters(signal), staleTime: 300_000 });
  const location = useLocation();
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }); }, [location.pathname]);
  return <>
    <a className="skip-link" href="#main">Skip to content</a>
    <header className="site-header"><div className="header-inner">
      <Link to="/" className="brand" aria-label="CineNext home"><img src="/favicon.svg" alt=""/><span>cine<span className="brand-light">next</span></span></Link>
      <nav aria-label="Main navigation"><NavLink to="/" end>Discover</NavLink><NavLink to="/cinemas">Cinemas</NavLink><NavLink to="/watchlist">Watchlist {saved > 0 && <span className="nav-count">{saved}</span>}</NavLink><NavLink to="/bookings">My tickets</NavLink>{user?.role==='ADMIN'&&<NavLink to="/admin">Admin</NavLink>}</nav>
      <div className="header-actions"><div className="city-control"><MapPin size={16}/><select aria-label="Your city" value={city} onChange={(e) => setCity(e.target.value)}>{(filters.data?.cities || [city]).map((item) => <option key={item} value={item}>{item}</option>)}</select><ChevronDown size={13}/></div>{user?<button className="header-auth" onClick={()=>{signOut();client.clear();navigate('/');}}>Sign out</button>:<Link className="header-auth" to="/login">Sign in / Register</Link>}</div>
    </div></header>
    <nav className="mobile-navigation" aria-label="Mobile navigation"><NavLink to="/" end>Discover</NavLink><NavLink to="/cinemas">Cinemas</NavLink><NavLink to="/watchlist">Watchlist</NavLink><NavLink to="/bookings">My tickets</NavLink>{user?.role==='ADMIN'&&<NavLink to="/admin">Admin</NavLink>}</nav>
    <main id="main"><Outlet/></main>
    <footer className="site-footer"><div className="footer-top"><Link to="/" className="brand"><img src="/favicon.svg" alt=""/><span>cinenext</span></Link><p>Great stories deserve the big screen.</p><Link to="/login?admin=1">Admin sign in <ArrowUpRight size={16}/></Link></div><div className="footer-bottom"><span>© {new Date().getFullYear()} CineNext. An independent cinema project.</span><span>Original demo films & artwork · All payments are simulated</span></div></footer>
  </>;
}
