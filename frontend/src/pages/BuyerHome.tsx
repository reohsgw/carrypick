import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Search as SearchIcon } from 'lucide-react';
import { api } from '../api';
import { OfferCard, ProductCard } from '../components/cards';
import { Screen } from '../components/Layout';
import { Code, ErrorBox, Loading, SectionHeading } from '../components/ui';
import { countryName } from '../format';
import { useI18n } from '../i18n';
import { IMAGES } from '../images';
import { useSession } from '../session';

const COUNTRY_KEY = 'carrypick.country';

function savedCountry(): string {
  try { return localStorage.getItem(COUNTRY_KEY) ?? 'JP'; } catch { return 'JP'; }
}

/** B1: Buyer home ("Discover"), following the Figma home page. */
export default function BuyerHome() {
  const { config } = useSession();
  const { t, lang } = useI18n();
  const navigate = useNavigate();
  const [country, setCountryState] = useState(savedCountry);
  const [q, setQ] = useState('');
  const board = useQuery({ queryKey: ['board'], queryFn: api.board });
  const ranking = useQuery({ queryKey: ['ranking', country], queryFn: () => api.ranking(country) });
  const offers = useQuery({ queryKey: ['offers', country], queryFn: () => api.offers(country) });
  const name = countryName(config, country, lang);

  // Offer count and first city per country, from the upcoming-trips board.
  const perCountry = useMemo(() => {
    const m: Record<string, { offers: number; city: string | null }> = {};
    for (const r of board.data ?? []) {
      const e = (m[r.countryCode] ??= { offers: 0, city: r.city });
      e.offers += r.offerCount;
    }
    return m;
  }, [board.data]);

  // Countries with trips first, so the row starts with places people can actually order from.
  const countries = useMemo(
    () => [...(config?.countries ?? [])].sort((a, b) => (perCountry[b.code]?.offers ?? 0) - (perCountry[a.code]?.offers ?? 0)),
    [config, perCountry],
  );

  function setCountry(code: string) {
    setCountryState(code);
    try { localStorage.setItem(COUNTRY_KEY, code); } catch { /* ignore */ }
  }

  const live = (offers.data ?? []).filter((o) => o.bookable).slice(0, 3);

  return (
    <Screen flush>
      <section className="hero-section">
        <img src={IMAGES.hero} alt="" />
        <div className="hero-overlay" />
        <div className="hero-inner">
          <span className="hero-pill">{t('brand.tag')}</span>
          <h1 className="hero-title font-display">{t('home.heroTitle')}</h1>
          <p className="hero-body">{t('home.heroBody')}</p>
          <form
            className="hero-search"
            role="search"
            onSubmit={(e) => { e.preventDefault(); navigate(`/search?q=${encodeURIComponent(q)}&country=${country}`); }}
          >
            <SearchIcon size={20} aria-hidden />
            <input placeholder={t('home.searchPlaceholder')} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t('nav.search')} />
            <button className="btn btn-lime btn-small">{t('home.searchBtn')}</button>
          </form>
        </div>
      </section>

      <section className="home-section">
        <SectionHeading eyebrow={t('home.destEyebrow')} title={t('home.destTitle')} />
        <div className="scroll-row">
          {countries.map((c) => {
            const info = perCountry[c.code];
            return (
              <button key={c.code} className="country-card" aria-pressed={c.code === country} onClick={() => setCountry(c.code)}>
                <Code className="is-big">{c.code}</Code>
                <div>
                  <p className="country-card-name">{lang === 'ko' ? c.nameKo : c.name}</p>
                  <div className="country-card-meta">
                    <span>{info?.city ?? ''}</span>
                    <span>{info ? t('home.offersCount', { n: info.offers }) : t('home.noTrips')}</span>
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </section>

      <section className="home-section">
        <SectionHeading
          eyebrow={t('home.trendEyebrow', { country: name })}
          title={t('home.trendTitle')}
          action={ranking.data?.demoData ? <span className="demo-tag">{t('common.demoData')}</span> : undefined}
        />
        {ranking.isLoading && <Loading />}
        {ranking.error && <ErrorBox error={ranking.error} onRetry={() => ranking.refetch()} />}
        {ranking.data && ranking.data.items.length === 0 && <p className="fine">{t('home.popularEmpty', { country: name })}</p>}
        {!!ranking.data?.items.length && (
          <div className="scroll-row">
            {ranking.data.items.map((it) => <ProductCard key={it.productName} item={it} country={country} />)}
          </div>
        )}
      </section>

      <section className="home-section stack">
        <div className="dark-panel">
          <p className="eyebrow">{t('home.liveEyebrow', { country: name })}</p>
          <h2 className="font-display">{t('home.liveTitle')}</h2>
          <p>{t('home.liveBody')}</p>
        </div>
        {offers.isLoading && <Loading />}
        {offers.data && live.length === 0 && <p className="fine center">{t('home.liveEmpty', { country: name })}</p>}
        {live.map((o) => <OfferCard key={o.id} offer={o} />)}
        {live.length > 0 && (
          <Link to={`/country/${country}`} className="link-button center-block">
            {t('home.browse', { country: name })} <ArrowRight size={15} />
          </Link>
        )}
      </section>

      <section className="cta-panel">
        <div>
          <p className="eyebrow">{t('home.ctaEyebrow')}</p>
          <h2 className="font-display">{t('home.ctaTitle')}</h2>
          <p>{t('home.requestBody')}</p>
          <Link to={`/requests/new?country=${country}`} className="btn btn-primary" style={{ marginTop: 22 }}>
            {t('home.ctaBtn')} <ArrowRight size={16} />
          </Link>
        </div>
        <ol className="steps">
          <li><span>01</span>{t('home.step1')}</li>
          <li><span>02</span>{t('home.step2')}</li>
          <li><span>03</span>{t('home.step3')}</li>
        </ol>
      </section>
    </Screen>
  );
}
