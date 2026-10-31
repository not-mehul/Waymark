# Editorial Dusk & Dawn

The shared design system for **Waymark**, **BookShelf** and **Ledger**.

One design language, two themes. **Dusk** is the dark theme: serif display type over a near-black surface with ambient amber and sage glows, like a late-night reading room. **Dawn** is the light theme: the same type and structure on warm paper cream, like a sunlit study rather than a sterile dashboard.

Both themes use generous whitespace, restrained ornament, monospace for anything data-flavoured, and sharp 2px corners. Dawn is not a light mode bolted on. Both themes come from a single set of semantic tokens.

This version merges the original reference with what each app worked out on its own: Waymark's component and interaction rules, Ledger's contrast-tuned Dawn accents and score tiers, and BookShelf's token naming and empty states. Section 17 lists every change from the original.

---

## 1. Principles

1. **Every colour is a token.** Component code never names a colour. A literal colour outside the two theme blocks is a bug.
2. **Tokens are named for their role, not their hue.** Use `--text-heading`, not `--cream`, because "heading text" means something in both themes and "cream" does not.
3. **Accents are seasoning.** An italic word, a number, an icon tint, the one active control. Never a whole surface.
4. **One question at a time.** Forms ask in the order the answers arrive. A choice that needs explaining gets a sentence next to it, never a tooltip.
5. **Pick, don't type.** Dates, times, places, ratings and categories are chosen from a control. Free text is for names and notes.
6. **Motion is responsiveness, not decoration.** Nothing bounces and nothing animates on load.
7. **It works with the radio off.** Fonts are bundled, never fetched, and nothing the UI needs to draw itself touches the network.

---

## 2. Theming architecture

Each token is defined twice, once per theme, under the same name.

```css
:root,
[data-theme="dark"] {        /* Dusk: the default */
  color-scheme: dark;
  --text-heading: #f5ecd9;
  /* …every token… */
}

[data-theme="light"] {       /* Dawn */
  color-scheme: light;
  --text-heading: #211d17;
  /* …the same tokens… */
}
```

`[data-theme="dark"]` is addressable on its own so that a share card, or any probe element, can read either palette whatever the page theme is.

**The theme preference has three states: System, Dusk and Dawn.** System follows `prefers-color-scheme` and keeps following it live. The header toggle is binary: tapping it while on System picks whichever side the OS is currently showing. Settings offers all three as a segmented control. The choice is stored per app, and new installs start on System.

```ts
type ThemeMode = 'system' | 'dusk' | 'dawn';

function applyTheme(mode: ThemeMode) {
  const dark = mode === 'system'
    ? matchMedia('(prefers-color-scheme: dark)').matches
    : mode === 'dusk';
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  setNativeBars(dark);            // status and navigation bar icons (§14)
  dispatchEvent(new Event('themechange'));   // canvas and SVG redraw (§10)
}
```

**Rules:**

- Never add a token to only one theme block.
- When a token is used as a **background**, check its paired text token in both themes. `--text-strong` flips from near-white to near-black, so as a fill it only works with `--on-invert` on top.
- Colours that need adjustable opacity (glows, tints, tinted borders) store an RGB triple, `--accent-amber-rgb: 212, 165, 116`, and are used as `rgba(var(--accent-amber-rgb), 0.25)`.

---

## 3. Colour tokens

Every value in this section is the source of truth. Step 2 of the plan generates each app's theme file from it.

### 3.1 Page surface

The page background is a three-stop vertical gradient, never a flat fill, with `background-attachment: fixed`.

| Token | Dusk | Dawn | Role |
|---|---|---|---|
| `--bg-grad-top` | `#14110e` | `#f3ece0` | Top of the gradient (warmest) |
| `--bg-grad-mid` | `#0f0d0b` | `#efe6d8` | Middle; also the solid "page" colour for sheets and native bars |
| `--bg-grad-bot` | `#0c0a08` | `#e9dfce` | Bottom (coolest) |

```css
html, body {
  background: linear-gradient(180deg,
    var(--bg-grad-top) 0%, var(--bg-grad-mid) 50%, var(--bg-grad-bot) 100%);
  background-attachment: fixed;
}
```

Dawn is warm paper. `#ffffff` is forbidden as a surface, and so is `#000000` in Dusk.

### 3.2 Text

Six steps, from highest emphasis down to decoration.

| Token | Dusk | Dawn | Role |
|---|---|---|---|
| `--text-strong` | `#f5f5f4` | `#1c1917` | Highest emphasis: input text, active labels |
| `--text-heading` | `#f5ecd9` | `#211d17` | Headings, serif display, key numbers |
| `--text-body` | `#e8e1d5` | `#3a342c` | Body text |
| `--text-muted` | `#d6d3d1` | `#4a443b` | Secondary text, inline strong |
| `--text-dim` | `#a8a29e` | `#6a6257` | Labels, hints, sublabels, footers |
| `--text-faint` | `#78716c` | `#938a7c` | Decoration only: separators, serials, disabled |

`--text-dim` is the dimmest token allowed for text someone needs to read. It passes 4.5:1 against every page and panel surface in both themes. `--text-faint` does not, and is reserved for things you could delete without losing information.

### 3.3 Accents

