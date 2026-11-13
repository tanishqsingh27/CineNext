import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { CheckCircle2, Ticket, Printer } from 'lucide-react';
import { bookingApi, errorMessage, type Booking } from '../lib/api';
import { useSession } from '../lib/session';
import { money, showtimeLabel } from '../lib/format';
import { AuthForm } from '../components/AuthForm';
import { EmptyState, ErrorState } from '../components/States';

function TicketCard({booking}:{booking:Booking}) {
  const client=useQueryClient(),[confirm,setConfirm]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
  async function cancel(){setBusy(true);try{await bookingApi.cancel(booking.id);await client.invalidateQueries({queryKey:['bookings']});await client.invalidateQueries({queryKey:['booking']});await client.invalidateQueries({queryKey:['seats']});setConfirm(false);}catch(error){setError(errorMessage(error));}finally{setBusy(false);}}
  return <article className="ticket-card"><div className="ticket-main"><div className={`ticket-status ${booking.status.toLowerCase()}`}><CheckCircle2 size={16}/>{booking.status==='CONFIRMED'?'Booking confirmed':'Booking cancelled · simulated refund issued'}</div><h2>{booking.movieTitle}</h2><p>{booking.theatreName} · {booking.screenName}</p><div className="ticket-facts"><div><span>DATE & TIME</span><strong>{new Intl.DateTimeFormat('en-IN',{dateStyle:'medium',timeZone:'Asia/Kolkata'}).format(new Date(booking.startsAt))}</strong><small>{showtimeLabel(booking.startsAt)} IST</small></div><div><span>YOUR SEATS</span><strong>{booking.seatLabels.join(', ')}</strong><small>{booking.seatLabels.length} ticket(s)</small></div><div><span>TOTAL</span><strong>{money(booking.totalPaise)}</strong><small>Simulated payment</small></div></div><p className="ticket-reference">Booking reference: {booking.id}</p>{error&&<p className="form-error" role="alert">{error}</p>}{booking.canCancel&&<div className="ticket-cancel">{confirm?<><p>Cancel all seats and issue a full simulated refund?</p><button className="button danger" disabled={busy} onClick={()=>void cancel()}>{busy?'Cancelling…':'Confirm cancellation'}</button><button className="text-button" onClick={()=>setConfirm(false)}>Keep booking</button></>:<button className="text-button" onClick={()=>setConfirm(true)}>Cancel booking</button>}</div>}</div>{booking.ticketCode&&<div className="ticket-qr"><QRCodeSVG value={booking.ticketCode} size={146} level="M" marginSize={2}/><strong>YOUR CINEMA PASS</strong><small>Show this QR at the cinema.</small><details><summary>Ticket verification code</summary><code>{booking.ticketCode}</code></details></div>}</article>;
}
export function Bookings() {
  const {id}=useParams(),user=useSession(s=>s.user);
  const history=useQuery({queryKey:['bookings',user?.id],queryFn:bookingApi.history,enabled:!!user&&!id,staleTime:0});
  const detail=useQuery({queryKey:['booking',id,user?.id],queryFn:()=>bookingApi.booking(id!),enabled:!!user&&!!id,staleTime:0});
  if(!user)return <div className="page-shell auth-page single"><AuthForm/></div>;
  const state=id?detail:history;
  const items=id?(detail.data?[detail.data]:[]):history.data||[];
  return <div className="page-shell secondary-page bookings-page"><div className="eyebrow"><Ticket size={16}/>GREAT STORIES. ALL IN ONE PLACE.</div><h1>{id?'See you at the movies.':'Your movie nights.'}</h1><p className="page-intro">Your bookings and digital tickets. Cancellations are available until 30 minutes before the show.</p><div className="booking-actions">{id&&<Link className="text-link" to="/bookings">View all bookings</Link>}<button className="button secondary" onClick={()=>window.print()}><Printer size={16}/>Print tickets</button></div>{state.isPending?<p role="status">Finding your tickets…</p>:state.isError?<ErrorState message={errorMessage(state.error)} retry={()=>void state.refetch()}/>:items.length?<div className="tickets-list">{items.map(booking=><TicketCard key={booking.id} booking={booking}/>)}</div>:<EmptyState title="Your first movie night awaits" message="Choose a movie and book your seats. Your tickets will appear here."/>}<Link className="text-link" to="/">Discover another story</Link></div>;
}
