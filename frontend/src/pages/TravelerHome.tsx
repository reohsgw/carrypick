import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, MapPinned, Plus } from 'lucide-react';
import { api } from '../api';
import { firstName, OfferRow, OrderRow, RequestCard, useRoute } from '../components/cards';
import { Screen } from '../components/Layout';
import { Empty, ErrorBox, Loading, SectionHeading, Sheet } from '../components/ui';
import { ACTIVE_STATUSES, daysUntil, krw, shortDate } from '../format';
import { useI18n } from '../i18n';
import { useSession } from '../session';

type Tab = 'overview' | 'requests' | 'offers';

/** T1: Traveler dashboard (Figma "Welcome back, Yuri."). */
export default function TravelerHome() {
  const { user } = useSession();
  const { t, lang } = useI18n();
  const route = useRoute();
  const [tab, setTab] = useState<Tab>('overview');
  const trips = useQuery({ queryKey: ['myTrips'], queryFn: api.myTrips });
  const orders = useQuery({ queryKey: ['myOrders', 'traveler'], queryFn: () => api.myOrders('traveler') });
  const offers = useQuery({ queryKey: ['myOffers'], queryFn: api.myOffers });
  const requests = useQuery({ queryKey: ['openRequests'], queryFn: api.openRequests });

  const active = (orders.data ?? []).filter((o) => ACTIVE_STATUSES.includes(o.status));
  const toDo = active.filter((o) => o.status === 'PAID' || o.status === 'PURCHASED');
  const pending = active
    .filter((o) => ['PAID', 'AWAITING_APPROVAL', 'PURCHASED', 'DELIVERED'].includes(o.status))
    .reduce((sum, o) => sum + o.reward, 0);
  const earned = (orders.data ?? []).filter((o) => o.status === 'SETTLED').reduce((sum, o) => sum + o.reward, 0);
  const next = trips.data?.[0];

  return (
    <Screen>
      <div className="dashboard-hero">
        <span className="pill">{t('td.pill')}</span>
        <h1 className="font-display">{t('td.welcome', { name: user ? firstName(user.name) : '' })}</h1>
        <p>
          {next
            ? `${route(next.city, next.countryCode)}, ${shortDate(next.departDate, lang)} – ${shortDate(next.returnDate, lang)}`
            : t('td.noTrip')}
        </p>
        <div className="hero-actions">
          <Link to="/t/trips/new" className="glass-btn"><Plus size={15} /> {t('t.addTrip')}</Link>
          {next && daysUntil(next.departDate) > 0 && (
            <Link to={`/t/offers/new?trip=${next.id}`} className="glass-btn">{t('t.offerItem')}</Link>
          )}
        </div>
      </div>

      <div className="stat-grid pull-up">
        <div className="stat-card"><span className="meta-label">{t('t.toDo')}</span><strong>{toDo.length}</strong><p>{t('td.toDoNote')}</p></div>
        <div className="stat-card"><span className="meta-label">{t('t.pending')}</span><strong>{krw(pending)}</strong><p>{t('td.pendingNote')}</p></div>
        <div className="stat-card"><span className="meta-label">{t('t.earned')}</span><strong>{krw(earned)}</strong><p>{t('td.earnedNote')}</p></div>
      </div>

      <div className="tabs" role="tablist">
        {(['overview', 'requests', 'offers'] as const).map((k) => (
          <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)}>{t(`td.tab.${k}`)}</button>
        ))}
      </div>

      {tab === 'overview' && (
        <>
          <Sheet title={t('t.active')} aside={<Link to="/t/orders" className="link-button">{t('common.seeAll')} <ArrowRight size={14} /></Link>} flush>
            {orders.isLoading && <Loading />}
            {orders.error && <ErrorBox error={orders.error} />}
            {orders.data && active.length === 0 && <p className="card-pad fine">{t('t.noActive')}</p>}
            <div className="rows">{active.map((o) => <OrderRow key={o.id} order={o} />)}</div>
          </Sheet>

          <section className="card">
            <div className="card-head">
              <h2>{t('td.journey')}</h2>
              <Link to="/t/trips/new" className="card-aside link-button"><Plus size={14} /> {t('t.addTrip')}</Link>
            </div>
            {trips.isLoading && <Loading />}
            {trips.data?.length === 0 && (
              <Empty icon={MapPinned} title={t('t.noTrips')}>
                <Link to="/t/trips/new" className="btn btn-primary">{t('t.registerTrip')}</Link>
              </Empty>
            )}
            {!!trips.data?.length && (
              <ol className="timeline">
                {trips.data.map((tr, i) => {
                  const d = daysUntil(tr.departDate);
                  return (
                    <li key={tr.id} className={`timeline-item ${i === 0 ? 'is-done' : ''}`} style={{ color: 'var(--ink)' }}>
                      <span className="timeline-dot" aria-hidden />
                      <div className="grow">
                        <span className="fine">{shortDate(tr.departDate, lang)} – {shortDate(tr.returnDate, lang)}</span>
                        <p className="timeline-label">{route(tr.city, tr.countryCode)}</p>
                        <p className="fine">{tr.luggageNote ?? ''}</p>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <span className="fine" style={{ display: 'block' }}>{d > 0 ? t('t.inDays', { n: d }) : t('t.onTrip')}</span>
                        {d > 0 && <Link to={`/t/offers/new?trip=${tr.id}`} className="link small">{t('t.offerItem')}</Link>}
                      </div>
                    </li>
                  );
                })}
              </ol>
            )}
          </section>
        </>
      )}

      {tab === 'requests' && (
        <>
          <SectionHeading
            eyebrow={t('td.matched')}
            title={t('td.worth')}
            action={<Link to="/t/requests" className="link-button">{t('common.seeAll')} <ArrowRight size={14} /></Link>}
          />
          {requests.isLoading && <Loading />}
          {requests.data?.length === 0 && <Empty title={t('rb.empty')} />}
          {requests.data?.slice(0, 5).map((r) => <RequestCard key={r.id} request={r} to={`/t/requests/${r.id}`} />)}
        </>
      )}

      {tab === 'offers' && (
        <Sheet title={t('t.offers')} aside={<Link to="/t/offers/new" className="link-button"><Plus size={14} /> {t('t.newOffer')}</Link>} flush>
          {offers.data?.length === 0 && <p className="card-pad fine">{t('t.noOffers')}</p>}
          <div className="rows">{offers.data?.map((o) => <OfferRow key={o.id} offer={o} />)}</div>
        </Sheet>
      )}
    </Screen>
  );
}
