import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowRight, Check, Minus, Plus, ShieldCheck } from 'lucide-react';
import { api } from '../api';
import { firstName, ReceiptStrip, useRoute } from '../components/cards';
import { Screen } from '../components/Layout';
import { ActionBar, ErrorBox, KV, Loading, Notice, ProductPhoto, Tag, initials } from '../components/ui';
import { countryName, daysUntil, krw, quote, shortDate } from '../format';
import { useErrorText, useI18n, type Key } from '../i18n';
import { useSession } from '../session';

/** B4: Offer detail, laid out like the Figma product page with a payment summary card. */
export default function OfferDetail() {
  const id = Number(useParams().id);
  const { config, user } = useSession();
  const { t, lang } = useI18n();
  const errText = useErrorText();
  const route = useRoute();
  const navigate = useNavigate();
  const offer = useQuery({ queryKey: ['offer', id], queryFn: () => api.offer(id) });
  const [qty, setQty] = useState(1);

  const book = useMutation({
    mutationFn: () => api.book(id, qty),
    onSuccess: (order) => navigate(`/orders/${order.id}/pay`),
  });

  if (offer.isLoading) return <Screen back title={t('offer.title')}><Loading /></Screen>;
  if (offer.error || !offer.data) return <Screen back title={t('offer.title')}><ErrorBox error={offer.error} /></Screen>;

  const o = offer.data;
  const mine = user?.id === o.traveler.id;
  const q = quote(config, o.estPrice * qty, o.reward * qty);
  const departsIn = daysUntil(o.trip.departDate);
  const country = countryName(config, o.countryCode, lang);
  const cat = o.category ? t(`cat.${o.category}` as Key) : '';

  return (
    <Screen back title={country} hideNav>
      <div className="detail-media"><ProductPhoto src={o.imageUrl} category={o.category} iconSize={56} /></div>

      <div>
        <p className="eyebrow">{t('offer.from', { category: cat, country })}</p>
        <h1 className="detail-title font-display">{o.productName}</h1>
        {o.description && <p className="body-text" style={{ marginTop: 14 }}>{o.description}</p>}
      </div>

      <div className="meta-grid" style={{ margin: 0 }}>
        <div><span className="meta-label">{t('offer.estPrice')}</span><strong className="meta-value" style={{ fontSize: 18 }}>{krw(o.estPrice)}</strong></div>
        <div><span className="meta-label">{t('card.reward')}</span><strong className="meta-value" style={{ fontSize: 18 }}>{krw(o.reward)}</strong></div>
        <div><span className="meta-label">{t('offer.leftLabel')}</span><strong className="meta-value" style={{ fontSize: 18 }}>{o.quantity}</strong></div>
      </div>

      <div className="trust-row">
        <span><ShieldCheck size={16} /> {t('offer.trust2')}</span>
        <span><Check size={16} /> {t('offer.trust1')}</span>
        {!o.bookable && <Tag>{o.status === 'SOLD_OUT' ? t('offer.soldOut') : t('offer.closed')}</Tag>}
        {o.bookable && departsIn <= 7 && <Tag tone="action">{t('offer.departsIn', { n: departsIn })}</Tag>}
      </div>

      <section className="card">
        <div className="person" style={{ marginBottom: 14 }}>
          <span className="avatar">{initials(o.traveler.name)}</span>
          <div>
            <p>{firstName(o.traveler.name)}</p>
            <small>{route(o.trip.city, o.countryCode)}</small>
          </div>
        </div>
        <KV label={t('offer.trip')}>{o.trip.city ?? country}, {shortDate(o.trip.departDate, lang)} – {shortDate(o.trip.returnDate, lang)}</KV>
        <KV label={t('offer.delivery')}>{t('offer.deliveryValue', { date: shortDate(o.trip.returnDate, lang) })}</KV>
        {o.trip.luggageNote && <KV label={t('offer.luggage')}>{o.trip.luggageNote}</KV>}
      </section>

      <aside className="summary-card">
        <div className="summary-head">
          <h2>{t('offer.summary')}</h2>
          {o.bookable && !mine && (
            <div className="stepper" aria-label={t('offer.qty')}>
              <button onClick={() => setQty(Math.max(1, qty - 1))} disabled={qty <= 1} aria-label={t('offer.fewer')}><Minus size={15} /></button>
              <span>{qty}</span>
              <button onClick={() => setQty(Math.min(o.quantity, qty + 1))} disabled={qty >= o.quantity} aria-label={t('offer.more')}><Plus size={15} /></button>
            </div>
          )}
        </div>
        <ReceiptStrip quote={q} note={t('offer.costNote', { price: krw(o.estPrice) })} />
      </aside>

      {book.error && <Notice tone="bad">{errText(book.error)}</Notice>}

      <ActionBar>
        {mine ? (
          <button className="btn btn-quiet btn-block" onClick={() => navigate(`/t/offers/${o.id}/edit`)}>{t('offer.mine')}</button>
        ) : (
          <button className="btn btn-primary btn-block btn-tall btn-split" disabled={!o.bookable || book.isPending} onClick={() => book.mutate()}>
            <span>{book.isPending ? t('offer.booking') : o.bookable ? t('offer.book', { n: qty }) : t('offer.unavailable')}</span>
            {o.bookable && <span className="btn-amount">{krw(q.total)} <ArrowRight size={15} style={{ verticalAlign: -2 }} /></span>}
          </button>
        )}
      </ActionBar>
    </Screen>
  );
}
