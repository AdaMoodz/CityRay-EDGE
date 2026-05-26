# MacroDroid Setup

Edge Pro includes a broadcast receiver for MacroDroid `Send Intent` actions.

The public app name is `EDGE_PRO`. The internal Android package remains `com.cityray.edge` so existing macros and upgrades keep working.

## Send Intent Target

- Target: `Broadcast`
- Package: `com.cityray.edge`
- Class: `com.cityray.edge.MacroDroidReceiver`

## Actions

Start Edge:

```text
com.cityray.edge.macrodroid.START_EDGE
```

Toggle Edge:

```text
com.cityray.edge.macrodroid.TOGGLE_EDGE
```

Hide Edge:

```text
com.cityray.edge.macrodroid.HIDE_EDGE
```

Open last split:

```text
com.cityray.edge.macrodroid.OPEN_LAST_PAIR
```

Open App Control:

```text
com.cityray.edge.macrodroid.OPEN_CONTROL
```

Open overlay permission:

```text
com.cityray.edge.macrodroid.OVERLAY_PERMISSION
```

Launch split slot:

```text
com.cityray.edge.macrodroid.LAUNCH_PAIR
```

Extra: `pair_index`

Values: `0` through `5` for split slots, `6` for last split.

Launch dock app:

```text
com.cityray.edge.macrodroid.LAUNCH_DOCK
```

Extra: `dock_index`

Values: `0` through `9` for Edge dock slots.
