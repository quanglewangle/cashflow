# cashflow (Android)

Cashflow forecast app, replacing the manual budget spreadsheet. Talks to
the [cashflow-backend](https://github.com/quanglewangle/cashflow-backend) REST API on
fimblefowl.co.uk, with a local Room database used purely as an offline
cache (same pattern as `rocloc`) -- the server is the source of truth.

## Screens

- **Forecast**: rolling 12-month view (brought forward / income / expense
  / carried forward per month). Tap a month to open its entries.
- **Entries**: one month's line items. "Mark paid" records the actual
  amount and flips status to incurred; add one-off items not backed by a
  template via the **+** button.
- **Grid**: spreadsheet view -- items as rows, months as columns --
  colour-coded by type. Card-charged items are greyed out (they don't
  touch cash until the statement is paid).
- **Items**: manage the recurring-item templates (monthly / annual /
  irregular) that generate each month's entries -- this is what makes
  annual payments (insurance, glasses, etc.) reappear automatically next
  year instead of being zeroed and lost like in the spreadsheet. Sorted
  by due day of month; due day shown as a badge on each row.
- **Cards**: manage credit card statement-closing and payment-due days.
- **Balance checkpoints**: record actual bank balances to anchor the
  forecast and spot drift.

## First-time server setup

See the [cashflow-backend README](https://github.com/quanglewangle/cashflow-backend#readme). In short: `createdb cashflow`, load
`schema.sql` (seeds the 3 cards, default categories, and an opening
balance row -- edit `opening_balance`/`opening_year`/`opening_month` in
that file, or via `PUT /settings`, to match reality), deploy the Go
service, add the nginx block.

## Building

```
./gradlew assembleDebug
```

Requires the JDK referenced in `gradle.properties`
(`/home/peter/.java21-jdk`) and an Android SDK at the path in
`local.properties` (not committed -- create your own pointing at your SDK).

## Known gaps / follow-ups

- No in-app screen for editing the opening balance yet -- edit
  `schema.sql` before first load, or `PUT /settings` directly.
- The forecast view is online-only (computed server-side); if the device
  is offline you can still browse/edit a previously-loaded month's
  entries from the Room cache, but the Forecast tab needs connectivity.
- No write-token UI -- if you deploy the backend with
  `CASHFLOW_WRITE_TOKEN` set, call `ApiService.setWriteToken(...)` after
  construction (e.g. in `Repository`'s constructor) before shipping.
