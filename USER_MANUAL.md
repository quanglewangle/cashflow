# Cashflow — User Manual

This app models your **likely future cash position** — it is not
accounting software. It doesn't need every transaction to reconcile
perfectly; it needs to give you a good-enough picture to make spending
decisions. Two things follow from that:

- Firm, separate obligations in different months (e.g. two consecutive
  card bills) are never expected to net against each other — each is
  just what it is.
- A "close enough" estimate that decays into real data over time is a
  legitimate, deliberate tool (see **Decaying one-off entries** below),
  not a hack.

## The three tabs

- **Cashflow** — the main screen. Shows this month's recurring items,
  one-off entries, and card purchases in one running list, with
  Brought forward / Carried forward for the month and a live Current
  balance figure at the top. Swipe/tap the arrows to move between
  months.
- **Danger** — a 6-month-ahead forecast highlighting the lowest point
  your balance is projected to hit. For the *current* month, it only
  looks from **today** onward — a dip earlier in the month that you
  already survived doesn't count as still-upcoming danger.
- **Cards** — manage your credit cards: purchases, subscriptions, and
  balance checkpoints per card.

The overflow menu (⋮) on the Cashflow tab has **Balances** (record a
main-account balance checkpoint) and **Categories** (manage the
category list).

## Recurring items

A recurring item is a template — Council Tax, Netflix, a pension, etc.
— that automatically generates an entry every month (or every year,
for annual items). Editing a template's amount/day only affects
*future* entries; a month that's already been generated keeps its own
value until you edit that specific entry.

Frequencies: **monthly**, **annual** (fires in one named month),
**last working day** (auto-computed each month), **four-weekly**
(drifts against the calendar), **three-monthly**.

## One-off entries

For anything that isn't recurring — a single purchase, a gift, an
irregular bill. Add one from the **+** button on the Cashflow tab.

### Decaying one-off entries

Optional "Decays by £/week" field. Use this for a contingency estimate
— e.g. "I expect roughly £400 of miscellaneous card spending this
month that I haven't logged individual purchases for yet." Set it once
with a weekly decay rate, and its effective amount shrinks on its own,
week by week, as real purchases replace the guess — instead of you
manually editing the number down every week. It never goes below £0,
and once you mark it paid, decay stops (the actual amount is used
from then on).

The list and edit dialog always show the **current, decayed** amount;
editing prefills the **original** amount (the base you're decaying
from), so re-saving other fields doesn't reset anything.

For some cards, this buffer is created for you automatically every
period rather than something you add by hand — see **Auto-generated
sundries buffer** under Credit cards below.

### Tagging a one-off entry to a card

The "Card" picker on the add/edit dialog is for something different
from "this was bought on the card" bookkeeping — it makes that entry's
amount **fold directly into that card's own payment total** for
whichever month you set, instead of counting against your main
balance separately. This is exactly what the decaying-sundries pattern
above is for: a buffer that inflates the *card's* upcoming bill
estimate, not a random extra expense line in your main forecast.

**Important**: once you've taken a fresh card checkpoint that actually
covers the spending the buffer was estimating, the buffer becomes
redundant and should be deleted — otherwise it keeps adding on top of
the real, verified figure. Adding a new checkpoint will now prompt you
to remove any leftover one-off tagged to that same payment period (see
below) — but if you create one *after* the prompt already fired, you
still need to remember to clean it up yourself once you're done
spending for that cycle.

## Credit cards

Each card (except any set up as a flat fixed payment, see below) has
its **own recurring item** that represents "pay the bill" — its amount
is computed automatically, not typed in.

### How a card's payment amount is worked out

1. Start from the **latest checkpoint** that applies to this payment
   period (see below), if any.
2. Add every **real purchase** logged after that checkpoint that
   belongs to the same statement cycle.
3. Add any **card-tagged one-off entries** (e.g. a decaying sundries
   buffer) set for this exact month.
