(function () {
  "use strict";

  // ------------------------------------------------------------ state
  var state = {
    profileId: null,
    editable: true,
    approvalStatus: null,
    connected: false,
    connectedProfileId: null,
    rawPaused: false,
    rawLines: 0,
    minimumMessages: 20
  };
  var BACKSLASH = String.fromCharCode(92);
  var CR = String.fromCharCode(13);
  var LF = String.fromCharCode(10);
  var TAB = String.fromCharCode(9);

  function $(id) { return document.getElementById(id); }
  function val(id) { var el = $(id); return el ? el.value : ""; }
  function setVal(id, v) { var el = $(id); if (el) { el.value = (v === null || v === undefined) ? "" : v; } }
  function text(id, v) { var el = $(id); if (el) { el.textContent = (v === null || v === undefined || v === "") ? "-" : v; } }
  function show(id, visible) { var el = $(id); if (el) { el.hidden = !visible; } }

  // ------------------------------------------------------------ escapes
  // The profile stores REAL control characters (a real CR LF). The form shows them as
  // backslash-r backslash-n so a technician can see and type them.
  function unescapeText(s) {
    if (!s || s.indexOf(BACKSLASH) < 0) { return s; }
    var out = "";
    for (var i = 0; i < s.length; i++) {
      var c = s.charAt(i);
      if (c !== BACKSLASH || i + 1 >= s.length) { out += c; continue; }
      var n = s.charAt(i + 1);
      if (n === "r") { out += CR; i++; }
      else if (n === "n") { out += LF; i++; }
      else if (n === "t") { out += TAB; i++; }
      else if (n === "0") { out += String.fromCharCode(0); i++; }
      else if (n === BACKSLASH) { out += BACKSLASH; i++; }
      else if (n === "x" && i + 3 < s.length && /^[0-9a-fA-F]{2}$/.test(s.substr(i + 2, 2))) {
        out += String.fromCharCode(parseInt(s.substr(i + 2, 2), 16)); i += 3;
      } else { out += c; }
    }
    return out;
  }
  function escapeText(s) {
    if (s === null || s === undefined) { return ""; }
    var out = "";
    for (var i = 0; i < s.length; i++) {
      var code = s.charCodeAt(i);
      if (code === 13) { out += BACKSLASH + "r"; }
      else if (code === 10) { out += BACKSLASH + "n"; }
      else if (code === 9) { out += BACKSLASH + "t"; }
      else if (code === 0) { out += BACKSLASH + "0"; }
      else if (code === 92) { out += BACKSLASH + BACKSLASH; }
      else if (code < 32 || code === 127) { out += BACKSLASH + "x" + ("0" + code.toString(16).toUpperCase()).slice(-2); }
      else { out += s.charAt(i); }
    }
    return out;
  }

  // ------------------------------------------------------------ api + ui helpers
  function api(method, path, body) {
    var opts = { method: method, headers: {} };
    if (body !== undefined) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(body);
    }
    return fetch(path, opts).then(function (r) {
      return r.text().then(function (t) {
        var data = {};
        try { data = t ? JSON.parse(t) : {}; } catch (e) { data = { status: "01", message: t }; }
        if (!r.ok || data.status === "01") {
          throw new Error(data.message || ("Request failed: " + r.status));
        }
        return data;
      });
    });
  }

  function toast(message, kind) {
    var el = document.createElement("div");
    el.className = "toast " + (kind || "");
    el.textContent = message;
    $("toasts").appendChild(el);
    setTimeout(function () { el.remove(); }, kind === "error" ? 7000 : 3500);
  }
  function fail(e) { toast(e && e.message ? e.message : String(e), "error"); }

  function confirmDialog(message, okLabel) {
    return new Promise(function (resolve) {
      var root = $("modal-root");
      var back = document.createElement("div");
      back.className = "modal-back";
      var box = document.createElement("div");
      box.className = "modal";
      var p = document.createElement("p");
      p.textContent = message;
      var actions = document.createElement("div");
      actions.className = "actions";
      var cancel = document.createElement("button");
      cancel.textContent = "Cancel";
      var ok = document.createElement("button");
      ok.className = "primary";
      ok.textContent = okLabel || "OK";
      cancel.onclick = function () { back.remove(); resolve(false); };
      ok.onclick = function () { back.remove(); resolve(true); };
      actions.appendChild(cancel); actions.appendChild(ok);
      box.appendChild(p); box.appendChild(actions); back.appendChild(box); root.appendChild(back);
      ok.focus();
    });
  }

  function technician() { return val("technician").trim(); }
  function fmtDate(iso) {
    if (!iso) { return "-"; }
    var d = new Date(iso);
    return isNaN(d.getTime()) ? iso : d.toLocaleString();
  }

  // ------------------------------------------------------------ form <-> profile
  var TEXT_FIELDS = ["name", "manufacturer", "model", "buildVersion", "firmwareVersion", "softwareVersion",
    "protocolName", "protocolVersion", "tcpHost", "serialPort", "parity", "flowControl", "extractionPattern",
    "weightUnit", "stableStatusToken", "technicianNotes", "connectionType", "readMode", "framingMode",
    "extractionRule", "stabilityRule"];
  var INT_FIELDS = ["tcpPort", "baudRate", "dataBits", "stopBits", "fixedMessageLength",
    "requiredConsecutiveMatches", "stabilityDurationMs", "connectTimeoutMs", "readTimeoutMs",
    "reconnectIntervalMs", "maxBufferBytes"];

  function intOrNull(s) {
    if (s === null || s === undefined || String(s).trim() === "") { return null; }
    var n = parseInt(s, 10);
    return isNaN(n) ? null : n;
  }

  function collectForm() {
    var p = { id: state.profileId };
    TEXT_FIELDS.forEach(function (f) { var v = val("f-" + f); p[f] = v === "" ? null : v; });
    INT_FIELDS.forEach(function (f) { p[f] = intOrNull(val("f-" + f)); });
    if (p.connectionType === "SERIAL" && !p.serialPort) { p.serialPort = val("f-serialPortSelect") || null; }
    p.messageDelimiter = unescapeText(val("f-messageDelimiter")) || null;
    p.queryCommand = unescapeText(val("f-queryCommand")) || null;
    if (p.extractionRule === "DELIMITED_TOKENS" && p.extractionPattern) { p.extractionPattern = unescapeText(p.extractionPattern); }
    var tol = val("f-stabilityTolerance").trim();
    p.stabilityTolerance = tol === "" ? null : tol;
    if (!p.weightUnit) { p.weightUnit = "kg"; }
    return p;
  }

  function populateForm(p) {
    TEXT_FIELDS.forEach(function (f) { setVal("f-" + f, p[f]); });
    INT_FIELDS.forEach(function (f) { setVal("f-" + f, p[f]); });
    setVal("f-parity", p.parity || "NONE");
    setVal("f-flowControl", p.flowControl || "NONE");
    setVal("f-messageDelimiter", escapeText(p.messageDelimiter));
    setVal("f-queryCommand", escapeText(p.queryCommand));
    if (p.extractionRule === "DELIMITED_TOKENS") { setVal("f-extractionPattern", escapeText(p.extractionPattern)); }
    setVal("f-stabilityTolerance", p.stabilityTolerance);
    setVal("f-serialPortSelect", p.serialPort || "");
    refreshConditionalFields();
  }

  function refreshConditionalFields() {
    var isTcp = val("f-connectionType") === "TCP";
    show("tcp-fields", isTcp);
    show("serial-fields", !isTcp);
    show("command-field", val("f-readMode") === "COMMAND_QUERY");
    var isDelim = val("f-framingMode") === "DELIMITER";
    show("delimiter-field", isDelim);
    show("fixedlength-field", !isDelim);
    var hints = {
      FIRST_NUMBER: "No pattern needed: the first digit run in the message is the weight (e.g. \"ST,GS,  12340 kg\" -> 12340).",
      DELIMITED_TOKENS: "Pattern = the separator between values, e.g. kg or , -- every numeric token becomes one value (multi-deck).",
      FIXED_POSITION: "Pattern = offset,length (0-based characters), e.g. 6,7 takes 7 characters starting at position 6.",
      REGEX: "Pattern = regular expression whose FIRST capturing group is the weight, e.g. GS,\\s*(\\d+)",
      STATUS_TOKEN: "Pattern = regex with TWO groups: (status)(value), e.g. ^(ST|US),GS,\\s*(\\d+) -- pair with stability rule Trust device status token."
    };
    text("pattern-hint", hints[val("f-extractionRule")] || "");
    var rule = val("f-stabilityRule");
    $("f-stableStatusToken").disabled = !state.editable || rule !== "STATUS_TOKEN";
    $("f-requiredConsecutiveMatches").disabled = !state.editable || rule !== "CONSECUTIVE_MATCH";
    $("f-stabilityTolerance").disabled = !state.editable || (rule !== "CONSECUTIVE_MATCH" && rule !== "DURATION");
    $("f-stabilityDurationMs").disabled = !state.editable || rule !== "DURATION";
    text("validation-hint", "TESTING -> VALIDATED needs at least " + state.minimumMessages
      + " messages in the current capture session, at least one valid reading, and the CORRECT confirmation ticked.");
  }

  function applyEditability(editable) {
    state.editable = editable;
    var inputs = $("profile-form").querySelectorAll("input, select, textarea");
    for (var i = 0; i < inputs.length; i++) { inputs[i].disabled = !editable; }
    $("btn-save").disabled = !editable;
    show("btn-new-version", !editable);
    show("readonly-notice", !editable);
    show("btn-delete", editable && state.profileId !== null);
    refreshConditionalFields();
  }

  function clearForm() {
    state.profileId = null;
    state.approvalStatus = null;
    $("profile-form").reset();
    setVal("f-messageDelimiter", BACKSLASH + "r" + BACKSLASH + "n");
    setVal("f-weightUnit", "kg");
    setVal("f-connectTimeoutMs", 10000); setVal("f-readTimeoutMs", 5000);
    setVal("f-reconnectIntervalMs", 5000); setVal("f-maxBufferBytes", 4096);
    $("status-pill").textContent = "DRAFT (unsaved)"; $("status-pill").className = "pill DRAFT";
    text("version-label", "-"); text("lifecycle-detail", ""); text("file-label", "");
    $("confirm-correct").checked = false;
    applyEditability(true);
    show("btn-delete", false);
    renderSamples([]);
    highlightSelected();
  }

  function loadProfile(id, keepForm) {
    return api("GET", "/api/profiles/" + id).then(function (r) {
      var p = r.profile;
      state.profileId = p.id;
      state.approvalStatus = p.approvalStatus;
      if (!keepForm) { populateForm(p); }
      $("status-pill").textContent = p.approvalStatus; $("status-pill").className = "pill " + p.approvalStatus;
      text("version-label", p.profileVersion);
      var detail = [];
      if (p.supersedesProfileNo) { detail.push("supersedes profile #" + p.supersedesProfileNo); }
      if (p.validatedOn) { detail.push("validated " + fmtDate(p.validatedOn) + (p.validatedByName ? " by " + p.validatedByName : "")); }
      if (r.nextStatus) { detail.push("next: " + r.nextStatus); }
      text("lifecycle-detail", detail.join(" | "));
      text("file-label", "File: " + r.file);
      $("confirm-correct").checked = false;
      applyEditability(r.editable);
      renderSamples(r.samples || []);
      highlightSelected();
      updateConnectButtons();
    });
  }

  function highlightSelected() {
    var rows = $("profile-list").querySelectorAll("tbody tr");
    for (var i = 0; i < rows.length; i++) {
      rows[i].className = "clickable" + (String(rows[i].dataset.id) === String(state.profileId) ? " selected" : "");
    }
  }

  function loadProfileList() {
    return api("GET", "/api/profiles").then(function (r) {
      var tbody = $("profile-list").querySelector("tbody");
      tbody.innerHTML = "";
      (r.profiles || []).forEach(function (row) {
        var tr = document.createElement("tr");
        tr.dataset.id = row.id;
        tr.className = "clickable";
        var name = document.createElement("td");
        name.innerHTML = "<div></div><div class=\"muted small\"></div>";
        name.firstChild.textContent = row.name;
        name.lastChild.textContent = "v" + row.profileVersion + " | " + (row.connectionType || "") + " " + (row.connection || "")
          + (row.sampleCount ? " | " + row.sampleCount + " sample(s)" : "");
        var status = document.createElement("td");
        status.innerHTML = "<span class=\"pill\"></span>";
        status.firstChild.textContent = row.approvalStatus;
        status.firstChild.className = "pill " + row.approvalStatus;
        tr.appendChild(name); tr.appendChild(status);
        tr.onclick = function () { loadProfile(row.id).catch(fail); };
        tbody.appendChild(tr);
      });
      show("profile-list-empty", (r.profiles || []).length === 0);
      highlightSelected();
    });
  }

  // ------------------------------------------------------------ live capture (SSE)
  function connectEvents() {
    var source = new EventSource("/api/capture/events");
    source.onmessage = function (m) {
      var ev;
      try { ev = JSON.parse(m.data); } catch (e) { return; }
      handleEvent(ev);
    };
    source.onerror = function () {
      $("conn-detail").textContent = "(event stream reconnecting...)";
    };
  }

  function appendRaw(asciiLine, hexLine, cssClass) {
    if (state.rawPaused) { return; }
    var a = $("raw-ascii");
    var h = $("raw-hex");
    var lineA = document.createElement("span");
    if (cssClass) { lineA.className = cssClass; }
    lineA.textContent = asciiLine + "\n";
    a.appendChild(lineA);
    if (hexLine !== null) {
      var lineH = document.createElement("span");
      if (cssClass) { lineH.className = cssClass; }
      lineH.textContent = hexLine + "\n";
      h.appendChild(lineH);
    }
    state.rawLines++;
    while (a.childNodes.length > 400) { a.removeChild(a.firstChild); }
    while (h.childNodes.length > 400) { h.removeChild(h.firstChild); }
    a.scrollTop = a.scrollHeight;
    h.scrollTop = h.scrollHeight;
    $("raw-count").textContent = "(" + state.rawLines + " chunks shown)";
  }

  function setConnection(status, detail) {
    $("conn-pill").textContent = status;
    $("conn-pill").className = "pill " + status;
    $("raw-conn").textContent = status;
    $("conn-detail").textContent = detail || "";
    state.connected = status === "CONNECTED";
    updateConnectButtons();
  }

  function updateConnectButtons() {
    var connected = state.connected;
    $("btn-disconnect").disabled = !connected;
    $("btn-apply-rules").disabled = !connected;
    $("send-text").disabled = !connected;
    $("btn-send").disabled = !connected;
    $("btn-request-reading").disabled = !connected || val("f-readMode") !== "COMMAND_QUERY";
  }

  function handleEvent(ev) {
    switch (ev.type) {
      case "raw":
        appendRaw(ev.ascii, ev.hex, null);
        break;
      case "sent":
        appendRaw(">> SENT: " + ev.ascii, null, "sent");
        break;
      case "parsed":
        text("pv-weight", ev.weight);
        text("pv-unit", ev.weight !== null ? ev.unit : "");
        $("pv-status").textContent = ev.stability;
        $("pv-status").className = "stab " + ev.stability;
        text("pv-raw", ev.rawMessage);
        text("pv-token", ev.statusToken);
        text("pv-values", ev.values && ev.values.length ? ev.values.join("  |  ") : "");
        text("pv-reason", ev.rejectionReason);
        break;
      case "statistics":
        text("st-received", ev.messagesReceived); text("st-valid", ev.valid); text("st-invalid", ev.invalid);
        text("st-stable", ev.stable); text("st-unstable", ev.unstable); text("st-errors", ev.communicationErrors);
        text("st-pending", ev.pendingBytes);
        if (ev.connectionState && ev.connectionState !== $("conn-pill").textContent) { setConnection(ev.connectionState, $("conn-detail").textContent); }
        break;
      case "status":
        setConnection(ev.status, ev.detail);
        if (ev.status === "CONNECTION_LOST") { toast("Connection lost: " + (ev.detail || ""), "error"); }
        break;
      case "error":
        appendRaw("!! " + ev.message, null, "sent");
        $("conn-detail").textContent = ev.message;
        break;
      case "rules":
        toast(ev.message, "ok");
        break;
      default:
        break;
    }
  }

  // ------------------------------------------------------------ actions
  function saveDraft() {
    var profile = collectForm();
    return api("POST", "/api/profiles", { profile: profile, technicianName: technician() }).then(function (r) {
      state.profileId = r.id;
      toast(r.message, "ok");
      return loadProfileList().then(function () { return loadProfile(r.id); });
    });
  }

  function connect() {
    var p = collectForm();
    if (!p.name) { toast("Enter a Profile Name first", "error"); return; }
    $("raw-ascii").innerHTML = ""; $("raw-hex").innerHTML = ""; state.rawLines = 0;
    var chain = state.editable ? saveDraft() : Promise.resolve();
    chain.then(function () {
      return api("POST", "/api/capture/connect", { profileId: state.profileId });
    }).then(function (r) {
      state.connectedProfileId = state.profileId;
      toast(r.message, "ok");
    }).catch(fail);
  }

  function applyRules() {
    api("POST", "/api/capture/apply-rules", { profile: collectForm() }).then(function (r) {
      toast(r.message + " -- click Save Draft to keep them in the profile", "ok");
    }).catch(fail);
  }

  function replay() {
    var body = { profile: collectForm(), rawText: val("replay-text") };
    api("POST", "/api/capture/replay", body).then(function (r) {
      $("replay-summary").textContent = r.source + ": " + r.messages + " message(s), " + r.valid + " valid, "
        + r.invalid + " invalid, " + r.stable + " stable" + (r.pendingBytes ? ", " + r.pendingBytes + " byte(s) left without a delimiter" : "");
      var tbody = $("replay-results").querySelector("tbody");
      tbody.innerHTML = "";
      (r.results || []).forEach(function (row) {
        var tr = document.createElement("tr");
        [row.rawMessage, row.weight, row.stability, row.rejectionReason].forEach(function (cell, i) {
          var td = document.createElement("td");
          if (i === 0) { var code = document.createElement("code"); code.textContent = cell || ""; td.appendChild(code); }
          else { td.textContent = cell === null || cell === undefined ? "" : cell; }
          if (i === 2) { td.className = "stab " + (cell || ""); }
          tr.appendChild(td);
        });
        tbody.appendChild(tr);
      });
      show("replay-results", true);
      if (r.messages === 0) { toast("No complete message was found -- check the delimiter / framing (" + r.totalBytes + " bytes examined)", "error"); }
    }).catch(fail);
  }

  function advance() {
    if (!state.profileId) { toast("Save the profile first", "error"); return; }
    api("POST", "/api/profiles/" + state.profileId + "/advance",
      { technicianConfirmedCorrect: $("confirm-correct").checked, technicianName: technician() })
      .then(function (r) { toast(r.message, "ok"); return loadProfileList().then(function () { return loadProfile(state.profileId, true); }); })
      .catch(fail);
  }

  function demote() {
    if (!state.profileId) { return; }
    confirmDialog("Send this profile back to DRAFT for rework?", "Send back").then(function (ok) {
      if (!ok) { return; }
      api("POST", "/api/profiles/" + state.profileId + "/demote", { technicianName: technician() })
        .then(function (r) { toast(r.message, "ok"); return loadProfileList().then(function () { return loadProfile(state.profileId, true); }); })
        .catch(fail);
    });
  }

  function newVersion() {
    if (!state.profileId) { return; }
    api("POST", "/api/profiles/" + state.profileId + "/new-version", { technicianName: technician() })
      .then(function (r) { toast(r.message, "ok"); return loadProfileList().then(function () { return loadProfile(r.id); }); })
      .catch(fail);
  }

  function deleteProfile() {
    if (!state.profileId) { return; }
    confirmDialog("Delete this profile and all of its saved evidence samples? This cannot be undone.", "Delete").then(function (ok) {
      if (!ok) { return; }
      api("DELETE", "/api/profiles/" + state.profileId).then(function (r) {
        toast(r.message, "ok");
        clearForm();
        return loadProfileList();
      }).catch(fail);
    });
  }

  function importJson(jsonText) {
    if (!jsonText || !jsonText.trim()) { toast("Paste the profile JSON first", "error"); return; }
    api("POST", "/api/profiles/import", { json: jsonText, technicianName: technician() }).then(function (r) {
      toast(r.message, "ok");
      setVal("import-json", "");
      return loadProfileList().then(function () { return loadProfile(r.id); });
    }).catch(fail);
  }

  function copyJson() {
    if (!state.profileId) { toast("Save the profile first", "error"); return; }
    api("GET", "/api/profiles/" + state.profileId + "/export-json").then(function (r) {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        return navigator.clipboard.writeText(r.json).then(function () { toast("Profile JSON copied to clipboard", "ok"); });
      }
      window.prompt("Profile JSON (copy this):", r.json);
    }).catch(fail);
  }

  // ------------------------------------------------------------ samples
  function renderSamples(samples) {
    var tbody = $("sample-list").querySelector("tbody");
    tbody.innerHTML = "";
    samples.forEach(function (s) {
      var tr = document.createElement("tr");
      var when = document.createElement("td");
      when.innerHTML = "<div></div><div class=\"muted small\"></div>";
      when.firstChild.textContent = fmtDate(s.capturedOn);
      when.lastChild.textContent = [s.stationName, s.stationCode, s.lane].filter(Boolean).join(" / ");
      var label = document.createElement("td");
      label.textContent = s.label;
      var notes = document.createElement("td");
      notes.innerHTML = "<div></div><code class=\"small\"></code><div class=\"muted small\"></div>";
      notes.firstChild.textContent = s.notes || "";
      notes.children[1].textContent = escapeText(s.rawAscii || "");
      notes.lastChild.textContent = s.parsedWeight ? "parsed " + s.parsedWeight + " " + (s.parsedStability || "") : (s.parsedRejectionReason ? "rejected: " + s.parsedRejectionReason : "");
      var del = document.createElement("td");
      var btn = document.createElement("button");
      btn.className = "sm danger"; btn.textContent = "x"; btn.title = "Delete this sample";
      btn.onclick = function () {
        confirmDialog("Delete this evidence sample?", "Delete").then(function (ok) {
          if (!ok) { return; }
          api("DELETE", "/api/samples/" + s.id).then(function () { return loadProfile(state.profileId, true); }).catch(fail);
        });
      };
      del.appendChild(btn);
      tr.appendChild(when); tr.appendChild(label); tr.appendChild(notes); tr.appendChild(del);
      tbody.appendChild(tr);
    });
  }

  function saveSample() {
    if (!state.profileId) { toast("Save the profile and connect before saving a sample", "error"); return; }
    var body = {
      stationName: val("s-stationName"), stationCode: val("s-stationCode"), location: val("s-location"),
      lane: val("s-lane"), label: val("s-label"), notes: val("s-notes"), technicianName: technician()
    };
    api("POST", "/api/profiles/" + state.profileId + "/samples", body).then(function (r) {
      toast(r.message, "ok");
      setVal("s-notes", "");
      return loadProfile(state.profileId, true).then(loadProfileList);
    }).catch(fail);
  }

  // ------------------------------------------------------------ serial ports
  function refreshPorts() {
    api("GET", "/api/serial-ports").then(function (r) {
      var sel = $("f-serialPortSelect");
      var current = sel.value;
      sel.innerHTML = "<option value=\"\">-- detected ports --</option>";
      (r.ports || []).forEach(function (p) {
        var o = document.createElement("option");
        o.value = p.systemPortName;
        o.textContent = p.systemPortName + " - " + (p.descriptivePortName || p.portDescription || "");
        sel.appendChild(o);
      });
      sel.value = current;
      if (r.error) { toast(r.error, "error"); }
      else if (!(r.ports || []).length) { toast("No COM ports detected on this computer", ""); }
    }).catch(fail);
  }

  // ------------------------------------------------------------ init
  function bind() {
    ["f-connectionType", "f-readMode", "f-framingMode", "f-extractionRule", "f-stabilityRule"].forEach(function (id) {
      $(id).addEventListener("change", function () { refreshConditionalFields(); updateConnectButtons(); });
    });
    $("f-serialPortSelect").addEventListener("change", function () { if (this.value) { setVal("f-serialPort", this.value); } });
    $("btn-refresh-ports").onclick = refreshPorts;
    $("btn-new").onclick = clearForm;
    $("btn-save").onclick = function () { saveDraft().catch(fail); };
    $("btn-connect").onclick = connect;
    $("btn-disconnect").onclick = function () { api("POST", "/api/capture/disconnect").then(function (r) { toast(r.message, ""); }).catch(fail); };
    $("btn-apply-rules").onclick = applyRules;
    $("btn-replay").onclick = replay;
    $("btn-advance").onclick = advance;
    $("btn-demote").onclick = demote;
    $("btn-new-version").onclick = newVersion;
    $("btn-delete").onclick = deleteProfile;
    $("btn-export").onclick = function () {
      if (!state.profileId) { toast("Save the profile first", "error"); return; }
      window.location.href = "/api/profiles/" + state.profileId + "/export";
    };
    $("btn-copy-json").onclick = copyJson;
    $("btn-import").onclick = function () { importJson(val("import-json")); };
    $("import-file").addEventListener("change", function () {
      var file = this.files && this.files[0];
      if (!file) { return; }
      var reader = new FileReader();
      reader.onload = function () { importJson(String(reader.result)); };
      reader.readAsText(file);
      this.value = "";
    });
    $("btn-save-sample").onclick = saveSample;
    $("btn-reset-stats").onclick = function () { api("POST", "/api/capture/reset-statistics").then(function (r) { toast(r.message, ""); }).catch(fail); };
    $("btn-clear-raw").onclick = function () { $("raw-ascii").innerHTML = ""; $("raw-hex").innerHTML = ""; state.rawLines = 0; $("raw-count").textContent = ""; };
    $("btn-pause-raw").onclick = function () { state.rawPaused = !state.rawPaused; this.textContent = state.rawPaused ? "Resume" : "Pause"; };
    $("btn-send").onclick = function () { api("POST", "/api/capture/send", { text: val("send-text") }).catch(fail); };
    $("btn-request-reading").onclick = function () { api("POST", "/api/capture/request-reading").catch(fail); };
    $("technician").addEventListener("change", function () { try { localStorage.setItem("ipb.technician", this.value); } catch (e) { /* ignore */ } });
    try { setVal("technician", localStorage.getItem("ipb.technician") || ""); } catch (e) { /* ignore */ }
  }

  function init() {
    bind();
    clearForm();
    api("GET", "/api/status").then(function (s) {
      state.minimumMessages = s.minimumMessagesForValidation || 20;
      $("data-dir").textContent = "Data: " + s.dataDirectory;
      if (s.connectionState) { setConnection(s.connectionState, ""); }
      refreshConditionalFields();
    }).catch(fail);
    loadProfileList().catch(fail);
    connectEvents();
    api("GET", "/api/capture/statistics").then(handleEvent).catch(function () { /* ignore */ });
  }

  document.addEventListener("DOMContentLoaded", init);
})();