| Token | Dusk | Dawn | Role |
|---|---|---|---|
| `--accent-amber` | `#d4a574` | `#815c2d` | Primary accent: italic emphasis, icons, links, active rule |
| `--accent-bright` | `#fcd34d` | `#e0a92a` | Fills only: primary button, FAB, active segment |
| `--accent-bright-hover` | `#fef3c7` | `#c89320` | Hover and press of bright fills |
| `--accent-sage` | `#9fb3a0` | `#566856` | Secondary accent: progress, "done" states, the cool note |
| `--danger` | `#f87171` | `#b33528` | Destructive actions, errors |
| `--on-accent` | `#1c1917` | `#1c1917` | Text on `--accent-bright` (same in both themes) |
| `--on-invert` | `#1c1917` | `#f3ece0` | Text on a `--text-strong` fill (inverted secondary button) |
| `--on-danger` | `#1c1917` | `#f6efe2` | Text on a `--danger` fill |

RGB triples, for opacity:

| Token | Dusk | Dawn |
|---|---|---|
| `--accent-amber-rgb` | `212, 165, 116` | `176, 125, 62` |
| `--accent-bright-rgb` | `252, 211, 77` | `224, 169, 42` |
| `--accent-sage-rgb` | `159, 179, 160` | `111, 135, 112` |
| `--danger-rgb` | `248, 113, 113` | `192, 57, 43` |
| `--cream-rgb` | `245, 236, 217` | `120, 95, 50` |

The Dawn RGB triples are deliberately lighter than the Dawn solid accents. Triples feed glows, tints and tinted borders, which need warmth more than contrast. The solid tokens are used for text and icons, so they are deepened to pass 4.5:1 on cream. The original `#b07d3e` amber measured 2.9:1, which fails even the 3:1 large-text threshold.

`--accent-bright` is never used for text in Dawn (1.7:1 on cream). It is a fill.

Amber and sage are a complementary warm and cool pair, both muted and never saturated. The pairing reads as considered rather than decorative.

### 3.4 Lines

| Token | Dusk | Dawn | Role |
|---|---|---|---|
| `--border` | `#44403c` | `#cdbfa6` | Solid borders: inputs, toggles, tab tracks |
| `--border-strong` | `rgba(68,64,60,0.6)` | `rgba(120,105,80,0.45)` | Emphasised hairlines |
| `--border-soft` | `rgba(68,64,60,0.5)` | `rgba(120,105,80,0.32)` | Panel borders, section dividers |
| `--border-faint` | `rgba(68,64,60,0.4)` | `rgba(120,105,80,0.22)` | Internal dividers, hairline grids |

### 3.5 Surfaces

Translucent layers that lift content off the background. These are the "reading zones".

| Token | Dusk | Dawn | Role |
|---|---|---|---|
| `--panel-bg` | `rgba(20,17,14,0.6)` | `rgba(255,251,243,0.7)` | Standard panel and card |
| `--panel-bg-faint` | `rgba(20,17,14,0.4)` | `rgba(255,251,243,0.45)` | Control tracks, stat tiles |
| `--input-bg` | `rgba(12,10,8,0.8)` | `rgba(255,252,245,0.9)` | Inputs: recessed against the panel |
| `--chip-bg` | `rgba(28,25,23,0.6)` | `rgba(245,238,226,0.85)` | Option chips |
| `--chip-bg-hover` | `rgba(28,25,23,0.85)` | `rgba(238,229,213,0.95)` | Chip hover and press |
| `--hover-bg` | `rgba(41,37,36,0.4)` | `rgba(205,191,166,0.3)` | Generic hover and press wash |
| `--disabled-bg` | `rgba(41,37,36,0.6)` | `rgba(220,210,192,0.6)` | Disabled control fill |
| `--sheet-bg` | `#0f0d0b` | `#efe6d8` | Bottom sheets and full-screen flows (opaque) |
| `--modal-bg` | `rgba(20,17,14,0.96)` | `rgba(252,248,240,0.98)` | Modal card, toast |
| `--modal-backdrop` | `rgba(12,10,8,0.82)` | `rgba(60,52,40,0.45)` | Dimming layer behind modals and sheets |

In Dusk, inputs are darker than their panel. In Dawn, inputs are lighter than the cream panel. Both read as recessed, by going in opposite directions.

### 3.6 Effects and states

| Token | Dusk | Dawn | Role |
|---|---|---|---|
| `--focus-ring` | `#d4a574` | `#815c2d` | Keyboard focus outline (2px, offset 2px). Solid, 3:1 or better on every surface |
| `--shadow-rgb` | `0, 0, 0` | `90, 72, 44` | Shadow colour |
| `--shadow-strength` | `1` | `0.4` | Shadow opacity multiplier |
| `--error-bg` | `rgba(69,10,10,0.2)` | `rgba(192,57,43,0.12)` | Error wash |
| `--notice-bg` | `rgba(252,211,77,0.08)` | `rgba(224,169,42,0.12)` | Notice and highlight wash |
| `--success-bg` | `rgba(159,179,160,0.12)` | `rgba(111,135,112,0.14)` | Confirmation wash ("Backup saved") |

Dawn shadows are warm brown, not black, because a black shadow on cream looks like dirt. Use them as `rgba(var(--shadow-rgb), calc(0.4 * var(--shadow-strength)))`.

### 3.7 Data colours

Graphics are the one place where colour carries meaning instead of emphasis, so they have their own tokens. These are for strokes, fills and markers only, and need 3:1. Numbers and labels beside them stay in the text tokens.

