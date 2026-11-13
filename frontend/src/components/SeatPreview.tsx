import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Armchair, Info, X, ArrowRight } from 'lucide-react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { bookingApi, catalogueApi, errorMessage } from '../lib/api';
import { money, showtimeLabel } from '../lib/format';
import { useSession } from '../lib/session';
import type { Seat, Showtime } from '../lib/types';
import { ErrorState } from './States';
import { AuthForm } from './AuthForm';

export function SeatPreview({showtime,close}:{showtime:Showtime;close:()=>void}) {
  const modal=useRef<HTMLDivElement>(null),requestKey=useRef(crypto.randomUUID());
  const [selected,setSelected]=useState<Seat[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState(''),[needsAuth,setNeedsAuth]=useState(false);
  const user=useSession(s=>s.user),navigate=useNavigate();
  const seats=useQuery({queryKey:['seats',showtime.id],queryFn:({signal})=>catalogueApi.seats(showtime.id,signal),staleTime:0,refetchInterval:5000});
  useEffect(()=>{
    if(seats.data) {const available=new Set(seats.data.rows.flatMap(row=>row.seats).filter(seat=>seat.status==='AVAILABLE').map(seat=>seat.id));setSelected(old=>old.filter(seat=>available.has(seat.id)));}
  },[seats.data]);
  useEffect(()=>{
    const previous=document.activeElement as HTMLElement|null,overflow=document.body.style.overflow;
    document.body.style.overflow='hidden';modal.current?.querySelector<HTMLButtonElement>('button')?.focus();
    const onKey=(event:KeyboardEvent)=>{
      if(event.key==='Escape'&&!busy) close();
      if(event.key==='Tab') {
        const items=modal.current?.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input, select, [tabindex="0"]');
        if(!items?.length)return;const first=items[0],last=items[items.length-1];
        if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
        if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
      }
    };
    document.addEventListener('keydown',onKey);
    return()=>{document.body.style.overflow=overflow;document.removeEventListener('keydown',onKey);previous?.focus();};
  },[close,busy]);
  function toggle(seat:Seat) {
    setError('');requestKey.current=crypto.randomUUID();
    setSelected(old=>old.some(item=>item.id===seat.id)?old.filter(item=>item.id!==seat.id):old.length<8?[...old,seat]:old);
  }
  async function reserve() {
    setNeedsAuth(false);setBusy(true);setError('');
    try {const hold=await bookingApi.hold(showtime.id,selected.map(seat=>seat.id),requestKey.current);close();navigate(`/checkout/${hold.id}`);}
    catch(error){setError(errorMessage(error));void seats.refetch();}finally{setBusy(false);}
  }
  const total=selected.reduce((sum,seat)=>sum+seat.pricePaise,0);
  return createPortal(<div className="modal-overlay" onMouseDown={event=>{if(event.target===event.currentTarget&&!busy)close();}}><div ref={modal} className="seat-modal" role="dialog" aria-modal="true" aria-labelledby="seat-title">
    <div className="modal-heading"><div><div className="eyebrow muted">THE BEST SEATS ARE YOURS TO CHOOSE</div><h2 id="seat-title">Make room for your people.</h2><p>{showtime.theatreName} · {showtime.screenName} · {showtimeLabel(showtime.startsAt)}</p></div><button className="icon-button" aria-label="Close seat selection" disabled={busy} onClick={close}><X size={22}/></button></div>
    <div className="preview-notice"><Info size={16}/><span>Choose up to 8 seats. Continue to hold them for up to 10 minutes while you complete a simulated payment.</span></div>
    {needsAuth?<><AuthForm onSuccess={()=>void reserve()}/><button className="text-button" onClick={()=>setNeedsAuth(false)}>Back to your selected seats</button></>:seats.isPending?<div className="seat-loading" role="status">Loading the auditorium…</div>:seats.isError?<ErrorState message={errorMessage(seats.error)} retry={()=>void seats.refetch()}/>:<>
      <div className="seat-map-scroll"><div className="seat-map"><div className="cinema-screen"><span>{seats.data.screenLabel} — ALL EYES THIS WAY</span></div>{seats.data.rows.map(row=><div key={row.label} className="seat-row"><span className="row-label">{row.label}</span>{row.seats.map((seat,index)=><button key={seat.id} className={`seat ${seat.status.toLowerCase()} ${selected.some(item=>item.id===seat.id)?'selected':''} ${index===Math.floor(row.seats.length/2)?'aisle':''}`} disabled={busy||seat.status!=='AVAILABLE'||(selected.length===8&&!selected.some(item=>item.id===seat.id))} aria-label={`${seat.label}, ${seat.category}, ${money(seat.pricePaise)}, ${seat.status.toLowerCase()}${seat.accessible?', accessible':''}`} aria-pressed={selected.some(item=>item.id===seat.id)} onClick={()=>toggle(seat)}><Armchair size={21}/>{seat.accessible&&<span className="accessible-dot"/>}</button>)}<span className="row-label">{row.label}</span></div>)}</div></div>
      <div className="seat-legend"><span><i/>Available</span><span><i className="selected"/>Selected</span><span><i className="unavailable"/>Held / booked / blocked</span><span><i className="accessible"/>Accessible</span></div>
      <div className="seat-inspector" aria-live="polite"><div><strong>{selected.length?`${selected.length} seat${selected.length>1?'s':''} selected`:'Select your seats'}</strong><span>{selected.length?selected.map(seat=>seat.label).join(', '):'Choose a seat to see its category and price'}</span></div><strong className="seat-price">{money(total)}</strong></div>
      {error&&<p className="form-error" role="alert">{error}</p>}
      <div className="modal-footer"><span>Availability is checked again when you continue.</span><button className="button primary" disabled={!selected.length||busy} onClick={()=>user?void reserve():setNeedsAuth(true)}>{busy?'Holding your seats…':user?'Continue to payment':'Sign in & continue'}<ArrowRight size={16}/></button></div>
    </>}
  </div></div>,document.body);
}
