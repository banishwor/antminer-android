/**
 * Antminer Cloud Relay — Google Apps Script endpoint (reference implementation).
 *
 * Implements the wire contract the Android app already speaks:
 *
 *   GET  ?user=<name>
 *     -> { status: "success", user, canView, miners: [ row, ... ] }
 *        Rows owned by <name>, plus rows whose owner granted <name>
 *        view access in the "Grants" sheet. canView lists those owners.
 *
 *   POST { user, mode: "upsert", miners: [ row, ... ] }
 *     -> { status: "success", count }
 *        UPSERT: one row per (user, id). A write replaces the existing
 *        row for that machine id — the sheet never accumulates a second
 *        row for a machine the app already relayed. lastUpdated is
 *        stamped here, server-side.
 *
 *   POST { user, action: "deleteRows", ids: [ ... ] }
 *     -> { status: "success", count }
 *        Deletes the caller's OWN rows with the given ids. The app uses
 *        this to heal legacy duplicates: rows a machine accumulated
 *        under a per-device profile UUID in app versions before v0.1.5.
 *
 * Sheet layout ("Miners" tab, header row 1) — 26 columns. The first
 * ten are the original v0.1.x layout and NEVER move; the extended
 * telemetry columns (app v0.1.6+) are appended after them:
 *   user | id | nickname | ip | hashrate5s | avgHashrate | maxTemp |
 *   maxFan | status | lastUpdated |
 *   model | firmware | uptimeSec | hwErrors | hashrate30m |
 *   idealHashrate | maxChipTemp | maxPcbTemp | fans | poolUrl |
 *   poolWorker | poolStatus | acceptedShares | rejectedShares |
 *   health | chainsJson
 *
 * Packed columns (single-cell encodings the client decodes):
 *   fans       comma-separated RPMs in fan order, e.g. "5910,5880,2400"
 *   health     4 chars, 1 = healthy / 0 = not, order: hashrate,
 *              network, fans, temperature — e.g. "1101"
 *   chainsJson compact JSON array, one object per hash board:
 *              {"i","rate","ideal","freq","asics","status","chipMax",
 *               "chipMin","pcbMax","pcbMin","hw"}
 * An empty cell always means "unknown" (the miner didn't report it, or
 * the row was written by an older app version).
 *
 * Header migration: a live sheet created by an older script version
 * has only the original 10 headers. On every request this script
 * fills in any MISSING header cells with the full header set (it never
 * overwrites, moves or deletes an existing header or data cell), and
 * reads tolerate short rows — a row with only 10 cells reads its
 * extended fields as "" (unknown). Existing data is never disturbed.
 *
 * Optional "Grants" tab (header row 1): owner | viewer
 *   A row means: <viewer> may see <owner>'s miners in Remote View.
 *
 * Deploy: paste into the Apps Script project bound to the spreadsheet,
 * then Deploy -> Web app (execute as Me, access: Anyone). The app talks
 * to the /exec URL; redeploying a new version keeps the same URL.
 */

var MINERS_SHEET = 'Miners';
var GRANTS_SHEET = 'Grants';
var HEADERS = ['user', 'id', 'nickname', 'ip', 'hashrate5s', 'avgHashrate',
               'maxTemp', 'maxFan', 'status', 'lastUpdated',
               'model', 'firmware', 'uptimeSec', 'hwErrors', 'hashrate30m',
               'idealHashrate', 'maxChipTemp', 'maxPcbTemp', 'fans',
               'poolUrl', 'poolWorker', 'poolStatus', 'acceptedShares',
               'rejectedShares', 'health', 'chainsJson'];

function minersSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(MINERS_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(MINERS_SHEET);
    sheet.appendRow(HEADERS);
    return sheet;
  }
  ensureHeaders_(sheet);
  return sheet;
}

/**
 * Extends the header row to the full HEADERS set, in place: any header
 * cell that is blank gets the header for its position. Existing header
 * text is never overwritten — the original ten columns keep their
 * positions, so a pre-v0.1.6 sheet migrates by gaining columns 11–26.
 */
function ensureHeaders_(sheet) {
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
    return;
  }
  var width = Math.max(sheet.getLastColumn(), HEADERS.length);
  var current = sheet.getRange(1, 1, 1, width).getValues()[0];
  for (var c = 0; c < HEADERS.length; c++) {
    if (String(current[c] === undefined || current[c] === null ? '' : current[c]).trim() === '') {
      sheet.getRange(1, c + 1).setValue(HEADERS[c]);
    }
  }
}

function readRows_(sheet) {
  var values = sheet.getDataRange().getValues();
  var rows = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    var row = {};
    // Short rows (written before the extended columns existed) read
    // their missing trailing cells as "" — unknown, never an error.
    for (var c = 0; c < HEADERS.length; c++) {
      var v = c < r.length ? r[c] : '';
      row[HEADERS[c]] = (v === undefined || v === null) ? '' : v;
    }
    row._rowIndex = i + 1; // 1-based sheet row
    rows.push(row);
  }
  return rows;
}