**Quality scale.** A five-step ramp from best to worst, shared by Ledger's score tiers, BookShelf's rating distribution and any future "how good was it" graphic.

| Token | Dusk | Dawn | Ledger tier |
|---|---|---|---|
| `--scale-1` | `#9fb3a0` | `#3f7a4a` | Exceptional (85+) |
| `--scale-2` | `#b8c9a0` | `#66862f` | Strong (72+) |
| `--scale-3` | `#d4a574` | `#a4662c` | Decent (58+) |
| `--scale-4` | `#c08050` | `#b0552a` | Mixed (42+) |
| `--scale-5` | `#f87171` | `#b9382b` | Challenging |

**Series.** For charts with unordered categories, and for segmented graphics. Use them in order and never use more than four. Past four, group the rest as "Other" in `--series-4`.

| Token | Dusk | Dawn |
|---|---|---|
| `--series-1` | `rgba(212,165,116,0.85)` amber | `rgba(129,92,45,0.85)` |
| `--series-2` | `rgba(159,179,160,0.85)` sage | `rgba(86,104,86,0.85)` |
| `--series-3` | `rgba(252,211,77,0.7)` bright | `rgba(224,169,42,0.8)` |
| `--series-4` | `rgba(245,236,217,0.35)` cream | `rgba(120,95,50,0.35)` |
| `--chart-grid` | `rgba(120,113,108,0.2)` | `rgba(120,105,80,0.25)` |
| `--chart-track` | `rgba(68,64,60,0.5)` | `rgba(120,105,80,0.2)` |

**Maps.** Water is the one cool note in a warm system. In Dusk the land is the same stone the panels are cut from. In Dawn the land is the paper and the water is a wash printed over it.

| Token | Dusk | Dawn |
|---|---|---|
| `--map-water` | `#0b1114` | `#d7e0e0` |
| `--map-land` | `#262220` | `#f6efe2` |
| `--map-coast` | `rgba(120,113,108,0.54)` | `rgba(147,138,124,0.6)` |
| `--map-grid` | `rgba(120,113,108,0.2)` | `rgba(120,105,80,0.25)` |
| `--map-visited` | `rgba(212,165,116,0.32)` | `rgba(176,125,62,0.28)` |
| `--map-route` | `#d4a574` | `#815c2d` |

**Camera.** The barcode scanner's viewfinder is black in both themes, because the camera image needs it. `--scanner-bg: #000`, `--scanner-fg: #fff` and `--scanner-scrim: rgba(0,0,0,0.45)` are the only pure black and white in the system, and they are defined once and shared by both themes.

---

## 4. Atmosphere

The trademark element: three large, very soft radial glows in a fixed layer behind content. They add depth and warmth without texture. The glow tints come from the RGB triples, so they shift with the theme.

```css
.glow-layer { position: fixed; inset: 0; pointer-events: none; overflow: hidden; z-index: 0; }
.glow { position: absolute; border-radius: 50%; }

.glow-1 { /* amber, top right */
  top: -20%; right: -15%; width: 900px; height: 900px;
  background: radial-gradient(circle,
    rgba(var(--accent-amber-rgb), 0.10) 0%,
    rgba(var(--accent-amber-rgb), 0.03) 30%, transparent 60%);
}
.glow-2 { /* sage, bottom left */
  bottom: -30%; left: -20%; width: 1000px; height: 1000px;
  background: radial-gradient(circle,
    rgba(var(--accent-sage-rgb), 0.08) 0%,
    rgba(var(--accent-sage-rgb), 0.02) 35%, transparent 65%);
}
.glow-3 { /* cream, centre */
  top: 40%; left: 30%; width: 600px; height: 600px;
  background: radial-gradient(circle, rgba(var(--cream-rgb), 0.03) 0%, transparent 60%);
}
```

**No `filter: blur()` on the glows.** A gradient with a long transparent tail is already soft. A blurred 1000px layer costs a full-screen render pass on every scroll frame in an Android WebView, and gains nothing you can see. This is Waymark's finding, applied to all three apps.

Avoid noise overlays, grain, and animated backgrounds. They fight text contrast.

---

## 5. Typography

Type is identical in both themes. Only colour changes.

### 5.1 Families

