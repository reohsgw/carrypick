import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api, type PaymentPrepare } from '../api';
import { useErrorText, useI18n } from '../i18n';
import { krw } from '../format';
import { Modal } from './ui';

/*
 * Payment launcher used by Checkout (B6) and the over-budget approval (B8).
 *
 * mode "mock": shows a fake card sheet inside the app (no keys, works offline).
 * mode "toss": loads the Toss Payments v2 SDK and opens the real TEST payment window.
 *              Toss redirects back to /pay/success or /pay/fail, where we call the server to confirm.
 * Either way the SERVER decides the amount (prepare) and checks it again (confirm).
 */

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      payment: (opts: { customerKey: string }) => {
        requestPayment: (req: Record<string, unknown>) => Promise<void>;
      };
    };
  }
}

const TOSS_SDK = 'https://js.tosspayments.com/v2/standard';

function loadTossSdk(): Promise<void> {
  if (window.TossPayments) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = TOSS_SDK;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('sdk'));
    document.head.appendChild(s);
  });
}

export function usePayment(orderId: number) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [mock, setMock] = useState<PaymentPrepare | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { t } = useI18n();
  const errorMessage = useErrorText();

  async function start(prepare: () => Promise<PaymentPrepare>) {
    setBusy(true);
    setError(null);
    try {
      const prep = await prepare();
      if (prep.mode === 'mock') {
        setMock(prep);
        return;
      }
      if (!prep.clientKey) throw new Error(t('pay.noKey'));
      await loadTossSdk();
      const toss = window.TossPayments!(prep.clientKey);
      const origin = window.location.origin;
      await toss.payment({ customerKey: prep.customerKey }).requestPayment({
        method: 'CARD',
        amount: { currency: 'KRW', value: prep.amount },
        orderId: prep.tossOrderId,
        orderName: prep.orderName,
        successUrl: `${origin}/pay/success?order=${orderId}`,
        failUrl: `${origin}/pay/fail?order=${orderId}`,
      });
      // The browser leaves the page here.
    } catch (e) {
      // Closing the Toss window rejects the promise; treat it as a soft cancel.
      // Toss SDK errors carry a code (e.g. USER_CANCEL, INVALID_CLIENT_KEY). Show it so problems can be looked up.
      console.error('Payment window error', e);
      const code = (e as { code?: string })?.code;
      if (code === 'USER_CANCEL' || code === 'PAY_PROCESS_CANCELED') setError(null);
      else if (e instanceof Error && e.message === 'sdk') setError(t('pay.sdkFail'));
      else if (e instanceof Error && !('isAxiosError' in e)) setError(code ? `${e.message} (${code})` : e.message);
      else setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function finishMock(success: boolean) {
    if (!mock) return;
    setBusy(true);
    try {
      if (success) {
        await api.confirmPayment({
          paymentKey: `mock_ok_${Date.now()}`,
          tossOrderId: mock.tossOrderId,
          amount: mock.amount,
        });
      } else {
        // Same path a declined card takes in Toss: the server marks the payment FAILED.
        await api.confirmPayment({
          paymentKey: `mock_fail_${Date.now()}`,
          tossOrderId: mock.tossOrderId,
          amount: mock.amount,
        });
      }
      await qc.invalidateQueries();
      setMock(null);
      navigate(`/pay/${success ? 'success' : 'fail'}?order=${orderId}&mock=1`, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function cancelMock() {
    if (mock) {
      try { await api.failPayment({ tossOrderId: mock.tossOrderId, code: 'USER_CANCEL', message: 'Payment window closed' }); } catch { /* ignore */ }
    }
    setMock(null);
    qc.invalidateQueries();
  }

  const sheet = (
    <Modal open={!!mock} onClose={cancelMock} title={t('pay.mockTitle')}>
      {mock && (
        <div className="stack">
          <div className="mockpay-card">
            <span className="mockpay-brand">{t('pay.mockCard')}</span>
            <span className="mockpay-number">4330 0000 0000 1234</span>
          </div>
          <div className="mockpay-amount">
            <span>{mock.orderName}</span>
            <strong>{krw(mock.amount)}</strong>
          </div>
          <p className="fine">{t('pay.mockNote')}</p>
          <button className="btn btn-primary btn-block" disabled={busy} onClick={() => finishMock(true)}>
            {t('pay.approve')}
          </button>
          <button className="btn btn-quiet btn-block" disabled={busy} onClick={() => finishMock(false)}>
            {t('pay.decline')}
          </button>
        </div>
      )}
    </Modal>
  );

  return { start, busy, error, sheet };
}
