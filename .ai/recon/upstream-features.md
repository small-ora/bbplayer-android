# BBPlayer — upstream feature & behavior recon (implementation inventory for a native Kotlin/Compose rewrite)

Recon of the cloned upstream at `/tmp/bbplayer-upstream` (read-only). All paths below are **repo-relative**.
Stack: Expo SDK + React Native, expo-router (file routes), Drizzle ORM + `expo-sqlite`, Zustand + Legend-State,
TanStack Query, React Native Paper (Material 3, dynamic color), Media3/ExoPlayer in a native Kotlin module
(`packages/orpheus`), lyric parsing lib (`packages/splash`), heatmap (`packages/heatmap`), palette extraction
(`packages/image-theme-colors`), a Kotlin Compose wave slider (`packages/expo-wavy-slider`), native misc (`packages/native`).

This document is written to be *portable*: each conclusion cites the file (and line where useful) that proves it.
Where the JS layer is only a shell over native Kotlin, the Kotlin file is cited instead — those are the files a Kotlin
team should read first.

Legend: `M` = app module (`apps/mobile`), `O` = `packages/orpheus`, `S` = `packages/splash`.

---

# 1. Screen & navigation inventory

## 1.1 Navigation skeleton

| Layer | File | Notes |
|---|---|---|
| Root stack (expo-router `Stack`) | `apps/mobile/src/app/_layout.tsx:261-421` | Every route registered explicitly, `headerShown:false` everywhere; page chrome is per-screen `Appbar`. |
| Player route animation | `M/src/app/_layout.tsx:314-320` | `animation: 'slide_from_bottom'` — player is a bottom-sheet-like full page, not a tab. |
| Modal host route | `M/src/app/_layout.tsx:392-400` | `modal` is `presentation:'transparentModal'`, `animation:'fade'`, `gestureEnabled:false`; it is *one* route that mounts a stack of app modals (see 1.4). |
| Bottom tabs | `M/src/app/(tabs)/_layout.tsx:114-181` | `expo-router/native-tabs` → real Android `BottomNavigationView` (Material3). 3 tabs only. |
| Tab labels/icons | `M/src/app/(tabs)/_layout.tsx:132-179` | 搜索 (magnify), 音乐库 (bookshelf), 我的 (account). Icons can be replaced by Bilibili theme artwork. |
| Rail (tablet) | `M/src/components/navigation/NavigationRail.tsx` | Hand-written rail drawn over the root stack when width ≥600 dp (Android only), because `native-tabs` has no rail. |
| Rail width / full-screen exclusions | `M/src/components/navigation/NavigationRail.tsx:19-24,39-47` | `FULLSCREEN_ROUTES = {player,onboarding,test,performance}`; rail = 96 dp (`NAVIGATION_RAIL_WIDTH`). |
| Tab hiding under rail | `M/src/app/(tabs)/_layout.tsx:100-101,116` | `showRail → <NativeTabs hidden>`. |
| Responsive breakpoints | `M/src/hooks/ui/useResponsiveLayout.ts:13-38` | medium 600, expanded 840, twoPane needs ≥840×600, compact <360×600; MAX_WIDTH content 960 / search 720 / dialog 560 / player 600. |
| Responsive page wrapper | `M/src/components/layout/ResponsiveScreen.tsx`, `ResponsiveContent.tsx` | Measures content area (minus insets/rail) and provides it through `ResponsiveLayoutContext` (`useResponsiveLayout.ts:65-95`). |
| Native tab page wrapper | `M/src/components/NativeTabPage.tsx` | Applies tab-bar height/inset handling to each tab screen. |
| Deep links / start screen | `M/src/app/+native-intent.ts:6-88` | `expo-sharing` → `/(tabs)`; `notification.click` → `/player`; `bbplayer://…`, `https://bbplayer.roitium.com/app/link-to/<path>` map through; first launch can start on `/library/0` if `startup_screen='library'`. |
| Header foreground color (theme) | `M/src/hooks/theme/useSkinForegroundColor.ts` | Tab titles on the search/library/my pages use the skin foreground color instead of `onSurface`. |

## 1.2 Tab 1 — 搜索 (Search home)

File: `M/src/app/(tabs)/index.tsx`.

| Behavior | Evidence |
|---|---|
| Large title "搜索" + pill `Searchbar` (`mode='bar'`, radius 9999) with placeholder `关键词 / b23.tv / 视频链接 / av / BV` | `:288-316` |
| Omni search: BV / AV / b23.tv short link / space URL / fav-folder URL / collection / series / uploader / keyword | `M/src/utils/search.ts:46-272` (see §2.2) |
| Search history: MMKV key `bilibili_search_history`, max 10, case-insensitive de-dup, newest first, id `history_<ts>` | `:44-45`, `:112-159` |
| Search suggestions overlay (debounced, absolutely positioned over history) | `:318-350`, `M/src/features/search/SearchSuggestions.tsx` |
| History list with delete-one / clear-all alerts | `:208-230`, `:191-206` |
| Android back / keyboard hide dismisses the focused search panel | `:75-99` |
| Receives Android/OS "share text" payloads (`expo-sharing` `useIncomingShare`) and runs them as a query | `:103-105`, `:232-262` |
| Bottom padding 86 dp when something is playing (mini-player clearance) | `:101` |
| Bilibili theme "appbar background" behind the title (`SkinAppbarBackground`, height top+100) | `:269` |
| Wide screens: title/searchbar/content clamped to `MAX_WIDTH.search = 720` | `:357-381` |

## 1.3 Tab 2 — 音乐库 (Library)

File: `M/src/app/(tabs)/library/[tab].tsx`.

| Sub-tab | Key | Content | Component |
|---|---|---|---|
| 播放列表 | `local` | Local playlists grid/list | `M/src/features/library/local/LocalPlaylistList.tsx` |
| 收藏夹 | `favorite` | Bilibili fav folders (created + collected) | `M/src/features/library/favorite/FavoriteFolderList.tsx` |
| 合集 | `collection` | Bilibili "合集" (season) the user follows | `M/src/features/library/collection/CollectionList.tsx` |
| 分 p | `multiPage` | Videos with multiple pages that the user has touched | `M/src/features/library/multipage/MultiPageVideosList.tsx` |

- `TabView` (react-native-tab-view) with icons `list-box`, `star-box-multiple`, `folder`, `folder-play` (`:24-32`, `:114-152`).
- Route param `/library/<index>` selects the sub-tab; returning to the tab keeps the last index (`:63-73`; rail remembers it at `NavigationRail.tsx:109-113`).
- Grid on tablets: `useLibraryGrid()` → 1 column on phones, up to 6 columns on ≥600 dp, cover size computed to fill the card (`M/src/features/library/shared/useLibraryGrid.ts:6-21`).
- Tab titles use skin foreground color (`:52`, `:96-101`).

## 1.4 Tab 3 — 我的 (Profile / stats)

File: `M/src/app/(tabs)/my.tsx`.

| Block | Behavior |
|---|---|
| Header "我的" + star (GitHub prompt unless dismissed) + settings gear | `:100-121` |
| Bilibili account row → avatar (with theme avatar frame overlay), name, "Bilibili 账号 / 登录 Bilibili" | `:126-186`; avatar frame `useSkinStore.avatarFrameIndex`/`activeAvatarFrameIndex` |
| BBPlayer account row (shared playlists / JWT) | `:188-200` |
| Content list: 歌单同步失败 (badge when `playlist_sync_queue.status='failed'`), 最近常听 → `/playlist/recently`, 我的关注 → `/following`, 下载 → `/downloaded`, 稍后再看 → `/playlist/remote/toview`, 那月今日 → `/history/<today-1month>` | `:224-310` |
| Listening record section: total hours + completed play count + weekly heatmap, link to `/history/overall` | `M/src/features/profile/ListeningRecordSection.tsx:21-140` |
| Two-pane (≥840×600): accounts column 300 dp on the left, stats on the right | `:123-137` |

## 1.5 Player page

File: `M/src/app/player.tsx` (route `/player`).

- Two modes by layout:
  - **Phone**: `PagerView` with 2 pages — main (cover/controls) and lyrics/subtitles (`:517-538`); page index also drives header crossfade.
  - **Two-pane** (`isTwoPane`): left `42%` playback pane, right lyrics pane with dividing hairline; comments side panel overlays the right pane (`:499-515`, `:564-578`).
- Background: Skia gradient from extracted cover palette (`packages/image-theme-colors`) with 400 ms tween, or `FluidBackground` shader when `playerBackgroundStyle='fluid'` (`:281-313`, `:406-446`). `pureWhiteBackground` disables all of it.
- Palette LRU cache of 8 covers (`:86-114`).
- Desktop-lyrics suppression while the in-app lyrics page is visible/foreground (`:181-201`) so overlay and page do not duplicate.
- Hardware back ladder: menu → chapters sheet → queue sheet → comments reply → comments → lyrics page → dismiss (`:357-392`).
- Podcast mode forces pager back to page 0 and swaps lyrics page for subtitle reader (`:258-264`, `:484-497`).

## 1.6 Route-by-route table

| Route | Purpose / what it shows | Evidence |
|---|---|---|
| `(tabs)/index` | Search home (§1.2) | `M/src/app/(tabs)/index.tsx` |
| `(tabs)/library/[tab]` | Library 4 sub-tabs (§1.3) | `M/src/app/(tabs)/library/[tab].tsx` |
| `(tabs)/my` | Profile + stats (§1.4) | `M/src/app/(tabs)/my.tsx` |
| `player` | Player (§1.5) | `M/src/app/player.tsx` |
| `onboarding` | 3-slide onboarding (welcome / disclaimer / start) ending in QR or phone login or guest | `M/src/app/onboarding.tsx:18-130` |
| `modal` | Host for the modal stack (§1.7) | `M/src/app/modal.tsx` |
| `playlist/remote/favorite/[id]` | Bilibili fav folder contents; play all, batch select, remove from folder | `M/src/app/playlist/remote/favorite/[id].tsx` |
| `playlist/remote/collection/[id]` | Bilibili season/合集 contents | `M/src/app/playlist/remote/collection/[id].tsx` |
| `playlist/remote/series/[id]` | Bilibili "系列" (series) contents (paged) | `M/src/app/playlist/remote/series/[id].tsx` |
| `playlist/remote/multipage/[bvid]` | Video details + page (分 P) list; the 1-item case is effectively "play this video" | `M/src/app/playlist/remote/multipage/[bvid].tsx` |
| `playlist/remote/uploader/[mid]` | UP 主 profile + uploads (searchable), follow/unfollow | `M/src/app/playlist/remote/uploader/[mid].tsx` |
| `playlist/remote/toview` | Bilibili 稍后再看, with progress ring per item | `M/src/app/playlist/remote/toview.tsx`, `features/playlist/remote/toview/components/ProgressRing.tsx` |
| `playlist/remote/search-result/global/[query]` | Global search results: video list + horizontal "相关 UP 主" header | `M/src/app/playlist/remote/search-result/global/[query].tsx:126-180`, `features/playlist/remote/search-result/components/SearchUserHeader.tsx` |
| `playlist/remote/search-result/fav/[query]` | Search inside one fav folder or globally inside favs | `M/src/app/playlist/remote/search-result/fav/[query].tsx` |
| `playlist/local/[id]` | Local playlist detail: header, track list, per-track menu, sort (drag), select-mode, batch add/remove, sync-to-Bilibili, share, pin, player-preference | `M/src/app/playlist/local/[id].tsx:1027-1160` |
| `playlist/recently` | "最近常听": top 10 tracks of the last 14 days | `M/src/app/playlist/recently/index.tsx:46-140` |
| `playlist/external-sync` | Netease/QQ playlist import + match review + save as local playlist | `M/src/app/playlist/external-sync.tsx` |
| `downloaded` | Completed downloads: search, select mode, play, play-next, export to folder, delete | `M/src/app/downloaded.tsx` |
| `download` | Download **tasks** (queued/downloading/failed/stopped) with retry | `M/src/app/download.tsx:16-40` |
| `following` | Followed UPs by group (chips) with in-list search | `M/src/app/following.tsx:24-60` |
| `history/overall` | All-time stats: total listening duration (NumberFlow) + per-track play counts (paged) | `M/src/app/history/overall.tsx:84-200` |
| `history/[date]` | One day's history aggregated by track + day total | `M/src/app/history/[date].tsx:52-90` |
| `comments/[bvid]` | Bilibili comment list, tap reply → replies page | `M/src/app/comments/[bvid].tsx` |
| `comments/reply` | Replies (楼中楼) of a root comment | `M/src/app/comments/reply.tsx` |
| `share/playlist` | Import a shared playlist from the BBPlayer backend (link/QR/token) | `M/src/app/share/playlist.tsx` |
| `settings` (+`_layout`) | Settings hub; master–detail split at ≥840 | `M/src/app/settings/index.tsx`, `_layout.tsx:10-38` |
| `settings/general` | Start screen, data sharing, expand multi-page on sync, update check, share log, debug log, dev/perf pages | `M/src/app/settings/general.tsx:120-220` |
| `settings/playback` | Default player mode, loudness normalization, simultaneous playback, resume on start, autoplay | `M/src/app/settings/playback.tsx:75-157` |
| `settings/lyrics` | Verbatim lyrics, old-school style, desktop lyrics (+lock), car lyrics, status-bar provider, lyric source | `M/src/app/settings/lyrics.tsx:267-462` |
| `settings/appearance` (index) | Pure white, spectrum visualizer, mini-player style, player background style, theme section | `M/src/app/settings/appearance/index.tsx:126-187` |
| `settings/appearance/theme-search` | Search Bilibili 装扮/收藏集 skins and install | `M/src/app/settings/appearance/theme-search.tsx` |
| `settings/cdn` | Preferred Bilibili CDN + node speed test | `M/src/app/settings/cdn.tsx:32-150` |
| `settings/storage` | Storage usage chart, clear caches, download parallelism | `M/src/app/settings/storage.tsx:34-200` |
| `settings/backup` | Local zip export/import + WebDAV config/upload/list/restore | `M/src/app/settings/backup.tsx` |
| `settings/app-data` | Browse the app-private directory (files) | `M/src/app/settings/app-data.tsx` |
| `settings/account` | BBPlayer account (shared playlists) profile/login | `M/src/app/settings/account.tsx:283-360` |
| `settings/bilibili-account` (index) | Logged-in profile, avatar frame, send-play-history toggle, logout | `M/src/app/settings/bilibili-account/index.tsx:80-200` |
| `settings/bilibili-account/qrcode-login` | QR login (generate + poll) | `M/src/app/settings/bilibili-account/qrcode-login.tsx:89-180` |
| `settings/bilibili-account/phone-login` | Phone + SMS + Geetest steps | `M/src/app/settings/bilibili-account/phone-login.tsx:19-106`, `_components/*` |
| `settings/about` / `settings/donate` | About/version/licenses; donation QR | `M/src/app/settings/about.tsx`, `donate.tsx` |
| `performance` | Dev perf metrics (startup, shake profiling) | `M/src/app/performance.tsx` |
| `test` | Internal test/developer screen | `M/src/app/test.tsx` |
| `+not-found` | 404 | `M/src/app/+not-found.tsx` |

