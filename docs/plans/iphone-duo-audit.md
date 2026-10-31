# iPhone Duo simulator audit

Date: 2026-10-31. Original audit followed by a local implementation and simulator
validation (see the implementation record below). Server-backed acceptance remains open.

## Baseline and scope

- Repository: https://github.com/music-assistant/mobile-app
- Unchanged source: `40ae3126ca5be27bff9e577f75bc991a67ef5a1e`.
- Xcode 27.2 RC, iOS 27.2 Duo simulator (device type iPhone19,4).
- Simulator UUID used: `E03D35B9-A46A-4D28-900A-C38AF9DFAA54`.
- Built, installed, launched, and exercised Discover, mini player, expanded player,
  and queue across outer/inner displays and rotation. Partially folded inner
  landscape inspected. This is not an exhaustive screen, dialog, or orientation audit.
- No real Music Assistant credentials were supplied. An aiohttp HTTP/WebSocket
  fixture supplied six synthetic tracks, a paused speaker, queue index 0,
  position 68 seconds, and volume 37. Artwork was absent. The fixture tracks
  appeared unavailable: disabled playback and “Cannot play this item” are not
  established app bugs. Audio, real authentication, reconnects, and streaming
  continuity were not tested.
- No public Duo-specific plan was identified in the initial issue/PR search;
  absence from public search is not evidence about private maintainer plans.
  Existing expanded navigation work: https://github.com/music-assistant/mobile-app/pull/197.

## Observed results

| State | Result |
| --- | --- |
| Inner portrait Discover | Bottom navigation and mini player render; tracks remain visible. |
| Inner landscape Discover | Existing navigation rail and inline mini-player controls activate. |
| Inner portrait player | Single player pane and collapsible queue button; header is below status area. |
| Inner landscape player | Existing 2:1 player/queue layout activates, six queue entries visible. Header overflow coincides with system status area. |
| Outer portrait player | Single pane fits, but front camera covers header's overflow-button location. |
| Outer landscape player | Side queue activates; camera overlaps the collapse control. |
| Partially folded inner landscape | Still the same 2:1 split, not crease-aligned. Player artwork occupies the left half; seek and volume sliders extend across the central crease. |
| Display/pose transitions | Current title, six-item queue, position, and volume remain displayed without manually reopening the app. This does not prove live audio continuity. |

The outer-camera overlap is a concrete compatibility defect. Fold-spanning controls
are a layout deficiency, not evidence of failed touch delivery: touch accuracy
on a physical crease has not been measured. Do not treat host-window rotation
or stale Simulator screenshots as app crashes; use device screenshots to inspect
the actual framebuffer.

## Source ownership and constraints

- `ui-utils/.../WindowClass.kt`: `isWide()` uses window width class and container
  aspect ratio, not device identity. Keep this behavior for normal windows.
- `nav/.../AdaptiveNavigationLayout.kt`: existing rail/bar switching works.
- `composeApp/.../home/players/PlayersPager.kt`: expanded content applies only
  `WindowInsets.safeDrawing.only(WindowInsetsSides.Top)`; player and queue use
  a width-based 2:1 split. Ordinary edge insets cannot describe an interior fold.
- `iosApp/iosApp/ContentView.swift`: Compose host ignores container safe area.
  This is relevant evidence, not proof that removing this modifier alone fixes
  every occlusion. Backgrounds should still be allowed to extend edge to edge.
- `iosApp/iosApp/iOSApp.swift`: phone orientation preference exists, but this
  audit did not establish it as an obstacle; both orientations rendered.
- No explicit Duo reserved-region handling found. Native system toolbar behavior
  does not automatically apply to Compose-drawn navigation.

## Smallest recommended implementation

Keep Kotlin/Compose, existing view models, navigation, and ordinary layouts.
Do not build a separate Duo app or replace the UI with SwiftUI.

1. Fix the demonstrated header occlusion first. Check what the current Compose
   runtime exposes for safe drawing on Duo. Extend existing inset handling to
   keep interactive header content out of the camera/status region in both
   orientations; do not solve it with device names or fixed camera padding.
   If existing insets cannot express the obstruction, use the native bridge below.
2. Add one small, availability-guarded iOS geometry bridge at the Compose host.
   Read current-view reserved regions, map their frames into Compose coordinates,
   and update on layout/pose changes. Export only active divisions/occlusions;
   use an empty set on older iOS and other platforms. Verify the exact Swift/Kotlin
   interop and SDK availability before choosing its API shape.
