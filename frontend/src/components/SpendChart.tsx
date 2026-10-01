import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { isoDate, money, shortDate, useApi, type CostRow, type Provider } from '../api';

const DAYS = 30;
const PROVIDERS: { id: Provider | ''; label: string }[] = [
  { id: '', label: 'All' }, { id: 'AWS', label: 'AWS' }, { id: 'GCP', label: 'GCP' }, { id: 'AZURE', label: 'Azure' },
];

function useWidth<T extends Element>() {
  const ref = useRef<T>(null);
  const [w, setW] = useState(0);
  useEffect(() => {
    const ro = new ResizeObserver(([e]) => setW(e.contentRect.width));
    ro.observe(ref.current!);
    return () => ro.disconnect();
  }, []);
  return [ref, w] as const;
}

export function SpendChart() {
  const [provider, setProvider] = useState<Provider | ''>('');
  const [hover, setHover] = useState<number | null>(null);
  const [ref, w] = useWidth<HTMLDivElement>();
  const H = 256;

  const days = useMemo(() => {
    const start = new Date();
    start.setDate(start.getDate() - (DAYS - 1));
    return Array.from({ length: DAYS }, (_, i) => {
      const d = new Date(start);
      d.setDate(d.getDate() + i);
      return isoDate(d);
    });
  }, []);

  const q = new URLSearchParams({ startDate: days[0], endDate: days[DAYS - 1] });
  if (provider) q.set('provider', provider);
  const { data, error, loading } = useApi<CostRow[]>(`/costs?${q}`);

  // The API returns one row per record; sum per day and fill gaps with 0.
  const series = useMemo(() => {
    const byDay = new Map(days.map((d) => [d, 0]));
    for (const r of data ?? []) if (byDay.has(r.date)) byDay.set(r.date, byDay.get(r.date)! + Number(r.total || 0));
    return [...byDay].map(([date, total]) => ({ date, total }));
  }, [data, days]);

  const max = Math.max(...series.map((p) => p.total), 1) * 1.1;
  const pts = series.map((p, i) => [(i / (DAYS - 1)) * w, H - (p.total / max) * H] as const);
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join('');
  const sum = series.reduce((a, p) => a + p.total, 0);

  const scrub = (e: PointerEvent<SVGSVGElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    const t = Math.min(Math.max((e.clientX - r.left) / r.width, 0), 1);
    setHover(Math.round(t * (DAYS - 1)));
  };

  return (
    <>
      <div className="head">
        <h2>Last 30 days</h2>
        <div className="filters" role="group" aria-label="Provider">
          {PROVIDERS.map((p) => (
            <button key={p.id} className={`chip ${provider === p.id ? 'on' : ''}`} aria-pressed={provider === p.id}
              onPointerDown={() => setProvider(p.id)} onClick={() => setProvider(p.id)}>
              {p.label}
            </button>
          ))}
        </div>
      </div>
      <div className="chart" ref={ref}>
        <div className="readout" aria-live="polite">
          <span className="r-val">{error ? '—' : money(hover == null ? sum : series[hover].total)}</span>
          <span className="r-date">{error ? `Couldn’t load — ${error.message}` : hover == null ? '30-day total' : shortDate(series[hover].date + 'T00:00')}</span>
        </div>
        <svg height={H} role="img" aria-label="Daily spend over the last 30 days" aria-busy={loading}
          onPointerDown={scrub} onPointerMove={scrub} onPointerLeave={() => setHover(null)}>
          {w > 0 && (
            <>
              <path className="area" d={`${line}L${w},${H}L0,${H}Z`} />
              {/* key re-mounts the path so the draw-in animation replays on new data */}
              <path key={`${provider}-${data ? 1 : 0}`} className="line draw" pathLength={1} d={line} />
              {hover != null && (
                <>
                  <line className="cursor" x1={pts[hover][0]} x2={pts[hover][0]} y1={0} y2={H} />
                  <circle className="dot" r={4} cx={pts[hover][0]} cy={pts[hover][1]} />
                </>
              )}
            </>
          )}
        </svg>
      </div>
    </>
  );
}
