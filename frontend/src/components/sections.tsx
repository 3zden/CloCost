import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { api, money, shortDate, useApi, type Account, type Alert, type Anomaly, type BudgetStatus, type CostSummary } from '../api';
import { spring } from '../motion';
import { Sheet } from './Sheet';
import { Bar, Rows, Tag } from './ui';

type Summary = ReturnType<typeof useApi<CostSummary>>;

export function Hero({ summary }: { summary: Summary }) {
  const total = useRef<HTMLHeadingElement>(null);
  const { data, error } = summary;
  useEffect(() => {
    if (!data) return;
    // Count up without re-rendering React on every frame.
    return spring({
      from: 0, to: Number(data.totalCost) || 0, response: 1.2,
      onUpdate: (x) => (total.current!.textContent = money(x, data.currency, 0)),
    });
  }, [data]);
  return (
    <section className="hero">
      <p className="eyebrow">Total cloud spend</p>
      <h1 className="display" ref={total}>—</h1>
      <p className="sub">
        {error ? '' : !data ? 'Loading…' : data.byProvider.length ? `Across ${data.byProvider.join(' · ')}` : 'No providers yet'}
      </p>
      {error && <p className="offline">Can’t reach the API ({error.message}).</p>}
    </section>
  );
}

export function Services({ summary }: { summary: Summary }) {
  const { data } = summary;
  const entries = Object.entries(data?.byService ?? {}).map(([k, v]) => [k, Number(v)] as const)
    .sort((a, b) => b[1] - a[1]).slice(0, 8);
  const max = entries[0]?.[1] || 1;
  return (
    <>
      <h3>By service</h3>
      <ul className="services">
        {data && !entries.length && <li className="m">No service data</li>}
        {entries.map(([name, v]) => (
          <li key={name}>
            <span className="name" title={name}>{name}</span>
            <Bar value={v / max} />
            <span className="val">{money(v, data?.currency, 0)}</span>
          </li>
        ))}
      </ul>
    </>
  );
}

function useForm(submit: (data: Record<string, string>) => Promise<unknown>, done: () => void) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const form = e.currentTarget;
    setBusy(true);
    setError(undefined);
    try {
      await submit(Object.fromEntries(new FormData(form)) as Record<string, string>);
      form.reset();
      done();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, onSubmit };
}

function Submit({ busy, error, children }: { busy: boolean; error?: string; children: ReactNode }) {
  return (
    <>
      {error && <p className="form-error" role="alert">Couldn’t save — {error}</p>}
      <button className="primary" disabled={busy}>{busy ? 'Saving…' : children}</button>
    </>
  );
}

export function Budgets() {
  const state = useApi<BudgetStatus[]>('/budgets');
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  const form = useForm(
    (d) => api('/budgets', { json: { name: d.name.trim(), limitAmount: Number(d.limitAmount), period: d.period, currency: 'USD' } }),
    () => { close(); state.reload(); },
  );
  return (
    <>
      <div className="head">
        <h2>Budgets</h2>
        <button className="pill" onClick={() => setOpen(true)}>New budget</button>
      </div>
      <Rows state={state} empty="No budgets yet">
        {({ budget: b, currentSpend, utilization }) => {
          const u = Number(utilization) || 0;
          return (
            <li key={b.id}>
              <div className="budget">
                <div className="split">
                  <span><span className="t">{b.name}</span> <span className="m">{b.period.toLowerCase()}</span></span>
                  <span className="r">
                    <span className="t">{money(currentSpend, b.currency, 0)}</span>{' '}
                    <span className="m">of {money(b.limitAmount, b.currency, 0)}</span>
                  </span>
                </div>
                <Bar value={u} />
                {u >= 0.85 && <p className="m note"><Tag tone="solid">{Math.round(u * 100)}%</Tag> Approaching limit</p>}
              </div>
            </li>
          );
        }}
      </Rows>
      <Sheet open={open} onClose={close} title="New budget" onSubmit={form.onSubmit}>
        <label>Name<input name="name" required maxLength={255} placeholder="Production" autoComplete="off" /></label>
        <label>Limit (USD)<input name="limitAmount" type="number" min="1" step="any" required placeholder="5000" inputMode="decimal" /></label>
        <label>Period<select name="period"><option value="MONTHLY">Monthly</option><option value="WEEKLY">Weekly</option></select></label>
        <Submit {...form}>Create</Submit>
      </Sheet>
    </>
  );
}

const sevTone = { CRITICAL: 'solid', WARNING: '', INFO: 'dim' } as const;

export function Activity() {
  const anomalies = useApi<Anomaly[]>('/anomalies');
  const alerts = useApi<Alert[]>('/alerts');
  return (
    <>
      <div className="head"><h2>Anomalies</h2></div>
      <Rows state={anomalies} empty="Nothing unusual. All quiet.">
        {(a) => (
          <li key={a.id}>
            <div>
              <div className="t">{a.service}</div>
              <div className="m">{a.provider} · {shortDate(a.detectedAt)} · expected {money(a.expectedCost)}</div>
            </div>
            <div className="r">
              <div className="big">+{Number(a.deviationPercent).toFixed(0)}%</div>
              <div className="m">{money(a.actualCost)}</div>
            </div>
          </li>
        )}
      </Rows>
      <h3>Alerts</h3>
      <Rows state={alerts} empty="No alerts">
        {(a) => (
          <li key={a.id}>
            <div>
              <div className="t">{a.message}</div>
              <div className="m">{new Date(a.triggeredAt).toLocaleString()}</div>
            </div>
            <Tag tone={sevTone[a.severity]}>{a.severity}</Tag>
          </li>
        )}
      </Rows>
    </>
  );
}

export function Accounts() {
  const state = useApi<Account[]>('/accounts');
  const [open, setOpen] = useState(false);
  const close = () => setOpen(false);
  const form = useForm(
    (d) => api('/accounts', { json: { provider: d.provider, displayName: d.displayName.trim(), secretRef: d.secretRef.trim() } }),
    () => { close(); state.reload(); },
  );
  return (
    <>
      <div className="head">
        <h2>Accounts</h2>
        <button className="pill" onClick={() => setOpen(true)}>Connect</button>
      </div>
      <Rows state={state} empty="No accounts connected">
        {(a) => (
          <li key={a.id}>
            <div>
              <div className="t">{a.displayName}</div>
              <div className="m">{a.provider}{a.connectedAt && ` · since ${shortDate(a.connectedAt)}`}</div>
            </div>
            <Tag tone={a.status === 'CONNECTED' ? 'solid' : 'dim'}>{a.status}</Tag>
          </li>
        )}
      </Rows>
      <Sheet open={open} onClose={close} title="Connect account" onSubmit={form.onSubmit}>
        <label>Provider<select name="provider"><option>AWS</option><option>GCP</option><option value="AZURE">Azure</option></select></label>
        <label>Display name<input name="displayName" required maxLength={100} placeholder="aws-prod" autoComplete="off" /></label>
        {/* A reference to a secret in your vault — never the credential itself. */}
        <label>Secret reference<input name="secretRef" required placeholder="vault://clocost/aws-prod" autoComplete="off" /></label>
        <Submit {...form}>Connect</Submit>
      </Sheet>
    </>
  );
}