| Role | Stack | Used for |
|---|---|---|
| Display serif | `Fraunces, "Iowan Old Style", "Apple Garamond", Baskerville, Georgia, "Noto Serif", serif` | Titles, section headings, card titles, stats, scores, verdicts, hints |
| Body sans | `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | Paragraphs, buttons, form values |
| Mono | `ui-monospace, "SF Mono", Menlo, Consolas, "Roboto Mono", "Liberation Mono", monospace` | Eyebrows, labels, codes, times, counts, numeric inputs |

Serif is always weight 400, with `letter-spacing: -0.01em`. An italic variant carries emphasis: one italic word inside a headline, set in `--accent-amber`. Italic is also the voice of section headings and hints.

**Fraunces is bundled.** Each app ships the Fraunces variable font (Latin subset, upright and italic, about 150 KB together, SIL Open Font License) as local WOFF2 files, so the serif looks the same on Android and iOS and still needs no network. The system serifs remain as fallbacks while the font loads (`font-display: swap`). Use weight 400 only; the optical-size axis adjusts itself to the font size.

### 5.2 Scale

Sizes are in `rem` on a 16px root. Never shrink the root on mobile.

| Style | Size / line-height | Family | Notes |
|---|---|---|---|
| Page title | 3rem / 1 (≥ 640px: 4rem / 0.95) | Serif | `letter-spacing: -0.02em` |
| Section heading | 1.875rem / 1.15 | Serif italic | |
| Sheet / modal title | 1.5rem / 1.2 | Serif italic | |
| Card title | 1.25rem / 1.25 | Serif | |
| Verdict / hero number | 2.25rem (≥ 640px: 3rem) | Serif | Scores, the "verdict" in a modal |
| Stat | 1.875rem / 1.05 | Serif | Stat tiles, dial centre |
| Tagline | 1.125rem / 1.45 | Serif italic | Header subtitle |
| Body | 1rem / 1.65 | Sans | |
| Body small | 0.875rem / 1.5 | Sans | Secondary rows, notices |
| Control | 0.875rem / 1.3 | Sans | Inputs, chips |
| Button label | 0.75rem, tracked 0.15em, caps, 500 | Sans | All button sizes |
| Data | 0.875rem / 1.3 | Mono | Times, codes, flight numbers |
| Data small | 0.75rem / 1.35 | Mono | Metadata in rows |
| Section label | 0.6875rem, tracked 0.25em, caps | Mono | `--text-dim`. Eyebrows, section markers, tab rail |
| Field label | 0.625rem, tracked 0.2em, caps | Mono | `--text-dim`. Above every field |
| Hint / footnote | 0.8125rem / 1.55 | Serif italic | `--text-dim` |

All-caps tracked mono is the core motif: eyebrows, labels, tabs and section markers. Never set all-caps at body size, and never track lowercase text.

Use `font-variant-numeric: tabular-nums` wherever numbers line up in a column: tables, rankings, timelines, counts.

---

## 6. Layout and spacing

### 6.1 Spacing scale

| Token | Value | Use |
|---|---|---|
| `--space-1` | 0.25rem (4px) | Icon to label |
| `--space-2` | 0.5rem (8px) | Inside a group |
| `--space-3` | 0.75rem (12px) | Between chips, between label and field |
| `--space-4` | 1rem (16px) | Between fields, grid gaps |
| `--space-5` | 1.5rem (24px) | Panel padding, between groups |
| `--space-6` | 2.5rem (40px) | Between sections |
| `--space-7` | 3.5rem (56px) | Below the page header |

### 6.2 Page

- Content max width `880px`, centred.
- Page padding: mobile `2.5rem 1.25rem 3.5rem`; at 640px and up `4rem 1.5rem 5rem`. Add the safe-area insets on top (§14).
- Content sits at `position: relative; z-index: 1`, above the glow layer.
- Section dividers are a 1px line in `--border-soft`.

### 6.3 Grids

- Paired elements: `repeat(auto-fit, minmax(280px, 1fr))`, gap 1rem.
- Hairline grids: a 1px gap over a `--border-faint` background, which draws single-pixel dividers without doubled borders. Use for stat rows and calendars.
- One column below 640px.

### 6.4 Shape

**Border radius is 2px everywhere**: panels, buttons, inputs, chips, badges, modals, sheets, FABs, thumbnails and share cards. No pills and no rounded cards. The only round things are things that are round: the dial, avatars, radio dots and map markers.

---

## 7. Surfaces

### 7.1 Panel

```css
.panel {
  background: var(--panel-bg);
  backdrop-filter: blur(4px);
  border: 1px solid var(--border-soft);
  border-radius: 2px;
  padding: 1.5rem;
}
```

Surface weights, lightest to densest: `--panel-bg-faint` (tracks, tiles) → `--panel-bg` (panels, cards) → `--input-bg` (recessed fields) → `--modal-bg` (modal, toast) → `--sheet-bg` (opaque).

`backdrop-filter` is optional. On Android it is skipped below WebView 76, and it is not worth a per-frame cost on long lists. The panel's translucency over the glows does the lifting either way. Never put `backdrop-filter` on list rows. Use it only on fixed chrome and panels.

### 7.2 Cards and rows

There are two list forms. Pick by content, and use the same form for the same content type across apps.

- **Card**: a panel with an optional thumbnail, a serif title, a mono metadata line and at most one trailing figure (score, rating, time). Use for things with a picture or a figure: a book, a city, a stay.
- **Row**: no panel. Rows are separated by `--border-faint` hairlines inside one panel. Use for long homogeneous lists: episodes, timeline items, settings, search results.

A tappable card or row gets `--hover-bg` on hover and press, never a lift, shadow or scale. A trailing chevron (`chevron-right`, `--text-faint`) means it opens a screen. No chevron means it acts in place.

Thumbnails are 2px-cornered, `--border-faint` outlined, at a fixed aspect ratio (2:3 posters and covers, 1:1 places). Placeholders are `--panel-bg-faint` with a centred `--text-faint` icon.

---

## 8. Components

The canonical set. Each app implements exactly these, with these names, so a reader moving between apps meets the same controls. Sources are noted where one app's version became the standard.

### 8.1 Buttons

All buttons share one height (44px minimum touch target, 40px visual on dense desktop rows), a 2px radius, and the button label style (caps, tracked 0.15em, 500 weight).

| Variant | Rest | Hover / press | Use |
|---|---|---|---|
| **Primary** | `--accent-bright` fill, `--on-accent` text, glow `0 0 40px rgba(var(--accent-amber-rgb),0.25)` | `--accent-bright-hover`, glow widens to 56px | The one forward action on a screen |
| **Secondary** | Transparent, 1px `--text-strong` border and text | Fill `--text-strong`, text `--on-invert` | Equal-weight alternatives |
| **Muted** | Transparent, 1px `--border`, `--text-dim` text | Text `--text-heading`, border `--text-dim` | Back, Cancel, Skip |
| **Danger** | Transparent, 1px `--danger` border and text | Fill `--danger`, text `--on-danger` | Delete. Outlined, never filled at rest (Waymark) |
| **Icon** | No fill or border, `--text-dim` icon | `--text-heading` | Close, overflow, fullscreen |
| **FAB** | 56px square, `--accent-bright`, `--on-accent` icon, glow plus soft shadow | `--accent-bright-hover` | The single "add" action of a top-level screen |

Disabled: `--disabled-bg` fill, `--text-faint` text, no glow, no border colour.

Paired buttons (Back and Next, Cancel and Save) are **the same width and height**. Colour carries the emphasis, not size (Waymark). The forward or committing action goes on the right.

Only one primary button per screen. The FAB counts as that screen's primary.

### 8.2 Chips and badges

| Kind | Look | Use |
|---|---|---|
| **Option chip** | `--chip-bg`, 1px `--border-soft`, `--text-muted`. Selected: `--accent-bright` fill, `--on-accent` text | Repeatable filters and quick picks |
| **Tag chip** | `rgba(amber, 0.14)` fill, 1px `--border-soft`, `--text-heading`, optional × | User tags and collections |
| **Status chip** | Mono 0.625rem caps, 1px tinted border, no fill. Sage = done or active, amber = in progress, dim = planned | Reading or watching status, booking state |
| **Count badge** | Mono 0.625rem, `rgba(amber,0.15)` fill, `--accent-amber` text, **2px radius** | Counts on tabs |

### 8.3 Choice cards (Waymark)

A choice that explains itself: icon, word and one sentence ("Ground: taxis, trains and transfers"). Use it at the top of a form when choosing a kind of thing, which happens once. Use option chips for filters, which happen often. Grid of `minmax(150px, 1fr)`. The selected card gets an `--accent-amber` 1px border and `rgba(amber,0.08)` wash.

### 8.4 Segmented control

A container with 1px `--border` over `--panel-bg-faint`. Inactive options are `--text-muted`, with `--hover-bg` on hover. **The active option is `--accent-bright` with `--on-accent` text and weight 500**, never `--text-strong` as a fill. Use it for two to four short, mutually exclusive options, such as a sort order or the theme setting. Past four options, use a tab rail or a picker.

### 8.5 Tab rail (Waymark and BookShelf)

Top-level and in-screen navigation. **One pattern in all three apps**, replacing Ledger's boxed tab grid.

- Words in the section label style (mono, caps, 0.25em) on a 1px `--border-soft` baseline.
- Inactive tabs are `--text-dim`, and `--text-heading` on hover. The active tab is `--text-heading` with a **2px `--accent-amber` rule** under the word.
- The rule slides between tabs (260ms settle). Nothing else moves.
- Horizontally scrollable when it overflows, with no visible scrollbar. Keep the active tab in view.
- A count badge may follow a label.
- Placed under the page header. **No bottom navigation bar.** These apps have three to five top-level sections and a FAB, and the tab rail keeps the editorial top-of-page structure.

### 8.6 Inputs

- `--input-bg` fill, 1px `--border`, 2px radius, padding `0.6875rem 0.875rem`, text `--text-strong`.
- Focus: the border becomes `--accent-amber`, plus the focus ring for keyboard focus.
- Placeholder: serif italic in `--text-faint`.
- Mono face for numbers, codes, times, ISBNs and flight numbers. Use `inputmode` to bring up the right keyboard.
- Label above in the field label style. Help text below in the hint style, never a tooltip.
- Error: border `--danger`, message below in `--danger` body small, `aria-describedby` to the message.
- Never browser-default styling. `select` elements use a custom chevron.

**Pickers** (Waymark). Date, time, month and zone fields are tappable fields shaped exactly like a text input (same height, border and padding) that open a picker modal. Calendars and clocks share one six-wide grid. A picker never changes a value unless the reader presses Choose.

**Slider** (Ledger metric slider). Track `--chart-track` 4px, fill `--accent-amber`, square 16px thumb in `--accent-bright` with a 1px `--on-accent` ring. Live value in serif to the right.

**Rating** (BookShelf). Half-star input, 26px Lucide stars, empty `--text-faint`, filled `--accent-bright`. Read-only ratings in lists use 14px stars in `--accent-amber`.

### 8.7 Step flow (Waymark)

For creating anything with more than four fields: a city entry, a book, a booking.

- One question per screen, asked plainly as a serif heading ("Where did you stay?"), with no field label under it.
- A progress rail at the top: one 2px segment per step, `--accent-amber` up to the current step, `--chart-track` after. Segments are tappable to go back. No "2 of 4" text.
- A footer with Back (muted) on the left and Next (primary) on the right, equal width. The last step's forward action commits ("Save").
- Answers live in the flow's state and survive going back and forth. Dismissing saves nothing.
- Editing an existing item uses a single scrolling form with the same fields, not a step flow.

### 8.8 Screen header (Waymark)

One line: back control, title and at most one action, on a shared baseline.

- **Top-level screen**: an eyebrow (section label style, the app's name or the date), the page title (serif, with one italic amber word if it has one), an optional tagline, and the theme toggle at the right.
- **Secondary screen**: back chevron, card-title-size serif title, and one icon action. No eyebrow and no explanatory paragraph. Explanations go next to the control they explain.

### 8.9 Theme toggle

A 2px-cornered track (`--input-bg`, 1px `--border`) holding a moon and a sun, with a sliding `--accent-amber` knob. The icon under the knob is `--on-accent`, the other is `--text-faint`. The knob moves on a 220ms `cubic-bezier(0.4,0,0.2,1)`. `role="switch"`, operable by Space and Enter, with `aria-checked` showing Dawn. Geometry is derived from three numbers (track, knob and inset), so each icon sits exactly under the knob.

### 8.10 Modal

Use it for focused tasks and confirmations that need attention: pickers, confirm dialogs, share previews, details on desktop.

- Centred card on `--modal-backdrop`. `--modal-bg` fill, 1px `rgba(amber,0.3)` border, 2px radius, max width 560px, padding 1.75rem.
- Shadow: `0 30px 80px rgba(var(--shadow-rgb), calc(0.7 * var(--shadow-strength))), 0 0 80px rgba(var(--accent-amber-rgb), 0.18), inset 0 1px 0 rgba(var(--cream-rgb), 0.05)`.
- Decorative 1px corner brackets in `rgba(amber, 0.5)`, drawn over the card so they never affect layout.
- Header: optional eyebrow, then serif italic title, then the close icon button.
- Entrance: fade plus `translateY(12px) scale(0.98)` to rest, **320ms** settle easing. Exit: 160ms fade.
- Dismiss with the close button, a backdrop tap, Escape or system back. On Android, the predictive back gesture shrinks it to 0.9 and fades to 0.7 under the finger.
- Use the native `<dialog>` with `showModal()`, so focus trapping and `inert` come for free.

**Confirm modal.** A plain question as the title, one line of consequence, and two equal buttons: Cancel (muted) on the left, the action (danger or primary) on the right, labelled with the verb ("Delete trip", not "OK"). Only use it for things that cannot be undone or typed back in a few seconds. For everything else, use the undo toast.

### 8.11 Sheet (Ledger)

Use it for longer forms, settings and filters on phones.

- Rises from the bottom, full width up to 560px, max height 90dvh, scrolls inside. Opaque `--sheet-bg`, 1px `--border-soft` top border, top corners 2px.
- A 32×3px `--border` grab handle centred at the top.
- Header row: serif italic title on the left, a muted "Close" or a primary "Save" on the right.
- Bottom padding includes the safe-area inset.
- Entrance: `translateY(100%)` to 0 over 320ms settle. Swipe down or system back dismisses it.
- At 900px and up, a sheet becomes a modal.

### 8.12 Toast and undo

One component in all apps, at the bottom centre above the safe area and the FAB.

- `--modal-bg`, 1px `--border`, 2px radius, padding `0.75rem 1.25rem`, body small text in `--text-body`. Max width `min(100% - 2rem, 480px)`.
- An optional single action at the right, in the button label style in `--accent-amber`. Use "Undo" after any deletion that could be recreated.
- Rises 16px and fades in over 260ms settle. **No overshoot.** It stays 4 seconds, or 6 with an action, and pauses while touched.
- `role="status"`, or `role="alert"` for failures.
- One at a time: a new toast replaces the current one.

### 8.13 Notice banner

An inline status line: an 18px icon, a one-line headline and optional detail.

| Kind | Fill | Border | Icon |
|---|---|---|---|
| Notice | `--notice-bg` | `rgba(amber,0.3)` | `--accent-amber` |
| Success | `--success-bg` | `rgba(sage,0.35)` | `--accent-sage` |
| Error | `--error-bg` | `rgba(danger,0.35)` | `--danger` |

If tapping the banner fixes the problem ("Notifications are off. Turn on"), the whole banner is a button with a trailing chevron. Otherwise it is inert, and has no button role.

### 8.14 Empty states

There are two kinds. Pick by cause.

- **Nothing here yet** (first run, or a section never used): a panel with a dashed 1px `--border-soft` border, padding `4rem 2rem`, a 40px Lucide icon at 1.25 stroke in `--text-faint`, a serif italic title in `--text-heading` ("No cities yet"), one serif line in `--text-dim` saying what goes here, and one primary or secondary button to add the first one. (BookShelf)
- **Nothing matches** (a filter or search found nothing): one line in the hint style, centred, with no panel, and a muted "Clear filter" button if a filter caused it. (Waymark)

### 8.15 Figures

- **Stat tile**: `--panel-bg-faint`, 1px `--border-soft`, centred serif stat in `--text-heading` over a field label. Lay tiles out in a hairline grid.
- **Meter / progress bar**: 4px track in `--chart-track`, fill in `--accent-sage` for completion or `--accent-amber` for a value. Animates to its value over 260ms, but only the first time it appears, not when returning from the back stack.
- **Score dial** (Ledger): a 270° arc. Track `--chart-track`, value stroke in the `--scale-*` token for its tier, a serif stat number in the centre, and the tier name below as a field label.
- **Bar list** (BookShelf stats, Ledger rankings): a label on the left, a 4px bar in `--series-1`, and a mono value on the right with tabular numbers.

### 8.16 Share cards

All three apps render share images (a city card, a year in review, a book, a trip). These rules are shared so the cards look like they belong to one family:

- 1080×1350 (4:5) by default, and 1080×1920 for stories.
- The theme follows the app's current theme. Read the palette from a probe element with `data-theme` set.
- Gradient surface plus the three glows at twice the opacity, a 2px hairline frame inset 48px in `rgba(amber,0.3)`, and corner brackets.
- An eyebrow at the top (the app's name and the date), a serif title, and one hero figure.
- A footer line in the field label style: app name on the left, a short tagline on the right. No URLs and no logos.

### 8.17 Settings

The same structure in every app, in a sheet on phones:

1. **Appearance**: theme (System, Dusk, Dawn segmented), language if available.
2. **App-specific**: metrics, categories, sources.
3. **Your data**: backup now, automatic backups, export (CSV, JSON, Markdown, zip), import.
4. **About**: version, licences, and a footnote in the italic-term pattern.

Each group starts with a section label. Rows are hairline-divided. A row's control goes on the right, and a hint goes under the label.

---

## 9. Icons

Lucide (ISC licence), inlined as SVG, with `stroke="currentColor"`, stroke width 2, round caps and joins, and no fills. Waymark draws extra glyphs in the same idiom: ink within 3–21 of the 24 box, optically centred, closed shapes closed.

| Context | Size |
|---|---|
| Inline with text | 14px |
| In buttons, chips and labels | 16px |
| Banners and list leading icons | 18px |
| Empty states | 40px at stroke 1.25 |
| FAB | 22px |

The toggle icons use stroke 2.2 so they stay crisp at small sizes. An icon on its own needs an `aria-label`, and an icon next to text is `aria-hidden`.

---

## 10. Canvas, SVG and maps

Graphics drawn in script (the score dial, the map, charts, share cards) cannot inherit CSS variables through attributes. Read the tokens live and redraw on `themechange`:

```ts
const token = (name: string, el = document.documentElement) =>
  getComputedStyle(el).getPropertyValue(name).trim();

