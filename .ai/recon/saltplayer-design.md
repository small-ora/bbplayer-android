# 椒盐音乐 / Salt Player — Visual Design System Recon

Target: reproduce the look in a **native Android app (Kotlin + Jetpack Compose, Material 3)**.
This document is derived from (a) the app's **own open-source design system source code** and
(b) **real screenshots** downloaded into `/root/bbplayer/.ai/recon/saltplayer/`.

- App: 椒盐音乐 / Salt Player, package `com.salt.music`, by Moriafly. Latest observed release: **12.6.0 (2026-11-02)**.
- Design system: **SaltUI** — <https://github.com/Moriafly/SaltUI> (Apache-2.0, Compose Multiplatform;
  SaltUI 1.0 was *extracted from Salt Player*, and "today SaltUI powers Salt Player"). This is a rare case
  where the design tokens are authoritative source, not visual guesswork.
- Files cited as source of truth (downloaded copies in `saltplayer/raw/`):
  `saltui-SaltColors.kt`, `saltui-SaltShapes.kt`, `saltui-SaltShapes.android.kt`, `saltui-SaltDimens.kt`,
  `saltui-SaltTextStyles.kt`, `saltui-SaltTextStyles.android.kt`, `saltui-SaltPalette.kt`,
  `saltui-SaltTheme.kt`, `AlphaIndication.kt`, `RoundedColumn.kt`.

Confidence tags used below: **[SRC]** = read from first-party source code; **[VIS]** = measured/observed
in screenshots; **[REL]** = from official release notes; **[INF]** = inference (flagged, low confidence).

---

## 0. One-paragraph design DNA

A **flat, airy, "one accent colour + translucent white veil"** system — closer to iOS grouped-inset
lists / Fluent than to stock Material 3. Page background is a plain light grey (`#F3F3F3`) and every
group of content is a **16 dp rounded, 50 %-white translucent card with a hairline border and no
shadow**. There is exactly **one accent colour** (default `#0470E6`, replaced by Monet dynamic colour),
used for: section labels, enabled switches, the selected segment, links, and the **currently playing row's
entire text**. Typography is a tiny 3-step scale (12 / 16 / 24 sp) with heavy reliance on weight and
opacity rather than size. Album art is always a **rounded square** (8 dp in lists, ~12 dp in the player).
The player/lyrics screens break the theme completely: they ignore the palette and paint a **blurred,
saturated album-art wash** behind white text, with a **2 dp progress line** and two rows of bare glyph
controls. Every surface in users' hands can be re-tinted from the wallpaper, so **no colour may be
hard-coded** in the Compose port.

---

## 1. Colour

### 1.1 The palette is *not* `MaterialTheme.colorScheme` — it is an 11-role model **[SRC]**

`SaltColors(highlight, onHighlight, text, subText, background, subBackground, popup, stroke, success, warning, error)`

| Role | Light default | Dark default | Meaning / usage |
|---|---|---|---|
| `highlight` | `#0470E6` | `#0088FF` | THE accent. Also = Monet `primary`. |
| `onHighlight` | `#FFFFFF` | `#FFFFFF` | text/icons on accent fills |
| `text` | `#1E1715` | `#EBEEF1` | primary text |
| `subText` | `#8C8C8C` | `#BFE1E6EB` (75 %) | metadata, subtitles, placeholders |
| `background` | `#F3F3F3` | `#202020` | page background |
| `subBackground` | `#80FFFFFF` (50 % white) | `#08FFFFFF` (3 % white) | **grouped cards / list surfaces — a translucent veil, not a solid** |
| `popup` | `subBackground.compositeOver(background)` | same | menus, dialogs (i.e. the *composited* value of the veil) |
| `stroke` | `subText @ 15 %` | `subText @ 10 %` | 1 px hairline around every card |
| `success` | `#0E8A00` | `#B8F2A9` | |
| `warning` | `#9D5D00` | `#FCE100` | |
| `error` | `#C42B1C` | `#FF99A4` | |

Extra pairs for banner/toast backgrounds **[SRC]**: warning bg `#FFF4CE`/`#433519`,
error bg `#FDE7E9`/`#442726`, success bg `#E6F8E0`/`#2B4C1F`.

Key consequences for the port:
1. **Surfaces are veils.** `subBackground` is 50 % white in light and 3 % white in dark. Cards therefore
   read as "whiter than the page" in light but as "lighter black" in dark, and they let background
   wallpaper/branding show through. In the port, use
   `surface = Color.White.copy(alpha = 0.50f)` over `background`, and
   `surface = Color.White.copy(alpha = 0.03f)` in dark — not `surfaceContainer*`.
