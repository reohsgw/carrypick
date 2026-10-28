import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, setUnauthorizedHandler, tokenStore, type AppConfig, type Mode, type User } from './api';

interface Session {
  user: User | null;
  loading: boolean;
  config: AppConfig | undefined;
  unread: number;
  login: (token: string, user: User) => void;
  logout: () => void;
  switchMode: (mode: Mode) => Promise<void>;
  refreshMe: () => void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [hasToken, setHasToken] = useState(() => !!tokenStore.get());

  const config = useQuery({ queryKey: ['config'], queryFn: api.config, staleTime: Infinity });
  const me = useQuery({
    queryKey: ['me'],
    queryFn: api.me,
    enabled: hasToken,
    refetchInterval: 30_000, // keeps the notification badge fresh
  });

  const logout = useCallback(() => {
    tokenStore.set(null);
    setHasToken(false);
    qc.clear();
  }, [qc]);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  const login = useCallback(
    (token: string, user: User) => {
      tokenStore.set(token);
      qc.setQueryData(['me'], { user, unreadNotifications: 0 });
      setHasToken(true);
    },
    [qc],
  );

  const switchMode = useCallback(
    async (mode: Mode) => {
      const user = await api.updateMe({ mode });
      qc.setQueryData(['me'], (old: { user: User; unreadNotifications: number } | undefined) => ({
        user,
        unreadNotifications: old?.unreadNotifications ?? 0,
      }));
    },
    [qc],
  );

  const value = useMemo<Session>(
    () => ({
      user: hasToken ? me.data?.user ?? null : null,
      loading: hasToken && me.isLoading,
      config: config.data,
      unread: me.data?.unreadNotifications ?? 0,
      login,
      logout,
      switchMode,
      refreshMe: () => qc.invalidateQueries({ queryKey: ['me'] }),
    }),
    [hasToken, me.data, me.isLoading, config.data, login, logout, switchMode, qc],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) throw new Error('useSession must be used inside SessionProvider');
  return s;
}
