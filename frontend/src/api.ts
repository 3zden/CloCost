import { useCallback, useEffect, useState } from 'react';

// Mirrors the Spring DTOs/entities in backend/src/main/java/org/aezden/backend.
export type Provider = 'AWS' | 'GCP' | 'AZURE';

export interface CostSummary {
  totalCost: number;
  byProvider: string[];
  currency: string;
  byService: Record<string, number>;
}
export interface CostRow { date: string; provider: Provider; total: number; currency: string }
export interface Budget { id: string; name: string; limitAmount: number; currency: string; period: 'WEEKLY' | 'MONTHLY' }
export interface BudgetStatus { budget: Budget; currentSpend: number; utilization: number }
export interface Anomaly {
  id: string; provider: Provider; service: string; detectedAt: string;
  expectedCost: number; actualCost: number; deviationPercent: number;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
}
export interface Alert { id: string; message: string; severity: 'INFO' | 'WARNING' | 'CRITICAL'; triggeredAt: string }
export interface Account {
  id: string; provider: Provider; displayName: string;
  status: 'CONNECTED' | 'DISCONNECTED' | 'ERROR'; connectedAt?: string;
}

// Empty base = same origin (nginx / Vite proxy). Set VITE_API_URL at build time to point elsewhere.
const BASE = (import.meta.env.VITE_API_URL ?? '') + '/api/v1';

export async function api<T>(path: string, init?: RequestInit & { json?: unknown }): Promise<T> {
  const { json, ...rest } = init ?? {};
  const res = await fetch(BASE + path, json === undefined ? rest : {
    ...rest, method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(json),
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json() as Promise<T>;
}

export function useApi<T>(path: string) {
  const [state, setState] = useState<{ data?: T; error?: Error; loading: boolean }>({ loading: true });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const ac = new AbortController();
    setState((s) => ({ ...s, loading: true }));
    api<T>(path, { signal: ac.signal }).then(
      (data) => setState({ data, loading: false }),
      (error: Error) => !ac.signal.aborted && setState({ error, loading: false }),
    );
    return () => ac.abort();
  }, [path, tick]);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { ...state, reload };
}

export const money = (n: number | string, currency = 'USD', digits = 2) =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: digits }).format(Number(n) || 0);
export const shortDate = (d: string | Date) =>
  new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
// Local calendar date, not UTC — toISOString() would shift days for non-UTC users.
export const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
