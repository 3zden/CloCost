import { useEffect, useLayoutEffect, useRef, type FormEvent, type PointerEvent, type ReactNode } from 'react';
import { project, reduceMotion, rubberband, spring } from '../motion';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
}

// Bottom sheet: drag to dismiss with 1:1 tracking, velocity handoff and momentum projection.
// Position lives in a ref and is written straight to the DOM — no React render per frame.
export function Sheet({ open, onClose, title, onSubmit, children }: Props) {
  const sheet = useRef<HTMLFormElement>(null);
  const scrim = useRef<HTMLDivElement>(null);
  const y = useRef(Infinity);
  const h = useRef(0);
  const target = useRef(Infinity);
  const stop = useRef(() => {});
  const drag = useRef({ grab: 0, hist: [] as [number, number][] });

  const render = (v: number) => {
    y.current = v;
    sheet.current!.style.transform = `translateY(${v}px)`;
    scrim.current!.style.opacity = String(Math.max(0, 1 - v / h.current));
  };

  const settle = (to: number, velocity = 0) => {
    stop.current();
    target.current = to;
    const bouncy = Math.abs(velocity) > 300; // bounce only when a flick carried momentum
    stop.current = spring({
      from: y.current, to, velocity,
      damping: bouncy ? 0.8 : 1, response: bouncy ? 0.3 : 0.4,
      onUpdate: render,
      onDone: () => { if (to !== 0) sheet.current!.style.visibility = 'hidden'; },
    });
  };

  useLayoutEffect(() => {
    const el = sheet.current!;
    scrim.current!.style.pointerEvents = open ? 'auto' : 'none';
    if (open) {
      el.style.visibility = 'visible';
      h.current = el.offsetHeight;
      if (y.current === Infinity || y.current >= h.current) render(h.current); // else: resume mid-flight
      settle(0);
      const t = setTimeout(() => el.querySelector('input')?.focus({ preventScroll: true }), reduceMotion() ? 0 : 350);
      return () => clearTimeout(t);
    }
    if (y.current !== Infinity && target.current !== h.current) settle(h.current); // a drag may already be closing it
  }, [open]);

  useEffect(() => () => stop.current(), []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const down = (e: PointerEvent) => {
    // ponytail: only grabbable while open; re-grabbing a closing sheet would need the parent to reopen it
    if (!open || (e.target as Element).closest('input, select, button, textarea')) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    stop.current(); // grab it mid-flight
    drag.current = { grab: e.clientY - y.current, hist: [[e.clientY, e.timeStamp]] };
  };
  const move = (e: PointerEvent) => {
    if (!e.currentTarget.hasPointerCapture(e.pointerId)) return;
    const raw = e.clientY - drag.current.grab;
    render(raw < 0 ? rubberband(raw, h.current) : raw);
    const hist = drag.current.hist;
    hist.push([e.clientY, e.timeStamp]);
    if (hist.length > 5) hist.shift();
  };
  const up = (e: PointerEvent) => {
    if (!e.currentTarget.hasPointerCapture(e.pointerId)) return;
    const [y0, t0] = drag.current.hist[0];
    const dt = e.timeStamp - t0;
    const v = dt > 0 ? ((e.clientY - y0) / dt) * 1000 : 0;
    const dismiss = y.current + project(v) > h.current / 2;
    if (dismiss) { settle(h.current, v); onClose(); } else settle(0, v);
  };

  return (
    <>
      <div className="scrim" ref={scrim} onClick={onClose} />
      <form
        ref={sheet} className="sheet" role="dialog" aria-modal="true" aria-label={title} aria-hidden={!open}
        onSubmit={onSubmit} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
      >
        <div className="grip" />
        <h2>{title}</h2>
        {children}
      </form>
    </>
  );
}
