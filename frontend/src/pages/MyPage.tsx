import { Link, useNavigate } from 'react-router-dom';
import { Bell, ChevronRight, ClipboardList, LogOut, MapPinned, Package } from 'lucide-react';
import { Screen } from '../components/Layout';
import { Sheet, Tag } from '../components/ui';
import { LangToggle, useI18n } from '../i18n';
import { useSession } from '../session';

/** C2: Profile, Buyer / Traveler mode, language, log out. */
export default function MyPage() {
  const { user, logout, unread, config } = useSession();
  const { t } = useI18n();
  const navigate = useNavigate();
  if (!user) return null;

  return (
    <Screen title={t('me.title')}>
      <div className="profile">
        <div className="avatar" aria-hidden>{user.name.slice(0, 1)}</div>
        <div className="grow">
          <div className="profile-name">{user.name}</div>
          <div className="profile-email">{user.email}</div>
        </div>
        {user.test && <Tag tone="action">{t('me.test')}</Tag>}
      </div>

      <Sheet title={t('lang.label')} aside={<LangToggle />} />

      <Sheet flush>
        <div className="rows">
          <Link to="/notifications" className="row menu-row">
            <Bell size={20} strokeWidth={1.75} aria-hidden />
            <span className="row-body">{t('me.notifications')}</span>
            {unread > 0 && <span className="dot is-inline">{unread}</span>}
            <ChevronRight size={18} className="row-chevron" aria-hidden />
          </Link>
          <Link to="/orders" className="row menu-row">
            <Package size={20} strokeWidth={1.75} aria-hidden />
            <span className="row-body">{t('me.orders')}</span>
            <ChevronRight size={18} className="row-chevron" aria-hidden />
          </Link>
          <Link to="/orders?view=buyer&tab=requests" className="row menu-row">
            <ClipboardList size={20} strokeWidth={1.75} aria-hidden />
            <span className="row-body">{t('me.requests')}</span>
            <ChevronRight size={18} className="row-chevron" aria-hidden />
          </Link>
          <Link to="/t" className="row menu-row">
            <MapPinned size={20} strokeWidth={1.75} aria-hidden />
            <span className="row-body">{t('nav.travel')}</span>
            <ChevronRight size={18} className="row-chevron" aria-hidden />
          </Link>
          <button className="row menu-row danger" onClick={() => { logout(); navigate('/login', { replace: true }); }}>
            <LogOut size={20} strokeWidth={1.75} aria-hidden />
            <span className="row-body">{t('me.logout')}</span>
          </button>
        </div>
      </Sheet>

      <p className="fine center">
        {t('me.footer', { mode: config?.paymentMode === 'toss' ? t('me.modeToss') : t('me.modeMock') })}
      </p>
    </Screen>
  );
}
