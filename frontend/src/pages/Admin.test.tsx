import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Admin } from './Admin';
import { api, catalogueApi } from '../lib/api';
import { useSession } from '../lib/session';

vi.mock('recharts',()=>({BarChart:()=>null,Bar:()=>null,XAxis:()=>null,YAxis:()=>null,Tooltip:()=>null,ResponsiveContainer:()=>null,CartesianGrid:()=>null}));
vi.mock('../lib/api', async importOriginal => ({
  ...await importOriginal<typeof import('../lib/api')>(),
  api:{get:vi.fn(),post:vi.fn(),put:vi.fn()},
  catalogueApi:{movies:vi.fn(),theatres:vi.fn()},
}));
beforeEach(()=>{
  vi.clearAllMocks();
  useSession.setState({user:{id:'test-admin',name:'Admin',email:'admin@example.test',role:'ADMIN'},token:'test-token'});
  vi.mocked(catalogueApi.movies).mockResolvedValue({items:[],page:0,size:50,totalElements:0,totalPages:0});
  vi.mocked(catalogueApi.theatres).mockResolvedValue([]);
  vi.mocked(api.get).mockImplementation(async url=>({data:url==='/admin/overview'?{movies:0,cinemas:0,confirmedBookings:0,revenuePaise:0,daily:[]}:[]}));
  vi.mocked(api.post).mockResolvedValue({data:{}});
});
async function movieForm(client=new QueryClient({defaultOptions:{queries:{retry:false}}})) {
  render(<QueryClientProvider client={client}><Admin/></QueryClientProvider>);
  await userEvent.click(screen.getByRole('button',{name:'Movies'}));
  await userEvent.type(screen.getByRole('textbox',{name:'Movie title'}),"Ocean's Return 2");
  await userEvent.type(screen.getByRole('textbox',{name:'Tagline'}),'One more voyage.');
  await userEvent.type(screen.getByRole('textbox',{name:'Synopsis'}),'A crew returns to the ocean for a final adventure.');
  await userEvent.type(screen.getByRole('textbox',{name:'Genres, comma separated'}),'Adventure, Drama');
}
describe('admin movie publication',()=>{
  it('generates a browser-valid slug and submits a complete movie',async()=>{
    await movieForm();
    const slug=screen.getByRole('textbox',{name:/^URL slug/}) as HTMLInputElement;
    expect(slug).toHaveValue('ocean-s-return-2');
    // Compile the actual rendered attribute with the same flag used by modern browsers.
    const pattern=new RegExp(`^(?:${slug.pattern})$`,'v');
    expect(pattern.test(slug.value)).toBe(true);
    expect(pattern.test('Upper Case!')).toBe(false);
    await userEvent.click(screen.getByRole('button',{name:'Add movie'}));
    expect(api.post).toHaveBeenCalledWith('/admin/movies',expect.objectContaining({title:"Ocean's Return 2",slug:'ocean-s-return-2',genres:['Adventure','Drama'],durationMinutes:120,rating:0,posterUrl:'/posters/orbit-nine.svg'}));
    expect(await screen.findByRole('status')).toHaveTextContent('Movie added.');
  });
  it('keeps a custom slug when the title changes and normalizes capitals/spaces',async()=>{
    await movieForm();const slug=screen.getByRole('textbox',{name:/^URL slug/});
    await userEvent.clear(slug);await userEvent.type(slug,'Custom Premiere');
    await userEvent.type(screen.getByRole('textbox',{name:'Movie title'}),' Extended');
    expect(slug).toHaveValue('custom-premiere');
  });
  it('blocks punctuation in a custom slug before calling the API',async()=>{
    await movieForm();const slug=screen.getByRole('textbox',{name:/^URL slug/}) as HTMLInputElement;
    await userEvent.clear(slug);await userEvent.type(slug,'invalid/slug!');
    await userEvent.click(screen.getByRole('button',{name:'Add movie'}));
    expect(slug.validity.patternMismatch).toBe(true);expect(api.post).not.toHaveBeenCalled();
  });
  it('shows the server field error and preserves entered values for correction',async()=>{
    vi.mocked(api.post).mockRejectedValue({isAxiosError:true,response:{status:400,data:{detail:'Release date: Choose a valid date in YYYY-MM-DD format.'}}});
    await movieForm();await userEvent.click(screen.getByRole('button',{name:'Add movie'}));
    await waitFor(()=>expect(screen.getByRole('alert')).toHaveTextContent('Release date: Choose a valid date'));
    expect(screen.getByRole('textbox',{name:'Movie title'})).toHaveValue("Ocean's Return 2");
  });
  it('refreshes every customer catalogue cache after an administrator saves',async()=>{
    const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const customerKeys=[['movies',''],['featured',''],['movie','existing'],['filters'],['theatres',''],['showtimes','existing'],['admin','screens']];
    for(const key of customerKeys)client.setQueryData(key,{old:true});
    await movieForm(client);await userEvent.click(screen.getByRole('button',{name:'Add movie'}));
    await waitFor(()=>{
      for(const key of customerKeys.filter(key=>key[0]!=='admin'))expect(client.getQueryState(key)?.isInvalidated).toBe(true);
    });
    // The active admin screen query is refetched immediately, so it is fresh after completion.
    expect(vi.mocked(api.get).mock.calls.filter(([url])=>url==='/admin/screens').length).toBeGreaterThan(1);
  });
});
