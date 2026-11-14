import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Discover } from './Discover';
import { Cinemas } from './Cinemas';
import { MovieDetail } from './MovieDetail';
import { Watchlist } from './Watchlist';
import { Layout } from '../components/Layout';
import { catalogueApi } from '../lib/api';
import { usePreferences } from '../lib/store';
import { useSession } from '../lib/session';
import type { Movie } from '../lib/types';

vi.mock('framer-motion',()=>({motion:{div:({children}:{children:ReactNode})=><div>{children}</div>},useReducedMotion:()=>true}));
vi.mock('../lib/api', async importOriginal=>({
  ...await importOriginal<typeof import('../lib/api')>(),
  catalogueApi:{movies:vi.fn(),movie:vi.fn(),filters:vi.fn(),theatres:vi.fn(),showtimes:vi.fn()},
}));
const film:Movie={id:'admin-premiere',slug:'admin-premiere',title:'Admin Premiere',tagline:'The next story starts here.',synopsis:'A newly published cinema programme.',genres:['Drama'],language:'English',durationMinutes:115,certificate:'U',releaseDate:'2026-11-14',rating:8,posterUrl:'/posters/paper-moons.svg',featured:true};
beforeEach(()=>{
  vi.clearAllMocks();
  useSession.getState().signOut();usePreferences.setState({city:'',saved:[]});
  vi.spyOn(window,'scrollTo').mockImplementation(()=>{});
  vi.mocked(catalogueApi.movies).mockResolvedValue({items:[],page:0,size:8,totalElements:0,totalPages:0});
  vi.mocked(catalogueApi.filters).mockResolvedValue({cities:[],genres:[],languages:[]});
  vi.mocked(catalogueApi.theatres).mockResolvedValue([]);
  vi.mocked(catalogueApi.showtimes).mockResolvedValue([]);
  vi.mocked(catalogueApi.movie).mockResolvedValue(film);
});
function page(element:ReactNode,path='/') {
  const client=new QueryClient({defaultOptions:{queries:{retry:false,gcTime:0}}});
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><Routes><Route element={<Layout/>}><Route path="/movies/:id" element={element}/><Route path="*" element={element}/></Route></Routes></MemoryRouter></QueryClientProvider>);
}
describe('administrator-managed customer catalogue',()=>{
  it('shows an honest empty catalogue with no sample film or city',async()=>{
    page(<Discover/>);
    expect(await screen.findByRole('heading',{name:'No movies added yet'})).toBeInTheDocument();
    expect(screen.queryByText(/Orbit Nine|CineNext Harbour|Mumbai|Bengaluru/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('region',{name:'Featured movie'})).not.toBeInTheDocument();
    expect(within(screen.getByRole('combobox',{name:'Your city'})).getAllByRole('option')).toHaveLength(1);
    expect(catalogueApi.movies).toHaveBeenCalledWith(expect.objectContaining({city:undefined}),expect.any(AbortSignal));
  });
  it('renders the saved movie in both hero and cards without requiring a showtime',async()=>{
    vi.mocked(catalogueApi.movies).mockResolvedValue({items:[film],page:0,size:8,totalElements:1,totalPages:1});
    page(<Discover/>);
    expect(await screen.findByRole('heading',{name:film.title,level:1})).toBeInTheDocument();
    expect(screen.getByRole('link',{name:'Explore Admin Premiere'})).toHaveAttribute('href','/movies/admin-premiere');
    const hero=screen.getByRole('region',{name:'Featured movie'});
    expect(within(hero).getByRole('img')).toHaveAttribute('src',film.posterUrl);
    expect(screen.queryByText(/ORBIT NINE|BEYOND THE KNOWN|NOW SHOWING/)).not.toBeInTheDocument();
  });
  it('offers only API cities and resets a removed persisted city',async()=>{
    usePreferences.setState({city:'Mumbai'});
    vi.mocked(catalogueApi.filters).mockResolvedValue({cities:['New Town'],genres:[],languages:[]});
    page(<Cinemas/>);
    const selector=screen.getByRole('combobox',{name:'Your city'});
    await waitFor(()=>expect(selector).toHaveValue(''));
    await waitFor(()=>expect(within(selector).getAllByRole('option').map(option=>option.textContent)).toEqual(['All cities','New Town']));
    await userEvent.selectOptions(selector,'New Town');
    await waitFor(()=>expect(catalogueApi.theatres).toHaveBeenCalledWith('New Town',expect.any(AbortSignal)));
  });
  it('does not invent cinema cards when no venues have been saved',async()=>{
    page(<Cinemas/>);
    expect(await screen.findByRole('heading',{name:'No cinemas added yet'})).toBeInTheDocument();
    expect(screen.queryAllByRole('article')).toHaveLength(0);
  });
  it('renders only saved venues and selects their city when exploring movies',async()=>{
    vi.mocked(catalogueApi.theatres).mockResolvedValue([{id:'cinema-owner',name:'Owner Cinema',city:'New Town',address:'1 Main Street',amenities:['Parking']}]);
    vi.mocked(catalogueApi.filters).mockResolvedValue({cities:['New Town'],genres:[],languages:[]});
    page(<Cinemas/>);
    expect(await screen.findByRole('heading',{name:'Owner Cinema'})).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(1);
    await userEvent.click(screen.getByRole('link',{name:'Explore movies in New Town'}));
    expect(usePreferences.getState().city).toBe('New Town');
  });
  it('shows no invented screening times on a saved movie without shows',async()=>{
    page(<MovieDetail/>,'/movies/admin-premiere');
    expect(await screen.findByRole('heading',{name:'No shows on this date'})).toBeInTheDocument();
    expect(screen.queryByText(/Original demo|fictional title|NOW SHOWING/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button',{name:/seats available/})).not.toBeInTheDocument();
  });
  it('omits archived saved movies while retaining available watchlist entries',async()=>{
    usePreferences.setState({saved:['old-demo',film.id]});
    vi.mocked(catalogueApi.movie).mockImplementation(async id=>{
      if(id==='old-demo')throw {isAxiosError:true,response:{status:404}};
      return film;
    });
    page(<Watchlist/>);
    expect(await screen.findByRole('link',{name:'Explore Admin Premiere'})).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(1);
  });
});
