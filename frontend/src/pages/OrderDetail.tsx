import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, FileText } from 'lucide-react';
import { api, assetUrl, type Order } from '../api';
import { OrderHero, ReceiptLine, ReceiptStrip, StatusTimeline } from '../components/cards';
import { Screen } from '../components/Layout';
import { usePayment } from '../components/payment';
import { ReceiptCapture } from '../components/receipt';
import { ActionBar, ErrorBox, Field, KV, Loading, Modal, MoneyInput, Notice, ProductThumb, Sheet, StatusBadge } from '../components/ui';
import { krw } from '../format';
import { useErrorText, useI18n, type Key } from '../i18n';

/** B8 (buyer view) and T7 (traveler view) of one order. */
export default function OrderDetail() {
  const id = Number(useParams().id);
  const { t } = useI18n();
  const order = useQuery({ queryKey: ['order', id], queryFn: () => api.order(id), refetchInterval: 15_000 });

  if (order.isLoading) return <Screen back title={t('order.title')}><Loading /></Screen>;
  if (order.error || !order.data) return <Screen back title={t('order.title')}><ErrorBox error={order.error} /></Screen>;

  const o = order.data;
  const buyer = o.myRole === 'BUYER';

  const extraLines = o.extraItemAmount > 0
    ? [
        { label: t('price.extra'), value: o.extraItemAmount },
        { label: t('price.extraFee'), value: o.extraFee },
      ]
    : undefined;

  return (
    <Screen back title={t('order.title')} hideNav>
      <OrderHero order={o} />

      <div className="status-strip pull-up">
        <StatusBadge status={o.status} />
        <span>{t(`next.${buyer ? 'b' : 't'}.${o.status}` as Key)}</span>
      </div>

      <Sheet>
        <div className="product-line is-inset">
          <ProductThumb src={o.imageUrl} category={o.category} size={52} />
          <div className="row-body">
            <div className="row-title">{o.productName}{o.quantity > 1 && <span className="qty"> × {o.quantity}</span>}</div>
            {o.productLink && (
              <a className="link small" href={o.productLink} target="_blank" rel="noreferrer">
                {t('order.link')} <ExternalLink size={14} aria-hidden />
              </a>
            )}
          </div>
        </div>
        {o.options && <KV label={t('order.options')}>{o.options}</KV>}
      </Sheet>

      <Sheet title={t('order.progress')}>
        <StatusTimeline order={o} />
      </Sheet>

      {o.receiptUrl && (
        <Sheet title={t('order.receipt')} aside={<span className="amount">{krw(o.actualPrice)}</span>}>
          <a href={assetUrl(o.receiptUrl) ?? '#'} target="_blank" rel="noreferrer" className="receipt-photo">
            {o.receiptUrl.endsWith('.pdf')
              ? <span className="link"><FileText size={18} aria-hidden /> {t('order.receiptPdf')}</span>
              : <img src={assetUrl(o.receiptUrl) ?? ''} alt={t('order.receipt')} />}
          </a>
        </Sheet>
      )}

      <aside className="summary-card">
        <div className="summary-head"><h2>{buyer ? t('order.amountsBuyer') : t('order.amountsTraveler')}</h2></div>
        <ReceiptStrip
          quote={{ budget: o.budget, reward: o.reward, fee: o.fee, tax: o.tax, total: o.initialCharge }}
          budgetLabel={o.source === 'REQUEST' ? t('price.itemBudget') : t('price.itemEstimate')}
          extra={extraLines}
        />
        {o.extraItemAmount > 0 && (
          <ReceiptLine label={t('price.extraTotal')} value={o.extraCharge} strong />
        )}
        {o.settlement && (
          <div className="settle">
            <ReceiptLine label={t('price.payout')} value={o.settlement.travelerPayout} />
            <ReceiptLine label={t('price.refund')} value={o.settlement.buyerRefund} />
            <p className="receipt-note">{t('price.simulated')}</p>
          </div>
        )}
      </aside>

      {buyer ? <BuyerActions order={o} /> : <TravelerActions order={o} />}
    </Screen>
  );
}

/* ------------------------------------------------------------------ buyer (B8) */

