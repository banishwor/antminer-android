# Cloud Relay endpoint (Google Apps Script)

The app relays miner snapshots to a Google Sheet through an Apps Script
web app (the URL in Device → Cloud Farm Relay). **The deployed script's
source is not part of this repository** — it lives in the script owner's
Google account. This folder documents the wire contract and ships a
reference implementation ([`Code.gs`](Code.gs)) that fulfils it.

## Wire contract

| Call | Payload | Response |
| --- | --- | --- |
| GET | `?user=<name>` | `{status, user, canView, miners[]}` — the user's own rows plus rows of owners who granted them view access |
| POST | `{user, mode: "upsert", miners[]}` | `{status, count}` — one row per `(user, id)`, replaced on each write |
| POST | `{user, action: "deleteRows", ids[]}` | `{status, count}` — deletes the caller's own rows with those ids |

Each miner row: `id` (the machine's **pool worker username** — its one
stable identity), `nickname`, `ip`, `hashrate5s`, `avgHashrate`,
`maxTemp`, `maxFan`, `status`, `lastUpdated` (stamped server-side).

## What the client guarantees (v0.1.5+)

- A machine is uploaded **only** under its worker username. If a saved
  profile does not know the name yet, that machine's upload is deferred
  (the background relay first reads it from the miner's own pool
  config); the per-device profile UUID is never sent as an id.
- Uploads declare `"mode": "upsert"` — replace, never append.
- After relaying, the client asks the endpoint to delete the user's own
  legacy duplicate rows: the same machine under an old UUID id (or a
  case-variant worker id), selected by `RelayUpload.legacyDuplicateIds`.

## Limitation of older endpoint versions

The originally deployed script predates the upsert/delete contract:
it does not implement `"mode": "upsert"` as a distinct operation and
does not answer `deleteRows` requests (they return an error the client
ignores). Against such an endpoint the client still never *creates*
duplicates, and the app collapses any existing ones on the read side —
but physical removal of legacy rows from the sheet requires either
redeploying [`Code.gs`](Code.gs) over the script (same `/exec` URL,
data untouched) or deleting the duplicate rows in the sheet by hand.