function publicRow_(row) {
  var out = {};
  for (var c = 0; c < HEADERS.length; c++) out[HEADERS[c]] = row[HEADERS[c]];
  return out;
}

function viewableOwners_(user) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(GRANTS_SHEET);
  var owners = [];
  if (sheet) {
    var values = sheet.getDataRange().getValues();
    for (var i = 1; i < values.length; i++) {
      if (String(values[i][1]).trim().toLowerCase() === user.toLowerCase()) {
        owners.push(String(values[i][0]).trim());
      }
    }
  }
  return owners;
}

function doGet(e) {
  var user = (e.parameter.user || '').trim();
  if (!user) return json_({ status: 'error', message: 'user is required' });

  var owners = viewableOwners_(user);
  var visible = owners.concat([user]).map(function (s) { return s.toLowerCase(); });
  var miners = readRows_(minersSheet_())
    .filter(function (row) {
      return visible.indexOf(String(row.user).trim().toLowerCase()) >= 0;
    })
    .map(publicRow_);

  return json_({
    status: 'success',
    user: user,
    canView: owners.join(','),
    miners: miners
  });
}

function doPost(e) {
  var body = JSON.parse(e.postData.contents);
  var user = (body.user || '').trim();
  if (!user) return json_({ status: 'error', message: 'user is required' });

  if (body.action === 'deleteRows') {
    return json_({ status: 'success', count: deleteRows_(user, body.ids || []) });
  }

  var miners = body.miners || [];
  var count = upsertRows_(user, miners);
  return json_({ status: 'success', count: count });
}

/** Payload value, or "" when the client didn't send the field at all. */
function val_(v) {
  return (v === undefined || v === null) ? '' : v;
}

/** One row per (user, id): replace in place, append only when new. */
function upsertRows_(user, miners) {
  var sheet = minersSheet_();
  var rows = readRows_(sheet);
  var byKey = {};
  rows.forEach(function (row) {
    byKey[String(row.user).trim().toLowerCase() + '|' + String(row.id).trim()] = row;
  });

  var now = new Date().toISOString();
  var count = 0;
  miners.forEach(function (m) {
    var id = String(m.id || '').trim();
    if (!id) return; // never write an identity-less row
    // Fields the client omits (unknown readings, or a pre-v0.1.6
    // client) are written as "" — the cell is cleared to "unknown"
    // rather than keeping a stale value from an earlier, richer write.
    var rec = {
      user: user,
      id: id,
      nickname: m.nickname || '',
      ip: m.ip || '',
      hashrate5s: val_(m.hashrate5s),
      avgHashrate: val_(m.avgHashrate),
      maxTemp: val_(m.maxTemp),
      maxFan: val_(m.maxFan),
      status: m.status || 'Online',
      lastUpdated: now,
      model: m.model || '',
      firmware: m.firmware || '',
      uptimeSec: val_(m.uptimeSec),
      hwErrors: val_(m.hwErrors),
      hashrate30m: val_(m.hashrate30m),
      idealHashrate: val_(m.idealHashrate),
      maxChipTemp: val_(m.maxChipTemp),
      maxPcbTemp: val_(m.maxPcbTemp),
      fans: m.fans || '',
      poolUrl: m.poolUrl || '',
      poolWorker: m.poolWorker || '',
      poolStatus: m.poolStatus || '',
      acceptedShares: val_(m.acceptedShares),
      rejectedShares: val_(m.rejectedShares),
      health: m.health || '',
      chainsJson: m.chainsJson || ''
    };
    var values = HEADERS.map(function (h) { return rec[h]; });
    var key = user.toLowerCase() + '|' + id;
    var existing = byKey[key];
    if (existing) {
      // Writing the full HEADERS width also extends a short legacy
      // row in place — same row, same first ten values, new columns
      // filled — without disturbing any other row.
      sheet.getRange(existing._rowIndex, 1, 1, HEADERS.length).setValues([values]);
    } else {
      sheet.appendRow(values);
      byKey[key] = { _rowIndex: sheet.getLastRow() };
    }
    count++;
  });
  return count;
}

/** Deletes the caller's own rows with the given ids. Returns rows removed. */
function deleteRows_(user, ids) {
  var sheet = minersSheet_();
  var wanted = {};
  ids.forEach(function (id) { wanted[String(id).trim()] = true; });
  var rows = readRows_(sheet);
  var removed = 0;
  // Bottom-up so row indices stay valid while deleting.
  for (var i = rows.length - 1; i >= 0; i--) {
    var row = rows[i];
    if (String(row.user).trim().toLowerCase() === user.toLowerCase() &&
        wanted[String(row.id).trim()]) {
      sheet.deleteRow(row._rowIndex);
      removed++;
    }
  }
  return removed;
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
