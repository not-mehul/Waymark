<script lang="ts">
  // The trip, drawn on the world, on a canvas. A flat chart for reading a
  // region and a globe for the long hauls; both draw the same coastline, the
  // same great-circle legs and the same marks, and both pan and zoom: drag,
  // pinch or the wheel. On the globe, dragging turns the planet. Labels are
  // placed so a cluster of hotels in one city does not print on top of
  // itself. Ported from WorldMap.kt.
  import { coastlineRings } from '../../catalog/coastline';
  import { arc, centroid, crossesDateLine, project, type MapPoint } from '../../domain/geo';
  import { placeLabels } from '../../domain/labels';
  import type { LatLon } from '../../domain/model';
  import { settings } from '../../state/app';
  import { CHART_RANK, type ChartPlace, type ChartRoute } from '../../state/map';
  import {
    FLAT_MAX,
    FLAT_MIN,
    FlatView,
    fitViewport,
    GLOBE_MAX,
    GLOBE_MIN,
    GlobeView,
    graticule,
    runsOf,
    type MapView,
    type Point
  } from './views';

  interface Props {
    places: ChartPlace[];
    routes: ChartRoute[];
    projection: 'FLAT' | 'GLOBE';
    /** What the map shows, for a screen reader: the canvas itself says nothing. */
    description: string;
    onselect: (segmentId: string) => void;
  }
  let { places, routes, projection, description, onselect }: Props = $props();

  const ROUTE_SAMPLES = 72;
  let canvas: HTMLCanvasElement;
  let width = $state(0);
  let height = $state(0);

  // Worked out once, not on every frame of a pinch: each leg's great circle.
  const legs = $derived(
    routes.map((route) => ({
      route,
      samples: route.flying ? arc(route.from, route.to, ROUTE_SAMPLES) : [route.from, route.to]
    }))
  );
  const focus = $derived(
    centroid([...places.map((p) => p.position), ...routes.flatMap((r) => [r.from, r.to])]) ?? {
      latitude: 20,
      longitude: 0
    }
  );

  // Flat: a centre in projected 0..1 space, and a scale in screen widths per
  // world width. Globe: the point facing the viewer, and how much bigger than
  // the viewport the sphere is drawn.
  let flatCentre = $state<MapPoint>({ x: 0.5, y: 0.5 });
  let flatZoom = $state(0);
  let spinLat = $state(20);
  let spinLon = $state(0);
  let globeZoom = $state(1);

  // Fit the trip once, on the first real layout.
  $effect(() => {
    if (flatZoom !== 0 || width === 0) return;
    const fitted = fitViewport(
      [...places.map((p) => project(p.position)), ...routes.flatMap((r) => [project(r.from), project(r.to)])],
      width,
      height
    );
    flatCentre = fitted.centre;
    flatZoom = fitted.zoom;
    spinLat = focus.latitude;
    spinLon = focus.longitude;
  });

  const view = $derived<MapView | null>(
    width === 0
      ? null
      : projection === 'FLAT'
        ? flatZoom > 0
          ? new FlatView(flatCentre, flatZoom, width, height)
          : null
        : new GlobeView(spinLat, spinLon, width, height, globeZoom)
  );

  // — Drawing ——————————————————————————————————————————————————————————————

  interface Ink {
    water: string;
    land: string;
    coast: string;
    grid: string;
    flight: string;
    ground: string;
    station: string;
    stay: string;
    stop: string;
    label: string;
    font: string;
  }

  function inkOf(element: HTMLElement): Ink {
    const css = getComputedStyle(element);
    const v = (name: string) => css.getPropertyValue(name).trim();
    return {
      water: v('--map-water'),
      land: v('--map-land'),
      coast: v('--map-coast'),
      grid: v('--map-grid'),
      flight: v('--accent-amber'),
      ground: v('--accent-sage'),
      station: v('--accent-amber'),
      stay: v('--accent-sage'),
      stop: v('--text-dim'),
      label: v('--text-muted'),
      font: v('--font-sans')
    };
  }

  function trace(ctx: CanvasRenderingContext2D, run: readonly Point[]) {
    ctx.moveTo(run[0]!.x, run[0]!.y);
    for (let i = 1; i < run.length; i++) ctx.lineTo(run[i]!.x, run[i]!.y);
  }

  /** Walk the shorter way round the limb from one cut edge back to the other. */
  function closeAlongLimb(ctx: CanvasRenderingContext2D, globe: GlobeView, from: Point, to: Point) {
    const start = globe.angleOf(from);
    let sweep = globe.angleOf(to) - start;
    while (sweep > Math.PI) sweep -= 2 * Math.PI;
    while (sweep < -Math.PI) sweep += 2 * Math.PI;
    const steps = Math.max(2, Math.trunc(Math.abs(sweep) / 0.08));
    for (let step = 1; step <= steps; step++) {
      const p = globe.onLimb(start + (sweep * step) / steps);
      ctx.lineTo(p.x, p.y);
    }
  }

  function strokeSampled(ctx: CanvasRenderingContext2D, line: readonly LatLon[], v: MapView) {
    ctx.beginPath();
    let started = false;
    line.forEach((point, index) => {
      if (!v.visible(point)) {
        started = false;
        return;
      }
      const previous = index === 0 ? null : line[index - 1]!;
      if (previous && v.breaks(previous, point)) started = false;
      const s = v.raw(point);
      if (started) ctx.lineTo(s.x, s.y);
      else ctx.moveTo(s.x, s.y);
      started = true;
    });
    ctx.stroke();
  }

  function drawRoute(
    ctx: CanvasRenderingContext2D,
    route: ChartRoute,
    samples: readonly LatLon[],
    v: MapView,
    ink: Ink
  ) {
    ctx.lineWidth = 1.8;
    ctx.setLineDash(route.flying ? [] : [7, 5]);
    // Drawn as runs, so the part behind the globe is a faint hint of where
    // it went rather than a line across the face of the world.
    let run: Point[] = [];
    let shown = true;
    const flush = () => {
      if (run.length > 1) {
        ctx.beginPath();
        trace(ctx, run);
        ctx.strokeStyle = shown ? (route.flying ? ink.flight : ink.ground) : ink.coast;
        ctx.globalAlpha = shown ? 1 : 0.35;
        ctx.stroke();
        ctx.globalAlpha = 1;
      }
      run = [];
    };
    samples.forEach((point, index) => {
      const visible = v.visible(point);
      const previous = index === 0 ? null : samples[index - 1]!;
      if (index === 0 || (previous && crossesDateLine(previous, point)) || visible !== shown) {
        // Keep the line continuous across a horizon change: the new run starts where the old one ended.
        const carry = previous && !crossesDateLine(previous, point) ? run.at(-1) : undefined;
        flush();
        if (carry) run.push(carry);
        shown = visible;
      }
      run.push(v.raw(point));
    });
    flush();
    ctx.setLineDash([]);
  }

  function drawMarks(ctx: CanvasRenderingContext2D, v: MapView, ink: Ink) {
    const radius = { STATION: 5, STAY: 4.5, STOP: 3.5 } as const;
    const tint = { STATION: ink.station, STAY: ink.stay, STOP: ink.stop } as const;
    const onScreen = places.flatMap((place) => {
      const point = v.screen(place.position);
      if (!point || point.x < -60 || point.x > width + 60 || point.y < -40 || point.y > height + 40)
        return [];
      return [{ place, point, r: radius[place.kind] }];
    });
    for (const { place, point, r } of onScreen) {
      // A ring, not a blob: the mark should not hide the place it marks.
      ctx.globalAlpha = 0.85;
      ctx.fillStyle = ink.water;
      ctx.beginPath();
      ctx.arc(point.x, point.y, r + 2, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = tint[place.kind];
      ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.arc(point.x, point.y, r, 0, Math.PI * 2);
      ctx.stroke();
      if (place.kind === 'STATION') {
        ctx.fillStyle = tint[place.kind];
        ctx.beginPath();
        ctx.arc(point.x, point.y, 1.6, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    // Marks first, then whichever labels fit: a trip with forty saved
    // restaurants should read as a trip, not as a wall of text.
    ctx.font = `10px ${ink.font}`;
    ctx.letterSpacing = '0.6px';
    ctx.textBaseline = 'top';
    const LABEL_HEIGHT = 13;
    const widths = new Map(onScreen.map(({ place }) => [place.id, ctx.measureText(place.label).width]));
    const placements = placeLabels(
      onScreen.map(({ place, point, r }) => ({
        id: place.id,
        anchorX: point.x,
        anchorY: point.y,
        width: widths.get(place.id)!,
        height: LABEL_HEIGHT,
        priority: CHART_RANK[place.kind],
        markRadius: r + 2
      })),
      { left: 0, top: 0, right: width, bottom: height },
      3
    );
    const byId = new Map(places.map((p) => [p.id, p]));
    for (const p of placements) {
      const w = widths.get(p.id)!;
      // A halo behind the text: a name over a coastline is a name nobody can read.
      ctx.globalAlpha = 0.72;
      ctx.fillStyle = ink.water;
      ctx.fillRect(p.left - 2, p.top - 1, w + 4, LABEL_HEIGHT + 2);
      ctx.globalAlpha = 1;
      ctx.fillStyle = ink.label;
      ctx.fillText(byId.get(p.id)!.label, p.left, p.top + 1);
    }
  }

  function draw() {
    if (!canvas || !view) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const ratio = window.devicePixelRatio || 1;
    if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
    }
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, width, height);
    const ink = inkOf(canvas);
    const v = view;

    ctx.save();
    if (v instanceof GlobeView) {
      // The lit sphere, the light over the viewer's left shoulder: the only shading in the app.
      const light = ctx.createRadialGradient(
        v.cx - v.discRadius * 0.3,
        v.cy - v.discRadius * 0.35,
        0,
        v.cx - v.discRadius * 0.3,
        v.cy - v.discRadius * 0.35,
        v.discRadius * 1.45
      );
      light.addColorStop(0, ink.water);
      light.addColorStop(0.72, ink.water);
      light.addColorStop(1, ink.land);
      ctx.fillStyle = light;
      ctx.beginPath();
      ctx.arc(v.cx, v.cy, v.discRadius, 0, Math.PI * 2);
      ctx.fill();
      ctx.clip();
    } else {
      ctx.fillStyle = ink.water;
      ctx.fillRect(0, 0, width, height);
    }

    // The land, filled, because a coastline drawn as a line reads as a route.
    // Zoomed in, the small islands cost the most and say the least.
    const minimum = v.worldFraction > 0.4 ? 0 : 12;
    ctx.fillStyle = ink.land;
    ctx.strokeStyle = ink.coast;
    ctx.lineWidth = 1;
    for (const ring of coastlineRings()) {
      if (ring.length < minimum) continue;
      for (const run of runsOf(ring, v)) {
        if (run.length < 3) continue;
        ctx.beginPath();
        trace(ctx, run);
        // A landmass cut by the horizon closes along the limb, so Asia does not grow a chord.
        const first = run[0]!;
        const last = run.at(-1)!;
        if (v instanceof GlobeView && (first.x !== last.x || first.y !== last.y))
          closeAlongLimb(ctx, v, last, first);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
      }
    }

    ctx.strokeStyle = ink.grid;
    ctx.lineWidth = 1;
    for (const line of graticule(v.graticuleStep)) strokeSampled(ctx, line, v);

    for (const { route, samples } of legs) drawRoute(ctx, route, samples, v, ink);
    drawMarks(ctx, v, ink);
    ctx.restore();
  }

  $effect(() => {
    void settings.mode;
    void view;
    void places;
    void legs;
    draw();
  });

  // — Gestures —————————————————————————————————————————————————————————————

  // Gesture bookkeeping, deliberately not reactive: nothing renders from it.
  // eslint-disable-next-line svelte/prefer-svelte-reactivity
  const pointers = new Map<number, Point>();
  let moved = 0;
  let down: Point | null = null;

  function pan(dx: number, dy: number) {
    if (projection === 'FLAT') {
      const scale = width * flatZoom;
      if (scale <= 0) return;
      flatCentre = {
        x: Math.min(1, Math.max(0, flatCentre.x - dx / scale)),
        y: Math.min(1, Math.max(0, flatCentre.y - dy / scale))
      };
    } else {
      // A drag the globe's width turns it half a turn at rest, less zoomed in,
      // so the gesture always moves the same amount of land.
      const sensitivity = 180 / globeZoom;
      spinLon -= (dx / width) * sensitivity;
      spinLat = Math.min(85, Math.max(-85, spinLat + (dy / height) * sensitivity));
    }
  }

  function zoomBy(factor: number) {
    if (projection === 'FLAT') flatZoom = Math.min(FLAT_MAX, Math.max(FLAT_MIN, flatZoom * factor));
    else globeZoom = Math.min(GLOBE_MAX, Math.max(GLOBE_MIN, globeZoom * factor));
  }

  const spread = () => {
    const [a, b] = [...pointers.values()];
    return a && b ? Math.hypot(a.x - b.x, a.y - b.y) : 0;
  };

  function onpointerdown(ev: PointerEvent) {
    canvas.setPointerCapture(ev.pointerId);
    pointers.set(ev.pointerId, { x: ev.offsetX, y: ev.offsetY });
    if (pointers.size === 1) {
      moved = 0;
      down = { x: ev.offsetX, y: ev.offsetY };
    }
  }

  function onpointermove(ev: PointerEvent) {
    const last = pointers.get(ev.pointerId);
    if (!last) return;
    const before = spread();
    const now = { x: ev.offsetX, y: ev.offsetY };
    pointers.set(ev.pointerId, now);
    if (pointers.size >= 2) {
      const after = spread();
      if (before > 0 && after > 0) zoomBy(after / before);
      pan((now.x - last.x) / 2, (now.y - last.y) / 2);
      moved += 100;
    } else {
      pan(now.x - last.x, now.y - last.y);
      moved += Math.hypot(now.x - last.x, now.y - last.y);
    }
  }

  function onpointerup(ev: PointerEvent) {
    const was = pointers.size;
    pointers.delete(ev.pointerId);
    if (was === 1 && moved < 8 && down) tap(down);
  }

  /** The nearest mark within reach opens its booking; an idea with none stays a mark. */
  function tap(at: Point) {
    if (!view) return;
    let best: { id: string | null; distance: number } = { id: null, distance: 36 };
    for (const place of places) {
      const p = view.screen(place.position);
      if (!p) continue;
      const d = Math.hypot(p.x - at.x, p.y - at.y);
      if (d < best.distance) best = { id: place.segmentId, distance: d };
    }
    if (best.id) onselect(best.id);
  }

  function onwheel(ev: WheelEvent) {
    ev.preventDefault();
    zoomBy(Math.exp(-ev.deltaY / 400));
  }
</script>

<div
  class="world-map"
  role="img"
  aria-label={description}
  bind:clientWidth={width}
  bind:clientHeight={height}
>
  <canvas
    bind:this={canvas}
    style:width="{width}px"
    style:height="{height}px"
    {onpointerdown}
    {onpointermove}
    {onpointerup}
    onpointercancel={onpointerup}
    {onwheel}
  ></canvas>
</div>