4. Subtract the **immediately-preceding period's own bill**, if it's
   still unpaid — a checkpoint is a snapshot of everything posted to
   the card, including a just-prior bill that hasn't actually been
   paid off yet; without this step that amount would be double-counted
   (once as its own month's bill, again baked into the checkpoint).
5. If none of the above produced a number (nothing logged yet), it
   falls back to the recurring item's flat default estimate.

Which calendar month a purchase (or checkpoint) belongs to is worked
out from the card's own **statement day**, **payment due day**, and
**month offset** (Cards tab → edit card). A purchase dated on/before
the statement day stays in that cycle; after it, rolls to the next.

### Auto-generated sundries buffer

Jenny's card and Visacard don't use step 5's flat default at all — their
recurring item's default is deliberately set to £0, and instead a fresh
decaying sundries buffer (step 3 above) is created **automatically for
every period**, so a month you haven't started spending on yet still
shows a sensible estimate instead of £0. This is the same decaying
one-off described above, just created for you rather than added by
hand — it decays, folds into the card's total, and shows up in "How was
this calculated?" exactly like a manual one would.

It still follows the same cleanup rule: once a fresh checkpoint actually
covers the period, the auto-generated buffer becomes redundant and the
checkpoint-add prompt will offer to remove it, same as a manual one.

Which cards use this and at what starting amount/decay rate isn't
editable from the app yet — it's set directly on the recurring item's
`sundries_amount`/`sundries_decay_per_week` fields on the backend. Ask
whoever manages the server if a card needs adding to or removed from
this list.

### Card checkpoints

Record the actual balance owed, checked against the real bank/card
app, from Cards → a card → checkpoints → **Add checkpoint**. This is
the main way to correct drift instead of trusting logged purchases
alone. If there's already a card-tagged one-off entry sitting in the
period the new checkpoint anchors, you'll be asked whether to remove
it — say yes once you're confident no more spending is coming for that
cycle.

### "How was this calculated?"

**Long-press** any card's payment row on the Cashflow tab to see
exactly what made up the figure: the checkpoint used, what it already
covers, purchases added since, any card-tagged one-offs, and any prior
unpaid bill netted out.

### Logging a purchase

Three ways:
- **Manually**: Cards tab → a card → **Log purchase**.
- **Google Pay tap-to-pay**: a notification appears automatically when
  a recognised card is tapped; tap it to confirm and log (nothing is
  ever added silently). Requires Notification Access to be granted to
  the app (Settings → Apps → Special access → Notification access) —
  this can get silently revoked by the OS after reinstalling the app,
  so if it stops working, check there first.
- **PayPal receipt email**: share the receipt email (from Gmail, etc.)
  to Cashflow via the Android share sheet. It parses the amount,
  merchant, date, and card, then opens the same confirm screen.

### A card that isn't paid in full each cycle

If a card carries a promotional/revolving balance and you pay down a
chosen amount each month rather than the full statement (this was set
up for Barclaycard), its recurring item is **not** linked to the card
(`credit_card_id` cleared) — it's just a plain flat monthly expense you
adjust manually via "mark paid", same as any ordinary bill. Checkpoints
for that card are still recorded for reference (Cards tab) but don't
drive the monthly figure.

## Categories

Categories can have **subcategories** (e.g. Car → Fuel / Insurance /
Servicing & MOT). Every category picker in the app groups them this
way automatically.

**Unclassified** is a deliberate catch-all, not a mistake — some things
are put there on purpose.

## Balance checkpoints (main account)

Separate from card checkpoints — this is your actual bank balance,
checked and recorded from the Cashflow tab (overflow menu → Balances,
or tap "Tap to record checkpoint" next to today's row). The forecast
always anchors to the most recent one at or before the period being
shown; anything before it is disregarded, since the checkpoint itself
is treated as **definitive**.

## Key figures

- **Brought forward** — the balance at the start of the month (chained
  from last month's Carried forward, unless a fresher checkpoint
  overrides it).
- **Carried forward** — Brought forward plus this month's income minus
  expenses (only counting what falls after a checkpoint, if one lands
  mid-month).
- **Current balance** — your balance *right now*: everything up to
  yesterday, plus only today's items that are actually marked
  done/paid so far (not the day's still-planned ones).