addEventListener('themechange', () => { drawMap(); drawDial(); });
```

Prefer inline SVG with `fill="var(--token)"` in Svelte markup where you can. It needs no redraw at all.

---

## 11. Motion

Almost none. This is a deliberately calm aesthetic.

| Token | Value | Use |
|---|---|---|
| `--ease-settle` | `cubic-bezier(0.16, 1, 0.3, 1)` | Everything that arrives. It decelerates into place and never overshoots |
| `--ease-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | Toggles and things that move between two positions |
| `--dur-hover` | 150ms | Colour and background shifts |
| `--dur-toggle` | 220ms | Theme knob, segmented and tab indicators |
| `--dur-settle` | 260ms | Toasts, meters, list rows settling in, tab rule |
| `--dur-enter` | 320ms | Modals and sheets |
| `--dur-exit` | 160ms | Anything leaving |

- Hover is a colour or background shift, never a transform.
- Touch press: a `--hover-bg` wash, and buttons may give slightly to `scale(0.97)` under the finger. This is the only scale effect, and only on press.
- Accordion chevrons rotate 180°.
- List rows may settle in (fade plus 8px rise) the first time they appear, never again on scrolling back.
- **Never animate on page load, on scroll, or for decoration.** Use no bounce or spring overshoot. Ledger's toast and its 0.4–0.5s colour fades change to the values above.
- `prefers-reduced-motion: reduce` sets every duration to 0 except opacity fades, which drop to 100ms.

