import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { Screen } from '../components/Layout';
import { ActionBar, Field, Notice, Sheet } from '../components/ui';
import { isoDay } from '../format';
import { useErrorText, useI18n } from '../i18n';
import { useSession } from '../session';

/** T2: Register a trip. */
export default function NewTrip() {
  const { config } = useSession();
  const { t, lang } = useI18n();
  const errText = useErrorText();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [countryCode, setCountry] = useState('JP');
  const [city, setCity] = useState('');
  const [departDate, setDepart] = useState(isoDay(7));
  const [returnDate, setReturn] = useState(isoDay(14));
  const [luggageNote, setLuggage] = useState('');

  const create = useMutation({
    mutationFn: () => api.createTrip({ countryCode, city, departDate, returnDate, luggageNote }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['myTrips'] });
      qc.invalidateQueries({ queryKey: ['board'] });
      navigate('/t', { replace: true });
    },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    create.mutate();
  }

  return (
    <Screen back title={t('trip.title')} hideNav>
      <form id="trip-form" className="stack" onSubmit={submit}>
        <Sheet>
          <div className="stack">
            <Field label={t('trip.country')}>
              <select value={countryCode} onChange={(e) => setCountry(e.target.value)}>
                {config?.countries.map((c) => <option key={c.code} value={c.code}>{lang === 'ko' ? c.nameKo : c.name}</option>)}
              </select>
            </Field>
            <Field label={t('trip.city')}>
              <input value={city} onChange={(e) => setCity(e.target.value)} maxLength={60} placeholder={t('trip.cityPh')} />
            </Field>
            <div className="two-col">
              <Field label={t('trip.depart')}>
                <input type="date" value={departDate} min={isoDay(1)} onChange={(e) => setDepart(e.target.value)} required />
              </Field>
              <Field label={t('trip.return')}>
                <input type="date" value={returnDate} min={departDate} onChange={(e) => setReturn(e.target.value)} required />
              </Field>
            </div>
            <Field label={t('trip.luggage')} hint={t('trip.luggageHint')}>
              <textarea value={luggageNote} onChange={(e) => setLuggage(e.target.value)} rows={2} maxLength={500} />
            </Field>
          </div>
        </Sheet>
        <p className="fine">{t('trip.note')}</p>
        {create.error && <Notice tone="bad">{errText(create.error)}</Notice>}
      </form>
      <ActionBar>
        <button form="trip-form" className="btn btn-primary btn-block" disabled={create.isPending}>
          {create.isPending ? t('order.saving') : t('trip.save')}
        </button>
      </ActionBar>
    </Screen>
  );
}
