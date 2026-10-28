import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { OrderHero, ReceiptStrip } from '../components/cards';
import { Screen } from '../components/Layout';
import { usePayment } from '../components/payment';
import { ActionBar, ErrorBox, Loading, Notice } from '../components/ui';
import { krw } from '../format';
import { useI18n } from '../i18n';
import { useSession } from '../session';

/** B6: Checkout. Order hero, payment summary and the (test) payment window. */
export default function Checkout() {
  const id = Number(useParams().id);
  const { config } = useSession();
  const { t } = useI18n();
  const navigate = useNavigate();
  const order = useQuery({ queryKey: ['order', id], queryFn: () => api.order(id) });
  const payment = usePayment(id);

  if (order.isLoading) return <Screen back title={t('pay.title')}><Loading /></Screen>;
  if (order.error || !order.data) return <Screen back title={t('pay.title')}><ErrorBox error={order.error} /></Screen>;

  const o = order.data;
  const payable = o.myRole === 'BUYER' && (o.status === 'PENDING_PAYMENT' || o.status === 'PAYMENT_FAILED');

  return (
    <Screen back={`/orders/${o.id}`} title={t('pay.title')} hideNav>
      <OrderHero order={o} />

      <aside className="summary-card pull-up">
        <div className="summary-head"><h2>{t('pay.heading')}</h2></div>
        <ReceiptStrip
          quote={{ budget: o.budget, reward: o.reward, fee: o.fee, tax: o.tax, total: o.initialCharge }}
          budgetLabel={o.source === 'REQUEST' ? t('price.itemBudget') : t('price.itemEstimate')}
          note={t('pay.note')}
        />
      </aside>

      {o.status === 'PAYMENT_FAILED' && <Notice tone="bad">{t('pay.failedBefore')}</Notice>}
      {!payable && <Notice>{t('pay.notPayable')}</Notice>}
      {payment.error && <Notice tone="bad">{payment.error}</Notice>}

      <p className="fine center">{config?.paymentMode === 'toss' ? t('pay.modeToss') : t('pay.modeMock')}</p>

      <ActionBar>
        {payable ? (
          <button className="btn btn-primary btn-block btn-tall" disabled={payment.busy}
            onClick={() => payment.start(() => api.preparePayment(o.id))}>
            {payment.busy ? t('pay.opening') : t('pay.pay', { amount: krw(o.initialCharge) })}
          </button>
        ) : (
          <button className="btn btn-quiet btn-block" onClick={() => navigate(`/orders/${o.id}`)}>{t('pay.viewOrder')}</button>
        )}
      </ActionBar>
      {payment.sheet}
    </Screen>
  );
}
