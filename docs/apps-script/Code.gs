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
 * Sheet layout ("Miners" tab, header row 1):
 *   user | id | nickname | ip | hashrate5s | avgHashrate | maxTemp |
 *   maxFan | status | lastUpdated
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
               'maxTemp', 'maxFan', 'status', 'lastUpdated'];

function minersSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(MINERS_SHEET);
  if (!sheet) {
    sheet = ss.insertSheet(MINERS_SHEET);
    sheet.appendRow(HEADERS);
  }
  return sheet;
}

function readRows_(sheet) {
  var values = sheet.getDataRange().getValues();
  var rows = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    var row = {};
    for (var c = 0; c < HEADERS.length; c++) row[HEADERS[c]] = r[c];
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
    var values = [user, id, m.nickname || '', m.ip || '',
                  m.hashrate5s || 0, m.avgHashrate || 0,
                  m.maxTemp || 0, m.maxFan || 0,
                  m.status || 'Online', now];
    var key = user.toLowerCase() + '|' + id;
    var existing = byKey[key];
    if (existing) {
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