function BuyerActions({ order: o }: { order: Order }) {
  const { t } = useI18n();
  const errText = useErrorText();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const payment = usePayment(o.id);
  const [approvalOpen, setApprovalOpen] = useState(o.status === 'AWAITING_APPROVAL');
  const refresh = () => qc.invalidateQueries();

  const confirm = useMutation({ mutationFn: () => api.confirmReceived(o.id), onSuccess: refresh });
  const reject = useMutation({ mutationFn: () => api.reject(o.id), onSuccess: () => { setApprovalOpen(false); refresh(); } });
  const cancel = useMutation({ mutationFn: () => api.cancelOrder(o.id), onSuccess: refresh });
  const err = confirm.error ?? reject.error ?? cancel.error;

  const canCancel = ['PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID'].includes(o.status);

  return (
    <>
      {err && <Notice tone="bad">{errText(err)}</Notice>}
      {payment.error && <Notice tone="bad">{payment.error}</Notice>}
      {canCancel && (
        <button className="link danger center-block" disabled={cancel.isPending}
          onClick={() => window.confirm(t('order.cancelConfirm') + (o.status === 'PAID' ? ` ${t('order.cancelRefund')}` : '')) && cancel.mutate()}>
          {t('order.cancel')}
        </button>
      )}

      <ActionBar>
        {(o.status === 'PENDING_PAYMENT' || o.status === 'PAYMENT_FAILED') && (
          <button className="btn btn-primary btn-block btn-tall" onClick={() => navigate(`/orders/${o.id}/pay`)}>
            {o.status === 'PAYMENT_FAILED' ? t('pay.tryAgain') : t('pay.pay', { amount: krw(o.initialCharge) })}
          </button>
        )}
        {o.status === 'AWAITING_APPROVAL' && (
          <button className="btn btn-primary btn-block btn-tall" onClick={() => setApprovalOpen(true)}>{t('order.reviewPrice')}</button>
        )}
        {o.status === 'DELIVERED' && (
          <button className="btn btn-primary btn-block btn-tall" disabled={confirm.isPending}
            onClick={() => window.confirm(t('order.receivedConfirm')) && confirm.mutate()}>
            {confirm.isPending ? t('order.confirming') : t('order.received')}
          </button>
        )}
        {['PAID', 'PURCHASED', 'COMPLETED', 'SETTLED', 'CANCELLED'].includes(o.status) && (
          <Link to="/orders" className="btn btn-quiet btn-block">{t('order.backToOrders')}</Link>
        )}
      </ActionBar>

      <Modal open={approvalOpen && o.status === 'AWAITING_APPROVAL'} onClose={() => setApprovalOpen(false)} title={t('order.approvalTitle')} eyebrow={t('st.AWAITING_APPROVAL')}>
        <div className="stack">
          <div className="compare">
            <div><span>{t('order.yourBudget')}</span><strong>{krw(o.budget)}</strong></div>
            <div><span>{t('order.actualPrice')}</span><strong className="danger">{krw(o.actualPrice)}</strong></div>
          </div>
          {o.receiptUrl && !o.receiptUrl.endsWith('.pdf') && <img className="receipt-small" src={assetUrl(o.receiptUrl) ?? ''} alt={t('order.receipt')} />}
          <p className="body-text">{t('order.approvalBody', { extra: krw(o.extraItemAmount), fee: krw(o.extraFee) })}</p>
          <button className="btn btn-primary btn-block btn-tall" disabled={payment.busy}
            onClick={() => { setApprovalOpen(false); payment.start(() => api.approve(o.id)); }}>
            {t('order.approve', { amount: krw(o.extraCharge) })}
          </button>
          <button className="btn btn-quiet btn-block danger" disabled={reject.isPending} onClick={() => reject.mutate()}>
            {t('order.reject')}
          </button>
        </div>
      </Modal>
      {payment.sheet}
    </>
  );
}

/* ------------------------------------------------------------------ traveler (T7) */

function TravelerActions({ order: o }: { order: Order }) {
  const { t } = useI18n();
  const errText = useErrorText();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [price, setPrice] = useState<number | ''>('');
  const [file, setFile] = useState<File | null>(null);
  const refresh = () => qc.invalidateQueries();

  const purchase = useMutation({
    mutationFn: () => api.purchase(o.id, Number(price), file!),
    onSuccess: () => { setOpen(false); refresh(); },
  });
  const deliver = useMutation({ mutationFn: () => api.deliver(o.id), onSuccess: refresh });
  const cancel = useMutation({ mutationFn: () => api.cancelOrder(o.id), onSuccess: refresh });
  const err = deliver.error ?? cancel.error;
  const over = price !== '' && price > o.budget;

  return (
    <>
      {o.status === 'PAID' && <Notice>{t('order.budgetLimit', { amount: krw(o.budget) })}</Notice>}
      {err && <Notice tone="bad">{errText(err)}</Notice>}
      {['PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID'].includes(o.status) && (
        <button className="link danger center-block" disabled={cancel.isPending}
          onClick={() => window.confirm(t('order.travelerCancelConfirm')) && cancel.mutate()}>
          {t('order.cancel')}
        </button>
      )}

      <ActionBar>
        {o.status === 'PAID' && (
          <button className="btn btn-primary btn-block btn-tall" onClick={() => setOpen(true)}>{t('order.bought')}</button>
        )}
        {o.status === 'PURCHASED' && (
          <button className="btn btn-primary btn-block btn-tall" disabled={deliver.isPending}
            onClick={() => window.confirm(t('order.handedOverConfirm')) && deliver.mutate()}>
            {deliver.isPending ? t('order.saving') : t('order.handedOver')}
          </button>
        )}
        {!['PAID', 'PURCHASED'].includes(o.status) && (
          <Link to="/t/orders" className="btn btn-quiet btn-block">{t('order.backToOrders')}</Link>
        )}
      </ActionBar>

      <Modal open={open} onClose={() => setOpen(false)} title={t('buy.title')} eyebrow={o.productName}>
        <form className="stack" onSubmit={(e) => { e.preventDefault(); purchase.mutate(); }}>
          <ReceiptCapture file={file} onFile={setFile} onAmount={setPrice} />
          <Field label={t('buy.price')} hint={t('buy.priceHint', { amount: krw(o.budget) })}>
            <MoneyInput value={price} onChange={setPrice} required />
          </Field>
          {over && <Notice tone="warn">{t('buy.over', { amount: krw(Number(price) - o.budget) })}</Notice>}
          {purchase.error && <Notice tone="bad">{errText(purchase.error)}</Notice>}
          <button className="btn btn-primary btn-block btn-tall" disabled={purchase.isPending || !price || !file}>
            {purchase.isPending ? t('buy.uploading') : t('buy.save')}
          </button>
        </form>
      </Modal>
    </>
  );
}
