import { Component, type ReactNode } from 'react';
import { useMounted } from '../motion';

export function Bar({ value }: { value: number }) {
  const on = useMounted();
  const w = Math.min(Math.max(value, 0), 1);
  return (
    <span className="track" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(w * 100)}>
      <i style={{ transform: `scaleX(${on ? w : 0})` }} />
    </span>
  );
}

export function Tag({ tone = '', children }: { tone?: '' | 'solid' | 'dim'; children: ReactNode }) {
  return <span className={`tag ${tone}`}>{children}</span>;
}

interface RowsProps<T> {
  state: { data?: T[]; error?: Error; loading: boolean };
  empty: string;
  children: (item: T) => ReactNode;
}
export function Rows<T>({ state, empty, children }: RowsProps<T>) {
  const { data, error, loading } = state;
  let body: ReactNode;
  if (error) body = <li className="empty">Couldn’t load — {error.message}</li>;
  else if (!data && loading) body = [0, 1].map((i) => <li key={i} className="skeleton" />);
  else if (!data?.length) body = <li className="empty">{empty}</li>;
  else body = data.map(children);
  return <ul className="rows" aria-busy={loading}>{body}</ul>;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, { error?: Error }> {
  state: { error?: Error } = {};
  static getDerivedStateFromError(error: Error) { return { error }; }
  componentDidCatch(error: Error) { console.error(error); }
  render() {
    if (!this.state.error) return this.props.children;
    return (
      <section className="hero">
        <p className="eyebrow">Something went wrong</p>
        <h1 className="display" style={{ fontSize: '3rem' }}>Please reload.</h1>
        <button className="pill" onClick={() => location.reload()}>Reload</button>
      </section>
    );
  }
}
