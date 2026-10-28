import { useEffect, useRef } from 'react';

type Particle = { x: number; y: number; vx: number; vy: number; size: number; life: number };

/**
 * Lime sparkles that follow the mouse, from the Figma design ("Mouse Cursor Sparkle Trail").
 * Mouse and pen only (not touch), and switched off when the user prefers reduced motion.
 */
export default function SparkleTrail() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    const particles: Particle[] = [];
    let frame = 0;
    let running = false;
    let previous = { x: -100, y: -100 };

    const resize = () => {
      const ratio = Math.min(window.devicePixelRatio, 2);
      canvas.width = window.innerWidth * ratio;
      canvas.height = window.innerHeight * ratio;
      canvas.style.width = `${window.innerWidth}px`;
      canvas.style.height = `${window.innerHeight}px`;
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    };

    const draw = () => {
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
      for (let i = particles.length - 1; i >= 0; i -= 1) {
        const p = particles[i];
        p.x += p.vx;
        p.y += p.vy;
        p.life -= 0.023;
        if (p.life <= 0) { particles.splice(i, 1); continue; }
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.fillStyle = `rgba(199,255,45,${p.life})`;
        ctx.shadowColor = '#c7ff2d';
        ctx.shadowBlur = 6;
        ctx.beginPath();
        for (let point = 0; point < 8; point += 1) {
          const radius = point % 2 === 0 ? p.size : p.size * 0.18;
          const angle = -Math.PI / 2 + (point * Math.PI) / 4;
          const x = Math.cos(angle) * radius;
          const y = Math.sin(angle) * radius;
          if (point) ctx.lineTo(x, y); else ctx.moveTo(x, y);
        }
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      }
      // Stop the loop when nothing is left to draw, so an idle page uses no CPU.
      if (particles.length) frame = requestAnimationFrame(draw);
      else running = false;
    };

    const move = (e: PointerEvent) => {
      if (e.pointerType === 'touch') return;
      const distance = Math.hypot(e.clientX - previous.x, e.clientY - previous.y);
      if (distance > 5 && distance < 180) {
        particles.push({
          x: e.clientX + (Math.random() - 0.5) * 8,
          y: e.clientY + (Math.random() - 0.5) * 8,
          vx: (Math.random() - 0.5) * 0.45,
          vy: -0.15 - Math.random() * 0.25,
          size: 2 + Math.random() * 3,
          life: 1,
        });
        if (!running) { running = true; frame = requestAnimationFrame(draw); }
      }
      previous = { x: e.clientX, y: e.clientY };
    };

    resize();
    window.addEventListener('resize', resize);
    window.addEventListener('pointermove', move, { passive: true });
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
      window.removeEventListener('pointermove', move);
    };
  }, []);

  return <canvas ref={canvasRef} className="sparkles" aria-hidden="true" />;
}
