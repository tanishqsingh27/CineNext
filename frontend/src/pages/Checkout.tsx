import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Clock3, ShieldCheck, Ticket, CreditCard } from 'lucide-react';
import { bookingApi, errorMessage } from '../lib/api';
import { money, showtimeLabel } from '../lib/format';
import { useSession } from '../lib/session';
import { AuthForm } from '../components/AuthForm';
import { ErrorState } from '../components/States';

export function Checkout() {
  const {id=''}=useParams(),user=useSession(s=>s.user),navigate=useNavigate(),client=useQueryClient();
  const hold=useQuery({queryKey:['hold',id,user?.id],queryFn:()=>bookingApi.getHold(id),enabled:!!user,retry:false,staleTime:0});
  const [now,setNow]=useState(Date.now()),[outcome,setOutcome]=useState('SUCCESS'),[busy,setBusy]=useState(false),[error,setError]=useState(''),[declined,setDeclined]=useState(false);
  const paymentKey=useRef(crypto.randomUUID());
  useEffect(()=>{const interval=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(interval);},[]);
  if(!user)return <div className="page-shell auth-page single"><AuthForm/></div>;
  if(hold.isPending)return <div className="page-shell detail-loading" role="status">Preparing your booking…</div>;
  if(hold.isError)return <div className="page-shell secondary-page"><ErrorState message={errorMessage(hold.error)} retry={()=>void hold.refetch()}/><Link className="text-link" to="/">Choose seats again</Link></div>;
  if(!hold.data)return null;
  const bookingHold=hold.data,remaining=Math.max(0,Math.floor((Date.parse(bookingHold.expiresAt)-now)/1000));
  async function pay() {
    setBusy(true);setError('');
    try {const result=await bookingApi.pay(id,outcome,paymentKey.current);await client.invalidateQueries({queryKey:['bookings']});if(result.booking)navigate(`/bookings/${result.booking.id}`,{replace:true});else{setDeclined(true);setError(result.message);}}
    catch(error){setError(errorMessage(error));}finally{setBusy(false);}
  }
  async function release() {setBusy(true);try{await bookingApi.release(id);navigate('/');}catch(error){setError(errorMessage(error));setBusy(false);}}
  return <div className="page-shell secondary-page checkout-page"><div className="eyebrow"><Ticket size={16}/>YOUR MOVIE NIGHT IS ALMOST HERE</div><h1>One last step<span className="accent-text">.</span></h1><p className="page-intro">Your seats are held while you complete checkout.</p>
    <div className="checkout-grid"><section className="checkout-summary"><div className="eyebrow muted">BOOKING SUMMARY</div><h2>{bookingHold.movieTitle}</h2><p>{bookingHold.theatreName} · {bookingHold.screenName}</p><p>{new Intl.DateTimeFormat('en-IN',{dateStyle:'medium',timeZone:'Asia/Kolkata'}).format(new Date(bookingHold.startsAt))} · {showtimeLabel(bookingHold.startsAt)} IST</p><div className="summary-line"><span>Seats</span><strong>{bookingHold.seatLabels.join(', ')}</strong></div><div className="summary-line"><span>Tickets</span><strong>{bookingHold.seatLabels.length}</strong></div><div className="summary-line total"><span>Total</span><strong>{money(bookingHold.totalPaise)}</strong></div><small>All prices are calculated by the server. No extra fees in this demo.</small></section>
    <section className="payment-card"><div className={`hold-timer ${remaining<60?'urgent':''}`}><Clock3 size={18}/>{remaining?`Seats held for ${Math.floor(remaining/60)}:${String(remaining%60).padStart(2,'0')}`:'Your hold has expired'}</div><h2><CreditCard size={23}/> Simulated payment</h2><p>This is a project demonstration. No card details are collected and no real money is charged.</p><label className="payment-choice"><input type="radio" name="payment" checked={outcome==='SUCCESS'} disabled={busy||declined} onChange={()=>{setOutcome('SUCCESS');paymentKey.current=crypto.randomUUID();}}/>Successful payment</label><label className="payment-choice"><input type="radio" name="payment" checked={outcome==='DECLINED'} disabled={busy||declined} onChange={()=>{setOutcome('DECLINED');paymentKey.current=crypto.randomUUID();}}/>Declined payment (test failure handling)</label>{error&&<p className="form-error" role="alert">{error}</p>}{declined||!remaining?<Link className="button primary" to="/">Choose seats again</Link>:<button className="button primary" disabled={busy} onClick={()=>void pay()}>{busy?'Confirming your booking…':`Pay ${money(bookingHold.totalPaise)} · simulated`}<ShieldCheck size={17}/></button>}<button className="text-button" disabled={busy} onClick={()=>void release()}>Release seats & return to movies</button></section></div>
  </div>;
}
