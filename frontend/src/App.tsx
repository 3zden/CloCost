import { useEffect, useState, type ReactNode } from 'react';
import { useApi, type CostSummary } from './api';
import { Accounts, Activity, Budgets, Hero, Services } from './components/sections';
import { SpendChart } from './components/SpendChart';

const SECTIONS = [
  { id: 'spend', label: 'Spend' },
  { id: 'budgets', label: 'Budgets' },
  { id: 'anomalies', label: 'Anomalies' },
  { id: 'accounts', label: 'Accounts' },
];

function Block({ id, children }: { id: string; children: ReactNode }) {
  return <section id={id} className="block reveal">{children}</section>;
}

// Reveal-on-scroll and the active nav item, both via IntersectionObserver.
function useScrollSpy() {
  const [active, setActive] = useState('');
  useEffect(() => {
    const blocks = document.querySelectorAll<HTMLElement>('.block');
    const reveal = new IntersectionObserver(
      (es) => es.forEach((e) => e.isIntersecting && e.target.classList.add('in')), { threshold: 0.12 });
    const spy = new IntersectionObserver(
      (es) => es.forEach((e) => e.isIntersecting && setActive(e.target.id)), { rootMargin: '-45% 0px -50% 0px' });
    blocks.forEach((b) => { reveal.observe(b); spy.observe(b); });
    return () => { reveal.disconnect(); spy.disconnect(); };
  }, []);
  return active;
}

export function App() {
  const summary = useApi<CostSummary>('/costs/summary');
  const active = useScrollSpy();
  return (
    <>
      <header className="bar">
        <div className="bar-inner">
          <a className="brand" href="#top">CloCost</a>
          <nav className="seg" aria-label="Sections">
            {SECTIONS.map((s) => (
              <a key={s.id} href={`#${s.id}`} className={active === s.id ? 'on' : ''}
                aria-current={active === s.id ? 'true' : undefined}>{s.label}</a>
            ))}
          </nav>
        </div>
      </header>
      <main id="top">
        <Hero summary={summary} />
        <Block id="spend"><SpendChart /><Services summary={summary} /></Block>
        <Block id="budgets"><Budgets /></Block>
        <Block id="anomalies"><Activity /></Block>
        <Block id="accounts"><Accounts /></Block>
      </main>
    </>
  );
}
