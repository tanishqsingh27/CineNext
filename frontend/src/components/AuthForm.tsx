import { useState, type FormEvent } from 'react';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { authApi, errorMessage } from '../lib/api';
import { useSession } from '../lib/session';

export function AuthForm({onSuccess,admin=false}:{onSuccess?:()=>void;admin?:boolean}) {
  const [register,setRegister]=useState(false),[name,setName]=useState(''),[email,setEmail]=useState(''),[password,setPassword]=useState('');
  const [busy,setBusy]=useState(false),[error,setError]=useState('');
  const signIn=useSession(s=>s.signIn);
  async function submit(event:FormEvent) {
    event.preventDefault();setBusy(true);setError('');
    try {
      const session=register?await authApi.register(name,email,password):await authApi.login(email,password);
      if(admin&&session.user.role!=='ADMIN') {setError('This is a customer account. Use an administrator account to access the cinema dashboard.');return;}
      signIn(session);onSuccess?.();
    } catch(error) {setError(errorMessage(error));} finally {setBusy(false);}
  }
  return <section className="auth-card">
    <div className="eyebrow"><ShieldCheck size={15}/>{admin?'CINEMA MANAGEMENT':'YOUR NEXT MOVIE NIGHT'}</div>
    <h1>{register?'A seat for every story.':admin?'Admin sign in.':'Welcome back.'}</h1>
    <p>{admin?'Manage movies, cinemas, showtimes, and verify tickets.':register?'Create your customer account to book seats and keep your tickets together.':'Sign in to book seats and find your tickets.'}</p>
    {!admin&&<div className="auth-tabs"><button type="button" className={!register?'active':''} onClick={()=>{setRegister(false);setError('');}}>Sign in</button><button type="button" className={register?'active':''} onClick={()=>{setRegister(true);setError('');}}>Create account</button></div>}
    <form onSubmit={submit} className="form-stack">
      {register&&<label>Full name<input autoComplete="name" value={name} onChange={e=>setName(e.target.value)} required maxLength={80}/></label>}
      <label>Email address<input type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} required maxLength={254}/></label>
      <label>Password<input type="password" autoComplete={register?'new-password':'current-password'} value={password} onChange={e=>setPassword(e.target.value)} required minLength={register?10:1} maxLength={72}/></label>
      {register&&<small>Use at least 10 characters. Customer accounts cannot grant administrator access.</small>}
      {error&&<p className="form-error" role="alert">{error}</p>}
      <button className="button primary" disabled={busy}>{busy?'Please wait…':register?'Create account & continue':'Sign in & continue'}<ArrowRight size={17}/></button>
    </form>
    <p className="auth-footnote">{admin?'Administrator accounts are provisioned by the project owner using the local Create-Admin setup command.':'Your session stays in this tab. Sign in again after a page reload.'}</p>
  </section>;
}
