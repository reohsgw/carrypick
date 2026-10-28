import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { CircleAlert, CircleCheck } from 'lucide-react';
import { api, type Order } from '../api';
import { Screen } from '../components/Layout';
import { Loading } from '../components/ui';
import { krw } from '../format';
import { useErrorText, useI18n } from '../i18n';

/**
 * B6 result. Toss redirects here after the payment window:
 *   /pay/success?order=..&paymentKey=..&orderId=<tossOrderId>&amount=..
 *   /pay/fail?order=..&code=..&message=..&orderId=<tossOrderId>
 * In mock mode the app navigates here itself with mock=1 after confirming.
 */
export default function PayResult({ success }: { success: boolean }) {
  const [params] = useSearchParams();
  const qc = useQueryClient();
  const { t } = useI18n();
  const errText = useErrorText();
  const orderId = Number(params.get('order'));
  const isMock = params.get('mock') === '1';
  const [state, setState] = useState<'working' | 'ok' | 'failed'>(isMock ? (success ? 'ok' : 'failed') : 'working');
  const [order, setOrder] = useState<Order | null>(null);
  const [message, setMessage] = useState<string | null>(params.get('message'));
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    (async () => {
      try {
        if (isMock) {
          const o = await api.order(orderId);
          setOrder(o);
          if (!success) setMessage(t('pay.declined'));
          return;
        }
        const tossOrderId = params.get('orderId') ?? '';
        if (success) {
          const o = await api.confirmPayment({
            paymentKey: params.get('paymentKey') ?? '',
            tossOrderId,
            amount: Number(params.get('amount')),
          });
          setOrder(o);
          const ok = o.status !== 'PAYMENT_FAILED' && o.status !== 'PENDING_PAYMENT';
          setState(ok ? 'ok' : 'failed');
          if (!ok) setMessage(o.payments.at(-1)?.failReason ?? null);
        } else {
          const o = await api.failPayment({
            tossOrderId,
            code: params.get('code') ?? undefined,
            message: params.get('message') ?? undefined,
          });
          setOrder(o);
          setState('failed');
        }
      } catch (e) {
        setState('failed');
        setMessage(errText(e));
      } finally {
        qc.invalidateQueries();
      }
    })();
  }, [isMock, orderId, params, qc, success, t, errText]);

  const paidExtra = order?.payments.some((p) => p.type === 'EXTRA' && p.status === 'SUCCESS');
  const lastPaid = order?.payments.filter((p) => p.status === 'SUCCESS' && p.type !== 'REFUND').at(-1)?.amount;

  return (
    <Screen title={t('pay.heading')} hideNav>
      {state === 'working' && <Loading label={t('pay.confirming')} />}
      {state === 'ok' && (
        <div className="result">
          <CircleCheck size={48} strokeWidth={1.5} className="result-icon is-good" aria-hidden />
          <h2 className="font-display">{t('pay.done')}</h2>
          {order && <p className="result-amount">{krw(lastPaid ?? order.initialCharge)}</p>}
          <p className="result-body">{paidExtra ? t('pay.doneExtra') : t('pay.doneBody')}</p>
        </div>
      )}
      {state === 'failed' && (
        <div className="result">
          <CircleAlert size={48} strokeWidth={1.5} className="result-icon is-bad" aria-hidden />
          <h2 className="font-display">{t('pay.failed')}</h2>
          <p className="result-body">{message ?? t('pay.failedBody')}</p>
        </div>
      )}
      {state !== 'working' && (
        <div className="stack">
          {state === 'failed' && order?.status === 'PAYMENT_FAILED' && (
            <Link to={`/orders/${orderId}/pay`} className="btn btn-primary btn-block">{t('pay.tryAgain')}</Link>
          )}
          <Link to={`/orders/${orderId}`} className={`btn btn-block ${state === 'ok' ? 'btn-primary' : 'btn-quiet'}`}>{t('pay.viewOrder')}</Link>
        </div>
      )}
    </Screen>
  );
}