---

## 12. Voice and copy

- **Restrained and precise.** No exclamation marks, no "Let's get started!", no marketing voice, no emoji in UI text.
- **Ask plainly.** Step headings are questions ("When do you fly?"). Buttons are verbs ("Save city", "Delete trip").
- **Short, dignified labels.** "The Ledger", "Up next", "Past trips", "Your data".
- **Explain next to the control.** A hint under the field, not a tooltip and not a paragraph at the top of the screen.
- **Technical terms used plainly.** Respect the reader's intelligence.
- **Errors say what happened and what to do**, in one sentence: "Couldn't reach TheTVDB. Your library is unaffected; try again when you're online."
- **The italic term pattern** for footnotes is a signature touch: *Private.* Everything stays on this device.
- Use sentence case for everything except all-caps mono labels, which are set in caps by CSS. Write the source text in sentence case so translations and screen readers get real words.

---

## 13. Responsive behaviour

| Width | Behaviour |
|---|---|
| < 640px | One column. Page title 3rem, verdict 2.25rem, reduced page padding. The header may stack. Sheets for long forms. |
| 640–899px | Paired grids reflow to two columns. Page title 4rem. |
| ≥ 900px | Sheets become modals. Detail views may open beside a list. |

Lay out with `dvh` and container-relative units. Never use `100vh`, which mobile browser chrome breaks.

