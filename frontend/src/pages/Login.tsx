import { useNavigate, useSearchParams } from 'react-router-dom';
import { AuthForm } from '../components/AuthForm';
import { useSession } from '../lib/session';
export function Login() {
  const navigate=useNavigate();const [params]=useSearchParams();
  const admin=params.get('admin')==='1';
  return <div className="page-shell auth-page"><div className="auth-intro"><div className="eyebrow muted">THE BEST STORIES ARE SHARED</div><h2>Your movie.<br/>Your people.<br/><span>Your seats.</span></h2><p>A few clicks between you and the big screen.</p></div><AuthForm admin={admin} onSuccess={()=>{const path=params.get('returnTo');navigate(path?.startsWith('/')&&!path.startsWith('//')?path:useSession.getState().user?.role==='ADMIN'?'/admin':'/');}}/></div>;
}
