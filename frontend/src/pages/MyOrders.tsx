import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ClipboardList, Package, Plus } from 'lucide-react';
import { api, type Order } from '../api';
import { OrderRow, RequestCard } from '../components/cards';
import { Screen } from '../components/Layout';
import { Empty, ErrorBox, Loading, Sheet } from '../components/ui';
import { useI18n } from '../i18n';

const DONE = new Set(['COMPLETED', 'SETTLED']);

type Tab = 'active' | 'done' | 'cancelled' | 'requests';

/** B7 (buyer dashboard, Figma "Your finds, all in one place.") and the traveler's order list. */
export default function MyOrders() {
  const { t } = useI18n();
  const [params, setParams] = useSearchParams();
  const role = params.get('view') === 'traveler' ? 'traveler' : 'buyer';
  const requestedTab = (params.get('tab') as Tab) ?? 'active';
  const tab = role === 'traveler' && requestedTab === 'requests' ? 'active' : requestedTab;
  const orders = useQuery({ queryKey: ['myOrders', role], queryFn: () => api.myOrders(role) });
  const requests = useQuery({ queryKey: ['myRequests'], queryFn: api.myRequests, enabled: role === 'buyer' });
  const buyer = role === 'buyer';

  const all = orders.data ?? [];
  const groups: Record<Exclude<Tab, 'requests'>, Order[]> = {
    active: all.filter((o) => !DONE.has(o.status) && o.status !== 'CANCELLED'),
    done: all.filter((o) => DONE.has(o.status)),
    cancelled: all.filter((o) => o.status === 'CANCELLED'),
  };
  const openRequests = requests.data?.filter((r) => r.status === 'OPEN').length ?? 0;
  const tabs: { key: Tab; count?: number }[] = [
    { key: 'active', count: groups.active.length },
    { key: 'done', count: groups.done.length },
    { key: 'cancelled', count: groups.cancelled.length },
  ];
  if (buyer) tabs.push({ key: 'requests', count: openRequests });

  function changeView(view: 'buyer' | 'traveler') {
    setParams({ view, tab: 'active' }, { replace: true });
  }

  return (
    <Screen>
      <div className="tabs" role="tablist" aria-label={t('orders.view.label')}>
        <button role="tab" aria-selected={buyer} onClick={() => changeView('buyer')}>{t('orders.view.buyer')}</button>
        <button role="tab" aria-selected={!buyer} onClick={() => changeView('traveler')}>{t('orders.view.traveler')}</button>
      </div>
      {buyer ? (
        <div className="dashboard-hero is-lime">
          <span className="pill">{t('bd.pill')}</span>
          <h1 className="font-display">{t('bd.title')}</h1>
          <p>{t('bd.copy')}</p>
          <div className="hero-actions">
            <Link to="/requests/new" className="btn btn-primary"><Plus size={16} /> {t('bd.new')}</Link>
          </div>
        </div>
      ) : (
        <div className="dashboard-hero">
          <span className="pill">{t('td.pill')}</span>
          <h1 className="font-display">{t('orders.travelerTitle')}</h1>
        </div>
      )}

      <div className="stat-grid pull-up">
        <div className="stat-card"><span className="meta-label">{t('bd.active')}</span><strong>{groups.active.length}</strong><p>{t('bd.activeNote')}</p></div>
        {buyer
          ? <div className="stat-card"><span className="meta-label">{t('bd.open')}</span><strong>{openRequests}</strong><p>{t('bd.openNote')}</p></div>
          : <div className="stat-card"><span className="meta-label">{t('orders.tab.cancelled')}</span><strong>{groups.cancelled.length}</strong><p>&nbsp;</p></div>}
        <div className="stat-card"><span className="meta-label">{t('bd.done')}</span><strong>{groups.done.length}</strong><p>{t('bd.doneNote')}</p></div>
      </div>

      <div className="tabs" role="tablist">
        {tabs.map((tb) => (
          <button key={tb.key} role="tab" aria-selected={tab === tb.key} onClick={() => setParams({ tab: tb.key }, { replace: true })}>
            {t(`orders.tab.${tb.key}`)}
            {!!tb.count && <span className="tab-count">{tb.count}</span>}
          </button>
        ))}
      </div>

      {tab !== 'requests' && (
        <>
          {orders.isLoading && <Loading />}
          {orders.error && <ErrorBox error={orders.error} onRetry={() => orders.refetch()} />}
          {orders.data && groups[tab].length === 0 && (
            <Empty icon={Package} title={tab === 'active' ? t('orders.emptyActive') : t('orders.empty')}>
              {buyer && tab === 'active' && <Link to="/" className="btn btn-primary">{t('orders.find')}</Link>}
              {!buyer && tab === 'active' && <Link to="/t/requests" className="btn btn-primary">{t('orders.seeRequests')}</Link>}
            </Empty>
          )}
          {orders.data && groups[tab].length > 0 && (
            <Sheet flush><div className="rows">{groups[tab].map((o) => <OrderRow key={o.id} order={o} />)}</div></Sheet>
          )}
        </>
      )}

      {tab === 'requests' && (
        <>
          {requests.isLoading && <Loading />}
          {requests.error && <ErrorBox error={requests.error} />}
          {requests.data?.length === 0 && (
            <Empty icon={ClipboardList} title={t('orders.noRequests')}>
              <Link to="/requests/new" className="btn btn-primary">{t('home.requestCta')}</Link>
            </Empty>
          )}
          {requests.data?.map((r) => (
            <RequestCard key={r.id} request={r} to={r.orderId ? `/orders/${r.orderId}` : `/my-requests/${r.id}`} />
          ))}
        </>
      )}
    </Screen>
  );
}
