# Worker limits and performance

```infobox
type: Setting
image: https://jcondedata.github.io/minecraft-alive-workplace/config/still-1.jpg
caption: The settings screen's numbers
Workers per Village: 0, no limit (0 to 500)
Worker Path Range: 48 blocks (16 to 128)
Villages Work Unseen: on
categories: Settings
related: settings.md, settings-reach-and-range.md, game-rules.md, jobs.md
```

Three settings keep a big server light when it has many busy villages. On a small server you can leave them alone.

## Workers per Village

Normally **0, no limit**. Set a number, and jobless villagers stop taking free workstations once that many workstations
in their village are taken. Nobody loses a job they already have. Use it to keep any one village from turning into a hundred-worker factory.

## Worker Path Range

Normally **48 blocks**, the same as vanilla. It's how far villagers with a job look for a path in one go. Lower is
lighter on the server: a longer walk is simply made in legs. Raise it only if workers get stuck on long walks.

## Villages Work Unseen

Normally **on**: villages keep working when no player is near them. While anyone is online, each worker's workstation
stays loaded, so farms are tended and builds go up across the map. Off: villages nobody is near take a break until
someone comes back. With nobody online at all, nothing is kept loaded either way.

## How they interact

- **Builds and quarries** have their own switch for staying loaded: the `workplaceKeepWorkLoaded`
  [game rule](game-rules.md).
- **How far workers reach** is set in [Reach and range settings](settings-reach-and-range.md); shorter ranges also
  mean shorter walks.
- **Village growth** has its own cap, **Population Cap**: see [Village life switches](settings-village-life-switches.md).