3. In the expanded player only, when an active division intersects its bounds,
   fit player and queue into the two unobstructed regions (left/right or top/bottom).
   Permit content to scroll within each pane where height is constrained. When
   there is no active division, retain today's 2:1 or collapsed-queue layout.
   Keep the same player/queue state; do not create a second controller or session.

For a compatibility-only first patch, step 1 is the smallest useful delivery.
For actual fold-aware player support, steps 1–3 are the smallest coherent scope.
Do not migrate navigation bars, globally change breakpoints, change orientation
preferences, or add a new library without a reproduced need. Discover's ordinary
resize behavior does not justify a redesign. Other dialogs/popovers require a
follow-up targeted pass before claiming app-wide Duo support.

## Acceptance gates for implementation

- In outer portrait and both landscapes, collapse and overflow controls are
  visible, outside occlusions, and can actually be tapped.
- Flat-open Discover still switches navigation appropriately; flat-open player
  retains current layouts when no active division exists.
- Partially folded with both fold axes: no seek/volume control or queue row
  crosses an active division; each pane remains usable at reduced height.
- Repeated close/open/rotate preserves selected speaker, queue, position, and
  current navigation. Validate real playback separately against a real server.
- Inspect player menu, player selector, search keyboard, and representative modal
  in affected poses. Confirm regular iPhone and iPad layouts do not regress.
- Stop after these gates; do not expand into a native-navigation rewrite.

## Reproduction notes

Mac clone used `/Users/runner/ma-audit` at the baseline revision. Install JDK 21
and download/extract the WebRTC framework used by project CI into
`iosApp/Frameworks` from
https://github.com/teancom/webrtc/releases/download/m125-cow-bloat-fix.1/WebRTC.xcframework.m125-cow-bloat-fix.1.zip.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,id=E03D35B9-A46A-4D28-900A-C38AF9DFAA54' \
  -derivedDataPath /tmp/ma-build CODE_SIGNING_ALLOWED=YES \
  CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM='' build
xcrun simctl install E03D35B9-A46A-4D28-900A-C38AF9DFAA54 \
  /tmp/ma-build/Build/Products/Debug-iphonesimulator/MusicAssistantClient.app