---

## 14. Mobile platform rules (Capacitor)

All three apps ship as Capacitor apps on Android and iOS.

- **Edge to edge.** `viewport-fit=cover`. Pad the page and fixed chrome with `env(safe-area-inset-*)`. The toast, FAB and sheets sit above the bottom inset.
- **System bars follow the theme.** Status and navigation bar icons are light in Dusk and dark in Dawn. Bars are transparent over the gradient. Update them on every `themechange`.
- **Back closes the topmost thing first**: picker, then modal, then sheet, then screen. It never exits the app from a secondary screen. Keep one overlay stack per app (Ledger's `ui.pushOverlay`). Support Android's predictive back where the WebView exposes it.
- **Touch targets** are at least 44×44px, with 8px between adjacent targets.
- **No hover-only affordances.** Anything revealed on hover must also be reachable by tap and keyboard.
- **No tap highlight.** Use `-webkit-tap-highlight-color: transparent`. The press state is the `--hover-bg` wash.
- **Long press** opens selection mode in lists that support bulk edit (BookShelf). Nothing else uses long press.
- **Keyboards.** Use `inputmode` and `enterkeyhint` on every input. The focused field scrolls into view above the keyboard.
- **Launch.** The splash and launcher backgrounds use `--bg-grad-mid` for the current theme, so there is no white flash. Set `data-theme` before first paint with an inline script in `index.html`.
- **App icons** share one construction: the `--bg-grad-mid` Dusk square, a centred serif glyph or Lucide-style mark in `--accent-amber`, and a 1px amber hairline frame.

---

## 15. Accessibility

- Text tokens down to `--text-dim` meet 4.5:1 on every page and panel surface in both themes. Data colours and the focus ring meet 3:1. `design/build-tokens.mjs` checks every pair, and a token change that fails the check fails the build.
- Hairline borders (`--border`) are below 3:1 by design. Controls are identified by their label and their recessed fill, not the border alone.
- Any token used as a background is paired with a named text token that contrasts in both themes: `--on-accent`, `--on-invert`, `--on-danger`.
- Every control is keyboard-operable. Switches use `role="switch"` and `aria-checked`. Tabs use `role="tablist"` and `aria-selected`. Expanding rows use `aria-expanded`.
- Focus-visible uses a 2px `--focus-ring` outline offset 2px. Never use `outline: none` on its own.
- `color-scheme` is set per theme so native scrollbars and form controls match.
- First load honours `prefers-color-scheme`. Animations honour `prefers-reduced-motion`.
- Colour never carries meaning alone. Score tiers also show the tier name, and status chips also show the word.
- Respect the OS text size. Layouts must hold at 200% text without horizontal scrolling.

---

## 16. Quick-start checklist

1. Import the generated tokens file. Never type a colour.
2. Add the glow layer and the gradient body.
3. Use serif for display, system sans for body and system mono for data.
4. Put an all-caps tracked mono label above every field and section.
5. Use a 2px radius everywhere. No pills.
6. Use translucent panels for content zones and rows inside them for long lists.
7. Keep accents sparse: italic words, numbers, icon tints, the one active control.
8. Allow one primary action per screen.
9. When a token is a background, use its paired `--on-*` text token.
10. For canvas and SVG, read tokens live and redraw on `themechange`.
11. `--text-dim` is the dimmest readable text. `--text-faint` is decoration.
12. Use step flows for creating, single forms for editing.
13. Use the undo toast instead of confirming, unless the action is truly irreversible.
14. Back closes the topmost overlay first.
15. Write copy like an editor, not a marketer.
16. Resist the urge to animate.

---

## 17. Changes from the original reference

**Fixes**

- Dawn `--accent-bright-hover` was written as `#c8932060` (a 38% transparent colour). It is now `#c89320`, which BookShelf already used. Waymark copied the typo.
- The secondary button's hover put `--on-accent` (`#1c1917`) on a `--text-strong` fill (`#1c1917`) in Dawn, so the text disappeared. There is now a separate `--on-invert` token, from Ledger. BookShelf has this bug today.
- The danger button's hover put near-black on Dawn red at 3.2:1. There is now a separate `--on-danger` token at 5.3:1.
- Dawn amber (`#b07d3e`, 2.9:1) and sage (`#6f8770`, 3.2:1) failed contrast as text and icons. They are deepened to `#815c2d` and `#566856` (4.5:1), and their RGB triples stay warm for glows. This follows Ledger, which had already deepened its Dawn accents.
- Dawn `--text-dim` and `--danger` were just under 4.5:1 on the gradient's bottom stop. They are nudged to `#6a6257` and `#b33528`.
- The focus ring was a grey at 50%, which fell below 3:1. It is now solid amber.

**Removed or replaced**

- The decision wheel tokens are gone, since none of the three apps has a wheel. They are generalised into `--series-*` chart tokens with the same four tints.
- `filter: blur()` on the glows is removed for performance (§4).
- Ledger's boxed tab grid becomes the tab rail. Ledger's pill badges become 2px. Ledger's overshoot toast loses its bounce.
- Modal entrance shortens from 500ms to 320ms. Half a second reads as lag on a phone.

**Added**

- A three-state theme mode (System, Dusk, Dawn), from Waymark.
- Data colours: the quality scale (Ledger tiers), chart series, map tokens (Waymark and Ledger) and the camera exemption (BookShelf).
- `--sheet-bg`, `--success-bg`, a spacing scale and motion tokens.
- Components: tab rail, choice cards, pickers, step flow, screen header, sheet, toast and undo, notice banner, two kinds of empty state, figures, share cards and settings structure.
- Mobile platform rules (§14) and the contrast check in the build (§15).

**Decided**

- Fraunces is bundled as the display serif in every app (§5.1).
- "System" is the default theme for new installs (§2).

**Source of truth**

Every value in §3, §5.1, §6 and §11 lives in `design/tokens.json` in each repo. `node design/build-tokens.mjs` checks contrast and generates the theme files: `src/styles/tokens.css` in the web apps, and `GeneratedTokens.kt` in Waymark until it moves to Svelte. Edit the JSON, never the generated files.
