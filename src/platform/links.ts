// Links between the apps (style.md §14.1). This file is identical in Waymark,
// BookShelf, Ledger, Colophon and Rosetta: change it in all five or none.
//
// Every app registers its own name as a URL scheme, on Android (a VIEW
// intent filter) and iOS (CFBundleURLTypes), and reads links of one shape:
//
//   <app>://<action>?<param>=<value>&…&source=<sending app>
//
// The actions each app answers are listed in LINK_ACTIONS. `open` is answered
// by every app and only brings it to the front. A link never changes data by
// itself: it opens a screen the person confirms, because any web page or app
// can fire a custom scheme.

export const APPS = ['waymark', 'bookshelf', 'ledger', 'colophon', 'rosetta'] as const;
export type AppName = (typeof APPS)[number];

/** The actions each app answers, with the parameters it reads. Add to it; never rename. */
export const LINK_ACTIONS = {
  waymark: {
    open: ['trip'],
    import: ['d']
  },
  bookshelf: {
    open: [],
    log: ['title', 'author', 'isbn', 'finished', 'progress']
  },
  ledger: {
    open: [],
    add: ['city', 'region', 'country', 'date']
  },
  colophon: {
    open: []
  },
  rosetta: {
    open: ['id', 'title']
  }
} as const satisfies Record<AppName, Record<string, readonly string[]>>;

export interface AppLink {
  app: AppName;
  /** Lower-case. Unknown actions are kept so the receiver can fall back to `open`. */
  action: string;
  params: Record<string, string>;
  /** The app that sent the link, when it said. */
  source: AppName | null;
}

const MAX_LINK_LENGTH = 8192;

function appName(value: string | null | undefined): AppName | null {
  const v = (value ?? '').toLowerCase();
  return (APPS as readonly string[]).includes(v) ? (v as AppName) : null;
}

/** `bookshelf://log?title=…&source=rosetta`. Empty values are left out. */
export function buildLink(
  app: AppName,
  action: string,
  params: Record<string, string | number | null | undefined>,
  source: AppName
): string {
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') continue;
    q.set(key, String(value));
  }
  q.set('source', source);
  return `${app}://${action}?${q.toString()}`;
}

/**
 * Reads a link to one of the apps, or null for anything else. Pass `app` to
 * accept only links to that app. Parameters keep their first value.
 */
export function parseLink(url: string, app?: AppName): AppLink | null {
  const text = url.trim();
  if (text.length > MAX_LINK_LENGTH) return null;
  const m = /^([a-z]+):\/\/([a-z-]*)\/?(?:\?([^#]*))?(?:#.*)?$/i.exec(text);
  if (!m) return null;
  const target = appName(m[1]);
  if (!target || (app && target !== app)) return null;
  const params: Record<string, string> = {};
  for (const [key, value] of new URLSearchParams(m[3] ?? '')) {
    if (key !== 'source' && !(key in params)) params[key] = value.trim();
  }
  return {
    app: target,
    action: (m[2] || 'open').toLowerCase(),
    params,
    source: appName(new URLSearchParams(m[3] ?? '').get('source'))
  };
}

/** True when the link names an action its app answers. */
export function isKnownAction(link: AppLink): boolean {
  return Object.prototype.hasOwnProperty.call(LINK_ACTIONS[link.app], link.action);
}

/** A YYYY-MM-DD date parameter, or null when it is missing or not a real date. */
export function dateParam(value: string | undefined): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const d = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== value ? null : value;
}

/** A 0–1 fraction parameter, or null. */
export function fractionParam(value: string | undefined): number | null {
  if (value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 && n <= 1 ? n : null;
}
