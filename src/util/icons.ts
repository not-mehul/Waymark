// Inlined Lucide icons (MIT). stroke="currentColor" so they inherit text color.

const wrap = (inner: string, size = 16, stroke = 2): string =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;

export const icons = {
  moon: () => wrap('<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z"/>', 14, 2.2),
  sun: () =>
    wrap(
      '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>',
      14,
      2.2
    ),
  x: () => wrap('<path d="M18 6 6 18M6 6l12 12"/>'),
  plus: () => wrap('<path d="M5 12h14M12 5v14"/>'),
  trash: () =>
    wrap(
      '<path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1.2 14a2 2 0 0 1-2 1.8H8.2a2 2 0 0 1-2-1.8L5 6"/><path d="M10 11v6M14 11v6"/>'
    ),
  download: () => wrap('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>'),
  arrowLeft: () => wrap('<path d="M19 12H5M12 19l-7-7 7-7"/>'),
  check: () => wrap('<path d="M20 6 9 17l-5-5"/>'),
  edit: () => wrap('<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>'),
  share: () =>
    wrap(
      '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"/>'
    ),
  bell: () =>
    wrap('<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.9 1.9 0 0 0 3.4 0"/>'),
  menu: () => wrap('<path d="M4 6h16M4 12h16M4 18h16"/>'),
  users: () =>
    wrap(
      '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>'
    ),
  archive: () =>
    wrap(
      '<rect x="2" y="3" width="20" height="5" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8M10 12h4"/>'
    ),
  chevronRight: () => wrap('<path d="m9 18 6-6-6-6"/>'),
  chevronLeft: () => wrap('<path d="m15 18-6-6 6-6"/>'),
  info: () => wrap('<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>'),
  calendar: () => wrap('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>'),
  upload: () => wrap('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>'),
  save: () =>
    wrap(
      '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><path d="M17 21v-8H7v8M7 3v5h8"/>'
    ),
  alert: () =>
    wrap(
      '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4M12 17h.01"/>'
    ),
  camera: () =>
    wrap(
      '<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z"/><circle cx="12" cy="13" r="3"/>'
    )
};

export type IconName = keyof typeof icons;