## 1.7 Modal & bottom-sheet inventory

All in-app dialogs/sheets go through a **modal registry** keyed by `ModalKey`; the `modal` route renders a `Portal`
stack, dimming each layer, with z-index `1000 + idx*100` and back-press popping the top layer
(`M/src/app/modal.tsx:47-90`, `M/src/components/ModalRegistry.tsx:104-140`, `M/src/types/navigation.ts`).

| Key | File | Content |
|---|---|---|
| `Alert` | `components/modals/AlertModal.tsx` | Promise-style alert used everywhere |
| `PlaybackSpeed` | `.../player/PlaybackSpeedModal.tsx` | 0.1–3.0 step 0.1 slider (`@expo/ui` Compose slider), reset |
| `SleepTimer` | `.../player/SleepTimerModal.tsx` | 15/30/45/60 min presets, custom minutes, live "remaining", cancel |
| `EditLyrics` | `.../lyrics/EditLyrics.tsx` | Raw LRC editor |
| `ManualSearchLyrics` | `.../lyrics/ManualSearchLyrics.tsx` | Search Netease/QQ/Kugou and pick a result |
| `LyricsSelection` | `.../player/LyricsSelectionModal.tsx` | Build shareable lyric card (font/color/align) and share |
| `SongShare` | `.../player/SongShareModal.tsx` | Shareable song card |
| `AddVideoToBilibiliFavorite` | `.../bilibili/AddVideoToBilibiliFavModal.tsx` | Multi-select fav folders for one video |
| `CookieLogin` | `.../login/CookieLoginModal.tsx` | Paste cookie |
| `CreatePlaylist` | `.../playlist/CreatePlaylistModal.tsx` | New local playlist |
| `DuplicateLocalPlaylist` | `.../playlist/DuplicateLocalPlaylistModal.tsx` | Copy playlist |
| `MergePlaylists` | `.../playlist/MergePlaylistsModal.tsx` | Merge local playlists |
| `UpdateTrackLocalPlaylists` | `.../playlist/UpdateTrackLocalPlaylistsModal.tsx` | Toggle a track's memberships |
| `BatchAddTracksToLocalPlaylist` | `.../playlist/BatchAddTracksToLocalPlaylist.tsx` | Batch add |
| `SaveQueueToPlaylist` | `.../playlist/SaveQueueToPlaylistModal.tsx` | Save current queue |
| `SyncLocalToBilibili` | `.../playlist/SyncLocalToBilibiliModal.tsx` | Target fav folder, create folder, expand-multi-page |
| `SyncOptions` / `PlaylistSyncProgress` / `ManualMatchExternalSync` | `.../playlist/*` | Bilibili sync options/progress; external import manual re-match |
| `InputExternalPlaylistInfo` | `.../playlist/InputExternalPlaylistInfo.tsx` | Paste Netease/QQ link or ID; auto-detects source |
| `EnableSharing` / `SubscribeToSharedPlaylist` | `.../playlist/*` | Shared-playlist invite/subscribe |
| `EditPlaylistMetadata` / `EditTrackMetadata` | `.../edit-metadata/*` | Rename/cover/description; track title/artist/cover |
| `UpdateApp` / `StarPrompt` / `DonationQR` | `.../app/*` | Hot-update prompt, GitHub star prompt, donation |
| `CoverDownloadProgress` / `ExportDownloadsProgress` / `SkinDownloadProgress` | `.../settings/*` | Progress sheets |
| `PlayerQueue` (not in registry — global) | `M/src/components/modals/PlayerQueueModal.tsx` | `TrueSheet`-based queue sheet mounted at root |

## 1.8 Bottom mini-player (`NowPlayingBar`)

File: `M/src/components/NowPlayingBar.tsx`; state in `M/src/hooks/stores/nowPlayingBarStore.ts`; mounted at root
so it survives navigation (`_layout.tsx:426`).

| Behavior | Evidence |
|---|---|
| Hidden on route roots `player, comments, onboarding, settings, performance, test` and on `playlist/external-sync` | `nowPlayingBarStore.ts:4-19` |
| Hide immediately on focus, restore only after the closing transition ends (no flicker while popping) | `_layout.tsx:268-302` |
| Two styles: `float` (48 dp pill, radius 24, elevation 7, 20 dp side margins) and `bottom` (70 dp, top corners 24, docked to the tab bar) | `settings/appearance/index.tsx:46-57`, `NowPlayingBar.tsx:505-538` |
| Docked style reserves the native tab bar height and safe-area bottom; float offsets by `max(tabBarHeight, inset.bottom)+10` | `:147-157`, `:274-285` |
| Left inset = rail width on tablets | `:296-301` |
| Cover 48×48 with 1 dp primary border (radius 12 docked / 24 floating) | `:341-358` |
| Title + artist; while swiping, cross-fades to "上一首 / 下一首" labels + their titles (opacity driven by drag offset / 40) | `:183-193`, `:360-438` |
| Gestures: tap → `/player`; swipe up → `/player`; horizontal swipe past 80 px → prev/next with a context-click haptic at threshold; gesture competition (fling vs pan vs tap) | `:195-267` |
| Controls: play/pause (spinner while `BUFFERING`), queue button (opens `PlayerQueueModal`), both with `Context_Click` haptics | `:105-114`, `:441-469` |
| 2 dp progress bar translated with Reanimated (no reflow) | `:54-103` |
| Background: `colors.elevation.level2`, or `colors.background` with a hairline border in pure-white mode. The store exposes a per-page `backgroundColor` hook, but every current call site passes `undefined` (`hooks/ui/useNowPlayingBar.ts`), so in practice it is always the elevation color | `:142`, `:316-320` |
| Accessibility/TV: separate focusable "打开播放器" pressable only when `Platform.isTV` | `:333-340` |

---

# 2. Core user flows

## 2.1 Cold start

1. `index.js` runs before the router: starts startup profiling, `enableFreeze(true)`, **initializes `playerSideEffects`** and update telemetry headers, then imports `expo-router/entry` (`M/index.js`).
2. Native boot splash is a static splash (`react-native-bootsplash`, colors in `M/boot-splash-colors.json`) that can be replaced by theme artwork/video; `AnimatedBootSplash` crossfades it out once `isReady && migrationsSuccess` (`M/src/app/_layout.tsx:429`, `M/src/components/AnimatedBootSplash.tsx:34-120`).
3. `runAppInit()` (`_layout.tsx:103-153`): read persisted app store, register update prefetch, clean logs >7 days, migrate legacy lyric JSON, init playback-context store, `usePlayerStore.initialize()`, `playerSideEffects.initialize()`, init queue store, overlay-permission check for desktop lyrics, push `downloadMaxParallelTasks`/`allowSimultaneousPlayback`/cookie into native.
4. DB migrations via `useFastMigrations(drizzleDb, migrations)`; on failure show a blocking error screen (`_layout.tsx:155-254`).
5. `reportUpdateLaunch()`, `playlistSyncWorker.recoverStuckRows()` (syncing→pending), and if `first_open` is true → `router.navigate('/onboarding')` (`_layout.tsx:224-238`).
6. Native `OrpheusMusicService.onCreate` restores the saved queue/index/position/repeat/shuffle from MMKV and `prepare()`s; autoplay only if `autoplayOnStartEnabled` (`O/android/.../service/OrpheusMusicService.kt:438-486`).
7. Expo hot update check on app focus (`_layout.tsx:201-222`) and version check (`useCheckUpdate`).

## 2.2 Search (BV / AV / short link / UP / folder / collection / series / keyword)

Single entry: `matchSearchStrategies(raw)` returns a tagged strategy (`M/src/utils/search.ts:29-272`).

| Input | Strategy | Destination |
|---|---|---|
| `b23.tv/...` | resolve via fetch, read `<link rel=canonical>` or final URL, then parse again | `M/src/lib/api/bilibili/api.ts:1286-1349`, `utils/search.ts:162-218` |
| URL with `ctype=21&fid=` | `COLLECTION` | `utils/search.ts:53-65` |
| URL with `ctype=11&fid=`, or bare `fid=` | `FAVORITE` | `:61-73` |
| `space.bilibili.com/<mid>[/lists[/<id>][?type=series]]`, `/space/<mid>` | `UPLOADER`, `COLLECTION` or `SERIES` | `:75-126` |
| any BV (regex `bv[0-9A-Za-z]{10}`) | `BVID` | `:241-248` |
| any `av<digits>` | `BVID` via `av2bv` | `:251-266`, `lib/api/bilibili/utils.ts:180-199` |
| otherwise | keyword `SEARCH` | `:268-271` |

Navigation + history policy: only `SEARCH` (and the three error-fallback cases) return 1, i.e. get written into search
history (`utils/search.ts:280-370`, `(tabs)/index.tsx:161-182`).

Keyword search page: infinite query over `/x/web-interface/wbi/search/type?search_type=video`, dedup by `bvid`,
HTML `<em>` highlight stripped for display but kept as `titleHtml` for bold highlighting
(`M/src/app/playlist/remote/search-result/global/[query].tsx:27-116`;
`M/src/features/playlist/remote/components/PlaylistItem.tsx:56-85`).

## 2.3 Play a video

1. Entry from any list row → `playTrack()` (e.g. `M/src/features/playlist/remote/search-result/hooks/useSearchInteractions.ts`).
2. Row is mapped to a domain `Track`: `uniqueKey = bilibili::<bvid>` or `bilibili::<bvid>::<cid>`, artist = UP, cover = `https:` + `pic`, duration parsed from `mm:ss` (`.../global/[query].tsx:27-53`).
3. `addToQueue({tracks, playNow, clearQueue, startFromKey, playlistId})` converts to `OrpheusTrack` (id = `uniqueKey`, url = internal URI) and submits (`M/src/utils/player.ts:26-61,158-197`).
4. `submitTracks()` decides player **mode** (`music`/`podcast`) from playlist preference or global default, then calls native `addToEnd(tracks, startFromKey, replace)` and optionally `play()` (`M/src/lib/player/playbackSession.ts:125-200`).
5. Native resolution happens lazily at data-source time: `orpheus://bilibili?bvid=…&cid=…&hires=0&dolby=0` is turned into signed CDN URLs (`M/src/utils/player.ts:199-211`; `O/android/.../util/DownloadUtil.kt:129-170`; `.../bilibili/BilibiliRepository.kt:57-138`).
6. `onTrackStarted` headless event → push lyrics to overlays; when the track ends `onTrackFinished` → write `play_history` + optional Bilibili report (`M/src/lib/player/PlayerSideEffects.ts:82-121`, `M/src/utils/player.ts:213-263`).

## 2.4 Login

Three mutually exclusive paths; all end in `useAppStore.setBilibiliCookie` which parses the cookie string, stores a
`Record<string,string>` and forwards the serialized cookie to native (`M/src/hooks/stores/useAppStore.ts:48-192`).

| Path | Flow | Evidence |
|---|---|---|
| QR | `GET https://passport.bilibili.com/x/passport-login/web/qrcode/generate` → render `url` as QR (react-native-qrcode-svg) → poll `/qrcode/poll?qrcode_key=` **every 2 s** (status `86101` waiting / `86090` scanned / `86038` expired / `0` success) → on success parse `Set-Cookie` header into objects → save → cancel + invalidate user/favorite queries → back after 800 ms | `M/src/app/settings/bilibili-account/qrcode-login.tsx:107-180`; API `M/src/lib/api/bilibili/api.ts:1187-1281` |
| Phone SMS | `GET passport .../captcha?source=main_web&t=` → Geetest in a `WebView` (`static.geetest.com/static/js/gt.0.4.9.js`) posts `{validate,seccode,challenge}` back → `POST .../web/sms/send` (cid,tel,source=main_mini_login,token,challenge,validate,seccode) → user enters code → `POST .../web/login/sms` (cid,tel,code,source,captcha_key,keep=1) → `Set-Cookie` | `M/src/app/settings/bilibili-account/phone-login.tsx`, `_components/GeetestVerifyStep.tsx:17-75`, `hooks/auth/usePhoneLogin.ts`, API `api.ts:1549-1787` |
| Manual cookie | Paste whole cookie string into `CookieLoginModal`; parser trims keys/values and warns on invalid chars | `M/src/components/modals/login/CookieLoginModal.tsx`, `useAppStore.ts:48-91` |

Notes: passport calls use an **iOS BiliApp UA** (`api.ts:64-65`) and `credentials:'omit'` so the RN cookie jar never interferes.
Logout clears the cookie, cancels+clears the query cache, and keeps downloads/local data
(`settings/bilibili-account/index.tsx:47-65`).

## 2.5 Favorites (收藏夹)

- Folder list: `GET /x/v3/fav/folder/created/list-all?up_mid=` and collected folders via `GET /x/v3/fav/folder/collected/list?pn&ps=20&up_mid&platform=web` (`api.ts:117-132`, `801-833`).
- Folder contents: `GET /x/v3/fav/resource/list?media_id&pn&ps=40` (+`keyword`,`type=0|1` to search within/all) (`api.ts:679-733`).
- All ids (batch ops): `GET /x/v3/fav/resource/ids?media_id=`, filtered to `type===2` (`api.ts:739-755`).
- Add/remove one video: `POST /x/v3/fav/resource/deal` with `rid=avid,add_media_ids,del_media_ids,type=2,csrf` (`api.ts:982-1006`). Multi-folder toggle UI: `AddVideoToBilibiliFavModal.tsx`.
- Batch remove: `POST /x/v3/fav/resource/batch-del` with `resources=<avid>:2,…`, `media_id`, `platform=web`, `csrf` (`api.ts:779-796`).
- Create folder: `POST /x/v3/fav/folder/add` (`title,intro,privacy,cover,csrf`) (`api.ts:137-165`).
- Which folders contain a video: `created/list-all?up_mid&rid&type=2` (`api.ts:1011-1033`).

## 2.6 Collections (合集) / series / uploader

- 合集 (season) all contents: `GET /x/space/fav/season/list?season_id&ps=20&pn=1` (`api.ts:838-854`).
- 系列 (series): metadata `GET /x/series/series?series_id`, archives `GET /x/series/archives?mid&series_id&ps&pn`, paged to completion with progress callback (`api.ts:856-977`).
- Uploader: profile `GET /x/space/wbi/acc/info?mid` (wbi), uploads `GET /x/space/wbi/arc/search?mid&pn&keyword&ps=30` (wbi, cookie required due to risk control —
  comment at `hooks/queries/bilibili/user.ts:94-96`), follow state `GET /x/relation?fid=`, follow/unfollow `POST /x/relation/modify` (`fid,act=1|2,re_src=11,csrf`) (`api.ts:559-579,656-674,1065-1090`).
- Followings: `GET /x/relation/followings?vmid&pn&ps=50`, search `…/followings/search?vmid&name&pn&ps=50`, groups `GET /x/relation/tags`, group members `GET /x/relation/tag?tagid&pn&ps=20` (`api.ts:581-651`).

## 2.7 Local playlists

