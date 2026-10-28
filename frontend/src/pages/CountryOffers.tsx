import { useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Plane, X } from 'lucide-react';
import { api } from '../api';
import { firstName, OfferCard } from '../components/cards';
import { Screen } from '../components/Layout';
import { Code, Empty, ErrorBox, Loading } from '../components/ui';
import { countryName, shortDate } from '../format';
import { useI18n, type Key } from '../i18n';
import { useSession } from '../session';

type Sort = 'soonest' | 'cheapest' | 'newest';

/** B2: Traveler offers for one country (Figma "Traveler offers" page). ?trip= narrows it to one trip. */
export default function CountryOffers() {
  const { code = 'JP' } = useParams();
  const [params, setParams] = useSearchParams();
  const tripId = params.get('trip') ? Number(params.get('trip')) : null;
  const { config } = useSession();
  const { t, lang } = useI18n();
  const name = countryName(config, code, lang);
  const offers = useQuery({ queryKey: ['offers', code], queryFn: () => api.offers(code) });
  const [category, setCategory] = useState<string>('All');
  const [sort, setSort] = useState<Sort>('soonest');

  const trip = useMemo(() => offers.data?.find((o) => o.trip.id === tripId), [offers.data, tripId]);
  const categories = useMemo(
    () => ['All', ...Array.from(new Set((offers.data ?? []).map((o) => o.category ?? 'Other')))],
    [offers.data],
  );
  const list = useMemo(() => {
    const items = (offers.data ?? [])
      .filter((o) => !tripId || o.trip.id === tripId)
      .filter((o) => category === 'All' || (o.category ?? 'Other') === category);
    return [...items].sort((a, b) => {
      if (sort === 'cheapest') return a.unitQuote.total - b.unitQuote.total;
      if (sort === 'newest') return b.id - a.id;
      return a.trip.returnDate.localeCompare(b.trip.returnDate);
    });
  }, [offers.data, category, sort, tripId]);

  return (
    <Screen back="/" title={name}>
      <div className="page-hero">
        <p className="eyebrow">{t('country.eyebrow', { country: name, n: offers.data?.filter((o) => o.bookable).length ?? 0 })}</p>
        <h1 className="page-title font-display">{t('country.title')}</h1>
        <p className="page-copy">{t('country.copy', { country: name })}</p>
        <div className="route-row">
          <Code className="is-big">{code}</Code>
          <ArrowRight size={16} />
          <Code className="is-big">KR</Code>
        </div>
      </div>

      {trip && (
        <div className="flight-filter">
          <Plane size={18} aria-hidden />
          <span className="grow">
            {firstName(trip.traveler.name)}, {trip.trip.city}, {shortDate(trip.trip.departDate, lang)} – {shortDate(trip.trip.returnDate, lang)}
          </span>
          <button className="icon-btn is-small" style={{ color: '#fff' }} aria-label={t('country.all')} onClick={() => setParams({}, { replace: true })}>
            <X size={18} />
          </button>
        </div>
      )}

      <div className="filters">
        <div className="chips">
          {categories.map((cat) => (
            <button key={cat} aria-pressed={cat === category} className="chip" onClick={() => setCategory(cat)}>
              {cat === 'All' ? t('country.all') : t(`cat.${cat}` as Key)}
            </button>
          ))}
        </div>
        <div className="filters-end">
          <span>{t('country.found', { n: list.length })}</span>
          <select className="select" value={sort} onChange={(e) => setSort(e.target.value as Sort)} aria-label="Sort">
            <option value="soonest">{t('country.sort.soonest')}</option>
            <option value="cheapest">{t('country.sort.cheapest')}</option>
            <option value="newest">{t('country.sort.newest')}</option>
          </select>
        </div>
      </div>

      {offers.isLoading && <Loading />}
      {offers.error && <ErrorBox error={offers.error} onRetry={() => offers.refetch()} />}
      {offers.data && list.length === 0 && (
        <Empty icon={Plane} title={t('country.empty', { country: name })}>
          <Link to={`/requests/new?country=${code}`} className="btn btn-primary">{t('home.requestCta')}</Link>
        </Empty>
      )}
      {list.map((o) => <OfferCard key={o.id} offer={o} />)}
      {list.length > 0 && (
        <p className="fine center">
          {t('country.footer')} <Link className="link" to={`/requests/new?country=${code}`}>{t('home.requestCta')}</Link>
        </p>
      )}
    </Screen>
  );
}