2. **Only one accent exists.** No secondary/tertiary colours anywhere in the system.
3. **Hairlines, not shadows.** `stroke` at 1 hairline px; no elevation in the entire app (**[VIS]**: the
   only shadows are on the big player cover and the mini player).
4. Measured page values from screenshots: plain library bg `#F3F3F3` exactly (**[VIS]** = the source
   default), tinted page bg `#F9FAFC` and card `#FDFDFD` when dynamic colour is active, dark page
   `#0B0C0E` with cards `#1A1F23` under a Monet theme (**[VIS]**, D-01/D-11).

### 1.2 Dynamic colour (Material You / Monet) is a first-class feature **[REL][VIS]**

- "动态颜色主题 (Android 12+, 基于 MD3 动态桌面壁纸取色)" shipped as experimental in **8.16.4**, and in
  **10.0.0-beta02** it was **promoted out of Labs into User Interface**. Also present: "壁纸取色"
  (wallpaper colour extraction, older devices), "动态壁纸取色", separate **主界面壁纸** (library
  wallpaper) and **播放界面壁纸** (player wallpaper) images.
- Effect observed: the *whole* neutral ramp gets tinted, not just the accent — mechanics page/cards go
  pale blue-white (`#F9FAFC`/`#FDFDFD`), a green-wallpaper user gets a sage-tinted sheet with dark-green
  switches and pale-green selected pills (D-15), and a black-and-white Monet scheme produced a reported
  contrast bug (issue #2177) — proof the neutrals track `background`/`surface`.
- **Implementation recipe for Compose:** build `SaltColors` from `dynamicLightColorScheme(context)` /
  `dynamicDarkColorScheme(context)` on API 31+, mapping
  `highlight = primary`, `background = surface` (or a `surfaceContainerLowest`-like tone),
  `subBackground = surfaceContainerHigh.copy(alpha=…)`, `text = onSurface`, `subText = onSurfaceVariant`;
  on < API 31 fall back to the fixed defaults above (the app ships a blue accent as the fallback).
  Provide a separate "follow cover art" mode for the *player only* (see 1.4).

### 1.3 Accent usage rules (verbatim from the screenshots)

Accent appears **only** as:
- 12–14 sp **section labels** above settings groups ("用户界面", "播放界面", "Theme", "Song", "User interface") — D-09, D-10.
- **Switch ON** state (track filled accent, white thumb) — D-10.
- **Selected** segment / chip / theme card: accent **1 dp border** + accent icon + accent label (D-10).
- **The currently playing list row**: title, metadata, separators and the quality badge *all* recolour to
  accent (`#1B6FE0`-ish in D-01). There is no equalizer animation on the row — colour is the indicator.
- Links and text buttons ("取消", "确定"), dialog primary pill buttons.
- Lyrics: the sung/active portion and the "current line" emphasis.
Never used for: card fills, nav backgrounds, headers, or the player screen (there the accent is the cover art).

### 1.4 Player background = album-art wash, *independent of theme* **[VIS]**

- The player and lyrics screens are full-bleed **blurred, saturation-boosted album art** (a huge blur
  radius, so it reads as a 2–3 colour gradient), plus a top→bottom scrim. In D-04 the page is a pale
  blue/pink wash taken from the cover; in D-05 the same layout is deep amber because the cover is amber;
  in D-08 it is crimson/black. So: **tint source = artwork, not wallpaper, not `highlight`**.
- Text on top is **white** (title 100 %, subtitle ~70 %), controls white, secondary row ~85 % white.
- Practical Compose recipe: `Bitmap` → downscale to ~24 px → `Modifier.blur(90.dp)` on a
  `ContentScale.Crop` image (or a 2-stop `Brush.verticalGradient` of the cover's dominant colours for
  cheapness), then `Brush.verticalGradient(listOf(Black@0.15, Black@0.35))` scrim, and derive a
  `coverDominant` colour for the status-bar icons.
- The **info panel** and **sheets** opened over the player use *glass* cards (translucent white ~8–12 %
  in light-on-art, ~4 % in dark) rather than `subBackground` (D-06: chips "播放界面保持屏亮", cards
  "音频信息 / 出自专辑 / 参与创作的艺术家").

---

## 2. Shape & elevation

### 2.1 Radius scale **[SRC]**

`SaltShapes { small = 8.dp, medium = 16.dp, large = 24.dp }` (Android actuals). Plus the legacy dimens
`corner = 12.dp`, `dialogCorner = 20.dp`, and `RoundedColumn` uses `shapes.medium` with a `Hairline` border.

Observed mapping (**[VIS]**, px measured at 1216 px / 411 dp ≈ 2.96×):

| Element | Radius |
|---|---|
| list thumbnail (48–56 dp) | **8 dp** (≈24 px) |
| mini-player cover (44–48 dp) | **8–10 dp** |
| big player cover (~330 dp) | **12 dp** |
| grouped card / settings group | **16 dp** (`medium`) |
| song-info cards on player | 16 dp |
| bottom sheets (Sleep Timer, sort, song menu) | **24–28 dp** top corners only |
| popup menu / popup card | 20–24 dp |
| dialogs | 20 dp (`dialogCorner`) |
| primary buttons / "Start" | **full pill** (r = height/2, height 52–56 dp) |
| search field | **full pill** (height 44–48 dp) |
| switches | M3 52×32 pill |
| artist avatar | circle (the only circle in the app) |

Extra: the **player page itself adapts its top corner to the device's rounded display corner**
("播放界面圆角现在会根据设备圆角进行适配" 10.0.0-beta02, later "移除播放界面圆角" 10.7.0, plus 12.4.1
"修复播放页圆角交接"). **[REL]** For a fresh port: skip device-corner matching, but if you present the
player as a sheet, use `RoundedCornerShape(28.dp)` top corners.

### 2.2 Elevation

There is effectively **no Material elevation**: no shadow under cards, lists, nav, or sheets (**[VIS]**).
Depth is created by *veil + hairline*, and in exactly two places by shadow:
the player cover (soft, ~12–24 dp ambient) and the mini player capsule (soft shadow + slight translucency).
Sheets and popups read as floating because of the scrim + 24 dp radius, not a shadow. Dialogs likewise.

---

## 3. Typography

### 3.1 SaltUI's deliberately tiny scale **[SRC]**

```
main        = 16.sp, default weight
sub         = 12.sp
paragraph   = 16.sp, lineHeight 1.5.em
largeTitle  = 24.sp, SemiBold
```
That's the entire token set — no `displayLarge … labelSmall` ladder. Compose port: define
`SaltTypography(main, sub, paragraph, largeTitle)` and set `ProvideTextStyle(main)`.
System font ("跟随系统字体缩放" is a setting **[REL]**), i.e. Roboto / Noto Sans CJK; CJK-heavy UI means
glyphs are visually denser than the sp numbers suggest.

### 3.2 Observed type roles

| Role | Size / weight / colour |
|---|---|
| App bar title ("歌曲", "设置") | **20–22 sp, SemiBold/Bold**, `text` |
| Big heading on detail/about ("郑兴", "Open Source License…") | **24 sp Semibold** (`largeTitle`), `text` |
| Settings section label | **13–14 sp Medium**, `highlight` |
| List row title | **16 sp, Medium→SemiBold**, `text` (accent when playing) |
| List row metadata (one line only) | **12 sp**, `subText`; quality badge 10–11 sp in accent |
| Settings row title / subtitle | 16 sp Medium / **12 sp `subText`, max 2 lines** |
| Player title | 20–22 sp Bold, white; subtitle 12–13 sp white@70 % |
| Player mini-lyrics (3–4 lines under the cover) | 16–18 sp, white@45–60 %, line height ~1.5 |
| Player technical line ("AudioTrack FLAC 16 bits 830 kbps") | 11–12 sp, white@55 %, centred |
| Progress times ("00:02 / 03:55") | 12–13 sp, white@65 % |
| Lyrics current line | **22–30 sp Bold** (user setting 14/22/30/34; player mini-lyric up to **48 sp**) **[REL]** |
| Lyrics non-current lines | **same size**, weight normal, opacity 35–55 % |
| Lyrics translation line | ~0.7× original, ~60 % opacity, normal weight |
| Menu / sheet item label | 15–16 sp, `text` |

Rules that matter: **only one metadata line per row** (`SQ  陈奕迅 · U 87`), built as
`[quality badge] artist · album` and ellipsised; **weights carry hierarchy** (16 sp Medium title vs
12 sp regular sub); **opacity carries hierarchy** in lyrics, not size.

---

## 4. Spacing & metrics

**[SRC]** `SaltDimens`: `item = 56.dp` (Android row min height), `itemIcon = 24.dp`, `padding = 16.dp`,
`subPadding = 12.dp`. `RoundedColumn` inner padding = (h 16, v 8), outer margin = (h 16, v 8),
plus `RoundedColumnInListEdgePadding = 5.dp` at list start/end.

**[VIS]** measured:
- Screen horizontal gutter **16 dp**; card-to-card gap **16–24 dp**; section label top margin ~20 dp.
- List row pitch ~**72–76 dp** with 56 dp cover; content start 16 dp, cover→text gap **10 dp**.
- Bottom dock: **mini-player row ~64 dp + progress 2–4 dp + nav row ~56–64 dp**, radius ~32 dp, side margin
  8–16 dp, floating above the gesture-bar inset; nav icons 24 dp at the quarter positions.
- Mini player height **~64–72 dp**, cover 44–48 dp, right icons 24 dp, horizontal padding 12–16 dp.
- Player: cover side = screen width − 32 dp (or −48 dp), centred; control deck bottom padding 24 dp +
  nav insets; transport glyph size **34–40 dp**; secondary glyphs **24 dp** spread edge-to-edge at
  ~1/6, 2/6 … of width; slider inset 16 dp.

---

## 5. Screen-by-screen patterns

### 5.1 Bottom dock: mini player **and** navigation in one card **[VIS D-02 — newest 12.x layout]**

The current (2025/26) main screen has **one floating rounded dock** at the bottom, not a bar plus a nav:

```
╭───────────────────────────────────────────────╮  radius ≈ 32 dp, side margin ≈ 8–16 dp
│ [cover 48] 当你老了            ⏸            │  mini player row, 64 dp
│            睡意昏沉                          │
│ ───────────────●──────────────────────────── │  2–4 dp progress line, inset 16–24 dp
│   ▦            ▥            ⌕            ☰   │  nav row, 56–64 dp, icon-only
╰───────────────────────────────────────────────╯
        (floats above the gesture bar, ≈8 dp bottom margin)
```

- **4 destinations, icons only, no labels**: **2×2 rounded grid** (library), **3-bar chart** (statistics),
  **magnifier** (search), **hamburger** (more/menu). The selected item is white/`text`, the others
  `subText` at ~45 %; **no M3 pill indicator, no labels, no divider**.
- The dock background is a translucent dark/light "capsule" (glass), radius ~32 dp, with a soft shadow —
  it is *the* signature shape of the newest version. Nav is hidden on pushed screens (settings, search,
  artist detail), where the mini player is instead a **floating rounded card with 16 dp side margins**
  (D-03) or a full-width bar attached to the bottom (older versions, D-01).
- Older layout for reference (still seen in many screenshots): plain mini-player bar with a bottom
  navigation row underneath; the port should implement the **dock** because it is the current design.

### 5.2 Mini player ("胶囊迷你播放条") **[VIS D-02/D-09/D-10/D-03, REL]**
- A **capsule/rounded bar** (height 64–72 dp; radius = half height when standalone, 32 dp inside the dock),
  background `popup`/glass, soft shadow, 16 dp side margins when floating.
- Layout: `[cover 44–48 dp, r 8–10 dp] [title 15–16 sp Medium; subtitle 12 sp subText] … [play/pause 24 dp] [queue 24 dp]`.
- A **thin progress line runs across the bottom of the dock** under the mini player (D-02) — not a wave,
  no knob.
- Content: normally `title / artist`; can instead show **a single scrolling lyric line** ("迷你播放条歌词")
  — a marquee that scrolls the active line **[REL 8.2.0-beta01, 7.13]**.
- Gestures **[REL]**: tap = open player; **swipe up = expand player** (7.2.0); left/right = previous/next
  (5.18, 8.x); cover crossfades on track change (7.0.3) and **shared-element morphs into the player cover**
  (12.3.0) with shadow animation (12.3.2); "Liquid Glass 弹性交互效果" (12.4.0) = the bar scales/squishes
  elastically on press.
- Distinctive: **cover is a small rounded square, never a circle**; the bar respects a "capsule" bottom
  radius that follows the display corner ("修复胶囊迷你播放条在部分设备上的圆角不重合").

### 5.3 Library list (songs) **[VIS D-01]**
- Top bar: **hamburger (3 thick lines)** left, title `歌曲` 20–22 sp bold, **search** right.
- Toolbar row under it: `shuffle icon + count` left ("⇄ 1609"), `A/Z ↓` sort icon and `list-layout`
  icon right. Same pattern on artist/search result pages (D-03, D-14).
- Row: 48–56 dp rounded-square cover (8 dp) · title 16 sp (SemiBold) · one metadata line
  `[SQ|HQ|HR badge] artist · album` 12 sp · trailing **`+` (add to queue)** in `subText` at ~70 % ·
  optional **⋮ drag handle** at the far right · right edge **A–Z index rail** (letters 10–11 sp,
  `subText`, tap = jump + bubble + haptic).
- Playing row: entire text in accent (no other marker).
- Floating round **"jump to current"** button (44 dp, white/`popup` circle with a **crosshair/target**
  glyph: ring + centre dot + 4 ticks) sits bottom-right above the mini player (D-03).
- Quality badge: 10–11 sp accent text in a `highlight @ 12 %` rounded 4–6 dp chip (light) / accent @ 18 % (dark).
- Sorting uses a **bottom sheet of text buttons in a 2-column grid** (no icons) — D-15.
- Album/artist detail pages: big circular/arced artist image or album cover, 24 sp semibold name, then
  `shuffle + track count` toolbar, then the same rows (D-14).

### 5.4 Now playing **[VIS D-04, D-05, D-06]**
Vertical stack, everything floating over the album wash:
1. **Header** (top, 16 dp inset): `title 20–22 sp bold` + `subtitle 12–13 sp` (artist/album) at left;
   a small **"audio info" glyph** (two arcs / waveform in a circle) at top right.
2. **Cover**: square, width = screen − 32…48 dp, **12 dp** radius, subtle shadow.
3. **Mini lyrics**: 3–4 lines, left-aligned, 16–18 sp, white 45–60 %, directly under the cover
   (in D-04: "词：… / 曲：… / 制作人：…").
4. **Technical line** centred: `AudioTrack  FLAC 16 bits  830 kbps` (11–12 sp, white 55 %).
5. **Progress**: a **2 dp line**, rounded caps, track = white 25 %, played = solid white; the thumb is a
   **small 4–6 dp white dot that only appears while dragging**. **Times sit *below* the bar**, 12–13 sp,
   left and right aligned. No wave, no bar-graph, no thickness animation.
6. **Transport row**: `prev ⏮` `play ▶` `next ⏭` as **bare glyphs 34–40 dp**, no circles, no containers,
   evenly spaced (centres at ~¼, ½, ¾ width).
7. **Secondary row (5 bare 24 dp glyphs)**: **loop/repeat, sleep timer (stopwatch), spectrum/audio info,
   queue (list), more (…)**, evenly spread edge to edge.
8. Inset handling: whole control deck sits above nav-bar insets; gesture pill visible on the scrim.

**Info panel / second page (D-06, reachable from the player)** — glass cards over the same wash:
header (title/artist + audio icon) → 2-column quick-action chips (`播放界面保持屏亮`, `沉浸模式`,
`Original Sound`, `DLNA (beta)`) → card `音频信息` (FLAC format stream / 2 Channels · 96000 Hz · 3375 kbps)
→ card `出自专辑` (cover 48 dp + album + artist) → card `参与创作的艺术家` (circular avatar + name + "2 首");
transport deck stays pinned at the bottom.

Also **[REL]**: 沉浸模式 (hides status bar, keeps cover + lyrics + basic transport; long-press play to toggle),
播放界面主题 (per-player theme incl. "跟随用户界面"), 播放界面歌词大小 up to 48 sp, 播放界面迷你歌词 show/hide,
song-title crossfade animation (8.3.0), and a rotating-disc/vinyl player theme existed historically (7.4.0) —
**current default is the square cover**.

### 5.5 Lyrics **[VIS D-07 portrait; D-08 landscape]**
- Full-screen over the blurred cover; **same header** as the player (title/subtitle + audio-info glyph).
- Lines **left-aligned** (not centred) by default — the app explicitly recommends "歌词居左" for its 3D
  lyric effect. Current line 100 % white + Bold (optional bold toggle, default **off**), neighbours
  ~35–55 % and slightly scaled; line spacing ~1.4–1.5; active line stays near **vertical centre**, with
  a large empty top area when few lines exist.
- **Karaoke**: per-character progressive highlight exists ("兼容性卡拉OK歌词" 10.6, "'真'逐字歌词" SPL/LRC
  10.7) — implemented as a **gradient/wipe fill over the glyphs**, not a background pill.
- **Translation** line under the original at ~0.7 size, ~60 % opacity, same highlight logic (issue #1993).
- Extras: **歌词辉光 (glow)** and **立体歌词 (3D)** in Labs, **交错滚动 (staggered scroll, default on)**,
  **blur-on-scroll** toggle, optional lyric **time labels**, ability to **hide the control panel** (PRO),
  and a **bottom-left source badge** (`词 EMBEDDED` in a small rounded chip) + **bottom-right "文A"
  translation toggle**.
- A separate **lyrics settings page** holds size (14/22/30/34, up to 48), bold, blur, centering, glow.
- Landscape (D-08): cover panel on the left, lyric column on the right; the cover art is **red-tinted**
  by the same wash; the control deck collapses into two icon rows at the bottom-left.

### 5.6 Settings **[VIS D-09, D-10, D-11, D-12, extra D-16]**
- Root: top bar `← 设置` (back chevron + 20 sp title, no background change — the bar is transparent over
  the page).
- Grouped cards (`RoundedColumn`): 16 dp radius, `subBackground`, hairline border, 16 dp inner H padding,
  rows ≥56 dp, separated by **nothing** (no dividers) but grouped by white space.
- Row: `[24 dp icon] [title 16 sp / subtitle 12 sp subText] [chevron › or M3 switch]`.
  **Icons are multicoloured and filled** (crown gold, blue audio, red palette, green lyrics, purple
  translate, yellow help, blue flask) — a signature, see §9.
- Card groups are introduced by a **small accent-coloured label** (`用户界面`, `播放界面`, `歌词`, `语言`…).
- First row is a **Pro banner**: crown + "椒盐音乐 Pro" + accent "已启用" + chevron.
- Appearance page (D-10): a 3-across **theme selector** built as tall rounded cards with an icon + label
  (`跟随系统`/`浅色`/`深色`), selected = accent border + accent content; then switches
  (`沉浸模式`, `隐藏状态栏`, `在列表中显示歌曲文件名`, `在列表中显示添加下一首播放`) each with a
  2–3 line explanatory **subtitle**; then a section of **"播放界面主题" preview cards** (icon + name,
  horizontal row).
- The **mini player is present on settings pages** (it is a global bottom overlay), sitting above the nav
  insets; nav is hidden here.
- Lab page (D-16): same list mechanics, but when a wallpaper is set the rows become **glass panels over the
  wallpaper** (blur + 4 % white) — the "Salt UI Material" experiment. Warning lines use accent-orange
  `⚠` text at 12 sp.

### 5.7 Search **[VIS D-03]**
- Pill search field at top (48 dp, `subBackground`, magnifier left, text 16 sp, `✕` clear button at right,
  text button `取消` outside the field).
- Results toolbar: `shuffle + count` left; `A/Z` and `list-layout` right.
- Rows identical to the library; **filename** shown with extension and the metadata line shows
  `HQ artist · album`; index rail on the right; empty result = just the empty list area (no illustration).

### 5.8 Menus, sheets, dialogs **[VIS D-11, D-12, D-15, D-02]**
- **Song context menu = a bottom sheet** (not a popup): one rounded card containing 56 dp rows, each an
  icon (24 dp, **its own colour**) + label; destructive items at the end ("永久删除" in red), grouped by
  blank 8 dp gaps. A small "current track" header card (cover + title + `HQ` + artist·album) sits above
  the list; a "search in list" pill appears above when the list is long (D-11 top).
- **Sort sheet** (D-15): title `排序` 20 sp bold, then a card with a 2-column grid of **plain text buttons**
  (no icons, 44–48 dp tall), selected row gets a **rounded pill highlight** (`highlight @ 10 %`, radius 12)
  — then a second card with three switches.
- **Sleep Timer sheet** (D-12): 28 dp top corners, title 20 sp bold, inner card (`surface` + 16 dp radius)
  with `Duration` + value, a **white M3 slider**; then a **full-width pill primary button** (white fill,
  black label, 52–56 dp); then an explanatory card with `subText` 13 sp body; bottom fine print 12 sp
  60 % opacity.
- **Popup menu anchored to content** (playlist overflow, D-02): 20–24 dp radius, dark glass bg, 48 dp rows,
  outline icons, first item highlighted with a **rounded pill** (`white @ 10 %`).
- **Dialogs**: 20 dp radius, title 20 sp semibold, body 16 sp/1.5, buttons as text buttons in accent,
  primary action may be a pill.

### 5.9 Empty states / default artwork **[VIS D-01, D-03]**
- Missing album art = **light-grey rounded square with a single small white circle/disc glyph**
  (visible in the playlist cover of "单车" and in the mini player of D-09). No text empty-state
  illustrations anywhere; empty lists are just empty with the toolbar still present. **[INF]** for
  search-with-no-results.

### 5.10 FAB
- **No FAB on the library / player / settings.** Two exceptions: a floating circular **"jump to current"**
  button above the mini player on long lists, and a rounded **"+" pill/round FAB** on the lyric-timestamp
  editor screen.

---

## 6. Motion & feedback

| Interaction | Behaviour | Source |
|---|---|---|
| Press feedback | **Custom `AlphaIndication`** — the pressed content simply draws at reduced alpha (a dim), *no M3 ripple circle*; hover/focus also alpha-only | [SRC] `AlphaIndication.kt`, `SaltConfigs.indication` |
| Mini player → player | **Shared-element cover morph** + "expand/collapse" animation with shadow animation; 12.2.0 rewrote it, 12.3.0 added the mini-bar→cover transition, 12.3.2 fixed its shadow | [REL] |
| Mini player press | Liquid-glass **elastic squash/stretch** (12.4.0) | [REL] |
| Track change | Cover crossfade (7.0.3); album/artist image "transition animation" (issue #2202) | [REL] |
| Song title change | Crossfade of title block (8.3.0) | [REL] |
| Play/pause | Icon morph/fade, no scale bounce | [REL] 7.4.0 |
| Lyrics scroll | **Staggered (交错) translate + scale + opacity**, default **on**; karaoke wipe per character; optional glow; jump-to-line animation; blur-on-scroll | [REL] 8.16.4, 10.6, 12.4 |
| Mini player text | **Marquee** for long titles/lyrics | [REL] 7.13 |
| A–Z index | Vibration on letter change | [REL] 8.12.1 |
| Lists | Reorder/queue-removal animations (`animateItem`), sorting animation | [REL] 10.5, 7.0.0-alpha08 |
| Progress | 2 dp line; thumb appears only during drag; **no wave** | [VIS] |
| Page transitions | Standard slide/fade between push screens (`AnimatedContent`), no shared-axis hero except the cover | [REL] 8.15.1 / 10.2.8 |

---

## 7. Spacing of the "player as art" — implementation sketch

```kotlin
@Composable fun NowPlaying(...) {
  Box(Modifier.fillMaxSize()) {
    BlurredCoverWash(art)                       // blur(90.dp) + vertical scrim
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp)) {
      TrackHeader(title, artist)                // 22sp bold / 12sp 70%
      Spacer(Modifier.weight(1f))
      Cover(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
      MiniLyrics(lines, max = 4)                // 16sp, white 55%
      Spacer(Modifier.height(24.dp))
      AudioSpecLine("AudioTrack  FLAC 16 bits  830 kbps")
      SaltProgressLine(progress, onSeek)        // 2.dp track + times *below*
      TransportRow()                            // 40.dp bare glyphs
      SecondaryRow()                            // loop/timer/audio/queue/more
    }
  }
}
```

---

## 8. Iconography

- **Two vocabularies in one app:**
  1. **Monochrome glyphs** for navigation & controls — mostly **filled** solid shapes (play triangle,
     pause bars, skip triangles, hamburger with 3 thick bars, `⋮`, `…`), and **thin 2 dp outline** glyphs
     for the secondary control row (loop, stopwatch, waveform, list, search magnifier, chevrons).
     Sizes: 24 dp standard, 28–40 dp transport.
  2. **Colour-filled icons** for *semantic* rows — settings groups and song menus give each action its own
     hue (gold crown, blue speaker/queue, red palette, green quotation marks, purple 文A, yellow question,
     blue flask, red trash, gray info). This polychrome menu is one of the most recognisable traits.
- SaltUI ships only a handful of tiny assets (`ic_item_arrow`, `ic_item_expand_arrow`, `ic_item_link`,
  `ic_arrow_drop_down`, eye/error/warning) **[SRC]** — the rest of the app icon set is app-private.
- Column right-edge affordances: chevron `›` for navigation, M3 switch for booleans, nothing for actions.
- The player header "audio info" glyph is a distinctive custom mark (two arcs around a dot / small waveform).

---

## 9. The 10 rules that make it look like 椒盐音乐

1. **Page `#F3F3F3`, content inside 16 dp cards of 50 % white + 1 hairline border, zero shadows.**
2. **One accent only** (Monet `primary`, fallback `#0470E6`), used for section labels, switches, selection
   borders and — uniquely — **the currently playing row's entire text**.
3. **Player & lyrics ignore the palette**: heavy-blur album-art wash + white text + 2 dp progress line with
   times *underneath* it.
4. **Rounded-square album art everywhere** (8 dp lists / 12 dp player); circles reserved for artists.
5. **One floating bottom dock**: icon-only 4-tab nav (grid / chart / search / hamburger) **merged with the
   capsule mini player and a 2 dp progress line**, ~32 dp radius, floating above the gesture bar.
6. **Two-row bare-glyph control deck** — big transport row (no circles) + 5 evenly spread secondary icons.
7. **Lyrics left-aligned, current line 100 % + bold, others 35–55 %**, translation subline, `词 EMBEDDED`
   source chip bottom-left.
8. **12/16/24 sp typography** with weight & opacity doing the hierarchy; one condensed metadata line per row.
9. **Alpha-dim press feedback instead of ripple**; shared-element cover morph from mini player.
10. **Polychrome filled icons inside settings/menus**, monochrome glyphs everywhere else.

---

## 10. Gaps, uncertainties, and what is *not* verified

- **F-Droid: no listing.** `https://f-droid.org/en/packages/com.salt.music/` returns **HTTP 404** — the app
  is not distributed on F-Droid. No F-Droid screenshots were used.
- **Google Play** page is scrapeable but exposes only **6** screenshots (all used: D-04, D-05, D-08, plus
  AUDIOFX/settings/USB-exclusive which were less relevant). The Play HTML does not contain a larger set;
  `w1080` size requests return full-res JPEGs (used).
- **CoolAPK** (`coolapk.com/apk/284064`, and the `/?id=` variants) returned **404**; APKPure returned
  **403**; APKMirror **403**; Tencent MyApp returned screenshots of an *unrelated* app (downloaded, identified
  as a different game, and deleted — not used); Huawei AppGallery and WeChat articles require JS.
- Therefore the **home/library-with-bottom-nav shot is a user screenshot from a GitHub issue** (D-02,
  issue #2216), not an official store asset. It is genuine app UI but from a user's device (dark wallpaper,
  custom layout), and the bottom-nav destination *names* are inferred from icon shapes, not read from a label.
- **Empty states, the album grid page, the playlist grid page and the About page have no clean screenshots**;
  descriptions there are extrapolated from adjacent screens and release notes (flagged [INF]).
- **Exact pixel metrics** (dp) are derived by measuring 1216/1280/1440-px-wide phone screenshots against an
  assumed 411 dp width (usually correct: 1080×2400 ≈ 411×914 dp). Treat ±2 dp tolerance.
- SaltUI is a *separate* library that Salt Player "adapts to" (12.5.0 "适配最新 SaltUI 组件样式"); the app's
  private screens may drift from the published tokens. Tokens here are SaltUI `main` branch as of fetch time.
- The Windows companion (SPW) shares the language but uses emoji icons, a vinyl disc and floating pills —
  **do not copy those into the Android port** (extra/13, extra/16 are Windows/desktop only).

## 11. Screenshot index

Primary set (12) in `.ai/recon/saltplayer/`:

| File | Shows |
|---|---|
| `01-library-song-list-light.jpg` | Songs list, light: hamburger + title + search bar; shuffle+count / A-Z sort / layout toolbar; 48 dp rounded covers, one-line metadata with **SQ** chip, trailing `+` and `⋮`; **playing row in accent blue**; A–Z index rail; floating "current" button; mini player. 1216×2688 |
| `02-playlist-detail-popup-bottomnav.jpg` | Playlist detail + anchored dark **popup menu** (rounded, icon rows, first row pill-highlighted) + **mini player + 4-icon bottom nav** (grid / chart / search / hamburger). 1216×2688 |
| `03-search-screen-light.jpg` | Search: pill field with `✕` + `取消`, results toolbar, filename rows with `HQ` chip, index rail, floating locate button, mini player. 1280×2772 |
| `04-now-playing-light.jpg` | Player, pale cover wash: title/subtitle, square cover r12, mini lyrics under cover, `AudioTrack FLAC 16 bits 830 kbps`, 2 dp line + times below, bare transport row, 5 secondary glyphs. 1080×2400 |
| `05-now-playing-amber-tint.jpg` | Same player layout proving **tint follows artwork** (deep amber wash). 1080×2400 |
| `06-now-playing-info-panel.jpg` | Player info panel: quick-action chips + glass cards (`音频信息`, `出自专辑`, `参与创作的艺术家`), transport pinned. 1440×3200 |
| `07-lyrics-portrait-dark.jpg` | Lyrics portrait: blurred art bg, huge left-aligned active line, dim neighbours, `词 EMBEDDED` chip, `文A` toggle. 1440×3216 |
| `08-lyrics-landscape-dark.jpg` | Lyrics landscape/tablet: cover left, lyric column right, crimson tint. 1080×675 |
| `09-settings-root.png` | Settings root: grouped cards, coloured leading icons, title+subtitle+chevron, Pro banner, mini player. 480×1067 |
| `10-settings-appearance.png` | Appearance: 3-across theme cards (follow/light/dark, selected = accent border), switches with explanatory subtitles, "playback theme" preview cards, mini player. 480×1067 |
| `11-dark-song-menu-sheet.jpg` | Dark bottom sheet: song header card, pill search, 56 dp menu rows with **per-action coloured icons**, destructive item red. 1264×2800 |
| `12-dark-sheet-sleep-timer.png` | Dark sheet: 28 dp corners, inner card + white slider, white pill primary button, body-text card. 1080×2400 |

Supplementary (`.ai/recon/saltplayer/extra/`): `13-landscape-player.jpg` (landscape two-column player),
`14-artist-detail.jpg` (artist header + rows + mini player), `15-sort-sheet-dynamic-color.jpg`
(**green Monet tint**: pale-green sheet, dark-green switches, pill-selected rows),
`16-lab-glass-over-wallpaper.png` (glass rows over user wallpaper — the "Salt UI Material" look),
`17-audiofx-dark.png` (AudioFX page, black + spectrum, branding style "SALT PLAYER V4 / AUDIOFX").

## 12. Source links

- Salt Player repo/README: <https://github.com/Moriafly/SaltPlayerSource>
- SaltUI (design system source): <https://github.com/Moriafly/SaltUI>
- SaltUI docs: <https://moriafly.com/program/salt-ui>
- Google Play listing: <https://play.google.com/store/apps/details?id=com.salt.music>
- Release notes used: <https://github.com/Moriafly/SaltPlayerSource/releases>
- F-Droid (no listing): <https://f-droid.org/en/packages/com.salt.music/> → 404
