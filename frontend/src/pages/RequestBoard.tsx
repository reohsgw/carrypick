import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, ClipboardList, MapPinned } from 'lucide-react';
import { api } from '../api';
import { RequestCard } from '../components/cards';
import { Screen } from '../components/Layout';
import { Code, Empty, ErrorBox, Loading } from '../components/ui';
import { boardDate } from '../format';
import { useI18n } from '../i18n';

/** T5: Open buyer requests that fit my trips (Figma "Open requests" page). */
export default function RequestBoard() {
  const { t } = useI18n();
  const trips = useQuery({ queryKey: ['myTrips'], queryFn: api.myTrips });
  const requests = useQuery({ queryKey: ['openRequests'], queryFn: api.openRequests });

  return (
    <Screen>
      <div className="page-hero">
        <p className="eyebrow">{t('rb.eyebrow')}</p>
        <h1 className="page-title font-display">{t('rb.title')}</h1>
        <p className="page-copy">{t('rb.copy')}</p>
        <Link to="/t" className="btn btn-quiet btn-small" style={{ marginTop: 16 }}>{t('td.pill')} <ArrowRight size={15} /></Link>
      </div>

      {trips.data && trips.data.length > 0 && (
        <div className="trip-chips" aria-label={t('rb.matching')}>
          {trips.data.map((tr) => (
            <span key={tr.id} className="trip-chip">
              <Code>{tr.countryCode}</Code>
              {tr.city ?? ''} {boardDate(tr.departDate)} – {boardDate(tr.returnDate)}
            </span>
          ))}
        </div>
      )}
      {(requests.isLoading || trips.isLoading) && <Loading />}
      {requests.error && <ErrorBox error={requests.error} onRetry={() => requests.refetch()} />}
      {trips.data?.length === 0 && (
        <Empty icon={MapPinned} title={t('rb.needTrip')}>
          <Link to="/t/trips/new" className="btn btn-primary">{t('t.registerTrip')}</Link>
        </Empty>
      )}
      {trips.data && trips.data.length > 0 && requests.data?.length === 0 && (
        <Empty icon={ClipboardList} title={t('rb.empty')} />
      )}
      {requests.data?.map((r) => <RequestCard key={r.id} request={r} to={`/t/requests/${r.id}`} />)}
    </Screen>
  );
}
