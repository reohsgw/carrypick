import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { api, type Notification } from '../api';
import { firstName } from '../components/cards';
import { Screen } from '../components/Layout';
import { Empty, ErrorBox, Loading, Sheet } from '../components/ui';
import { dateTime, krw } from '../format';
import { useI18n, type Key, type T } from '../i18n';
import { useSession } from '../session';

/** The server sends a kind plus names and numbers, so the sentence is written in the viewer's language. */
function sentence(n: Notification, t: T): string {
  if (!n.kind) return n.message;
  return t(`n.${n.kind}` as Key, {
    actor: n.actorName ? firstName(n.actorName) : '',
    product: n.productName ?? '',
    amount: n.kind === 'BOOKED' ? n.amount : n.amount == null ? '' : krw(n.amount),
  });
}

/** C3: Notifications about order status changes. Opening the screen marks them read. */
export default function Notifications() {
  const { refreshMe } = useSession();
  const { t, lang } = useI18n();
  const list = useQuery({ queryKey: ['notifications'], queryFn: api.notifications });

  useEffect(() => {
    if (!list.data?.some((n) => !n.read)) return;
    api.readAllNotifications().then(refreshMe).catch(() => {});
  }, [list.data, refreshMe]);

  return (
    <Screen back title={t('n.title')}>
      {list.isLoading && <Loading />}
      {list.error && <ErrorBox error={list.error} onRetry={() => list.refetch()} />}
      {list.data?.length === 0 && <Empty icon={Bell} title={t('n.empty')} />}
      {!!list.data?.length && (
        <Sheet flush>
          <ul className="rows notif-list">
            {list.data.map((n) => {
              const body = (
                <>
                  <span className="notif-text">{sentence(n, t)}</span>
                  <span className="notif-at">{dateTime(n.createdAt, lang)}</span>
                </>
              );
              return (
                <li key={n.id} className={n.read ? '' : 'is-unread'}>
                  {n.orderId ? <Link to={`/orders/${n.orderId}`} className="notif">{body}</Link> : <div className="notif">{body}</div>}
                </li>
              );
            })}
          </ul>
        </Sheet>
      )}
    </Screen>
  );
}