CRUD lives in `PlaylistService` (`M/src/lib/services/playlistService.ts`): `createPlaylist`, `updatePlaylistMetadata`,
`deletePlaylist`, `addManyTracksToLocalPlaylist`, `batchRemoveTracksFromLocalPlaylist`,
`reorderSingleLocalPlaylistTrack`, `getPlaylistTracks`, `setPlayerPreference` (`:386-882`).

Ordering uses **fractional indexing** strings in `playlist_tracks.sort_key`, with `position` ordinal only for dynamic
playlists (`M/src/lib/db/schema.ts:177-196`, `playlistService.ts:190-330`).

Playlist detail actions (`M/src/app/playlist/local/[id].tsx`): play all / shuffle (via `useLocalPlaylistPlayer`),
select mode with all/invert/batch-delete/batch-add (`:1200-1230`), per-track menu (play next, add to playlist, view
video, view uploader, download, copy cover URL, edit metadata, delete) (`features/playlist/local/hooks/useLocalPlaylistMenu.ts:100-254`),
player preference (inherit/music/podcast), drag sort, clear invalid videos, sync to Bilibili, share/pin/delete
(`[id].tsx:1027-1160`).

## 2.8 Import a playlist from Netease / QQ

1. `InputExternalPlaylistInfo` accepts a link or raw id; `parseExternalPlaylistInfo` recognises `music.163.com` and `*.qq.com` and extracts `id=` (`M/src/lib/utils/playlistUrlParser.ts:1-23`).
2. `fetchExternalPlaylist(id, source)`:
   - Netease: `POST /eapi/v6/playlist/detail` (`s=0,id,n=1000,t=0`) → `playlist.{name,coverImgUrl,description,trackCount,creator}` + tracks `{name, ar[].name, al.name, al.picUrl, dt, tns[0]}` (`M/src/lib/api/netease/api.ts:162-179`, `lib/services/externalPlaylistService.ts:54-100`).
   - QQ: `GET c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg?id&format=json&newsong=1&platform=jqspaframe.json` → `cdlist[0].{dissname,logo,desc,songnum,nickname,songlist[]}` (`lib/api/qqmusic/api.ts:180-206`, `externalPlaylistService.ts:101-140`).
3. Match loop: for each track, wait ≥1200 ms (anti rate-limit), search Bilibili with `skipCookie:true` (to avoid personalised results), query = `"<title> <translatedTitle> - <artists>"`, then first result that is not in blacklisted zones and whose duration is within 20 s (`externalPlaylistService.ts:146-279`).
4. UI shows per-row matched/unmatched, lets the user open `ManualMatchExternalSync` per row, then "save as local playlist" (`M/src/app/playlist/external-sync.tsx:400-520`).

## 2.9 Download → cache → export m4a

- Download: `Orpheus.downloadTrack({id,url,title,artist,artwork,duration})` with the internal `orpheus://` URI (`features/player/components/menu/PlayerMenuContent.tsx:105-140`); Media3 `DownloadManager` persists to `filesDir/media_download` with `NoOpCacheEvictor` (`O/.../manager/DownloadCache.kt:20-28`), max parallel 1/2/3/6 (`M/src/app/settings/storage.tsx:34-92`, `O/.../util/DownloadUtil.kt:26-63`).
- Progress notification via `DownloadNotificationHelper`; JS receives `onDownloadUpdated` and `onCoverDownloadProgress` (`O/src/ExpoOrpheusModule.ts:111-117`); missing covers can be back-filled (`downloadMissingCovers`).
- "Downloaded" screen lists completed tasks; long-press enters select mode; per-item menu: 导出 / 删除 / 下一首播放 (`M/src/app/downloaded.tsx:98-171`).
- Export: choose a destination via SAF (`selectDirectory`, `ACTION_OPEN_DOCUMENT_TREE`) — the app pre-seeds the export sheet with `orpheus://public-music` (public Music dir) (`downloaded.tsx:60`; `O/.../util/DirectoryPickerContract.kt`).
- Export options in the sheet: filename pattern (default `{name}`, variables `{id}{name}{artist}{bvid}{cid}`, sanitized, `.m4a` appended), embed lyrics, convert to LRC, crop cover art (`components/modals/settings/ExportDownloadsProgressModal.tsx:39-78`).
- Native export: copy cached bytes into a temp `.m4a` (m4s and m4a are both ISOBMFF — no transcode), write tags with **jaudiotagger** (`FieldKey`, `ArtworkFactory`), optional LRC file, copy to SAF/public Music, emit `onExportProgress` (`O/.../util/ExportDownloadsHelper.kt:32-140`).
- Cache clear: `Image.clearDiskCache()` + `clearCacheAsync()` (native) + `Orpheus.clearPlaybackCache()` (Media3 LRU only) (`M/src/app/settings/storage.tsx:48-66`).

## 2.10 Lyrics: fetch / search / adjust

- Automatic on track start and on lyrics-page open: cache-first JSON at `document/lyrics/<uniqueKey with :: → -->.json`, else network (`M/src/lib/services/lyricService.ts:209-310`).
- Bilibili-specific: first ask `/x/player/wbi/v2` for `bgm_info.music_title`, strip `《…》`, use that as the search keyword (`lyricService.ts:549-578`).
- Providers run in parallel, first success wins (`Promise.any`), the losers are aborted (`lyricService.ts:89-186`). Source is `settings.lyricSource` (`auto|netease|qqmusic|kugou`, default `netease`).
- Manual search: `ManualSearchLyrics` modal lists per-provider results (`title / artist / duration`) and can fetch a specific id (`lyricService.ts:389-440`).
- Offset: per-track `misc.userOffset` in seconds, nudged ±0.5 s in `LyricsOffsetControl`, tinted live (`adjustedCurrentTime = currentTime - offset`), persisted on close (`features/player/components/lyrics/PlayerLyrics.tsx:160-234`, `LyricsOffsetControl.tsx`).
- Edit raw lyrics: `EditLyrics` modal writes back to the same JSON; "skip" writes `{manualSkip:true}` so auto-fetch stops (`lyricService.ts:316-328`).
- Preload next track's lyrics (`lyricService.ts:750-776`) — note the auto-trigger on `onTrackStarted` is commented out (`M/src/lib/player/PlayerSideEffects.ts:64-68`).

## 2.11 Theme apply (Bilibili skins)

1. Search: `GET /x/garb/v2/mall/home/search?key_word&pn&ps` (`api.ts:1792-1814`) → results tagged `collection` (act_id+lottery_id) or `suit` (item_id) (`M/src/lib/api/bilibili/garb.ts`).
2. Fetch declaration: collection → `/x/vas/dlc_act/asset_bag?act_id&lottery_id` then N+1 `/x/garb/v2/user/suit/benefit?item_id&part=emoji_package` per reward; suit → `/x/garb/v2/mall/suit/detail?item_id` (`M/src/lib/theme/adapter.ts:315-545`).
3. Validate with arktype schema → download every asset → map URLs to local file paths → write `skin.json` with `manifest` + derived `appSkin` (`theme/schema.ts:111-126`, `theme/transformer.ts:186-268`, `theme/SkinManager.ts`, `theme/downloadManager.ts`).
4. Activate: `useSkinStore.activeSkinId` (+ per-asset indices) persisted in MMKV key `skin-storage` (`hooks/stores/useSkinStore.ts:46-163`); `useActiveSkin()` lazily loads and caches `AppSkin` (`hooks/theme/useActiveSkin.ts`).
5. Applied surfaces: tab icons (native tab bar), rail icons, appbar header art, tab-bar background, accent/foreground colors, avatar frame, play/pause + seek thumb art, loading animation, like animation, boot splash poster/video (§7).

## 2.12 Sleep timer

- UI: 15/30/45/60 min presets + custom minutes, shows remaining, cancel (`components/modals/player/SleepTimerModal.tsx:13-136`).
- Native: `SleepTimeController` posts a delayed runnable that **pauses** the player; `getStopTimeMs()` returns wall-clock end so the UI can count down (`O/.../util/SleepTimeController.kt:22-62`).
- There is **no volume fade-out** for the sleep timer (only loudness normalization fades volume in). If the rewrite wants a fade, it is a new behavior.

## 2.13 Stats / heatmap

- Recorded on natural finish/leave: `completed = effectivePlayed ≥ max(floor(duration*0.9), duration-2)` (`M/src/utils/player.ts:213-238`).
- Heatmap: `SELECT date(start_time/1000,'unixepoch','localtime'), count(*) GROUP BY date` (`M/src/hooks/queries/playHistory.ts:17-36`) rendered by `@bbplayer/heatmap` weekly grid (`packages/heatmap/src/components/WeeklyHeatMap*.tsx`).
- Summary card: `count(*)` and `sum(track.duration)` where `completed=1` (`features/profile/ListeningRecordSection.tsx:21-28`).
- Overall page: total duration + paged per-track play counts (`app/history/overall.tsx:91-180`); a day page aggregates by track and sums durations (`app/history/[date].tsx:65-89`).
- "Recent" list: most played in the last 14 days, limit 10 (`app/playlist/recently/index.tsx:46`, `trackService.getMostPlayedTracksInLastDays`).

---

# 3. Data model

## 3.1 SQLite schema (Drizzle, `apps/mobile/src/lib/db/schema.ts`)

