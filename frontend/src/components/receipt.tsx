import { useEffect, useRef, useState } from 'react';
import { Camera, ScanLine, ShieldAlert } from 'lucide-react';
import { api, type ReceiptCategory, type ReceiptScan } from '../api';
import { krw } from '../format';
import { useErrorText, useI18n, type Key } from '../i18n';
import { Notice } from './ui';

const FLAG_KEYS: Partial<Record<ReceiptCategory, Key>> = {
  alcohol: 'buy.flag.alcohol',
  tobacco: 'buy.flag.tobacco',
  supplement: 'buy.flag.supplement',
  medicine: 'buy.flag.medicine',
  meat: 'buy.flag.meat',
};

function money(amount: number | null, currency: string | null): string {
  if (amount == null) return '-';
  if (!currency) return amount.toLocaleString();
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount);
  } catch {
    return `${amount.toLocaleString()} ${currency}`;
  }
}

/**
 * T7 receipt step: pick a photo, optionally let the AI read it, then the traveler confirms the won amount.
 * The AI never saves anything by itself; it only fills in the price field, which stays editable.
 */
export function ReceiptCapture({ file, onFile, onAmount }: {
  file: File | null;
  onFile: (f: File | null) => void;
  onAmount: (krw: number) => void;
}) {
  const { t } = useI18n();
  const errText = useErrorText();
  const input = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [scan, setScan] = useState<ReceiptScan | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!file || !file.type.startsWith('image/')) { setPreview(null); return; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  async function read() {
    if (!file) return;
    setBusy(true);
    setErr(null);
    try {
      const res = await api.scanReceipt(file);
      setScan(res);
      if (res.totalKrw != null) onAmount(res.totalKrw);
    } catch (e) {
      setErr(errText(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="capture">
      <div className="capture-pick">
        {preview ? (
          <img className="capture-thumb" src={preview} alt="" />
        ) : (
          <div className="capture-thumb is-empty"><Camera size={22} strokeWidth={1.5} aria-hidden /></div>
        )}
        <div className="capture-pick-body">
          <span className="field-label">{t('buy.receipt')}</span>
          <span className="field-hint">{file ? file.name : t('buy.receiptHint')}</span>
          <button type="button" className="link" onClick={() => input.current?.click()}>
            {file ? t('buy.change') : t('buy.choose')}
          </button>
        </div>
        <input
          ref={input}
          type="file"
          accept="image/*,application/pdf"
          aria-label={t('buy.receipt')}
          hidden
          onChange={(e) => { onFile(e.target.files?.[0] ?? null); setScan(null); setErr(null); }}
        />
      </div>

      {file && file.type.startsWith('image/') && !scan && (
        <button type="button" className="btn btn-scan btn-block" onClick={read} disabled={busy}>
          <ScanLine size={18} aria-hidden className={busy ? 'is-scanning' : ''} />
          {busy ? t('buy.scanning') : t('buy.scan')}
        </button>
      )}
      {err && <Notice tone="bad">{err}</Notice>}

      {scan && (
        <div className="scan">
          <p className={`scan-source ${scan.mock ? 'is-mock' : ''}`}>{scan.mock ? t('buy.scanMock') : t('buy.scanResult')}</p>
          {scan.store && <div className="scan-store">{scan.store}</div>}
          <ul className="scan-items">
            {scan.items.map((it, i) => (
              <li key={i}>
                <span className="scan-item-name">
                  {it.name}
                  {it.quantity && it.quantity > 1 ? <small> × {it.quantity}</small> : null}
                </span>
                <span>{money(it.price, scan.currency)}</span>
              </li>
            ))}
          </ul>
          <div className="scan-total">
            <span>{t('buy.totalForeign')}</span>
            <strong>{money(scan.total, scan.currency)}</strong>
          </div>
          {scan.totalKrw != null && (
            <div className="scan-krw">
              <div>
                <strong>{t('buy.totalKrw', { amount: krw(scan.totalKrw) })}</strong>
                {scan.krwPerUnit != null && scan.currency && scan.currency !== 'KRW' && (
                  <small>{t('buy.rate', { rate: scan.krwPerUnit, cur: scan.currency })}</small>
                )}
              </div>
              <button type="button" className="btn btn-quiet btn-small" onClick={() => onAmount(scan.totalKrw!)}>
                {t('buy.useAmount')}
              </button>
            </div>
          )}
          {scan.customsFlags.length > 0 && (
            <Notice tone="warn" icon={ShieldAlert}>
              <strong>{t('buy.flags')}</strong>
              <ul className="flag-list">
                {scan.customsFlags.map((f) => FLAG_KEYS[f] && <li key={f}>{t(FLAG_KEYS[f]!)}</li>)}
              </ul>
            </Notice>
          )}
          {scan.note && !scan.mock && <p className="fine">{scan.note}</p>}
        </div>
      )}
    </div>
  );
}