xcrun simctl launch E03D35B9-A46A-4D28-900A-C38AF9DFAA54 io.music-assistant.mobile-client
xcrun simctl io E03D35B9-A46A-4D28-900A-C38AF9DFAA54 screenshot --display=1 /tmp/outer.png
xcrun simctl io E03D35B9-A46A-4D28-900A-C38AF9DFAA54 screenshot --display=3 /tmp/inner.png
```

Build result: **BUILD SUCCEEDED**. Disabling signing entirely launches into a
Siri entitlement failure; use Xcode ad-hoc simulator signing as above.

For repeat validation prefer a real development server with the same six-track
shape. The disposable fixture bypassed discovery with a synthetic saved direct
connection to forwarded port 18095; it was not a faithful server substitute.

Apple reference:
https://developer.apple.com/documentation/technologyoverviews/preparing-your-app-for-iphone-duo

No implementation, commit, push, or pull request was made during the original audit.

## Implementation record (2026-10-31)

The implementation checkout matched the audit baseline exactly; no newer source
differences needed reconciliation.

### Settled implementation decisions

- Keep the SwiftUI representable, Compose navigation, window breakpoints, orientation
  preference, player/view models and playback/session ownership unchanged.
- Expanded `PlayersPager` consumes top **and horizontal** `safeDrawing` insets.
  Native inspection confirmed that Compose receives the outer display's 84-point
  side safe area. No fixed camera padding or device-name checks are needed.
- `ContentView.swift` owns one UIKit container and one Compose child controller.
  On iOS 27.1+, read `UIView.reservedRegions(kind:)` for `.division` and
  `.occlusion`, filter `isActive`, and convert the already-margin-expanded frames
  to the Compose child's coordinates. Query from `updateConstraints` (required for
  UIKit's automatic tracking) and after layout (updated coordinate conversion).
  `PlayerWindowGeometry` is per host; older systems and other platforms stay empty.
- `PlayerPaneLayout` converts points using Compose density and subtracts its
  `boundsInWindow` origin, accounting for already-consumed insets and pager headers.
  Only an active intersecting division changes arrangement. Player and queue occupy
  opposite sides of its outward-rounded bounds, for either axis. Clip each folded
  pane; give the player a scrollable minimum content height from the existing
  medium-height breakpoint. The queue retains its existing lazy scrolling.
- Header/player/queue have stable composition slots; do not create another player,
  controller or session. Disable the full-player vertical dismiss/expand gesture
  while folded so it does not compete with pane scrolling; collapse remains a button.
  Without a division, retain the 2:1 wide layout or collapsible narrow queue.
- A header occlusion must not always push content downward: iPad rotation can
  report a full-height side strip. Choose the largest remaining rectangle beside
  or below an intersecting occlusion. The initial downward-only implementation
  reproduced an offscreen iPad header; the corrected version was rendered again
  through portrait/landscape rotation successfully.

### Validation and provenance

- New Mac satellite: macos-27, Xcode 27.2 RC, JDK 21. Use the build/signing and
  WebRTC dependency commands above, substituting a currently available simulator
  UUID. This run used Duo `0C7042A9-3F1D-4D59-B5CE-E239F77762FF`.
- Final normal `App()` build, after removing all temporary validation code and
  geometry logging: **BUILD SUCCEEDED** with ad-hoc simulator signing.
- `CI=true ./gradlew :composeApp:detekt :composeApp:iosSimulatorArm64Test`:
  **BUILD SUCCESSFUL**, 1,041 tests, zero failures/errors. No UI-test dependency
  was added; these existing tests do not replace the rendered checks.
- Render validation used the real `ExpandedPlayerPage`, queue, overflow menu and
  `SelectPlayerSheet`, temporarily called from the normal Compose host. Inputs
  came from the repository's `AppMediaItemFixtures.track` and
  `PlayerDataFixtures.playerData`: six tracks converted with `toQueueTrack` /
  `toQueue`, elapsed time 68 seconds, speaker name "Validation speaker", volume
  37. Collapse opened a temporary confirmation alert; volume actions updated the
  fixture state. This was not a fake network server and did not establish playback.
  The temporary entry and alert are removed from delivered source.
- Inspected outer portrait, both outer landscapes, flat inner, both fold axes,
  ordinary iPhone 17 Pro portrait, and iPad Pro portrait/landscape. For outer
  landscape testing enable the app's existing `allow_landscape_all_devices`
  preference in simulator defaults; do not change orientation code.
- Native inner landscape division: x=455.5…495.5 in a 951×669-point window.
  Portrait division: y=455.5…495.5 in a 669×951-point window. Flat pose removes it.
- Pointer checks opened/dismissed the overflow menu and real player selector,
  scrolled upper player content independently of the lower queue, and exercised
  collapse confirmation in outer portrait and both outer landscapes. Overflow
  opened in outer portrait, both outer landscapes and the folded pane. Initial
  landscape tap captures were inconclusive; fresh pointer taps on the settled
  layout produced visible menus, with accessibility confirming their entries.
  Accessibility exposed `Collapse`, `More`, the current speaker, six named queue
  rows, seek value 68, and volume 37. After a volume adjustment to 60 and
  close/open/rotate, it still exposed the same speaker, six rows, seek 68 and
  volume 60.26. This demonstrates fixture/UI continuity, not audio continuity.
- Evidence is under `.amp/in/artifacts/duo-audit/` (not source-controlled): original
  transferred baseline images, inspected `duo-interactions-final.mov`, and final
  captures in `duo-evidence/`. Duo captures are native 3×, iPad captures 2×.
  Use Device Hub for pose/rotation and pointer input. `simctl io ... screenshot
  --display=1` captures outer; `--display=3` captures inner. The desktop recording
  is cropped to the device and shows a real moving pointer. `idb ui describe-all`
  was useful for accessibility; direct idb touch/rotation was unreliable here.

### Remaining acceptance gates / single next objective

Connect a real development Music Assistant server and run the full navigation,
search-keyboard, modal, selected-player and live-playback continuity pass across
folding/rotation. Credentials/server data were not available in this run. Discover
navigation and search keyboard were not rerun with the implementation; the original
audit remains the only Discover evidence. Real seek/volume writes, audio, reconnects,
and physical-device touch accuracy are not validated. Do not label this app-wide Duo
support or claim every audit gate passed. No native-navigation redesign is justified.
