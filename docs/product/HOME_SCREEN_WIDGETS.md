# Home-screen widgets

Luteal exposes two independent Android home-screen widget providers:

- **Luteal · Mon cycle** reads the local cycle history and uses the same
  `CycleEstimateCalculator` as the Today screen.
- **Luteal · Duo** reads only the grant-filtered `DuoProjection` cache. It never
  derives shared content directly from the tracker's private repositories.

## Privacy boundary

Every widget instance starts concealed. Its Glance preference stores only the
concealed state and refresh feedback; health data remains in Room. A concealed
render does not load cycle data, so the generated `RemoteViews` contains only
neutral copy.

Revealing content necessarily sends that visible content to the launcher. The
launcher may retain a previous render, preview, or screenshot after the widget
is concealed again. The Settings privacy copy states this limitation. Widgets
never show private notes, symptoms, mood, energy, or support-message content.

## Recorded facts and estimates

The personal widget derives a cycle day only from an open, recorded cycle start
that is not in the future. Estimates remain ranges and always carry an explicit
estimated label. `NeedsMoreHistory` and `IntervalsOutOfRange` remain distinct
states; neither is presented as a diagnosis or a recording failure.

The Duo widget never advances a cached shared cycle day locally. Doing that
would assume no new cycle was recorded and would create information that was
not actually shared.

## Responsive layouts

Glance uses a bounded `SizeMode.Responsive` set:

| Layout | Minimum size | Content |
| --- | ---: | --- |
| Compact | 110 × 72 dp | One primary fact and privacy control |
| Standard | 180 × 110 dp | Cycle fact and estimated range |
| Wide | 250 × 110 dp | Two-column recorded and estimated facts |
| Expanded | 250 × 180 dp | Supporting state and contextual action |

French text and the 48 dp privacy control determine the breakpoints. Launchers
may map these dimensions to different cell counts.

## Updates

- A process-scoped observer coalesces Room and DataStore changes while the app
  is alive.
- One WorkManager task re-renders date-sensitive content after local midnight.
- Date, time-zone, time, and locale broadcasts reschedule maintenance.
- Duo uses one immediate and one twelve-hour periodic worker, both constrained
  to network connectivity and gated by `SyncMode.ONLINE_CLOUD`.
- Transient Duo failures preserve the last valid cache. A confirmed missing
  relationship clears it.

Widget rendering itself never accesses the network.

## Verification

The feature is covered by pure snapshot tests, Glance layout tests, translation
parity tests, and an Android database migration test. Release checks must still
include representative launchers, 200 percent font scaling, TalkBack, light and
dark themes, process death, resize, and local date changes.
