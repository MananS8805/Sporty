# Roadmap

Ideas for turning Sports Hub into the best all-sports insights app. The app stays phone-only (no server, no account) unless noted.
Status key: **Done** (shipped), **Next** (recommended order), **Later**.

## Already built

- Follow teams across about 290 competitions, including national football and cricket teams
- Home feed: story circles, team-coloured hero cards with countdown, day-grouped timeline, news
- Team pages: parallax hero, next match, forecast ring, form, table position, squad, where to watch
- Forecasts from bookmaker odds (via ESPN) with the margin removed, and a breakdown of every input
- Squads: football rosters, F1 drivers, cricket match squads
- Where to watch: curated India table (last checked 2026-10-14), ESPN broadcasters elsewhere, search fallback
- Kickoff, goal and full-time notifications (WorkManager, about 15-minute resolution)
- Settings: notification toggles, reminder lead time, region override

## Tier 1: make it dependable (Next)

1. **Offline cache.** Persist the last feed, team pages and squads so Home opens instantly and works without signal. Cold start currently waits several seconds on the network.
2. **Hosted rights file.** Move the where-to-watch table to a small JSON file fetched at runtime (for example from this repo), so rights changes reach the app without a release.
3. **Data-source resilience.** ESPN's feeds are public but unofficial and can change or block. Add a backup source per sport, and surface a clear "data provider unavailable" state.
4. **Crash and error reporting**, so problems are found without a user report.
5. **Remaining UI checks.** Verify My teams, Settings and the squad screen on device, fix news thumbnails if they still fail to load, and send a real notification end to end.

## Tier 2: make it feel alive (Next)

6. **Live match center.** Event timeline (goals, cards, wickets), lineups, team stats and commentary, from ESPN match pages.
7. **Lock-screen live score and home-screen widget** (Glance). Next match and live score at a glance.
8. **Weekly/daily digest notification** summarising the week's matches across your teams.
9. **Calendar sync.** Add fixtures to Google Calendar and flag clashes between your teams.
10. **Pre-match preview.** Head-to-head, form, injuries and lineups before kickoff.
11. **Post-match recap.** Stats and key moments.
12. **Cricket scorecards.** Batting and bowling cards from ESPN match pages.
13. **F1 weekend view.** Qualifying grid, race results, pit stops and lap data (Jolpica).

## Tier 3: breadth and depth (Later)

14. **More sports on the same ESPN adapter:** tennis, golf, UFC, MotoGP, women's leagues.
15. **Player following** (for example Hamilton, Kohli) with stats and form.
16. **Where to watch by subscription.** Pick the services you pay for; hide the rest; deep-link into the apps.
17. **Cricket and F1 forecasts.** Needs a free The Odds API key supplied by the user (cricket only, to be verified). No free expert source was found for F1.
18. **Shareable match-preview cards** for group chats.
19. **Tablet, foldable and Wear OS layouts.**

## Needs a server (out of scope while phone-only)

- Instant push alerts (today: about 15-minute checks)
- Friends' prediction leagues and leaderboards
- Shared accounts across devices

## Known limits and risks

- ESPN's API is unofficial and rate-limits by IP; the app throttles, caches and backs off, but a block is possible.
- Cricket and F1 have no free expert forecast, so none is shown.
- IPL squads: ESPN publishes only the playing XI from the latest match, not the full squad.
- India broadcast rights come from news sources and change each season; La Liga's India rights are unconfirmed.
- Goal alerts can lag by up to about 15 minutes (Android's minimum for background checks).
- A Play Store release needs a review of the data sources' terms and an HTTPS-only build.
