import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, Plane, ShoppingBag } from 'lucide-react';
import { api } from '../api';
import { Wordmark } from '../components/Layout';
import { Field, Notice } from '../components/ui';
import { LangToggle, useErrorText, useI18n } from '../i18n';
import { IMAGES } from '../images';
import { useSession } from '../session';

const DEMO_PASSWORD = 'carrypick123';

/** C1: Log in / Sign up, laid out like the Figma auth screen. */
export default function Login() {
  const { login, user } = useSession();
  const { t } = useI18n();
  const errText = useErrorText();
  const navigate = useNavigate();
  const [tab, setTab] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (user) return <Navigate to="/" replace />;

  async function submit(e?: FormEvent, override?: { email: string; password: string }) {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res =
        tab === 'signup' && !override
          ? await api.signup({ email, password, name, mode: 'BUYER' })
          : await api.login(override ?? { email, password });
      login(res.token, res.user);
      navigate('/', { replace: true });
    } catch (err) {
      setError(errText(err));
    } finally {
      setBusy(false);
    }
  }

  const signup = tab === 'signup';

  return (
    <div className="auth">
      <div className="auth-photo">
        <img src={IMAGES.hero} alt="" />
        <div className="auth-top">
          <Wordmark />
          <LangToggle />
        </div>
        <div className="auth-photo-text">
          <span>{t('login.trust1')}</span>
          <span>{t('login.trust2')}</span>
        </div>
      </div>

      <div className="auth-body">
        <p className="eyebrow">{signup ? t('login.eyebrowUp') : t('login.eyebrowIn')}</p>
        <h2 className="font-display">{signup ? t('login.titleUp') : t('login.titleIn')}</h2>
        <p className="auth-copy">{signup ? t('login.copyUp') : t('login.copyIn')}</p>

        <form onSubmit={submit} className="stack">
          {signup && (
            <Field label={t('login.name')}>
              <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={40} autoComplete="name" />
            </Field>
          )}
          <Field label={t('login.email')}>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" placeholder="you@example.com" />
          </Field>
          <Field label={t('login.password')}>
            <input
              type="password" value={password} onChange={(e) => setPassword(e.target.value)} required
              minLength={signup ? 8 : 1} placeholder={signup ? t('login.passwordHint') : ''}
              autoComplete={signup ? 'new-password' : 'current-password'}
            />
          </Field>
          {error && <Notice tone="bad">{error}</Notice>}
          <button className="btn btn-primary btn-block btn-tall" disabled={busy}>
            {busy ? t('login.wait') : t(`login.submit.${tab}`)}
            {!busy && <ArrowRight size={16} />}
          </button>
        </form>

        <p className="auth-switch">
          {signup ? t('login.haveAccount') : t('login.newHere')}{' '}
          <button type="button" onClick={() => { setTab(signup ? 'login' : 'signup'); setError(null); }}>
            {signup ? t('login.tab.login') : t('login.createAccount')}
          </button>
        </p>

        {!signup && (
          <div className="demo">
            <div className="demo-head">
              <span>{t('login.demo')}</span>
              <span className="fine">{t('login.demoHint', { pw: DEMO_PASSWORD })}</span>
            </div>
            <div className="demo-buttons">
              <button className="btn btn-quiet btn-small" disabled={busy}
                onClick={() => submit(undefined, { email: 'buyer@carrypick.test', password: DEMO_PASSWORD })}>
                <ShoppingBag size={16} aria-hidden /> {t('login.demoBuyer')}
              </button>
              <button className="btn btn-quiet btn-small" disabled={busy}
                onClick={() => submit(undefined, { email: 'traveler@carrypick.test', password: DEMO_PASSWORD })}>
                <Plane size={16} aria-hidden /> {t('login.demoTraveler')}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
