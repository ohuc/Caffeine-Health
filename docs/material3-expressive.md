# Material 3 Expressive — working guidelines

> **Read this before building or changing any UI in this app.** These are the rules we
> design against, distilled from the official Material 3 / M3 Expressive guidance
> (m3.material.io, developer.android.com) and adapted to this project's design system.
> When a rule here conflicts with the project's own conventions in `CLAUDE.md`
> (Instrument Serif metrics, `CustomColors`, flat/no-glow direction), the project wins —
> M3 Expressive is the *framework*, our design system is the *brand* on top of it.

## The five pillars (what "expressive" means)

1. **Emphasis & hierarchy** — every surface should have ONE clear hero. Make the most
   important element bigger, bolder, or more colorful than everything around it.
   Users find key elements up to ~4× faster when hierarchy is strong. Don't let two
   elements fight to be the focal point.
2. **Containment & grouping** — group related content into contained surfaces (cards,
   pills, rows). Containment is how you create clarity; use generous spacing and a
   brighter surface to lift the important group. **One concept = one group:** don't split
   the same idea across two stacked sections (e.g. a "smart" strip above a "pinned" strip).
   Merge them into one surface with one hero — two competing zones for the same thing reads
   as clutter and dilutes the focal point.
   *Hero moments: stick to one or two per screen; too many overwhelm.*
3. **Shape** — shape carries brand and rhythm. Pick a consistent shape language and
   reuse it; don't sprinkle many different corner radii. (See shape scale below.)
4. **Color emphasis** — use color to direct attention, not just decorate. Vibrant
   accents = "look here." Neutral surfaces = "context."
5. **Motion (physics)** — motion should feel alive: spring-based, not linear. Reserve
   bigger motion for meaningful state changes; keep ambient motion subtle.

## Layout & content rules (the ones we keep breaking)

- **No dead space.** A card/surface with a fixed height must fill it intentionally.
  If content is optional (e.g. a sensor that may not exist), the layout must still read
  as balanced when it's absent — either (a) the surface always has a fallback hero in
  that slot, or (b) the surface shrinks to fit. **Never leave a hero slot empty so the
  remaining content floats against one edge with a void below it.** (This is exactly the
  room-card bug from 2026-06: hiding temp/humidity left tall cards half-empty.)
- **`SpaceBetween` requires content at BOTH ends.** Only use `Arrangement.SpaceBetween`
  in a fixed-size container when you can guarantee a top block AND a bottom block. If the
  bottom block can vanish, give it a fallback or switch to `Center`/`Top`.
- **Match heights within a row.** In a multi-column grid, cards in the same visual row
  should share a height. Adapt the row's height to its content, but keep the pair equal.
- **One hero metric per card.** Show the single most glanceable stat large (our signature
  Instrument Serif numerals). Secondary stats go small and labeled. If the primary stat
  is unavailable, fall back to the next most relevant always-present stat — don't just
  delete the hero.
- **Don't render placeholder/zero data as if it were real.** No `0°`/`0%` when there is
  no sensor. Model "absent" as `null`, then either omit or substitute a real fallback.

## Shape scale (M3)

| Token | Radius | Typical use |
|-------|--------|-------------|
| extraSmall | 4.dp | tiny chips, snackbars |
| small | 8.dp | small buttons |
| medium | 12.dp | default cards, menus |
| large | 16.dp | cards, sheets headers |
| extraLarge | 24.dp | bottom sheets, big cards, FABs |
| full | 50% | pills, toggles, icon circles |

Reuse one or two of these consistently. This app leans on `large`/`extraLarge` for cards
and `full`/`PillShape` for pills and the accent bar.

## Color — emphasis ladder

From the M3 color system; **but for consistently colorful brand surfaces prefer
`MaterialTheme.customColors`** (sky/sand/mint/cyan/lavender/coral) per `CLAUDE.md`, since
those are independent of Material You dynamic color.

- `surface` + `onSurface` → **highest** emphasis (primary text/metrics).
- `surfaceContainer` / `surfaceContainerHigh` → raised group containers (our card bg).
- `surfaceVariant` + `onSurfaceVariant` → **lower** emphasis (captions, labels, units).
- `primary` / `primaryContainer` → selected/active, prominent actions.
- `tertiary` / `tertiaryContainer` → contrasting accent, "extra attention" elements.
- Always pair a color with its matching `on*` role for contrast (e.g. `onPrimary` on
  `primary`). Never mismatch (e.g. `primaryContainer` text on `tertiaryContainer`).
- `glanceInkOn()` already does luminance-based black/white ink on filled accents — use it.

## Typography (M3 scale + our brand)

- Brand: **Instrument Serif** for display/headline, the greeting, section labels, and all
  large metric numbers (signature — preserve). **Montserrat** for everything else
  (titles, labels, body). Use `RollingNumberText` for animated numeric values.
- M3 role sizes for reference: displayL 57/64, headlineL 32/40, titleL 22/28 (Medium),
  titleM 16/24 (Medium), bodyL 16/24, labelL 14/20 (Medium), labelS 11/16 (Medium).
- Drive hierarchy with weight + size, not color alone. Labels/units: small, Medium
  weight, `onSurfaceVariant`, often with positive letterSpacing.

## Motion

- Prefer spring specs (`spring(dampingRatio, stiffness)`) over fixed `tween` for
  enter/exit and bounds changes; tween is fine for simple fades/crossfades.
- Keep ambient/idle motion subtle; reserve expressive motion for real state changes.
- When list items can be added/removed/reordered, key composables by item identity
  (`key(id) { … }`) so animations follow the item, not the slot. (Lesson from the
  scenes/favorites flash-back bug.)
- Respect the project direction: **flat, clean, no radial-gradient glow blobs**; use
  stock M3 components (e.g. `Slider`) rather than custom glowing ones.

## Quick pre-ship checklist

- [ ] Is there exactly one clear hero on this surface?
- [ ] Does every fixed-height container fill its space with no awkward void in any data state (sensor present / absent, 0 / many devices, long / short names)?
- [ ] Are optional elements modeled as nullable and either omitted or given a fallback — never shown as fake zeros?
- [ ] Consistent shape language (1–2 radii) and consistent accent usage?
- [ ] Captions/units use `onSurfaceVariant` + small Medium weight; metrics use the serif?
- [ ] Animations keyed by identity; springs for state changes; flat (no glows)?
