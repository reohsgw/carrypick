import type { AppConfig, Country, OrderStatus, Quote } from './api';
import type { Lang } from './i18n';

export const krw = (n: number | null | undefined) =>
  n == null ? '-' : `₩${Math.round(n).toLocaleString('ko-KR')}`;

function toDate(iso: string): Date {
  return iso.length === 10 ? new Date(`${iso}T00:00:00`) : new Date(iso);
}

/** "10월 28일" / "Oct 28" */
export function shortDate(iso: string | null | undefined, lang: Lang): string {
  if (!iso) return '-';
  return toDate(iso).toLocaleDateString(lang === 'ko' ? 'ko-KR' : 'en-US', { month: 'short', day: 'numeric' });
}

/** "10.28", the way a departures board prints it. */
export function boardDate(iso: string): string {
  const d = toDate(iso);
  return `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
}

export function dateTime(iso: string | null | undefined, lang: Lang): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString(lang === 'ko' ? 'ko-KR' : 'en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

/** yyyy-mm-dd for <input type="date">, n days from today (local time). */
export function isoDay(daysFromToday = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromToday);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function daysUntil(isoDate: string): number {
  const target = new Date(`${isoDate}T00:00:00`).getTime();
  const today = new Date(`${isoDay(0)}T00:00:00`).getTime();
  return Math.round((target - today) / 86_400_000);
}

export function countryOf(config: AppConfig | undefined, code: string): Country {
  return config?.countries.find((c) => c.code === code) ?? { code, name: code, nameKo: code, currency: '', flag: '' };
}

export function countryName(config: AppConfig | undefined, code: string, lang: Lang): string {
  const c = countryOf(config, code);
  return lang === 'ko' ? c.nameKo : c.name;
}

/*
 * IATA city codes, so trips read like boarding passes (TYO, PAR, BKK).
 * Unknown cities fall back to a code for the country's main city.
 */
const CITY_CODES: Record<string, string> = {
  tokyo: 'TYO', '도쿄': 'TYO', osaka: 'OSA', '오사카': 'OSA', kyoto: 'UKY', '교토': 'UKY', fukuoka: 'FUK', '후쿠오카': 'FUK',
  sapporo: 'SPK', '삿포로': 'SPK', okinawa: 'OKA', naha: 'OKA', nagoya: 'NGO',
  'new york': 'NYC', '뉴욕': 'NYC', 'los angeles': 'LAX', la: 'LAX', 'san francisco': 'SFO', seattle: 'SEA', hawaii: 'HNL', honolulu: 'HNL',
  paris: 'PAR', '파리': 'PAR', nice: 'NCE', lyon: 'LYS',
  london: 'LON', '런던': 'LON', manchester: 'MAN', edinburgh: 'EDI',
  bangkok: 'BKK', '방콕': 'BKK', 'chiang mai': 'CNX', phuket: 'HKT',
  hanoi: 'HAN', '하노이': 'HAN', 'ho chi minh': 'SGN', saigon: 'SGN', 'da nang': 'DAD', danang: 'DAD', '다낭': 'DAD',
  bali: 'DPS', '발리': 'DPS', jakarta: 'JKT',
  taipei: 'TPE', '타이베이': 'TPE', kaohsiung: 'KHH',
};
const COUNTRY_HUB: Record<string, string> = { JP: 'TYO', US: 'NYC', FR: 'PAR', GB: 'LON', TH: 'BKK', VN: 'HAN', ID: 'JKT', TW: 'TPE' };
export const HOME_CODE = 'SEL';

export function cityCode(city: string | null | undefined, countryCode: string): string {
  const key = (city ?? '').trim().toLowerCase();
  return CITY_CODES[key] ?? COUNTRY_HUB[countryCode] ?? countryCode;
}

/** Same formula as the Spring PricingService. The server value is the one actually charged. */
export function quote(config: AppConfig | undefined, budget: number, reward: number): Quote {
  const feeRate = config?.feeRate ?? 0.1;
  const threshold = config?.taxThresholdKrw ?? 150000;
  const taxRate = config?.taxRate ?? 0.1;
  const fee = Math.round(feeRate * (budget + reward));
  const tax = budget > threshold ? Math.round(taxRate * budget) : 0;
  return { budget, reward, fee, tax, total: budget + reward + fee + tax };
}

export const CATEGORIES = ['Snacks', 'Food', 'Beauty', 'Fashion', 'Home', 'Electronics', 'Other'] as const;

export type Tone = 'neutral' | 'action' | 'progress' | 'good' | 'bad';

export const STATUS_TONE: Record<OrderStatus, Tone> = {
  PENDING_PAYMENT: 'action',
  PAYMENT_FAILED: 'bad',
  PAID: 'progress',
  AWAITING_APPROVAL: 'action',
  PURCHASED: 'progress',
  DELIVERED: 'action',
  COMPLETED: 'good',
  SETTLED: 'good',
  CANCELLED: 'neutral',
};

export const ACTIVE_STATUSES: OrderStatus[] = ['PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'AWAITING_APPROVAL', 'PURCHASED', 'DELIVERED'];