DB is `expo-sqlite` + Drizzle; migrations in `apps/mobile/drizzle/0000..0022*.sql`, run through
`useFastMigrations` at startup. **Tables that used to exist and were deliberately removed**:
`search_history` (dropped in `drizzle/0002_groovy_maximus.sql:16`, now MMKV) and `track_downloads`
(dropped in `drizzle/0012_blushing_human_fly.sql:1`, now Media3's own DB).

### `artists` (`schema.ts:12-48`)
| Field | Type | Notes |
|---|---|---|
| id | integer PK autoincrement | |
| name | text NOT NULL | |
| avatarUrl / signature | text | |
| source | text enum `bilibili|local` NOT NULL | |
| remoteId | text | Bilibili `mid` for bilibili artists |
| createdAt / updatedAt | integer timestamp_ms, defaults `unixepoch()*1000`, `updatedAt` `$onUpdate` |

Indices: `unique(source, remoteId) WHERE source!='local'`; `unique(name) WHERE source='local'`;
`index(name)`; CHECK `(source='local' AND remote_id IS NULL) OR (source!='local' AND remote_id IS NOT NULL)`.

### `tracks` (`schema.ts:50-77`)
| Field | Type | Notes |
|---|---|---|
| id | integer PK | |
| uniqueKey | text UNIQUE NOT NULL | `bilibili::<bvid>` or `bilibili::<bvid>::<cid>` or local key |
| title | text NOT NULL | |
| artistId | FK artists ON DELETE SET NULL | |
| coverUrl | text | |
| duration | integer (seconds) | |
| createdAt / updatedAt | timestamp_ms | |
| source | text enum `bilibili|local` | |

Indices: `artistId`, `title`, `source`.

### `bilibili_metadata` (`schema.ts:198-215`) — 1:1 with tracks
`trackId` PK/FK cascade · `bvid` NOT NULL · `cid` nullable · `isMultiPage` boolean NOT NULL ·
`mainTrackTitle` (parent video title for a 分 P) · `videoIsValid` boolean default true (deleted videos in fav folders).
Index `(bvid, cid)`.

### `local_metadata` (`schema.ts:217-222`) — 1:1 with tracks
`trackId` PK/FK cascade · `localPath` NOT NULL (used directly as the playable URI).

### `play_history` (`schema.ts:79-97`)
`id` PK · `trackId` FK cascade · `startTime` integer (ms) · `durationPlayed` integer (seconds) · `completed` boolean ·
`createdAt`. Indices `trackId`, `startTime`.

### `playlists` (`schema.ts:99-150`)
| Field | Notes |
|---|---|
| id | PK |
| playerPreference | enum `inherit|music|podcast`, default `inherit` |
| title | NOT NULL |
| authorId | FK artists ON DELETE SET NULL |
| description, coverUrl | |
| itemCount | integer default 0 (denormalised counter, maintained by service) |
| type | enum `favorite|collection|series|multi_page|local|dynamic` |
| remoteSyncId | integer; when set the playlist is remote-synced (Bilibili fav folder id, season id, series id, or bvid) |
| lastSyncedAt | timestamp_ms |
| shareId / shareRole / lastShareSyncAt | shared-playlist (BBPlayer backend) fields; role `owner|editor|subscriber` |
| isPinned | boolean default false |
| createdAt / updatedAt | |

Indices: `title`, `type`, `authorId`, `shareId`.

### `dynamic_playlist_sources` (`schema.ts:152-175`)
Composite PK `(playlistId, sourcePlaylistId)`, both FK cascade; `position` integer; `createdAt`.
For `type='dynamic'` playlists that concatenate other playlists. Indices `(playlistId)`, `(playlistId, position)`,
`(sourcePlaylistId)`.

### `playlist_tracks` (`schema.ts:177-196`)
Composite PK `(playlistId, trackId)`, both FK cascade; `sortKey` text NOT NULL (**fractional index**);
`createdAt`. Indices `trackId`, `(playlistId, sortKey)`.

### `playlist_sync_queue` (`schema.ts:224-253`)
Outbox for pushing local edits to a Bilibili folder: `id` PK · `playlistId` FK cascade ·
`operation` enum `add_tracks|remove_tracks|reorder_track|update_metadata` · `payload` json · `status` enum
`pending|syncing|done|failed` · `operationAt` (LWW timestamp captured at enqueue) · `createdAt`.
Indices `status`, `playlistId`. Worker: `M/src/lib/workers/PlaylistSyncWorker.ts`.

### Relations (`schema.ts:258-343`)
artists→tracks/authoredPlaylists; tracks→artist/playlistLinks/bilibiliMetadata/localMetadata/playHistory;
playlists→author/trackLinks/dynamicSources/dynamicDependents; dynamic source self-relations with names
`dynamicPlaylist` / `dynamicSourcePlaylist`.

## 3.2 Domain objects → storage map

| Domain object | Where it lives | Evidence |
|---|---|---|
| Track (video / local file) | `tracks` + `bilibili_metadata` or `local_metadata`; `uniqueKey` is the cross-system id | `schema.ts:50-77,198-222` |
| Artist / UP | `artists` (remoteId = mid) | `schema.ts:12-48` |
| Playlist (local / remote mirror / dynamic / shared) | `playlists` (+ `playlist_tracks`, `dynamic_playlist_sources`) | `schema.ts:99-196` |
| Favorite folder / collection / series | mirrored as `playlists` rows with `type` and `remoteSyncId` | `schema.ts:115-125` |
| Play history / stat | `play_history` | `schema.ts:79-97` |
| Search history | **MMKV** `bilibili_search_history` (JSON array, max 10) | `(tabs)/index.tsx:44-45` |
| Lyric cache | file `document/lyrics/<key>.json` (`LyricFileData`), not SQL | `lib/services/lyricService.ts:213-217,347-387` |
| Download | Media3 DownloadManager DB + `filesDir/media_download`; cover files next to it | `O/.../manager/DownloadCache.kt`, `DownloadUtil.kt` |
| Theme / skin | MMKV `skin-storage` (zustand) + `<document>/skins/<id>/skin.json` | `useSkinStore.ts:94-96`, `lib/theme/runtime.ts:72-121` |
| Settings (app) | MMKV `app-storage` (zustand persist) | `useAppStore.ts:125-127`, `utils/mmkv.ts:8-23` |
| Player queue/repeat/shuffle/position | native MMKV `player_queue_store` (+ `config_*` flags) | `O/.../util/GeneralStorage.kt:16-45` |
| Per-track resume (podcast) | native MMKV `track_resume_store` | `O/.../util/TrackResumeStorage.kt:20-31` |
| Loudness (measured_i) per URI | native MMKV `loudness_normalization_store` | `O/.../util/LoudnessStorage.kt:12-17` |
| Playback context (session id + mode) | MMKV `playback-context-store` (Legend-State) | `hooks/stores/playbackContextStore.ts:16-32` |
| Skin boot-splash preload | MMKV `boot_splash_preload` (`video|card`) | `components/AnimatedBootSplash.tsx:63-71` |
| WebDAV credentials | MMKV `webdav_backup_url|_username|_directory` (password in `expo-secure-store`) | `app/settings/backup.tsx:120-140` |

## 3.3 Identity conventions to preserve

- `uniqueKey` strings: `bilibili::<bvid>` (single video), `bilibili::<bvid>::<cid>` (one 分 P)
  (`app/playlist/remote/multipage/[bvid].tsx:46` vs `.../global/[query].tsx:30`). Lyrics file names replace `::` with `--`.
- Playback `Track.id` given to the native player **is** `uniqueKey`, so `play_history`, lyrics cache and the player
  queue all agree (`utils/player.ts:47-54`). Any Kotlin rewrite must keep a single stable id or re-map everything.
- Favourite/collection/series "playlist id" = `remoteSyncId`; `local` playlists use only the DB id.

---

# 4. Playback engine

## 4.1 Layering

```
Compose/RN UI
  └─ @bbplayer/orpheus (TS)  ──NativeModule 'Orpheus'──►  ExpoOrpheusModule.kt (1503 lines)
                                                           ├─ OrpheusMusicService : MediaLibraryService
                                                           │    ├─ ExoPlayer (Media3)
                                                           │    ├─ MediaLibrarySession (MediaSession)
                                                           │    ├─ DownloadManager + 2 SimpleCache
                                                           │    └─ UnifiedLyricsManager (desktop / status bar / car)
                                                           └─ OrpheusDownloadService
```
TS surface: `packages/orpheus/src/ExpoOrpheusModule.ts:165-470` (native interface), hooks in `packages/orpheus/src/hooks/*`.
Native implementation: `packages/orpheus/android/src/main/java/expo/modules/orpheus/ExpoOrpheusModule.kt` (module methods)
and `.../service/OrpheusMusicService.kt` (player/session/queue/persistence).

## 4.2 Media3 service, session, notification

- `class OrpheusMusicService : MediaLibraryService()`; `ExoPlayer` built with `DefaultRenderersFactory`, `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC)`, `handleAudioBecomingNoisy=true` (`OrpheusMusicService.kt:57-59,253-273`).
- `MediaLibrarySession` id `"OrpheusSession"`, session activity = app launch intent with `data=orpheus://player` and `SINGLE_TOP|CLEAR_TOP`, `GlideBitmapLoader` for artwork (`:320-324`, `util/GlideBitmapLoader.kt`).
- Notification is `DefaultMediaNotificationProvider` with **4 custom buttons**: previous, play/pause (icon swaps), next, and a custom "repeat" session command `CMD_TOGGLE_REPEAT_MODE` that cycles OFF→ALL→ONE (`:169-243`, `util/CustomCommands.kt`).
- `onPlaybackResumption` intentionally returns empty items (the app restores its own queue) (`:423-435`).
- `onTaskRemoved`: stops the service only if not playing or queue empty (`:126-132`).
- Foreground start is forced by overriding `onUpdateNotification(..., true)` (`:134-136`).
- Car/Auto: lyrics are written into `MediaMetadata.title/artist` when `isCarLyricsEnabled` (`:775-827`).
- Headless JS task `OrpheusHeadlessTaskService` carries `onTrackStarted / onTrackFinished / onTrackPaused / onTrackResumed / onRequestClearLyrics` to JS even when the app is dead (`:516-579`, `packages/orpheus/src/headless.ts`).

## 4.3 Queue, repeat, shuffle

- Queue = Media3 media items; order = *physical* order; shuffle is **not** a physical reorder (that was O(n²)) — it uses `player.setShuffleOrder(DefaultShuffleOrder(...))` + `shuffleModeEnabled` (`O/.../service/ShuffleManager.kt:34-98`).
- `getQueue()` returns the traversal order when shuffle is on, so the UI list matches what will actually play (`ExpoOrpheusModule.ts:277-282`; `ShuffleManager.getTraversalOrder()` at `:76-98`).
- `playNext(track)` inserts after current and calls `repositionAsNext()` so it is truly next even under shuffle; duplicate items are moved, not duplicated (`ExpoOrpheusModule.ts:300-306`, `ShuffleManager.kt:100-129`).
- `moveTrack` reorders the traversal when shuffle is on (`ShuffleManager.moveInTraversal`, `:131-139`).
- Repeat modes map to Media3 `REPEAT_MODE_OFF/ALL/ONE` (`ExpoOrpheusModule.ts:10-14`); repeat-one wraps skip-next/prev from last/first (`:255-265`).
- `reverseRemainingQueue()` reverses items after the current one (no-op under shuffle).
- Queue changes are persisted to MMKV on every `onTimelineChanged` / transition (`OrpheusMusicService.kt:645-653,716-722`, `GeneralStorage.saveQueue`).

## 4.4 Audio source resolution (the important part for Kotlin)

The player never receives a CDN URL from JS. It receives an internal URI and resolves it at data-source open time:

`orpheus://bilibili?bvid=<BV>&cid=<cid?>&hires=0|1&dolby=0|1&quality=<id?>` — built in `M/src/utils/player.ts:199-211`
(always `hires=0&dolby=0`, no explicit `quality`, so native default 30280 applies).
Local files bypass this and pass the raw `localMetadata.localPath`.

`DownloadUtil.BilibiliResolver` (`O/.../util/DownloadUtil.kt:129-170`) parses the URI and calls
`BilibiliRepository.resolveAudioUrls(bvid, cid, audioQuality, enableDolby, enableHiRes, cookie)`
(`O/.../bilibili/BilibiliRepository.kt:57-138`):

| Step | Endpoint | Params |
|---|---|---|
| WBI keys (cached per local date) | `GET https://api.bilibili.com/x/web-interface/nav` | none; reads `data.wbi_img.img_url/sub_url`, extracts filename stems (`:28-52`) |
| cid fallback when missing | `GET /x/player/pagelist?bvid=` | first `data[0].cid` (`:172-191`) |
| stream | `GET /x/player/wbi/playurl` (wbi-signed) | `bvid, cid, fnval=4048, fnver=0, fourk=1, qlt=<audioQuality>, voice_balance=1`, `wts`, `w_rid`; header `Cookie` (`:73-86`) |

Quality selection inside native: `dolby` if requested and present → `flac` (Hi-Res) if requested → `dash.audio` entry
whose `id === qlt` → else `dash.audio[0]` (highest); if `dash` is missing, fall back to `durl[0]` (old videos)
(`BilibiliRepository.kt:102-137`). The JS twin of this logic (`M/src/lib/api/bilibili/api.ts:394-511`) additionally tags the
`durl` fallback with a fake `quality: 114514`, but that code path is not used by the player (see below).
**There is no user-facing quality picker** (no settings key, no UI): playback is effectively “best normal AAC (30280),
Dolby/Hi-Res disabled”. The only place quality/dolby/hires is user-visible is not present; `getAudioStream()` is dead
code except for the CDN speed test which uses `getAudioStreamInfo` (`M/src/lib/services/bilibiliCdnService.ts:79`).

CDN preference and mirror fallback:
- `streamUrls()` partitions mirrors into “regular” and `mcdn` and orders them by `preferredBilibiliCdn`
  (`auto|ali|tencent|huawei|akamai`) with mcdn last (`BilibiliRepository.kt:140-170`). Host rules are enumerated there
  (`upos-sz-mirrorali*`, `upos-sz-mirrorcos*`/`upos-tf-all-tx`, `upos-sz-mirrorhw*`/`mirror08`/`upos-tf-all-hw`,
  `upos-hz-mirrorakam.akamaized.net`).
- `BilibiliFallbackDataSource` walks the mirror list and retries **mid-stream** preserving the byte range and cache key,
  retrying on `403/404/408/410/429/5xx` and generic IO (`O/.../network/BilibiliFallbackDataSource.kt:31-89`).
- Headers: `Referer: https://www.bilibili.com/` added to the resolved DataSpec (`DownloadUtil.kt:155-163`); the HTTP
  factory sets a desktop Chrome UA (`DownloadUtil.kt:99-103`); the Retrofit client adds UA + Referer
  (`O/.../bilibili/NetworkModule.kt:36-50`).

## 4.5 Caching

Two `SimpleCache` instances (`O/.../manager/DownloadCache.kt`):
| Cache | Dir | Evictor | Purpose |
|---|---|---|---|
| LRU | `cacheDir/media_cache_lru` | `LeastRecentlyUsedCacheEvictor(256 MB)` | streaming cache (write-through while playing) (`:30-39`) |
| Stable | `filesDir/media_download` | `NoOpCacheEvictor` | pinned downloads, also read-only source for export (`:19-28`) |

Read path is a **cache chain**: `CacheDataSource(downloadCache)` → upstream `CacheDataSource(lruCache)` → ResolvingDataSource(→ Bilibili resolver) →
`BilibiliFallbackDataSource` → OkHttp (`DownloadUtil.kt:65-108`). `FLAG_IGNORE_CACHE_ON_ERROR`, and the download layer
writes no data (`setCacheWriteDataSinkFactory(null)`) so cache writes only happen in the LRU layer.
`clearPlaybackCache()` clears only the LRU (`DownloadCache.clearLruCache`, `:46-50`).

## 4.6 Downloads

Media3 `DownloadManager` with `StandaloneDatabaseProvider`, fixed thread pool 6, `Requirements(0)` (no
network/unmetered constraint), `maxParallelDownloads` clamped 1..6, default 1 (`DownloadUtil.kt:26-63`).
Per-track request carries `TrackRecord` JSON in `DownloadRequest.data` (used later for filename/tags).
Helper ops exposed to JS: `downloadTrack`, `multiDownload`, `removeDownload(s)`, `resumeDownload`, `retryDownload`,
`getDownloads`, `getCompletedDownloadTasks`, `getUncompletedDownloadTasks`, `clearUncompletedDownloadTasks`,
`getDownloadStatusByIds`, `downloadMissingCovers`, `getDownloadedCoverUri`, `exportDownloads`
(`packages/orpheus/src/ExpoOrpheusModule.ts:329-405`). Cover download manager caches artwork per track so notifications
and exports work offline (`O/.../manager/CoverDownloadManager.kt`).

## 4.7 Loudness normalization

- `playurl` returns `volume.measured_i` / `target_i` (`BilibiliModels.kt:55-59`); the resolver stores `measured_i`
  keyed by the **orpheus URI** (`DownloadUtil.kt:146-153` → `LoudnessStorage`).
- On track transition the service reads that value and applies gain = `10^((-14 - measured_i)/20)` clamped to `[0,1]`,
  set as `player.volume` with a **600 ms fade-in** (30 steps of 16 ms) (`OrpheusMusicService.kt:838-858`;
  `O/.../util/Volume.kt:16-57`). Target is hard-coded `-14.0` (comment: Bilibili's value appears fixed).
- Default **on** in native (`GeneralStorage.isLoudnessNormalizationEnabled()` default `true`, `GeneralStorage.kt:77-79`);
  UI toggle labelled "实验性" in playback settings (`M/src/app/settings/playback.tsx:93-110`).
- Known limitation: gain only attenuates (never boosts above 1.0).

## 4.8 Resume / autoplay

Three distinct mechanisms:
1. **Session snapshot**: queue + index + position + repeat + shuffle saved in `player_queue_store`
   (`GeneralStorage.kt:20-24,109+`), restored on service start when `restorePlaybackPositionEnabled`
   (`OrpheusMusicService.kt:438-486`).
2. **Per-track resume** for podcast mode: `TrackResumeStorage` keyed by media id, saved every 5 s while playing, on
   pause, on seek and on leaving the track; **cleared** when the track completes naturally or repeats; applied on
   entering a track only when `playbackResumeStrategy == PODCAST` (`OrpheusMusicService.kt:88-96,625-690,727-758`;
   `TrackResumeStorage.kt:49-89`). Strategy is pushed from JS per playback session (`playbackContextStore.ts:61-65`).
3. **Autoplay on start**: `autoplayOnStartEnabled` (default false) → `player.playWhenReady = true` during restore
   (`OrpheusMusicService.kt:475`).
Also, a cold start with a restored item re-emits `onTrackStarted(PLAYLIST_CHANGED)` so JS can refresh lyrics/overlays (`:478-484`).

## 4.9 Speed, spectrum, other config

- `setPlaybackSpeed` / `getPlaybackSpeed` → `player.setPlaybackSpeed`; UI range 0.1–3.0 step 0.1 with reset
  (`ExpoOrpheusModule.ts:429-432`, `PlaybackSpeedModal.tsx:182-238`).
- Spectrum visualizer: `updateSpectrumData(Float32Array)` with `SPECTRUM_SIZE = 512`; requires `RECORD_AUDIO`
  permission on Android and is off by default (`ExpoOrpheusModule.ts:183,444,499`,
  `settings/appearance/index.tsx:72-111`, `O/.../manager/SpectrumManager.kt`).
- `setAllowSimultaneousPlayback` — when off, audio focus is taken exclusively (`ExpoOrpheusModule.ts:347-348`).
- `isNotificationArtworkEnabled` — hides artwork from the media notification in pure-white mode (`providers.tsx:102-106`).
- `setDownloadMaxParallelTasks`, `setBilibiliCookie` (native keeps its own copy for header injection).

## 4.10 Playback state/events contract (needed to drive a Compose UI)

Events from native (`ExpoOrpheusModule.ts:90-129`): `onPlaybackStateChanged{IDLE=1,BUFFERING=2,READY=3,ENDED=4}`,
`onTrackStarted{trackId,reason}`, `onTrackFinished{trackId,finalPosition,duration}`, `onPositionUpdate{position,duration,buffered}`,
`onIsPlayingChanged`, `onPlaybackSpeedChanged`, `onPlayerError`, `onDownloadUpdated`, `onCoverDownloadProgress`,
`onExportProgress`, `onStatusBarLyricsStatusChanged`, `onQueueChanged`, `onSpectrumVisualizerError`.
Position events feed a sticky emitter (`M/src/lib/player/progressListener.ts`) and a "smooth progress" hook that
interpolates between them (`M/src/hooks/player/useSmoothProgress.ts`).

Error taxonomy worth copying: ExoPlayer codes 2000-7999 / ≤-100 / 1002 / 1003 are treated as user/content/device-side
and **not** reported; `-412` (captcha) and `-101` (not logged in) map to specific Chinese messages
(`M/src/lib/player/PlayerSideEffects.ts:24-270`).

---

# 5. Lyrics

## 5.1 SPL format (Salt Player Lyrics) — grammar as implemented

Parser: `packages/splash/src/parser/index.ts` (+ `spans.ts`, `utils/time.ts`). Types: `packages/splash/src/types.ts:4-54`.

```
SPL       := (meta | line)*
meta      := '[' [a-zA-Z]+ ':' any ']'                          // e.g. [ti:…], [ar:…]  → meta map
line      := timeTag+ content
timeTag   := '[' [-]?MM ':' [-]?SS '.' F{1,6} ']'  |  '<' same '>'   // also accepted bare pre-tag form
content   := text | text wordTag text ...                       // word tags = same syntax inside the line
```

Rules extracted from code:

| Rule | Evidence |
|---|---|
| Blank lines skipped; a line that is *only* `[letters:value]` is metadata, not a lyric | `parser/index.ts:21-31` |
| Multiple leading time tags on one line (e.g. `[00:10.00][01:20.00]chorus`) expand into **one entry per timestamp** | `:34-82` |
| Tags are sorted ascending before use | `:68` |
| A line without a timestamp inherits the previous line's timestamp(s) (continuation of the same lyric, used for multi-line translations) | `:37-53` |
| Two lines with the same timestamp → first candidate is the main text, the rest become `translations[]` | `:88-95` |
| Line end time: explicit trailing empty tag if present, else next line's start, else start + **10 000 ms** | `:104-113`; `spans.ts:70-74` |
| Time parsing accepts `[mm:ss.SS]`, `<mm:ss.SS>`, and decimals of 1–6 digits: `.1`=100 ms, `.02`=20 ms, `.123456`→rounded ms; negatives clamp to 0 | `utils/time.ts:13-23` |
| Malformed line with no timestamp and no predecessor throws `SplParseError(line,msg)`; `verify()` returns a Result instead of throwing | `types.ts:59-66`, `parser/index.ts:150-163` |

## 5.2 Per-word / per-char timing model (karaoke)

`parseSpans(rawContent, lineStartTime, lineNumber)` (`parser/spans.ts:12-82`):

1. Split the line on every `<mm:ss.SS>` **or** `[mm:ss.SS]` token (`parts = raw.split(/([<[]\d{1,3}:\d{1,2}\.\d{1,6}[>\]]))/)`).
2. Even indices are text → become `LyricSpan{text, startTime: currentTime, endTime: 0, duration: 0}`.
3. Odd indices are timestamps → close the previous span (`endTime = t`, `duration = t - start`), set `currentTime = t`,
   and remember it as `explicitLineEnd`. A tag earlier than the current time is ignored with a warning.
4. If the line does **not** end with a tag, `explicitEnd` is cleared so the caller computes the line end and the last
   span is stretched to it (`parser/index.ts:115-126`).
5. `isDynamic = parts.length > 1` (any inline tag ⇒ verbatim/karaoke line).

`LyricSpan` is therefore a *word/segment* (Netease YRC gives per-word, QQ/Kugou give plain lines ⇒ no spans).
`duration` is precomputed for renderers. Consumers:
- App UI interpolates per span (see 5.6).
- Native overlay timeline computes line progress, not span progress (`O/.../manager/LyricsTimeline.kt:15-62`), except the
  floating window `LyricView` which receives `LyricsLine.spans` and animates the highlight
  (`O/.../view/LyricView.kt`, `O/.../model/LyricsModels.kt`).

## 5.3 Translation / romaji merge

`parseAndMergeLyrics({lrc, tlyric, romalrc})` (`packages/splash/src/parser/merge.ts:30-64`):

- Main lyrics parsed first; each secondary track parsed independently.
- Secondary track must share ≥ **20 %** of its line start times with the main track (`isMatch`, `:14-24`), otherwise it is
  discarded (guards against provider mismatches).
- Alignment is by **exact `startTime` equality** (Map), not nearest-neighbour.
- Result lines get `translation`, `romaji`, and a legacy `translations[]` array (translation first, then romaji).

## 5.4 Netease YRC/LRC → SPL converter

`packages/splash/src/converter/netease.ts:22-107`:
- JSON YRC line `{"t":ms,"c":[{"tx":"字"},…]}` → `[mm:ss.mmm]<concat text>` (drops per-word timings).
- YRC `[startMs,durMs](wStart,wDur,0)word…` → emits `<mm:ss.mmm>` tags per word, inserting an explicit gap-close tag
  before a silence, and appends a final end tag (this is how verbatim Japanese lyrics get per-word timing).
- Standard LRC lines (incl. `[mm:ss:xx]` Netease quirk) are normalized to `[mm:ss.xx]`; metadata tags pass through.
- `formatSplTime` pads `mm:ss.mmm` and clamps negatives to 0 (`:6-20`).

## 5.5 Providers, matching, caching

| Provider | Search | Lyrics | Parsing | Matching |
|---|---|---|---|---|
| Netease | `POST /weapi/cloudsearch/pc` (`s,type=1,limit=30,offset`) (`M/src/lib/api/netease/api.ts:66-105`) | `POST /eapi/song/lyric/v1` (`id,lv=-1,tv=-1,rv=-1,kv=-1,yv=-1,os=ios,ver=1`) (`:27-64`) | `yrc` if present else `lrc`; `ytlrc/yromalrc` or `tlyric/romalrc`; all run through `parseYrc` (`:107-129`) | takes result **#1** (no duration check, deliberate — `:147-150`) |
| QQ Music | `POST https://u.y.qq.com/cgi-bin/musicu.fcg` `DoSearchForQQMusicDesktop` (`num_per_page, page_num, query, search_type=0`) (`M/src/lib/api/qqmusic/api.ts:25-83`) | `GET https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=&g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&nobase64=1` (`:90-111`) | `he.decode` of `lyric`/`trans` (`:118-129`) | first result whose `interval` is within ±3 s among top 5 (`:136-173`) |
| Kugou | `GET http://mobilecdn.kugou.com/api/v3/search/song?api_ver=1&area_code=1&correct=1&pagesize&plat=2&tag=1&sver=5&showtype=10&page=1&keyword&version=8990`, UA `IPhone-8990-searchSong` (`M/src/lib/api/kugou/api.ts:26-78`) | `GET http://krcs.kugou.com/search?keyword=%20-%20&ver=1&hash=&client=mobi&man=yes` → candidate `{id,accesskey}` → `GET http://lyrics.kugou.com/download?charset=utf8&accesskey=&id=&client=mobi&fmt=lrc&ver=1`, Base64-decode (`:80-140`) | plain LRC (`:142-148`) | ±3 s among top 5 (`:150-182`) |

Orchestration (`M/src/lib/services/lyricService.ts`):
- keyword cleaning: prefer text inside `《…》` / `「…」`; else strip `【…】` and `“…”` (`:62-81`).
- For Bilibili tracks, keyword is first replaced by the video's `bgm_info.music_title` from `/x/player/wbi/v2`
  (`:549-578`) — this is the single most important trick for hit rate on Bilibili music videos.
- All configured providers run concurrently; the first success aborts the rest; if all fail, an aggregate
  `LyricNotFoundError` is returned (`:89-186`).
- `settings.lyricSource` = `auto|netease|qqmusic|kugou` (default `netease`; `auto` = first responder, with an explicit
  UI warning that it ignores match quality — `settings/lyrics.tsx:179-187`).
- Cache file: `document/lyrics/<uniqueKey.replaceAll('::','--')>.json` holding
  `{id, updateTime, lrc, tlyric?, romalrc?, misc:{userOffset?}, manualSkip?, errorMessage?}`
  (`:213-217`, `:347-387`; type `M/src/types/player/lyrics.ts`). Writes are serialized through a promise chain.
- Offline: cache miss + offline → `LyricNotFoundError('当前处于离线状态…')` (`:298-308`).
- Legacy cache migration runs once per install, guarded by a `.migration_v2_done` marker (`:446-547`).

## 5.6 In-app lyric rendering

- Data: `buildDisplayLyrics()` appends one invisible "padding" line so the last lyric can scroll to center; parse errors
  yield `lines=null` and the raw text is shown instead (`PlayerLyrics.tsx:53-88`, `:291-314`).
- Scroll: `ScrollView` (not a list); item Y captured on layout; target scroll = `y - windowHeight*0.15`
  (`:118-130`). Manual drag pauses auto-scroll, and **2 s** after the user stops it re-centers on the current line
  (`useLyricSync.ts:276-299`).
- Current line index: binary search over `startTime/1000` on every progress event (`useLyricSync.ts:327-347`), also
  re-synced on mount and on app foreground.
- Line highlight: active line `opacity 1` + color animated to `primary`; also `scale 1.05` and `translateX 12` in the
  "modern" style (300 ms) (`LyricLineItem.tsx:195-225`).
- Verbatim (karaoke): each span rendered twice — inactive text + an absolutely-positioned active copy inside a clipped
  container whose width is `layoutWidth * localProgress`, where `localProgress = (t - span.start)/(span.end - span.start)`
  (`KaraokeWord.tsx:35-93`). Only the active line renders spans (`LyricLineItem.tsx:180-243`) to save work.
- Two visual styles: `modern` (left-aligned 24 sp, translation 14 sp) and `oldSchool` (centered 14 sp, "restore old
  lyrics style" setting) (`LyricLineItem.tsx:59-159` vs `:161-280`; styles at `:282-343`).
- Translation/romaji subtext; when both exist the overlay has a toggle to switch which is shown (`PlayerLyrics.tsx:262-266`, `:439-447`).
- Offset badge ±0.5 s steps; live underlay recomputation: `adjustedCurrentTime = currentTime - offset`
  (`PlayerLyrics.tsx:170-177`); persisted to `misc.userOffset` on close.
- Tap a line → seek to that line (`- offset`) (`useLyricSync.ts:301-325`).
- Edge fade: `MaskedView` + two 60 dp `LinearGradient`s from background (`PlayerLyrics.tsx:387-431`).
- Control overlay (prev/play/next, edit, offset, translation toggle) is hidden when the available height < 520 dp
  (`:41-42`, `:104-105`).
- Sharing: `LyricsSelectionModal` + `LyricsShareCard` render a bitmap card with font/color/align options.

## 5.7 Desktop / status-bar / car lyrics (Android-only integrations)

- JS pushes a normalized `LyricsData {lyrics:[{timestamp,endTime,text,translation?,romaji?,spans?}], offset}` via
  `Orpheus.setLyrics(data, consumers)` (`packages/orpheus/src/ExpoOrpheusModule.ts:489-497`; mapping in
  `lyricService.ts:696-741`). Pushes are debounced/coalesced with 300 ms quiet time and a revision counter.
- `UnifiedLyricsManager` fans out to 3 consumers (DESKTOP, STATUS_BAR, CAR), filters blank lines, sorts by timestamp
  (`O/.../manager/UnifiedLyricsManager.kt:34-112`). Consumers can be updated individually (overrides map).
- **Desktop (floating) lyrics** — `FloatingLyricsManager`: `TYPE_APPLICATION_OVERLAY` window, full width, top gravity,
  y persisted; drag vertically (slop 10 px, clamped to status bar height); tap toggles a settings panel with
  prev / play-pause / next, font size slider 10–40, 8 preset colors, display mode cycle (translation → romaji → none),
  "清空歌词" (marks the track `manualSkip` via a headless event) and close; "lock" sets `FLAG_NOT_TOUCHABLE`
  (`O/.../manager/FloatingLyricsManager.kt:30-243`, `O/.../view/LyricView.kt`). Window is *suppressed* (GONE, without
  changing the user setting) while the in-app lyrics page is visible or the app is foreground on the lyrics pane
  (`M/src/app/player.tsx:181-201`).
- **Status bar lyrics** — three backends selected by `statusBarLyricsProvider`:
  `superlyric` (needs the SuperLyric Xposed/LSPosed module), `lyricon` (AIDL remote provider, requires API ≥ 27),
  `meizu` (Flyme native status-bar lyric service) (`O/.../manager/StatusBarLyricsManager.kt`,
  `SuperLyricBackend.kt`, `LyriconBackend.kt`, `MeizuStatusBarLyricsBackend.kt`,
  `createStatusBarBackend()` at `OrpheusMusicService.kt:761-773`). Settings page shows availability, connects on enable,
  and auto-disables Lyricon if connection fails (`M/src/app/settings/lyrics.tsx:116-160,363-449`).
- **Car** — current lyric line is written into `MediaMetadata.title`, original title+artist into `title - artist` of the
  artist field; updated via `player.replaceMediaItem` without disturbing audio (`OrpheusMusicService.kt:790-827`).

---

# 6. Bilibili & third-party APIs

## 6.1 Request conventions (`M/src/lib/api/bilibili/client.ts`)

- Base URL `https://api.bilibili.com` (`:61`).
- Default headers: desktop Chrome UA (`Chrome/157`), `Accept: application/json, text/plain, */*`,
  `Accept-Language: zh-CN,zh;q=0.9,en;q=0.8`, `Referer: https://www.bilibili.com/`, `Origin: …` (`:10-23`).
- Cookie is **manually** serialized from the store and sent as a `Cookie` header; `credentials:'omit'` so the RN cookie
  jar is bypassed (`:88-128`).
- Envelope `{code,message,data}`; `code!==0` → typed `BilibiliApiError` (except `/x/web-interface/nav`, where `-101`
  is expected when logged out) (`:167-188`).
- `postWithCsrf()` appends `csrf` = cookie `bili_jct` to an `application/x-www-form-urlencoded` body (`:345-362`;
  `utils.ts:201-223`).
- Passport endpoints use an iOS BiliApp UA constant `PASSPORT_UA` (`api.ts:64-65`) and `credentials:'omit'`.

## 6.2 WBI signing (required by several endpoints)

Two implementations that must agree: TS `M/src/lib/api/bilibili/wbi.ts:12-143` and Kotlin `O/.../bilibili/WbiUtil.kt:7-72`.

1. `GET /x/web-interface/nav` → `data.wbi_img.img_url/sub_url`; keys are the file stems (`extractKey`).
   TS caches them in MMKV `wbi_keys` with a timestamp and refreshes when the local date changes (`wbi.ts:55-135`);
   Kotlin caches in memory per `yyyyMMdd` (`BilibiliRepository.kt:28-52`).
2. `mixin_key = mixinKeyEncTab.map(i => (imgKey+subKey)[i]).join('').slice(0,32)` (same 64-entry permutation table in
   both files).
3. Add `wts = unix seconds`, sort params by key, filter `!'()*` from values, URL-encode, join `k=v&…`, then
   `w_rid = md5(query + mixin_key)`; final query `…&wts=…&w_rid=…` (TS) / params map incl. `wts`,`w_rid` (Kotlin).

Endpoints requiring WBI: `search/type`, `player/wbi/playurl`, `space/wbi/acc/info`, `space/wbi/arc/search`,
`player/wbi/v2`.

## 6.3 Endpoint inventory (all actually called)

Legend: auth = “cookie” means a logged-in `SESSDATA`-bearing cookie is needed for correct results; “csrf” means a
`bili_jct`-derived `csrf` field is posted.

### Search / browse

| Method | Path | Params | Auth | Response fields used | Evidence |
|---|---|---|---|---|---|
| GET | `/x/web-interface/wbi/search/type` | `keyword, search_type=video|bili_user, page, wts, w_rid` | optional (skipped during import matching) | video: `result[]{bvid,aid,title,author,mid,pic,duration(mm:ss),senddate,typeid,type}`; `numPages`; filters `type!=='ketang'` and non-empty bvid | `api.ts:170-259` |
| GET | `/x/web-interface/search/square` | `limit=10` | – | `trending.list[]{keyword,…}` | `api.ts:264-279` |
| GET | `https://s.search.bilibili.com/main/suggest` | `main_ver=v1, term, userid=<mid?>` | optional | `result.tag[]` | `api.ts:284-357` |
| GET | `/x/web-interface/ranking/v2` | `rid=<partition>` | – | `list[]` — **method exists but is unused** | `api.ts:97-112` |
| GET | `/x/v2/history` | – | cookie | history videos — used by `useRecentlyPlayed` which is **currently unconsumed** | `api.ts:84-92`, `hooks/queries/bilibili/user.ts:73-87` |

### Video / playurl

| Method | Path | Params | Auth | Response fields used | Evidence |
|---|---|---|---|---|---|
| GET | `/x/web-interface/view` | `bvid` | – | `title,pic,duration,owner{mid,name},…` | `api.ts:760-774` |
| GET | `/x/player/pagelist` | `bvid` | – | `[]{cid,page,part,duration}` | `api.ts:516-530` |
| GET | `/x/player/wbi/playurl` | `bvid,cid,fnval=4048,fnver=0,fourk=1,qlt,voice_balance=1,wts,w_rid` | cookie (for high qualities) | `dash.audio[]{id,base_url,backup_url}`, `dash.dolby.audio[]`, `dash.flac.audio`, `durl[]{url,backup_url}`, `volume{measured_i,target_i}` | `api.ts:362-391`; `BilibiliRepository.kt:73-138` |
| GET | `/x/player/wbi/v2` | `bvid,cid,wts,w_rid` | – | `bgm_info.music_title`, subtitle list, `last_play_time`, interaction flags | `api.ts:1409-1430` |
| GET | arbitrary subtitle JSON URL | must be `https:` and host `*.hdslb.com`/`*.bilibili.com` | – | `body[]{from,to,content}` | `api.ts:1433-1477` |

### User / relations

| Method | Path | Params | Auth | Notes | Evidence |
|---|---|---|---|---|---|
| GET | `/x/web-interface/nav` | – | cookie | WBI keys; also `-101` handling | `client.ts:167-174`, `wbi.ts:113-115` |
| GET | `/x/space/myinfo` | – | cookie | self profile | `api.ts:535-543` |
| GET | `/x/space/wbi/acc/info` | `mid,wts,w_rid` | cookie | other user profile (`name,face,sign,…`) | `api.ts:656-674` |
| GET | `/x/relation` | `fid=<mid>` | cookie | follow state | `api.ts:545-557` |
| POST | `/x/relation/modify` | `fid, act=1|2, re_src=11, csrf` | cookie+csrf | follow/unfollow | `api.ts:559-579` |
| GET | `/x/relation/followings` | `vmid,pn,ps=50` | cookie | following list | `api.ts:581-595` |
| GET | `/x/relation/followings/search` | `vmid,name,pn,ps=50` | cookie | | `api.ts:597-619` |
| GET | `/x/relation/tags` | – | cookie | following groups | `api.ts:621-632` |
| GET | `/x/relation/tag` | `tagid,pn,ps=20` | cookie | group members | `api.ts:634-651` |
| GET | `/x/space/wbi/arc/search` | `mid,pn,keyword,ps=30,wts,w_rid` | cookie (risk-controlled) | uploader videos | `api.ts:1061-1090` |

### Favorites / collections / series

| Method | Path | Params | Auth | Notes | Evidence |
|---|---|---|---|---|---|
| GET | `/x/v3/fav/folder/created/list-all` | `up_mid` (+`rid`,`type=2` for “which folders contain this video”) | cookie | fav folders | `api.ts:114-132`, `1008-1033` |
| GET | `/x/v3/fav/folder/collected/list` | `pn,ps=20,up_mid,platform=web` | cookie | followed collections | `api.ts:798-833` |
| POST | `/x/v3/fav/folder/add` | `title,intro,privacy,cover,csrf` | cookie+csrf | create folder | `api.ts:134-165` |
| GET | `/x/v3/fav/resource/list` | `media_id,pn,ps=40` (+`keyword,type=0|1`) | cookie | folder contents; search-in-folder | `api.ts:676-733` |
| GET | `/x/v3/fav/resource/ids` | `media_id` | cookie | batch id list, filter `type===2` | `api.ts:735-755` |
| POST | `/x/v3/fav/resource/deal` | `rid=<avid>,add_media_ids,del_media_ids,type=2,csrf` | cookie+csrf | add/remove one video | `api.ts:979-1006` |
| POST | `/x/v3/fav/resource/batch-del` | `resources=<avid>:2,…`, `media_id`, `platform=web`, `csrf` | cookie+csrf | batch remove | `api.ts:776-796` |
| GET | `/x/space/fav/season/list` | `season_id,ps=20,pn=1` | cookie | collection contents | `api.ts:835-854` |
| GET | `/x/series/series` | `series_id` | cookie | series meta `{meta{series_id,name,description,total,mid}}` | `api.ts:856-870` |
| GET | `/x/series/archives` | `mid,series_id,ps,pn` | cookie | `archives[]{aid,bvid,title,pic,duration,pubdate,upMid}`, `page.total` | `api.ts:872-895` |

### History / comments / likes / toview

| Method | Path | Params | Auth | Notes | Evidence |
|---|---|---|---|---|---|
| GET | `/x/v2/history` | – | cookie | | `api.ts:84-92` |
| POST | `/x/v2/history/report` | `aid,cid,progress,csrf` | cookie+csrf | only when `sendPlayHistory` on and track is bilibili | `api.ts:1035-1059`, `utils/player.ts:67-148` |
| GET | `/x/v2/reply/main` | `oid=<avid>,type=1,mode=3|2,next,plat=1` | optional | comment list | `api.ts:1092-1122` |
| GET | `/x/v2/reply/reply` | `oid,type=1,root,pn,ps=20` | optional | replies | `api.ts:1124-1154` |
| POST | `/x/v2/reply/action` | `oid,type=1,rpid,action=0|1,csrf` | cookie+csrf | like comment | `api.ts:1156-1182` |
| GET | `/x/web-interface/archive/has/like` | `bvid` | cookie | returns 0/1 | `api.ts:1351-1369` |
| POST | `/x/web-interface/archive/like` | `bvid, like=1|2, csrf` | cookie+csrf | duplicate-like code `65006` treated as success | `api.ts:1371-1404` |
| GET | `/x/v2/history/toview` | – | cookie | watch-later list | `api.ts:1479-1490` |
| POST | `/x/v2/history/toview/del` | `viewed=true` or `aid` | cookie+csrf | | `api.ts:1492-1535` |
| POST | `/x/v2/history/toview/clear` | – | cookie+csrf | | `api.ts:1537-1544` |

### Login (passport)

| Method | URL | Params | Notes | Evidence |
|---|---|---|---|---|
| GET | `https://passport.bilibili.com/x/passport-login/web/qrcode/generate` | – | `{url, qrcode_key}` | `api.ts:1184-1196` |
| GET | `https://passport.bilibili.com/x/passport-login/web/qrcode/poll` | `qrcode_key` | status enum (`0` success, `86101` wait, `86090` scanned, `86038` expired); reads `Set-Cookie` header | `api.ts:1198-1281`, `types/apis/bilibili.ts` |
| GET | `https://passport.bilibili.com/x/passport-login/captcha` | `source=main_web, t=<ms>` | Geetest `gt/challenge/token` | `api.ts:1546-1603` |
| POST | `https://passport.bilibili.com/x/passport-login/web/sms/send` | form: `cid,tel,source=main_mini_login,token,challenge,validate,seccode` | `captcha_key` in response | `api.ts:1605-1692` |
| POST | `https://passport.bilibili.com/x/passport-login/web/login/sms` | form: `cid,tel,code,source=main_mini_login,captcha_key,keep=1` | reads `Set-Cookie` | `api.ts:1694-1787` |
| GET | b23.tv short URL | – | resolves via `<link rel=canonical>` or final URL | `api.ts:1283-1349` |

### Theme / garb (装扮 & 收藏集)

| Method | Path | Params | Notes | Evidence |
|---|---|---|---|---|
| GET | `/x/garb/v2/mall/home/search` | `key_word,pn,ps` | search skins/collections | `api.ts:1789-1814` |
| GET | `/x/vas/dlc_act/asset_bag` | `act_id,lottery_id` | collection cards + reward entries (`collect_list[]{redeem_item_type,redeem_item_id,redeem_item_name,card_item}`) | `api.ts:1816-1836` |
| GET | `/x/garb/v2/mall/suit/detail` | `item_id` | suit components: `suit_items.{card,card_bg,space_bg,thumbup,skin,loading,play_icon}` | `api.ts:1838-1853` |
| GET | `/x/garb/v2/user/suit/benefit` | `item_id,part=emoji_package` | resolves a component's `part_id` (1 avatar frame, 3 thumbup, 9 theme skin, 10 loading, 11 play icon) + `properties` | `api.ts:1855-1873`, `theme/adapter.ts:72-202` |

## 6.4 Third-party: Netease

- Hosts: `https://music.163.com` (weapi/linuxapi) and `https://interface3.music.163.com` (eapi) (`M/src/lib/api/netease/request.ts:21-25`).
- Crypto modes (`M/src/lib/api/netease/crypto.ts`):
  - **weapi**: AES-CBC(JSON, presetKey `0CoJUm6Qyw8W8jud`, iv `0102030405060708`) → AES-CBC(again, random 16-char base62 key) + RSA(`encSecKey`) using the embedded public key (`:246-264`).
  - **eapi**: `md5("nobody"+url+"use"+json+"md5forencrypt")`, body `url-36cd479b6b5-json-36cd479b6b5-digest`, AES-ECB key `e82ckenh8dichen8`, hex (`:275-287`).
  - **eapi response** is AES-ECB decrypted when `e_r=true` (`:289-327`, `request.ts:159-165`).
- Cookie header carries `os, appver, osver, deviceId, __csrf`; UA is `NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)` (`request.ts:27-35,100-120`).
- Endpoints used: `POST /weapi/cloudsearch/pc` (search), `POST /eapi/song/lyric/v1` (lyrics), `POST /eapi/v6/playlist/detail` (playlist import) (`api.ts:59-63,72-94,162-179`).

## 6.5 Third-party: QQ Music

- `POST https://u.y.qq.com/cgi-bin/musicu.fcg` JSON body `{comm:{ct:"19",cv:"1859",uin:"0"}, req:{method:"DoSearchForQQMusicDesktop", module:"music.search.SearchCgiService", param:{grp:1,num_per_page,page_num,query,search_type:0}}}`; headers UA Firefox, `Referer: https://y.qq.com/` (`qqmusic/api.ts:25-83`).
- Lyrics: `GET https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=&g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&nobase64=1` (`:90-111`).
- Playlist: `GET https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg?id&format=json&newsong=1&platform=jqspaframe.json` (`:180-206`).

## 6.6 Third-party: Kugou

- Search `GET http://mobilecdn.kugou.com/api/v3/search/song?...` (cleartext HTTP), headers `User-Agent: IPhone-8990-searchSong`, `UNI-UserAgent: iOS11.4-Phone8990-1009-0-WiFi` (`kugou/api.ts:26-78`).
- Lyrics two-step over HTTP (`http://krcs.kugou.com/search`, `http://lyrics.kugou.com/download`) with Base64 payload (`:80-140`).

## 6.7 BBPlayer backend (optional, shared playlists)

- Base `https://be.bbplayer.roitium.com` (overridable with `EXPO_PUBLIC_BBPLAYER_API_URL`), JWT `Authorization: Bearer` (`M/src/lib/api/bbplayer/client.ts:7-57`).
- Hono RPC client (`hc<AppType>`) calling `playlists.$post`, `playlists/:id.subscribe.$post`, `playlists/:id.changes.$get`,
  `playlists/:id.preview.$get`, `me.playlists.$get`, `playlists/:id.invite[.rotate].$post` (`lib/facades/sharedPlaylist.ts:162-780`).
- 401 clears the BBPlayer account (`client.ts:35-46`). Sync is incremental with `lastShareSyncAt` as server-time cursor
  (`schema.ts:127-132`); local outbox for edits is `playlist_sync_queue`.
- Also used for playlist sharing: `app/share/playlist.tsx`, `components/modals/playlist/EnableSharingModal.tsx`.

## 6.8 Gaps / things a Kotlin team must watch

1. **Cleartext HTTP** for Kugou (`http://mobilecdn.kugou.com`, `http://krcs.kugou.com`, `http://lyrics.kugou.com`) — needs
   `usesCleartextTraffic` or network-security-config exceptions. QQ playlist passes `Referer: http://y.qq.com`.
2. **WBI rotation**: keys are per-day; if Bilibili changes the mixin table all search/playurl/space calls break.
3. **`fnval=4048`** is a bitmask enabling DASH + Dolby + Hi-Res + 8K etc.; `qlt` is the audio quality id.
4. **Cookie is dual-managed**: JS store → `Orpheus.setBilibiliCookie` → native `OrpheusConfig.bilibiliCookie`
   (`_layout.tsx:137-144`, `DownloadUtil.kt:144`). A Kotlin rewrite can keep it in one place.
5. Risk control: uploader listing requires cookie; search during import deliberately drops cookie.
6. b23.tv resolution has no API — it parses the landing HTML for `<link rel="canonical">`.

---

# 7. Theme system

## 7.1 Where the colors come from

| Layer | Implementation | Evidence |
|---|---|---|
| Base Material 3 scheme | `react-native-paper` MD3 light/dark, but **colors are replaced with Android 12+ dynamic color** from expo-router: `Color.android.dynamic.<token>` with Paper defaults as fallback | `M/src/lib/theme/material3Colors.ts:27-81`, `components/providers.tsx:95-128` |
| Pure-white mode (e-ink) | `MD3LightTheme` with every surface forced `#FFFFFF`, `Appearance.setColorScheme('light')`, system bars forced dark icons | `material3Colors.ts:7-22`, `providers.tsx:107-112,163` |
| Skin (Bilibili 装扮) | downloaded asset bundle overriding specific surfaces only; never replaces the whole scheme | `M/src/lib/theme/types.ts:53-104` |
| Cover-derived colors | player page only: palette from cover via native module | `M/src/app/player.tsx:281-347` |

## 7.2 Skin data model

`AppSkin` (`M/src/lib/theme/types.ts:53-104`), stored per installed skin as `skin.json` (`InstalledSkin`, `:16-34`):

```
skins[]          : background.head, tabBar.background,
                   tabBar.icons.{home,library,settings}.{default,selected},
                   colors.{color, colorMode, colorSecondPage, tailColor, tailColorSelected}
bootSplash.items[] : {id, name, card, video}
loadings[]       : {animation, frame, preview}
sliderThumbs[]   : {dragLeft, dragRight, normal}
thumbUps[]       : {animation, durationMs, preview}
avatarFrames[]   : string[]  (image paths)
```

Raw Bilibili asset vocabulary mapped onto that model (`M/src/lib/theme/transformer.ts:186-268`):

| Bilibili field (theme skin, `part_id=9`) | Used as |
|---|---|
| `head_bg` | appbar header background (`background.head`) |
| `tail_bg` | tab bar background |
| `tail_icon_main` / `tail_icon_selected_main` | 搜索 tab icon |
| `tail_icon_channel` / `…selected…` | 音乐库 tab icon |
| `tail_icon_myself` / `…selected…` | 我的 tab icon |
| `tail_icon_dynamic`, `tail_icon_shop`, `tail_icon_pub_btn_bg` and their selected variants | present in the raw manifest schema, but **not mapped into `AppSkin`** and never rendered |
| `color` | foreground color used for page titles (`useSkinForegroundColor`) |
| `color_mode` (`light|dark`) | foreground fallback white/#212121 |
| `color_second_page` | stored in `AppSkin.colors.colorSecondPage`, **never read by any UI** |
| `tail_color` / `tail_color_selected` | stored in `AppSkin.colors.tailColor*`, **never read by any UI** (tab bar uses theme/dynamic colors) |
| `package_url` / `package_md5` | downloadable package (skin assets zip) |
| `image_cover` / `image_preview` | skin list artwork |

Other components: `avatar_frames[]` (`part 1`), `thumbups[]` (`part 3`, animated like burst), `loadings[]` (`part 10`,
animated loading art), `play_icons[]` (`part 11` → `sliderThumbs`: the **seek thumb** art), plus collection
`cards[]` (used as boot-splash cards and share cards) and `space_backgrounds[]`.

Validation: arktype schema in `M/src/lib/theme/schema.ts:17-126` (8 leaf asset types composed into
`skinAssetDeclarationSchema`), producing `SkinAssetDeclaration`; `InstalledSkin` derived via `transformManifestToInstalledSkin`
which rewrites every remote URL to a local path through a `mapping` produced by the downloader
(`theme/transformer.ts:126-268`, `theme/downloadManager.ts`).

## 7.3 Application points

| Surface | How |
|---|---|
| App bar / page header art | `SkinAppbarBackground` drawn behind the search/library/my titles (`components/navigation/SkinAppbarBackground.tsx`, used at `(tabs)/index.tsx:269`, `library/[tab].tsx:79`, `my.tsx:93`) |
| Tab bar icons + indicator | native tab bar props from `activeSkin.skins[i].tabBar.icons`, icon size 50 dp, transparent indicator when skin icons are used (`(tabs)/_layout.tsx:20-63,103-130`) |
| Rail icons | `NavigationRail.tsx:130-180` (48 dp images) |
| Header foreground | `useSkinForegroundColor` (`hooks/theme/useSkinForegroundColor.ts:9-46`) |
| Avatar frame | overlaid on the avatar in `my.tsx:150-170` and `settings/bilibili-account/index.tsx:106-113`; size settings `activeAvatarFrameIndex` |
| Play/pause + seek thumb | `PlayerSlider` passes `thumbImageUri`/`dragging` variants and a user-tunable size/offset (`features/player/components/main/PlayerSlider.tsx:365-405`; offsets in `useSkinStore`) |
| Loading animation | `activeLoadingIndex` → skin loading animation/frame (used by the custom `ActivityIndicator` / skin download UI) |
| Like animation | `SkinThumbUpBurst` triggered on like when not already liked (`features/player/components/main/PlayerTrackInfo.tsx:77-88`) |
| Boot splash | poster or video from `bootSplash.items`, chosen by `selectedSkinBootSplashAssetId` + `selectedSkinBootSplashMode` (`'poster'|'video'`), optional full playback `playFullSkinBootSplashAnimation`; a MMKV cache (`boot_splash_preload`) avoids I/O race (`components/AnimatedBootSplash.tsx:40-120`) |
| Wallpaper/space + card backgrounds | downloaded and validated, but the gallery tabs for them are commented out (`features/theme/ThemeSection.tsx:36-42`) ⇒ currently unused; space-bg portraits are still recycled into boot-splash cards (`theme/adapter.ts:423-454`) |

Per-skin multi-variant: one skin can contain several `skins[]` entries (e.g. light/dark variants); the user picks
`activeSkinIndex`.

## 7.4 Light/dark and “dynamic color”

- The app follows the system scheme (`useColorScheme`) and does **not** expose a manual light/dark switch; the only
  override is pure-white mode forcing light (`providers.tsx:107-112`).
- Dynamic (Material You) color is the default accent source on Android 12+; falling back to MD3 baseline on older
  devices/other platforms (`material3Colors.ts:30-80`).
- Skin colors are applied **on top of** the dynamic scheme and only where explicitly wired (headers, tab icons, player
  controls), so a skin never breaks contrast of dialogs/lists.
- Player page color: `lightMuted` / `darkMuted` / `muted` palette entries, 400 ms animated gradient to background
  (`player.tsx:315-347`); fluid mode uses a shader driven by the palette (`features/player/components/visuals/FluidBackground.tsx`).

## 7.5 Installing a skin (sequence)

1. `settings/appearance/theme-search` → `searchGarbSkins(keyword)`.
2. `fetchGarbSkinAssetDeclaration(item)` — collection needs `act_id+lottery_id` then N+1 benefit calls; suit is one call.
3. arktype-validate, then `SkinManager` downloads every referenced URL into `document/skins/<id>/`, writing `skin.json`
   with both the raw `manifest` and the derived `appSkin`.
4. `useSkinStore.addInstalledSkin(meta)` (zustand → MMKV `skin-storage`, version 3 with a `coverUri→coverPath` migration).
5. Activation sets `activeSkinId`; asset-specific indices (`activeSkinIndex`, `activePlayIconIndex`,
   `activeThumbUpIndex`, `activeLoadingIndex`, `activeAvatarFrameIndex`) are separate keys.
6. `useActiveSkin()` loads + caches `AppSkin` in memory (`theme/runtime.ts:11,93-121`); `pureWhiteBackground` disables it
   entirely (`useActiveSkin.ts:49`).

---

# 8. Settings inventory

## 8.1 App settings (Zustand `app-storage`, type `Settings` in `M/src/types/core/appStore.ts:3-20`)

Defaults from `M/src/hooks/stores/useAppStore.ts:131-147`.

| Key | Default | Type / values | Effect | UI |
|---|---|---|---|---|
| `defaultPlayerMode` | `'music'` | `music|podcast` | mode for new playback when the playlist has `inherit` | settings/playback |
| `podcastSubtitleLanguage` | undefined | string (lang code) | preferred subtitle track for podcast subtitles | not exposed in current settings UI (used by subtitle hooks) |
| `sendPlayHistory` | `false` | bool | report progress to Bilibili `/x/v2/history/report` | settings/bilibili-account |
| `enableDebugLog` | `false` | bool | verbose file logging | settings/general |
| `enableOldSchoolStyleLyric` | `false` | bool | centered small lyric style | settings/lyrics |
| `playerBackgroundStyle` | `'fluid'` | `gradient|fluid` (legacy `md3|streamer` migrated) | player page background | settings/appearance |
| `pureWhiteBackground` | `false` | bool | e-ink mode: white surfaces, disables skin/palette/artwork | settings/appearance |
| `nowPlayingBarStyle` | `'float'` | `float|bottom` | mini-player style | settings/appearance (Android) |
| `lyricSource` | `'netease'` | `auto|netease|qqmusic|kugou` | auto lyric provider | settings/lyrics |
| `enableVerbatimLyrics` | `true` | bool | per-word karaoke highlight | settings/lyrics |
| `enableDataCollection` | `true` | bool | Sentry + Observe analytics; requires app restart | settings/general |
| `downloadMaxParallelTasks` | `1` | 1|2|3|6 | pushed to Media3 DownloadManager | settings/storage |
| `enableMinimalistMode` | `false` | bool | declared, **no consumer found** (dead setting) | – |
| `allowSimultaneousPlayback` | `false` | bool | audio focus behavior | settings/playback |
| `expandMultiPageOnSync` | `null` | `bool|null` (null = never asked) | when syncing a local playlist to Bilibili, expand multi-page videos into separate entries; asked on first sync | settings/general / SyncLocalToBilibili |
| `hideStarPrompt` | `false` | bool | hides the GitHub star icon/prompt | my tab star button |

Persisted auth: `bilibiliCookie: Record<string,string>|null`, `bilibiliUserInfo {mid,name,face,cachedAt}`,
`bbplayerToken`, `bbplayerAccount {id,username,name,face}` (`types/core/appStore.ts:22-41`).

## 8.2 Native player config (MMKV `player_queue_store`, `O/.../util/GeneralStorage.kt`)

| Key | Default | Effect | UI path |
|---|---|---|---|
| `config_restore_position_enabled` | `false` | restore last queue+index+position on cold start | 播放设置 → 启动时恢复上次播放进度 |
| `config_autoplay_on_start_enabled` | `false` | start playing after restore | 播放设置 → 软件启动时自动播放（易社死） |
| `config_loudness_normalization_enabled` | `true` | loudness gain | 播放设置 → 响度均衡（实验性） |
| `config_bilibili_cdn` | `'auto'` | preferred mirror (`auto|ali|tencent|huawei|akamai`) | CDN 设置 |
| `config_notification_artwork_enabled` | `true` | media notification artwork (auto-off in pure-white) | implicit |
| `config_spectrum_visualizer_enabled` | `false` | spectrum analyzer (needs RECORD_AUDIO) | 外观 → 显示音频频谱 |
| `config_status_bar_lyrics_enabled` | `false` | push lyrics to status bar backend | 歌词设置 |
| `config_status_bar_lyrics_provider` | `'superlyric'` (fallback `lyricon` on API≥27) | `superlyric|lyricon|meizu` | 歌词设置 |
| `config_car_lyrics_enabled` | `false` | lyrics in media metadata | 歌词设置 |
| `config_desktop_lyrics_display_mode` | `0` | 0 translation / 1 romaji / 2 none | floating window panel |
| `config_desktop_lyrics_highlight_color` | `#FFC107` | highlight color | floating window panel |
| `config_desktop_lyrics_text_size` | `18f` | 10–40 | floating window panel |
| `config_desktop_lyrics_y` | 0 | vertical offset of overlay | drag |
| `state_desktop_lyrics_shown` / `_locked` | false | overlay visibility / drag-lock | 歌词设置 |
| `saved_queue_json_list`, `saved_index`, `saved_position`, `saved_repeat_mode`, `saved_shuffle_mode` | – | session snapshot (see 4.8) | implicit |

`track_resume_store`: `config_resume_strategy` (0 none / 1 podcast) + `resume::<mediaId>` JSON
`{id,position,duration,updatedAt}` (`O/.../util/TrackResumeStorage.kt`).
`loudness_normalization_store`: `"<orpheus uri>" -> measured_i` (`O/.../util/LoudnessStorage.kt`).

## 8.3 Skin settings (MMKV `skin-storage`, `useSkinStore.ts:46-163`)

`activeSkinId(null)`, `activeSkinIndex(0)`, `activePlayIconIndex(0)`, `activeThumbUpIndex(0)`, `activeLoadingIndex(0)`,
`activeAvatarFrameIndex(0)`, `selectedSkinBootSplashAssetId(null)`, `selectedSkinBootSplashMode('poster')`,
`playFullSkinBootSplashAnimation(false)`, `skinSliderThumbSize(20)`, `skinSliderThumbOffsetX(0)`,
`skinSliderThumbOffsetY(0)`, `installedSkins[]`.

## 8.4 Other app-level persisted keys (MMKV, `M/src/types/storage.ts:1-38`)

`first_open`, `startup_screen` (`home|library`), `ignore_alert_replace_playlist`, `skip_version` (update nags),
`enable_sentry_report`, `enable_debug_log`, `send_play_history`, `bilibili_cookie`, `wbi_keys`,
`player_background_style`, `now_playing_bar_style`, `enable_persist_current_position`, `current_position`,
`enable_loudness_normalization`, `db_schema_version`, `sort_key_migrated_v1/v2/v3`, `play_history_migrated_v1`,
`independent_account_migrated_v1`, `play_history_ms_migrated_v1`, `bbplayer_jwt`, `shared-playlist-members`,
`skin-storage`, `boot_splash_preload`, `enable_startup_profiling`, `enable_shake_profiling`,
`webdav_backup_url|_username|_directory`, `podcast_subtitles_hint_shown`, `bilibili_search_history`
(not in the typed schema; used via `useMMKVObject`), `download-manager-storage-v2`, `player-storage-full`,
`app-storage`, `playback-context-store`.

## 8.5 Settings that exist elsewhere but behave like settings

- **Per-playlist** `playerPreference` (`inherit|music|podcast`) — `playlists.playerPreference`.
- **Per-track** lyric offset (`misc.userOffset`) and `manualSkip` — inside the lyric JSON file.
- **Per-track** resume position — native `track_resume_store`.
- **Alert dismissal flags**: `ignore_alert_replace_playlist`, `podcast_subtitles_hint_shown`.

## 8.6 Notable absences (don’t design around them)

No audio-quality picker, no Dolby/Hi-Res toggle, no equalizer, no playback pitch, no crossfade, no “skip silence”,
no manual light/dark switch, no font-size setting for lyrics, no download Wi-Fi-only constraint (`Requirements(0)`),
no per-playlist sort persistence beyond fractional keys.

---

# 9. UI/UX specifics worth copying

## 9.1 Gestures

| Gesture | Where | Behavior |
|---|---|---|
| Horizontal swipe on mini-player | `NowPlayingBar.tsx:204-253` | >80 px → prev/next, haptic at threshold, label cross-fade |
| Swipe up / tap on mini-player | `:195-201`, `:255-261` | open `/player` |
| Left/right pager inside player | `player.tsx:517-538` | main ⇄ lyrics; header title crossfades with page offset |
| Drag queue rows | `PlayerQueueModal.tsx:86-92` | long-press 300 ms then drag, auto-scroll near edges (`EDGE_ZONE 64`, `SCROLL_STEP 8`), 64 dp rows |
| Drag-to-reorder playlist tracks | `app/playlist/local/[id].tsx` select-mode “排序” | long-press enters sort; fractional reindex |
| Lyric list drag | `PlayerLyrics.tsx:191-198` | user scroll suspends auto-follow; 2 s later re-center |
| Seek slider drag | `PlayerSlider.tsx:273-343` | wave grows from 3→12 dp while dragging, clock tick haptic per chapter boundary (podcast), drag-start/end haptics |
| Pull to refresh | remote/search pages | `RefreshControl` |
| Double-tap title bar | `useDoubleTapScrollToTop` | scroll list to top |
| Back | everywhere | Android back is intercepted by player page ladder, modal stack, and search panel |

## 9.2 List item layout (the shared “track row”)

`features/playlist/remote/components/PlaylistItem.tsx` (remote) and `features/playlist/local/components/LocalTrackList.tsx`:
- Height 64 dp (`theme/dimensions.ts:8`), flat `Surface elevation 0`, cover 48 dp radius 12 (`:1-2`), index number in a
  narrow leading column; in select mode the number is hidden and a checkbox takes its place (`PlaylistItem.tsx:154-187`).
- Title `bodySmall` (1 line; search results render `<em>` runs bold + primary via `HighlightedText`), then a second line
  “artist • duration” (dot separator, `formatDurationToHHMMSS`) (`:56-85`, `:206-240`).
- Current track = translucent black/white 12 % overlay (`:114-130`); tapping it does nothing (`:139`), tapping another row
  starts playback. Long-press **500 ms** enters multi-select; then rows toggle selection, and the app bar switches to
  “已选择 N 首” with all/invert/add/remove actions (`[query].tsx:126-180`, `downloaded.tsx:98-171`).
- Trailing `⋮` per-row menu (`MenuView`, Material 3 menu) with play-next / add-to-playlist / view video / view uploader /
  download / copy cover URL / edit metadata / delete (`features/playlist/local/hooks/useLocalPlaylistMenu.ts:100-254`).
- Optional cover column (`showCoverImage`) used by compact lists.
- Skeletons: `PlaylistSkeleton.tsx` / `TrackListItemSkeletonGroup` while loading, animated only after the screen
  transition is ready (`useScreenTransitionReady`).

## 9.3 Player page layout

- Header: chevron-down (page 0) / chevron-left (lyrics page), status text “正在播放 (已缓存)” crossfading to the track
  title as you swipe, `⋮` menu (`PlayerHeader.tsx:29-80`).
- Cover: circular for music (max 480 dp, computed from available area), rounded square (squircle, radius ratio 0.22) for
  podcast; spectrum ring around it when enabled; initial-letter gradient placeholder when no cover
  (`PlayerTrackInfo.tsx:104-140`, `M/src/utils/color.ts`).
- Title (tap to expand to multiple lines), artist (tap → uploader page), small like button with burst animation.
- Wave slider (custom Compose view): 30 dp wavelength, velocity 15 while playing, track 3 dp; time labels 00:00 / total.
- Controls: prev / play-pause (Lottie morph 8↔0 frames, 80–96 dp) / next; secondary row with mode, queue, download,
  comments, more (`PlayerControlContent.tsx:342-344`).
- Podcast mode: ±15 s circular buttons with Lottie, chapter title above the slider, subtitle reader page.
- Bottom safe-area padding inflates on compact heights.

## 9.4 Lyrics page behavior

- Only two pages, no tab bar; the player controls sit in an overlay on the lyrics page when height ≥520 dp.
- Top/bottom 60 dp gradient masks; a half-window padding item at the end lets the final line reach the center.
- Active line scales/translates in the modern style; karaoke fill is a clipped duplicate text layer (cheap, no per-char
  spans needed at layout time).
- Offset control is a floating right-edge vertical pill (+0.5 / value / −0.5 / ✓), not a dialog.
- Tapping a line seeks; “编辑歌词” opens a full-screen raw editor; translation/romaji switch appears only if both exist.

## 9.5 Tablet / rail adaptation

| Width | Behavior |
|---|---|
| <360 dp or height <600 | `isCompact` → tighter paddings, smaller covers, fewer controls |
| ≥600 | bottom tabs hidden, 96 dp rail overlay (Android), library becomes a grid (up to 6 columns), settings remains single-column |
| ≥840 | two-pane player (42 % playback + lyrics/comments), settings master–detail, my page two columns (accounts 300 dp + stats) |
| ≥840 & height ≥600 | `isTwoPane` (excludes landscape phones) |
| Wide single-column pages | content clamped to `MAX_WIDTH.content = 960`, search page 720, dialog 560, player controls 600 |

The rail stays visible across secondary routes and highlights the last tab; tapping a tab first dismisses the stack
(`NavigationRail.tsx:113-135`). Full-screen routes hide it.

## 9.6 Motion, haptics, feedback

- Haptics: `Context_Click` on play/pause, prev/next, mini-player threshold, cover tap; `Drag_Start`/`Gesture_End` on
  slider; `Clock_Tick` on podcast chapter crossings (`utils/haptics` usage throughout).
- Lottie: play/pause, skip-prev, skip-next, rewind-15, forward-15 — all tinted at runtime by `tintLottieSource`
  (`utils/lottie.ts`), because `colorFilters` don’t apply.
- Transitions: player `slide_from_bottom`; modals fade + per-layer dim; settings split uses fade; tab switching native.
- Toasts: `sonner-native` with title/description, plus inline loading toasts for Lyricon connection.
- Empty/error states: dedicated components (`PlaylistError`, `DataFetchingError`, `TabDisabled`), retry buttons.
- TV support: focus-ring color follows theme primary (`providers.tsx:130-133`), `TvFocusScope`, `Platform.isTV` branches.

---

# 10. Suggested MVP vs later scope for a native Kotlin/Compose rewrite

## 10.1 Essential for a believable v1 (must ship)

**Foundations**
1. **Bilibili auth**: QR login + manual cookie paste (phone SMS is high effort/low frequency — defer).
   Keep cookie in `EncryptedSharedPreferences`/DataStore and inject into every request.
2. **Domain DB** (Room): `artists`, `tracks`, `bilibili_metadata`, `local_metadata`, `playlists`, `playlist_tracks`,
   `play_history`. Preserve `uniqueKey` (`bilibili::BV…[::cid]`) and fractional `sort_key` semantics.
3. **Omni search**: BV / AV / b23.tv / keyword / space link → correct destination; search history (max 10).
4. **Video → track ingestion** via `/x/web-interface/view` + `/x/player/pagelist`, plus fav folder / collection / series
   / uploader listing (read-only browsing is enough for v1) and search results.
5. **Playback**: Media3 `MediaLibraryService` + `ExoPlayer`, `orpheus://`-equivalent internal URI resolved in a
   `ResolvingDataSource`, WBI-signed `playurl` (fnval 4048, qlt 30280), multi-mirror fallback, LRU stream cache,
   MediaSession + media notification with prev/play/next + repeat cycle, queue persistence, repeat/shuffle via
   `setShuffleOrder`, sleep timer.
6. **Player UI**: cover + title/artist + wave-ish seekbar + prev/play/next + queue sheet + speed + sleep timer + like.
7. **Local playlists**: create/rename/delete, add/remove tracks, reorder, play all/shuffle, pin; player-preference.
8. **Lyrics**: cache-first auto fetch from Netease (+QQ/Kugou as alternates), SPL parse incl. per-span karaoke,
   translation/romaji merge, offset ±0.5 s persisted, tap-to-seek, auto-scroll with 2 s resume.
9. **Mini-player** with the same gesture set (open player, swipe prev/next, queue button).
10. **Stats**: play_history recording with the 90 % completion rule, weekly heatmap, total hours/plays.
11. **Settings**: the §8.1/8.2 subset that affects playback + lyrics + appearance (default mode, resume, autoplay,
    loudness, lyric source, verbatim, pure white, mini-player style, CDN preference).
12. **Downloads**: single/batch download to app storage, task list with retry, offline playback from cache, delete.
13. **Light/dark** following system + Material You dynamic color; no skin engine yet (but leave asset hooks).

**Deliberately simple in v1**: single-activity Compose + Navigation (bottom bar 3 destinations, player as a full
screen route with bottom-sheet animation), no tablet rail (or a static rail), no sharing/backend, no theme install.

## 10.2 Later / optional (v1.x–v2)

| Feature | Why later | Notes |
|---|---|---|
| Skin (Bilibili 装扮/收藏集) install + apply | large surface, N+1 API calls, asset download + validation, per-surface wiring | biggest differentiator; isolate behind an interface from day 1 |
| Desktop floating lyrics overlay | needs `SYSTEM_ALERT_WINDOW`, custom view, drag/lock/panel | high perceived value, moderate cost |
| Status-bar lyrics (SuperLyric/Lyricon/Meizu) | third-party deps / OEM-specific | keep an extension point |
| Car lyrics in metadata | cheap once metadata updates exist | 1 day |
| Podcast mode (subtitle reader, search in subtitles, chapters, ±15 s, per-track resume) | much extra UI + subtitle fetch | keep `playbackMode` in the model now |
| Local ↔ Bilibili folder sync (outbox, LWW, sync-failures UI) | correctness-heavy | data model already supports it |
| Netease/QQ playlist import | fuzzy matching + rate limits (1.2 s delay) | independent screen |
| Download → m4a export with tags/cover/lyrics | SAF + tag writing (jaudiotagger or manual MP4 atoms) | nice-to-have |
| Shared playlists / BBPlayer account / WebDAV backup | server dependency, auth | optional ecosystem |
| Spectrum visualizer | RECORD_AUDIO permission + FFT | cosmetic |
| Wave slider animation | custom Compose path animation | cosmetic but iconic |
| Theme boot-splash video | extra codec/size risk | cosmetic |
| TV focus support, 6-column library grid, two-pane player | form-factor polish | after phone is solid |

## 10.3 Concrete porting plan (ordered, each step independently shippable)

1. **Core lib**: `bilibili-api` (Ktor/OkHttp + WBI signer + cookie store), `wbi`, `bv2av`/`av2bv`, `json` models.
2. **Room schema** mirroring §3.1 + DAOs; migration-free fresh install; seed none.
3. **Playback service** (Media3) with the internal URI resolver + cache chain + notification; a debug screen to play any BV.
4. **Search/BV ingest** + minimal library lists (fav/collection/uploader/search) + player screen.
5. **Local playlists** + queue sheet + repeat/shuffle + sleep timer + speed.
6. **Lyrics** (splash-like parser in Kotlin, Netease/QQ/Kugou clients, cache file, offsets, karaoke rendering).
7. **Stats** + profile tab (heatmap).
8. **Downloads** + downloaded screen.
9. **Settings** screens, CDN/quality plumbing, loudness normalization.
10. **Skin engine** (parse garb → download → map to theme tokens), then overlays (desktop/status bar).

## 10.4 Risks & blockers found in the upstream

1. **WBI depends on a hard-coded 64-entry permutation table** and a per-day key fetch; it silently breaks when Bilibili
   rotates. Must live in one place with tests (`M/src/lib/api/bilibili/wbi.ts`, `O/.../bilibili/WbiUtil.kt`).
2. **Undocumented/HTML-scraping dependency**: b23.tv resolution parses `<link rel=canonical>` (`api.ts:1324-1347`).
3. **Cleartext HTTP** for Kugou lyric endpoints — Android will block unless configured.
4. **Netease weapi/eapi crypto** (AES/RSA + response decryption) is a non-trivial port; consider limiting v1 auto-lyrics to
   Netease `/eapi/song/lyric/v1` only (that is the one endpoint that needs eapi) and QQ (plain JSON) while dropping Kugou.
5. **Quality is fixed** at 30280 with Dolby/Hi-Res disabled; if the rewrite wants quality selection it must add UI *and*
   pass `qlt`/`dolby`/`hires` through the resolver (native already supports it).
6. **Two sources of truth for theme/skin and queue persistence** (JS MMKV + native MMKV). In a native app keep one.
7. **Play-history reporting** to Bilibili is opt-in and can trip risk control; keep default off.
8. **`uniqueKey` coupling**: lyrics file names, DB records, player queue ids and play history all key off it. Any change is
   a data migration.
9. **Scale**: upstream has ~377 TS files / 71k LOC in `apps/mobile/src` plus ~11k lines of native Kotlin. A faithful port of
   everything is far beyond a v1; the MVP list above is the defensible subset.
10. **Third-party lyric endpoints are unofficial** and rate-limit/break without notice; design the provider layer as
    pluggable with graceful failure (upstream already treats it as `Promise.any` + cache).

---

# Appendix A — file index for the porting team (highest value first)

| Area | Files to read |
|---|---|
| Playback engine (native) | `packages/orpheus/android/src/main/java/expo/modules/orpheus/service/OrpheusMusicService.kt`, `.../ExpoOrpheusModule.kt`, `.../util/DownloadUtil.kt`, `.../bilibili/BilibiliRepository.kt`, `.../bilibili/WbiUtil.kt`, `.../manager/DownloadCache.kt`, `.../network/BilibiliFallbackDataSource.kt`, `.../service/ShuffleManager.kt`, `.../util/SleepTimeController.kt`, `.../util/Volume.kt`, `.../util/GeneralStorage.kt`, `.../util/TrackResumeStorage.kt` |
| Playback API surface (JS) | `packages/orpheus/src/ExpoOrpheusModule.ts` (single best overview) |
| Lyrics engine | `packages/splash/src/{parser,converter,utils}/*`, `packages/orpheus/android/.../manager/{UnifiedLyricsManager,LyricsTimeline,FloatingLyricsManager,StatusBarLyricsManager,*Backend}.kt`, `packages/orpheus/android/.../view/LyricView.kt` |
| Bilibili API | `apps/mobile/src/lib/api/bilibili/{api.ts,client.ts,wbi.ts,utils.ts,garb.ts}` |
| Lyrics service/providers | `apps/mobile/src/lib/services/lyricService.ts`, `lib/api/{netease,qqmusic,kugou}/*` |
| DB | `apps/mobile/src/lib/db/schema.ts`, `apps/mobile/drizzle/*.sql` |
| Screens | `apps/mobile/src/app/**` (route tree in §1.6) |
| Player UI | `apps/mobile/src/features/player/components/**` |
| Playlists | `apps/mobile/src/app/playlist/**`, `features/playlist/**`, `lib/services/playlistService.ts` |
| Theme | `apps/mobile/src/lib/theme/*`, `features/theme/ThemeSection.tsx`, `hooks/stores/useSkinStore.ts`, `hooks/theme/*` |
| Settings | `apps/mobile/src/app/settings/**`, `hooks/stores/useAppStore.ts`, `types/core/appStore.ts`, `types/storage.ts` |
| Architecture notes (upstream) | `apps/mobile/docs/ARCHITECTURE.md`, `apps/mobile/docs/TECHNICAL_DEBT.md`, `apps/mobile/AGENTS.md` (repo root) |

# Appendix B — inventory numbers

- Routes: 52 files under `apps/mobile/src/app`; 46 route modules after removing 2 `_layout` and 4 `_components` internals; ~43 user-facing routes.
- Modal keys: 29 registered components (`components/ModalRegistry.tsx:104-140`) + the globally mounted queue sheet.
- DB tables: 9 (after drops); migrations: 23.
- Bilibili endpoints actually called: 40 distinct web/player paths in `M/src/lib/api/bilibili/api.ts` (+ `/x/web-interface/nav` for WBI, 5 passport URLs, the `s.search.bilibili.com` suggest URL, subtitle JSON URLs and b23.tv) — see §6.3.
- Third-party hosts: `music.163.com`, `interface3.music.163.com`, `u.y.qq.com`, `i.y.qq.com`, `c.y.qq.com`,
  `mobilecdn.kugou.com`, `krcs.kugou.com`, `lyrics.kugou.com`, `s.search.bilibili.com`,
  `passport.bilibili.com`, `be.bbplayer.roitium.com`, `*.bilivideo.com`/`*.hdslb.com` (media), `static.geetest.com`.
