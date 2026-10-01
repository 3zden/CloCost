import { useEffect, useState } from 'react';

export const reduceMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

interface SpringOpts {
  from: number; to: number; velocity?: number;
  damping?: number;  // Apple's damping ratio: 1 = no overshoot
  response?: number; // seconds; not a duration
  onUpdate: (x: number) => void; onDone?: () => void;
}

// Interruptible spring: starts from the live value passed in and carries velocity. Returns cancel.
export function spring({ from, to, velocity = 0, damping = 1, response = 0.4, onUpdate, onDone }: SpringOpts) {
  if (reduceMotion()) { onUpdate(to); onDone?.(); return () => {}; }
  const k = (2 * Math.PI / response) ** 2;
  const c = (4 * Math.PI * damping) / response;
  let x = from, v = velocity, last = performance.now(), raf = 0;
  const step = (now: number) => {
    let dt = Math.min((now - last) / 1000, 0.064);
    last = now;
    for (; dt > 0; dt -= 1 / 240) { // fixed substeps keep stiff springs stable
      const h = Math.min(dt, 1 / 240);
      v += (-k * (x - to) - c * v) * h;
      x += v * h;
    }
    if (Math.abs(x - to) < 0.1 && Math.abs(v) < 1) { onUpdate(to); onDone?.(); return; }
    onUpdate(x);
    raf = requestAnimationFrame(step);
  };
  raf = requestAnimationFrame(step);
  return () => cancelAnimationFrame(raf);
}

// Apple's momentum projection (Designing Fluid Interfaces)
export const project = (v: number, d = 0.998) => (v / 1000) * d / (1 - d);
export const rubberband = (o: number, dim: number, c = 0.55) => (o * dim * c) / (dim + c * Math.abs(o));

// Flips to true one frame after mount so CSS transitions run from their initial state.
export function useMounted() {
  const [on, setOn] = useState(false);
  useEffect(() => {
    const id = requestAnimationFrame(() => requestAnimationFrame(() => setOn(true)));
    return () => cancelAnimationFrame(id);
  }, []);
  return on;
}
