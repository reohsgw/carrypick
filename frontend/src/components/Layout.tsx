import type { ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { Bell, ChevronLeft, Home, Package, Plane, Search, User } from 'lucide-react';
import { useI18n } from '../i18n';
import { useSession } from '../session';
import { initials } from './ui';

/**
 * Screen: sticky header + content + the role's bottom navigation.
 * Without a title the header shows the brand, the Shopping/Traveling pill, the bell and the avatar,
 * as in the Figma design. With a title it shows a back button and the title.
 */
export function Screen({ title, back, right, children, hideNav, flush }: {
  title?: string; back?: boolean | string; right?: ReactNode; children: ReactNode;
  hideNav?: boolean; flush?: boolean;
}) {
  const navigate = useNavigate();
  const { t } = useI18n();
  return (
    <div className="screen">
      <header className="app-header">
        {back && (
          <button
            className="icon-btn"
            aria-label={t('common.back')}
            onClick={() => (typeof back === 'string' ? navigate(back) : navigate(-1))}
          >
            <ChevronLeft size={22} />
          </button>
        )}
        {title ? <h1 className="header-title">{title}</h1> : <BrandHeader />}
        <div className="header-right">{right ?? (!title && <HeaderActions />)}</div>
      </header>
      <main className={`content ${flush ? 'is-flush' : ''} ${hideNav ? 'has-actionbar' : ''}`}>{children}</main>
      {!hideNav && <BottomNav />}
    </div>
  );
}

export function Wordmark({ to }: { to?: string }) {
  const inner = (
    <>
      <span className="logo-mark"><Plane size={16} strokeWidth={2} /></span>
      <span className="brand-name">carrypick<b>.</b></span>
    </>
  );
  return to ? <Link to={to} className="brand" aria-label="CarryPick">{inner}</Link> : <span className="brand" aria-label="CarryPick">{inner}</span>;
}

function BrandHeader() {
  return <Wordmark to="/" />;
}

function HeaderActions() {
  const { user, unread } = useSession();
  const { t } = useI18n();
  if (!user) return null;
  return (
    <>
      <NavLink to="/notifications" className="icon-btn" aria-label={t('me.notifications')}>
        <Bell size={20} />
        {unread > 0 && <span className="dot">{unread > 9 ? '9+' : unread}</span>}
      </NavLink>
      <Link to="/me" className="avatar-btn" aria-label={t('me.title')}>{initials(user.name)}</Link>
    </>
  );
}

/** Kept for pages that want just the bell on the right. */
export function BellLink() {
  return <HeaderActions />;
}

function BottomNav() {
  const { user } = useSession();
  const { t } = useI18n();
  if (!user) return null;
  const items = [
    { to: '/', label: t('nav.discover'), icon: Home, end: true },
    { to: '/search', label: t('nav.search'), icon: Search },
    { to: '/t', label: t('nav.travel'), icon: Plane },
    { to: '/orders', label: t('nav.orders'), icon: Package },
    { to: '/me', label: t('nav.me'), icon: User },
  ];
  return (
    <nav className="mobile-nav" aria-label="Main">
      {items.map((it) => (
        <NavLink key={it.to} to={it.to} end={it.end} className="mobile-nav-item">
          <it.icon size={20} strokeWidth={1.8} aria-hidden />
          <span>{it.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
