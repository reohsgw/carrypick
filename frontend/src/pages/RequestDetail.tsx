import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink } from 'lucide-react';
import { api } from '../api';
import { firstName, ReceiptStrip } from '../components/cards';
import { Screen } from '../components/Layout';
import { ActionBar, ErrorBox, Field, KV, Loading, Notice, ProductThumb, Sheet, Tag } from '../components/ui';
import { countryName, daysUntil, krw, shortDate } from '../format';
import { useErrorText, useI18n, type Key } from '../i18n';
import { useSession } from '../session';

/**
 * T6: Request detail with Accept (traveler).
 * The same screen shows a buyer their own request with a Withdraw button.
 */
export default function RequestDetail() {
  const id = Number(useParams().id);
  const { config, user } = useSession();
  const { t, lang } = useI18n();
  const errText = useErrorText();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const req = useQuery({ queryKey: ['request', id], queryFn: () => api.request(id) });
  const trips = useQuery({ queryKey: ['myTrips'], queryFn: api.myTrips });
  const [tripId, setTripId] = useState<number | ''>('');

  const accept = useMutation({
    mutationFn: () => api.acceptRequest(id, tripId === '' ? undefined : tripId),
    onSuccess: (order) => { qc.invalidateQueries(); navigate(`/orders/${order.id}`, { replace: true }); },
  });
  const cancel = useMutation({
    mutationFn: () => api.cancelRequest(id),
    onSuccess: () => { qc.invalidateQueries(); navigate('/orders?tab=requests', { replace: true }); },
  });

  if (req.isLoading) return <Screen back title={t('rd.title')}><Loading /></Screen>;
  if (req.error || !req.data) return <Screen back title={t('rd.title')}><ErrorBox error={req.error} /></Screen>;

  const r = req.data;
  const country = countryName(config, r.countryCode, lang);
  const mine = user?.id === r.buyer.id;
  const matching = (trips.data ?? []).filter(
    (tr) => tr.countryCode === r.countryCode && daysUntil(tr.returnDate) >= 0 && tr.returnDate <= r.deadline,
  );

  return (
    <Screen back title={t('rd.title')} hideNav>
      <div className="product-head">
        <ProductThumb src={r.imageUrl} size={88} />
        <div>
          <h2 className="product-title">{r.productName}</h2>
          <p className="product-sub">{mine ? t('rd.yours') : t('rd.by', { name: firstName(r.buyer.name) })}</p>
          <div className="tag-row">
            <Tag tone={r.status === 'OPEN' ? 'good' : 'neutral'}>{t(`req.status.${r.status}` as Key)}</Tag>
            <Tag>{t('rd.needBy', { date: shortDate(r.deadline, lang) })}</Tag>
          </div>
        </div>
      </div>

      <Sheet>
        <KV label={t('rd.country')}>{country}</KV>
        {r.options && <KV label={t('rd.options')}>{r.options}</KV>}
        {r.productLink && (
          <KV label={t('rd.link')}>
            <a href={r.productLink} target="_blank" rel="noreferrer" className="link">{t('rd.open')} <ExternalLink size={14} aria-hidden /></a>
          </KV>
        )}
        <KV label={t('rd.budget')}><strong className="amount">{krw(r.maxBudget)}</strong></KV>
        <KV label={t('rd.reward')}><strong className="amount reward">{krw(r.reward)}</strong></KV>
      </Sheet>
      {r.note && <p className="quote">{r.note}</p>}

      {mine ? (
        <Sheet title={t('rd.youPay')}>
          <ReceiptStrip quote={r.quote} budgetLabel={t('price.itemBudget')} />
        </Sheet>
      ) : (
        r.status === 'OPEN' && (
          <Sheet title={t('rd.whichTrip')}>
            {matching.length === 0 ? (
              <Notice tone="warn">{t('rd.noTrip', { country, date: shortDate(r.deadline, lang) })}</Notice>
            ) : (
              <Field label={t('of.trip')}>
                <select value={tripId === '' ? matching[0].id : tripId} onChange={(e) => setTripId(Number(e.target.value))}>
                  {matching.map((tr) => (
                    <option key={tr.id} value={tr.id}>{tr.city ?? country}, {shortDate(tr.returnDate, lang)}</option>
                  ))}
                </select>
              </Field>
            )}
            <p className="fine">{t('rd.rules', { budget: krw(r.maxBudget), reward: krw(r.reward) })}</p>
          </Sheet>
        )
      )}

      {(accept.error || cancel.error) && <Notice tone="bad">{errText(accept.error ?? cancel.error)}</Notice>}

      <ActionBar>
        {mine && r.status === 'OPEN' && (
          <button className="btn btn-quiet btn-block danger" disabled={cancel.isPending}
            onClick={() => window.confirm(t('rd.withdrawConfirm')) && cancel.mutate()}>
            {t('rd.withdraw')}
          </button>
        )}
        {mine && r.orderId && (
          <button className="btn btn-primary btn-block" onClick={() => navigate(`/orders/${r.orderId}`)}>{t('rd.viewOrder')}</button>
        )}
        {!mine && r.status === 'OPEN' && (
          <button className="btn btn-primary btn-block" disabled={matching.length === 0 || accept.isPending}
            onClick={() => accept.mutate()}>
            {accept.isPending ? t('rd.accepting') : t('rd.accept', { reward: krw(r.reward) })}
          </button>
        )}
      </ActionBar>
    </Screen>
  );
}
