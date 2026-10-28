import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  AlertCircle, Camera, Cookie, Headphones, Lamp, Package, Shirt, ShoppingBag, Sparkles, UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react';
import { api, assetUrl, type OrderStatus } from '../api';
import { STATUS_TONE } from '../format';
import { useErrorText, useI18n, type Key } from '../i18n';
import { productPhoto } from '../images';

export function Loading({ label }: { label?: string }) {
  const { t } = useI18n();
  return (
    <div className="state" role="status">
      <span className="spinner" aria-hidden /> {label ?? t('common.loading')}
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  const text = useErrorText();
  return (
    <div className="notice is-bad" role="alert">
      <AlertCircle size={18} aria-hidden />
      <span className="grow">{text(error)}</span>
      {onRetry && <button className="link" onClick={onRetry}>{t('common.retry')}</button>}
    </div>
  );
}

/** Inline message. tone: info (default), warn, bad, good */
export function Notice({ tone = 'info', icon: Icon, children }: { tone?: 'info' | 'warn' | 'bad' | 'good'; icon?: LucideIcon; children: ReactNode }) {
  return (
    <div className={`notice is-${tone}`} role={tone === 'bad' ? 'alert' : undefined}>
      {Icon && <Icon size={18} aria-hidden />}
      <div className="grow">{children}</div>
    </div>
  );
}

export function Empty({ icon: Icon = Package, title, children }: { icon?: LucideIcon; title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <Icon size={26} strokeWidth={1.6} aria-hidden />
      <p className="empty-title">{title}</p>
      {children}
    </div>
  );
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  const { t } = useI18n();
  return <span className={`badge tone-${STATUS_TONE[status]}`}>{t(`st.${status}` as Key)}</span>;
}

export function Tag({ children, tone = 'neutral' }: { children: ReactNode; tone?: string }) {
  return <span className={`badge tone-${tone}`}>{children}</span>;
}

/** Small dark code badge, like "JP" or "TYO". */
export function Code({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <span className={`code ${className}`}>{children}</span>;
}

/** Section title from the Figma design: small uppercase eyebrow + serif heading + optional action. */
export function SectionHeading({ eyebrow, title, action }: { eyebrow?: string; title: string; action?: ReactNode }) {
  return (
    <div className="section-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h2 className="font-display">{title}</h2>
      </div>
      {action}
    </div>
  );
}

const CATEGORY_ICON: Record<string, LucideIcon> = {
  snacks: Cookie, food: UtensilsCrossed, beauty: Sparkles, fashion: Shirt, home: Lamp, electronics: Headphones,
};

export function categoryIcon(category: string | null | undefined): LucideIcon {
  return CATEGORY_ICON[(category ?? '').toLowerCase()] ?? ShoppingBag;
}

/** Photo (own upload or category photo), or a line icon when there is none. Fills its parent. */
export function ProductPhoto({ src, category, iconSize = 36 }: { src?: string | null; category?: string | null; iconSize?: number }) {
  const url = productPhoto(src, category);
  const Icon = categoryIcon(category);
  return url ? <img src={url} alt="" /> : <div className="placeholder"><Icon size={iconSize} strokeWidth={1.4} aria-hidden /></div>;
}

/** Small square thumbnail for list rows. */
export function ProductThumb({ src, category, size = 56 }: { src?: string | null; category?: string | null; size?: number }) {
  const url = productPhoto(src, category);
  const Icon = categoryIcon(category);
  return (
    <div className="thumb" style={{ width: size, height: size }}>
      {url ? <img src={url} alt="" /> : <Icon size={Math.round(size * 0.42)} strokeWidth={1.5} aria-hidden />}
    </div>
  );
}

export function Field({ label, hint, optional, children }: { label: string; hint?: string; optional?: boolean; children: ReactNode }) {
  const { t } = useI18n();
  return (
    <label className="field">
      <span className="field-label">
        {label}
        {optional && <span className="field-optional">{t('common.optional')}</span>}
      </span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}

/** KRW amount input with thousand separators. */
export function MoneyInput({ value, onChange, placeholder, required, id }: {
  value: number | ''; onChange: (v: number | '') => void; placeholder?: string; required?: boolean; id?: string;
}) {
  return (
    <div className="money">
      <span className="money-prefix" aria-hidden>₩</span>
      <input
        id={id}
        inputMode="numeric"
        required={required}
        placeholder={placeholder}
        value={value === '' ? '' : value.toLocaleString('ko-KR')}
        onChange={(e) => {
          const digits = e.target.value.replace(/[^0-9]/g, '');
          onChange(digits === '' ? '' : Math.min(Number(digits), 100_000_000));
        }}
      />
    </div>
  );
}

/** Upload a product photo; returns the server URL. */
export function ImagePicker({ value, onChange }: { value: string | null; onChange: (url: string | null) => void }) {
  const { t } = useI18n();
  const errText = useErrorText();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  return (
    <div className="image-picker">
      {value ? (
        <div className="image-preview">
          <img src={assetUrl(value) ?? ''} alt="" />
          <button type="button" className="link" onClick={() => onChange(null)}>{t('img.remove')}</button>
        </div>
      ) : (
        <button type="button" className="btn btn-quiet btn-small" onClick={() => input.current?.click()} disabled={busy}>
          <Camera size={16} aria-hidden /> {busy ? t('img.uploading') : t('img.add')}
        </button>
      )}
      <input
        ref={input}
        type="file"
        accept="image/*"
        hidden
        onChange={async (e) => {
          const file = e.target.files?.[0];
          if (!file) return;
          setBusy(true);
          setErr(null);
          try {
            onChange((await api.upload(file)).url);
          } catch (ex) {
            setErr(errText(ex));
          } finally {
            setBusy(false);
            e.target.value = '';
          }
        }}
      />
      {err && <span className="field-error">{err}</span>}
    </div>
  );
}

/** Bottom sheet with a serif title and a Close pill, as in the Figma request form. Escape closes it. */
export function Modal({ open, onClose, title, eyebrow, children }: {
  open: boolean; onClose: () => void; title: string; eyebrow?: string; children: ReactNode;
}) {
  const { t } = useI18n();
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal aria-label={title}>
        <div className="modal-head">
          <div>
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            <h2 className="font-display">{title}</h2>
          </div>
          <button className="modal-close" onClick={onClose}>{t('common.close')}</button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** Sticky bottom bar for the main action on detail screens. */
export function ActionBar({ children }: { children: ReactNode }) {
  return <div className="action-bar">{children}</div>;
}

/** White rounded card with an optional small heading row. */
export function Sheet({ title, aside, children, flush }: { title?: string; aside?: ReactNode; children?: ReactNode; flush?: boolean }) {
  return (
    <section className={`card ${flush ? 'is-flush' : ''}`}>
      {(title || aside) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {aside && <div className="card-aside">{aside}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

/** Label / value line used in detail cards. */
export function KV({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="kv">
      <span>{label}</span>
      <span className="kv-value">{children}</span>
    </div>
  );
}

/** Two-letter initials for the round avatar. */
export function initials(name: string): string {
  const clean = name.replace(/\s*\(.*\)\s*$/, '').trim();
  const parts = clean.split(/\s+/);
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : clean.slice(0, 2)).toUpperCase();
}
