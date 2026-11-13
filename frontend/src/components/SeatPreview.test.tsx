import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { SeatPreview } from './SeatPreview';
import { bookingApi, catalogueApi } from '../lib/api';
import { useSession } from '../lib/session';
import type { Showtime } from '../lib/types';

vi.mock('../lib/api', () => ({ catalogueApi: { seats: vi.fn() }, bookingApi:{hold:vi.fn()},authApi:{login:vi.fn(),register:vi.fn()}, errorMessage: () => 'Catalogue unavailable' }));
const show: Showtime = { id: 'test-show', movieId: 'orbit-nine', theatreId: 'cinema', theatreName: 'Test Cinema', screenName: 'Screen 1', city: 'Mumbai', startsAt: '2026-11-13T04:30:00Z', format: 'LASER 2D', language: 'English', priceFromPaise: 22000, currency: 'INR', availableSeats: 9, totalSeats: 10 };
const renderSelection = (close = vi.fn()) => render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><Routes><Route path="/" element={<SeatPreview showtime={show} close={close}/>}/><Route path="/checkout/:id" element={<p>Checkout opened</p>}/></Routes></MemoryRouter></QueryClientProvider>);
beforeEach(() => {
  vi.clearAllMocks();useSession.getState().signOut();
  vi.mocked(catalogueApi.seats).mockResolvedValue({showtimeId:show.id,screenLabel:'SCREEN',currency:'INR',rows:[{label:'A',seats:Array.from({length:10},(_,index)=>({id:`a${index+1}`,label:`A${index+1}`,row:'A',number:index+1,category:'PREMIUM',pricePaise:30000,status:index===0?'BLOCKED':'AVAILABLE',accessible:index===1}))}]});
});
describe('multi-seat selection', () => {
  it('keeps multiple seats selected, totals their prices, and supports deselection', async () => {
    renderSelection();
    expect(await screen.findByRole('button',{name:/A1, PREMIUM/})).toBeDisabled();
    await userEvent.click(screen.getByRole('button',{name:/A2, PREMIUM/}));
    await userEvent.click(screen.getByRole('button',{name:/A3, PREMIUM/}));
    expect(screen.getByText('2 seats selected')).toBeInTheDocument();expect(screen.getByText('₹600')).toBeInTheDocument();
    expect(screen.getByRole('button',{name:/A2, PREMIUM/})).toHaveAttribute('aria-pressed','true');
    await userEvent.click(screen.getByRole('button',{name:/A2, PREMIUM/}));
    expect(screen.getByText('1 seat selected')).toBeInTheDocument();expect(screen.getByText('₹300')).toBeInTheDocument();
  });
  it('enforces the eight-seat maximum while preserving deselection', async () => {
    renderSelection();await screen.findByRole('button',{name:/A2, PREMIUM/});
    for(let number=2;number<=9;number++)await userEvent.click(screen.getByRole('button',{name:new RegExp(`A${number}, PREMIUM`)}));
    expect(screen.getByRole('button',{name:/A10, PREMIUM/})).toBeDisabled();expect(screen.getByRole('button',{name:/A2, PREMIUM/})).toBeEnabled();
  });
  it('requires sign-in before sending a hold request', async () => {
    renderSelection();await userEvent.click(await screen.findByRole('button',{name:/A2, PREMIUM/}));
    await userEvent.click(screen.getByRole('button',{name:'Sign in & continue'}));
    expect(screen.getByRole('textbox',{name:'Email address'})).toBeInTheDocument();expect(bookingApi.hold).not.toHaveBeenCalled();
  });
  it('sends all selected seat IDs and moves to the real hold checkout', async () => {
    useSession.setState({user:{id:'user-1',name:'Test',email:'test@example.test',role:'USER'},token:'unit-test-token'});
    vi.mocked(bookingApi.hold).mockResolvedValue({id:'hold-1',showtimeId:show.id,seatIds:['a2','a3'],seatLabels:['A2','A3'],totalPaise:60000,currency:'INR',expiresAt:'2026-11-13T04:20:00Z',status:'ACTIVE',movieTitle:'Orbit Nine',theatreName:'Test Cinema',screenName:'Screen 1',startsAt:show.startsAt});
    renderSelection();await userEvent.click(await screen.findByRole('button',{name:/A2, PREMIUM/}));await userEvent.click(screen.getByRole('button',{name:/A3, PREMIUM/}));
    await userEvent.click(screen.getByRole('button',{name:'Continue to payment'}));
    expect(bookingApi.hold).toHaveBeenCalledWith('test-show',['a2','a3'],expect.any(String));expect(await screen.findByText('Checkout opened')).toBeInTheDocument();
  });
  it('supports Escape and restores body scrolling and focus', async () => {
    const trigger=document.createElement('button');document.body.append(trigger);trigger.focus();const close=vi.fn(),result=renderSelection(close);
    await screen.findByRole('button',{name:/A2, PREMIUM/});await userEvent.keyboard('{Escape}');expect(close).toHaveBeenCalledOnce();result.unmount();
    expect(document.body.style.overflow).toBe('');expect(document.activeElement).toBe(trigger);trigger.remove();
  });
  it('shows a retry when inventory cannot be loaded', async () => {
    vi.mocked(catalogueApi.seats).mockRejectedValueOnce(new Error('offline'));renderSelection();
    await waitFor(()=>expect(screen.getByText('Catalogue unavailable')).toBeInTheDocument());expect(screen.getByRole('button',{name:/try again/i})).toBeInTheDocument();
  });
});
