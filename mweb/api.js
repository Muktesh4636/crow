/**
 * Kokoroko mweb API — same base URL and routes as app/src/.../MainActivity.kt
 * (API_BASE_URL, Bearer auth, JSON bodies). Requires CORS on the backend
 * (Access-Control-Allow-Origin for your mweb host).
 */
const KokorokoApi = (function () {
  const API_BASE_URL = "https://fight.pravoo.in";
  const LS_ACCESS = "kokoroko_access";
  const LS_REFRESH = "kokoroko_refresh";
  const LS_DEMO_USER = "kokoroko_local_demo_user";
  const LOCAL_DEMO_TOKEN = "LOCAL_DEMO_SESSION";
  const LOCAL_DEMO_LOGINS = { svs: "svs" };

  const AUTH_LOGIN = "/api/auth/login/";
  /** Optional; if missing (404), mweb uses APK-style “account created → log in” flow. */
  const AUTH_REGISTER = "/api/auth/register/";
  const AUTH_WALLET = "/api/auth/wallet/";
  const AUTH_PAYMENT_METHODS = "/api/auth/payment-methods/";
  const AUTH_PROFILE = "/api/auth/profile/";
  const AUTH_LOGOUT = "/api/auth/logout/";
  const AUTH_BANK_DETAILS = "/api/auth/bank-details/";
  const AUTH_WITHDRAWS_INITIATE = "/api/auth/withdraws/initiate/";
  const AUTH_DEPOSITS_MINE = "/api/auth/deposits/mine/";
  const AUTH_WITHDRAWS_MINE = "/api/auth/withdraws/mine/";
  const AUTH_DEPOSIT_UPLOAD = "/api/auth/deposits/upload-proof/";
  const SUPPORT_CONTACTS = "/api/support/contacts/";

  const GAME_MERON_WALA_BET = "/api/game/meron-wala/bet/";
  const GAME_MERON_WALA_BETS_MINE = "/api/game/meron-wala/bets/mine/";
  const GAME_GUNDU_BET = "/api/game/bet/";
  const GAME_GUNDU_BETS_MINE = "/api/game/bets/mine/";

  const GUNDUATA_VIRTUAL = "https://gunduata.club/game/index.html";
  const COCKFIGHT_HLS = () => joinUrl("/hls/live/stream/index.m3u8");

  function joinUrl(path) {
    const base = API_BASE_URL.replace(/\/$/, "");
    const p = path.startsWith("/") ? path : "/" + path;
    return base + p;
  }

  function getAccess() {
    try {
      return localStorage.getItem(LS_ACCESS);
    } catch {
      return null;
    }
  }
  function getRefresh() {
    try {
      return localStorage.getItem(LS_REFRESH);
    } catch {
      return null;
    }
  }
  function isLocalDemo() {
    return getAccess() === LOCAL_DEMO_TOKEN;
  }
  function isAuthed() {
    return !!getAccess();
  }
  function clearSession() {
    try {
      localStorage.removeItem(LS_ACCESS);
      localStorage.removeItem(LS_REFRESH);
      localStorage.removeItem(LS_DEMO_USER);
    } catch {}
  }
  function saveSession(access, refresh) {
    try {
      localStorage.setItem(LS_ACCESS, access);
      if (refresh) localStorage.setItem(LS_REFRESH, refresh);
      else localStorage.removeItem(LS_REFRESH);
      localStorage.removeItem(LS_DEMO_USER);
    } catch {}
  }
  function saveLocalDemo(username) {
    try {
      localStorage.setItem(LS_ACCESS, LOCAL_DEMO_TOKEN);
      localStorage.removeItem(LS_REFRESH);
      localStorage.setItem(LS_DEMO_USER, (username || "user").trim() || "user");
    } catch {}
  }
  function getLocalDemoUser() {
    try {
      return localStorage.getItem(LS_DEMO_USER);
    } catch {
      return null;
    }
  }

  function authHeaders() {
    const t = getAccess();
    if (!t || t === LOCAL_DEMO_TOKEN) return {};
    return { Authorization: "Bearer " + t };
  }

  function extractErrorText(text) {
    try {
      const j = JSON.parse(text);
      if (j.error) return String(j.error);
      if (j.detail) return String(j.detail);
      for (const k of Object.keys(j)) {
        const v = j[k];
        if (Array.isArray(v) && v.length) return k + ": " + v[0];
        if (typeof v === "string" && v) return v;
      }
    } catch {
      if (text && text.length < 200) return text;
    }
    return "";
  }

  async function apiFetch(path, opts) {
    const method = opts.method || "GET";
    const headers = Object.assign({ Accept: "application/json" }, authHeaders(), opts.headers || {});
    if (opts.body && opts.json) headers["Content-Type"] = "application/json; charset=utf-8";
    const r = await fetch(joinUrl(path), {
      method,
      headers,
      body: opts.body,
      credentials: "omit"
    });
    const text = await r.text();
    return { ok: r.ok, status: r.status, text };
  }

  function dispatchAuth() {
    window.dispatchEvent(new CustomEvent("kokoroko:auth"));
  }

  function emptyWallet() {
    return {
      bank: null,
      upiId: null,
      qrImageUrl: null,
      paymentMethods: [],
      walletId: null,
      balance: "0",
      unavailableBalance: "0",
      withdrawableBalance: "0"
    };
  }

  function pickString(obj, keys) {
    for (const k of keys) {
      if (obj == null) continue;
      const v = obj[k];
      if (v == null) continue;
      const s = typeof v === "string" ? v.trim() : String(v);
      if (s && s !== "null") return s;
    }
    return null;
  }
  function optAmountString(obj, keys) {
    for (const k of keys) {
      if (obj == null) continue;
      if (!(k in obj) || obj[k] == null) continue;
      const v = obj[k];
      if (typeof v === "number") return String(v);
      if (typeof v === "string" && v.trim()) return v.trim();
    }
    return null;
  }

  function parsePaymentMethodsArray(arr) {
    const out = [];
    for (let i = 0; i < arr.length; i++) {
      const o = arr[i];
      if (!o || typeof o !== "object") continue;
      out.push({
        id: o.id != null ? Number(o.id) : 0,
        name: (o.name || o.method || "Method").toString(),
        type: (o.type || o.method_type || "upi").toString().toLowerCase(),
        upiId: o.upi_id ? String(o.upi_id) : o.upiId ? String(o.upiId) : null
      });
    }
    return out;
  }

  function parseWalletFromObject(data, root) {
    const d = data || root || {};
    const bank =
      d.bank_account || d.bank || d.bank_details
        ? d.bank_account || d.bank || d.bank_details
        : d.account_number
        ? d
        : null;
    let bankObj = null;
    if (bank && typeof bank === "object") {
      bankObj = {
        accountHolder: pickString(bank, ["account_holder", "account_holder_name", "account_name", "name"]) || "",
        bankName: pickString(bank, ["bank_name", "bank"]) || "",
        accountNumber: pickString(bank, ["account_number", "account_no", "acc_number"]) || "",
        ifsc: pickString(bank, ["ifsc", "ifsc_code", "IFSC", "ifscCode"]) || ""
      };
    }
    const pm = d.payment_methods || d.methods;
    let methods = null;
    if (Array.isArray(pm) && pm.length) {
      methods = parsePaymentMethodsArray(pm);
    }
    return {
      bank: bankObj && (bankObj.accountHolder || bankObj.accountNumber) ? bankObj : null,
      upiId: pickString(d, ["upi_id", "upi", "vpa", "merchant_upi"]),
      qrImageUrl: pickString(d, ["qr_code", "qr_url", "qr_image", "qr_image_url"]),
      paymentMethods: methods,
      walletId: d.id != null ? Number(d.id) : null,
      balance: optAmountString(d, ["balance", "available_balance", "total_balance"]),
      unavailableBalance: optAmountString(d, ["unavailable_balance", "locked_balance", "pending_balance"]),
      withdrawableBalance: optAmountString(d, ["withdrawable_balance", "available_withdrawal"])
    };
  }
  function parseWalletResponseBody(text) {
    const t = (text || "").trim();
    if (!t) return emptyWallet();
    if (t.startsWith("[")) {
      const arr = JSON.parse(t);
      if (Array.isArray(arr) && arr.length) {
        return Object.assign(emptyWallet(), { paymentMethods: parsePaymentMethodsArray(arr) });
      }
    }
    const root = JSON.parse(t);
    const data = root.data || root.wallet || root;
    if (data.payment_details && Array.isArray(data.payment_details)) {
      return parseWalletFromObject(
        Object.assign({}, data, { paymentMethods: data.payment_details }),
        root
      );
    }
    return parseWalletFromObject(data, root);
  }

  /**
   * Register: POST to AUTH_REGISTER. APK’s SignupScreen does not require server success; if the
   * endpoint is absent, callers should show “Account created. Please log in.” and return to login.
   */
  async function register({ username, phone, password, otp }) {
    const u = (username || "").trim();
    const p = (phone || "").replace(/\D/g, "").trim();
    const pw = (password || "").trim();
    const o = (otp || "").replace(/\D/g, "").trim();
    if (u.length < 2) return { ok: false, apksuccess: false, error: "Please enter a username." };
    if (p.length < 6) return { ok: false, apksuccess: false, error: "Please enter a valid phone number." };
    if (pw.length < 4) return { ok: false, apksuccess: false, error: "Please enter a password." };
    const body = {
      username: u,
      phone_number: p,
      password: pw
    };
    if (o) body.otp = o;
    try {
      const { ok, status, text } = await apiFetch(AUTH_REGISTER, {
        method: "POST",
        body: JSON.stringify(body),
        json: true
      });
      if (status === 201 || status === 200) {
        return { ok: true, apksuccess: false, error: null };
      }
      if (status === 404 || status === 405) {
        return { ok: false, apksuccess: true, error: null };
      }
      if (status === 400 || status === 422) {
        return { ok: false, apksuccess: false, error: extractErrorText(text) || "Could not create account" };
      }
      return { ok: false, apksuccess: true, error: null };
    } catch {
      return { ok: false, apksuccess: true, error: null };
    }
  }

  async function login(username, password) {
    const u = (username || "").trim().toLowerCase();
    if (LOCAL_DEMO_LOGINS[u] && LOCAL_DEMO_LOGINS[u] === password) {
      saveLocalDemo(username);
      dispatchAuth();
      return { ok: true, error: null };
    }
    const body = JSON.stringify({ username: (username || "").trim(), password });
    const { ok, status, text } = await apiFetch(AUTH_LOGIN, { method: "POST", body, json: true });
    if (!ok) {
      return { ok: false, error: extractErrorText(text) || "Login failed (" + status + ")" };
    }
    let j;
    try {
      j = JSON.parse(text);
    } catch {
      return { ok: false, error: "Invalid server response" };
    }
    if (j.error) return { ok: false, error: j.error };
    const access = (j.access || j.token || "").trim();
    const refresh = (j.refresh || j.refresh_token || "").trim() || null;
    if (!access) return { ok: false, error: "No token in response" };
    saveSession(access, refresh);
    dispatchAuth();
    return { ok: true, error: null };
  }

  async function logout() {
    if (isLocalDemo()) {
      clearSession();
      dispatchAuth();
      return { ok: true };
    }
    const t = getAccess();
    if (t) {
      await apiFetch(AUTH_LOGOUT, {
        method: "POST",
        body: "{}",
        json: true
      });
    }
    clearSession();
    dispatchAuth();
    return { ok: true };
  }

  async function fetchWallet() {
    if (!isAuthed()) return { data: null, error: "Sign in to load payment details." };
    if (isLocalDemo()) return { data: emptyWallet(), error: null };
    const { ok, status, text } = await apiFetch(AUTH_WALLET, { method: "GET" });
    if (status === 401) {
      clearSession();
      dispatchAuth();
      return { data: null, error: "Session expired" };
    }
    if (!ok || !text) return { data: null, error: "Could not load wallet (" + status + ")" };
    try {
      return { data: parseWalletResponseBody(text), error: null };
    } catch (e) {
      return { data: null, error: (e && e.message) || "Parse error" };
    }
  }

  async function fetchPaymentMethodsOnly() {
    if (!isAuthed() || isLocalDemo()) return { data: null, error: isLocalDemo() ? null : "Not signed in" };
    const { ok, status, text } = await apiFetch(AUTH_PAYMENT_METHODS, { method: "GET" });
    if (!ok || !text) return { data: null, error: "Could not load (" + status + ")" };
    try {
      return { data: parseWalletResponseBody(text), error: null };
    } catch {
      return { data: null, error: "Parse error" };
    }
  }

  function profileFromJson(text) {
    const root = JSON.parse(text);
    const data = root.data || root.profile || root.user || root;
    const genderRaw = data.gender;
    let gender = null;
    if (genderRaw && String(genderRaw).trim()) {
      const g = String(genderRaw).toUpperCase();
      if (g === "MALE" || g === "M") gender = "Male";
      else if (g === "FEMALE" || g === "F") gender = "Female";
      else gender = "Other";
    }
    return {
      id: data.id != null ? Number(data.id) : 0,
      username: pickString(data, ["username", "name", "full_name", "display_name"]) || "",
      email: pickString(data, ["email", "email_address"]) || "",
      phoneNumber: pickString(data, ["phone_number", "phone", "mobile"]) || "",
      gender,
      isStaff: !!data.is_staff
    };
  }

  async function fetchProfile() {
    if (!isAuthed()) return { data: null, error: "Sign in to load your profile." };
    if (isLocalDemo()) {
      const u = getLocalDemoUser() || "user";
      return { data: { id: -1, username: u, email: "", phoneNumber: "", gender: null, isStaff: false }, error: null };
    }
    const { ok, status, text } = await apiFetch(AUTH_PROFILE, { method: "GET" });
    if (status === 401) {
      clearSession();
      return { data: null, error: "Session expired" };
    }
    if (!ok) return { data: null, error: "Could not load profile" };
    try {
      return { data: profileFromJson(text), error: null };
    } catch {
      return { data: null, error: "Invalid profile response" };
    }
  }

  async function postProfile(body) {
    if (!isAuthed() || isLocalDemo()) return { ok: false, error: isLocalDemo() ? "Demo account — not saved" : "Sign in" };
    const gMap = { Male: "MALE", Female: "FEMALE", Other: "OTHER" };
    const json = {
      username: (body.username || "").trim(),
      phone_number: (body.phoneNumber || "").trim(),
      email: (body.email || "").trim()
    };
    if (body.gender) json.gender = gMap[body.gender] || "OTHER";
    const { ok, status, text } = await apiFetch(AUTH_PROFILE, {
      method: "POST",
      body: JSON.stringify(json),
      json: true
    });
    if (ok) return { ok: true, error: null };
    return { ok: false, error: extractErrorText(text) || "Update failed (" + status + ")" };
  }

  async function fetchBankDetails() {
    if (!isAuthed() || isLocalDemo()) return { data: null, error: isLocalDemo() ? "Demo" : "Sign in" };
    const { ok, status, text } = await apiFetch(AUTH_BANK_DETAILS, { method: "GET" });
    if (!ok) return { data: null, error: "Could not load" };
    try {
      const root = JSON.parse(text);
      const payload = root.data || root;
      if (payload.bank_accounts && Array.isArray(payload.bank_accounts) && payload.bank_accounts[0]) {
        const b = payload.bank_accounts[0];
        return {
          data: {
            upiId: (payload.upi_accounts && payload.upi_accounts[0] && payload.upi_accounts[0].upi_id) || "",
            bank: {
              accountHolder: (b.account_name || "").trim(),
              bankName: (b.bank_name || "").trim(),
              accountNumber: (b.account_number || "").trim(),
              ifsc: (b.ifsc_code || "").trim()
            }
          },
          error: null
        };
      }
      return { data: { upiId: (payload.upi_id || "").trim(), bank: null }, error: null };
    } catch {
      return { data: null, error: "Parse error" };
    }
  }

  async function postWithdrawInitiate(amount, method, details) {
    if (!isAuthed() || isLocalDemo()) return { data: null, error: "Demo or sign in" };
    const { ok, status, text } = await apiFetch(AUTH_WITHDRAWS_INITIATE, {
      method: "POST",
      body: JSON.stringify({ amount, withdrawal_method: method, withdrawal_details: details }),
      json: true
    });
    if (ok) {
      const j = JSON.parse(text);
      return { data: j, error: null };
    }
    return { data: null, error: extractErrorText(text) || "Withdrawal failed" };
  }

  async function postDepositUpload(file, amountInt, paymentMethodId) {
    if (!isAuthed() || isLocalDemo()) return { data: null, error: "Sign in with a real account" };
    const fd = new FormData();
    fd.append("screenshot", file, file.name || "screenshot.jpg");
    fd.append("amount", String(amountInt));
    fd.append("payment_method", String(paymentMethodId));
    const t = getAccess();
    const r = await fetch(joinUrl(AUTH_DEPOSIT_UPLOAD), {
      method: "POST",
      headers: t && t !== LOCAL_DEMO_TOKEN ? { Authorization: "Bearer " + t } : {},
      body: fd
    });
    const text = await r.text();
    if (!r.ok) return { data: null, error: extractErrorText(text) || "Upload failed" };
    try {
      return { data: JSON.parse(text), error: null };
    } catch {
      return { data: null, error: "Invalid response" };
    }
  }

  function extractTxArray(text) {
    const t = (text || "").trim();
    if (!t) return [];
    if (t.startsWith("[")) return JSON.parse(t);
    const root = JSON.parse(t);
    for (const key of ["results", "data", "deposits", "withdrawals", "withdraws", "items", "records", "list"]) {
      if (Array.isArray(root[key])) return root[key];
    }
    const d = root.data;
    if (d && typeof d === "object") {
      for (const key of ["deposits", "withdrawals", "results"]) {
        if (Array.isArray(d[key])) return d[key];
      }
    }
    const statusKeys = ["successful", "rejected", "pending", "approved", "completed", "failed", "all"];
    const merged = [];
    for (const k of statusKeys) {
      if (Array.isArray(root[k])) merged.push.apply(merged, root[k]);
    }
    if (merged.length) return merged;
    return [];
  }

  async function fetchDepositsMine() {
    if (!isAuthed() || isLocalDemo()) return { data: [], error: isLocalDemo() ? "Use a real account" : "Sign in" };
    const { ok, text } = await apiFetch(AUTH_DEPOSITS_MINE, { method: "GET" });
    if (!ok) return { data: [], error: "Load failed" };
    try {
      return { data: extractTxArray(text), error: null };
    } catch {
      return { data: [], error: "Parse error" };
    }
  }
  async function fetchWithdrawsMine() {
    if (!isAuthed() || isLocalDemo()) return { data: [], error: isLocalDemo() ? "Use a real account" : "Sign in" };
    const { ok, text } = await apiFetch(AUTH_WITHDRAWS_MINE, { method: "GET" });
    if (!ok) return { data: [], error: "Load failed" };
    try {
      return { data: extractTxArray(text), error: null };
    } catch {
      return { data: [], error: "Parse error" };
    }
  }

  async function postGundataBet(number, stakeInt) {
    if (!isAuthed()) return { data: null, error: "Sign in to place a bet" };
    if (isLocalDemo()) return { data: null, error: "Demo account cannot place bets" };
    if (number < 1 || number > 6) return { data: null, error: "Pick 1–6" };
    const chip = (Math.floor(stakeInt * 100) / 100).toFixed(2);
    const { ok, status, text } = await apiFetch(GAME_GUNDU_BET, {
      method: "POST",
      body: JSON.stringify({ number, chip_amount: chip }),
      json: true
    });
    if (status === 200 || status === 201) {
      const j = JSON.parse(text);
      return { data: { walletBalance: j.wallet_balance, message: j.message }, error: null };
    }
    return { data: null, error: extractErrorText(text) || "Bet failed" };
  }

  async function fetchGundataBetsMine() {
    if (!isAuthed()) return { data: [], error: "Sign in" };
    if (isLocalDemo()) return { data: [], error: null };
    const { ok, text, status } = await apiFetch(GAME_GUNDU_BETS_MINE, { method: "GET" });
    if (status === 401) {
      clearSession();
      return { data: [], error: "Session expired" };
    }
    if (!ok) return { data: [], error: "Failed" };
    try {
      const arr = JSON.parse(text);
      return { data: Array.isArray(arr) ? arr : [], error: null };
    } catch {
      return { data: [], error: "Invalid response" };
    }
  }

  async function postMeronWalaBet(side, stake) {
    if (!isAuthed()) return { data: null, error: "Sign in to place a bet" };
    if (isLocalDemo()) return { data: null, error: "Demo account cannot place bets" };
    const { ok, text, status } = await apiFetch(GAME_MERON_WALA_BET, {
      method: "POST",
      body: JSON.stringify({ side, stake: Number(stake) }),
      json: true
    });
    if (status === 201 || status === 200) {
      const j = JSON.parse(text);
      return { data: { walletBalance: j.wallet_balance, betId: j.bet_id }, error: null };
    }
    return { data: null, error: extractErrorText(text) || "Bet failed" };
  }

  async function fetchMeronWalaBetsMine() {
    if (!isAuthed() || isLocalDemo()) return { data: [], error: isAuthed() ? null : "Sign in" };
    const { ok, text, status } = await apiFetch(GAME_MERON_WALA_BETS_MINE, { method: "GET" });
    if (status === 401) {
      clearSession();
      return { data: [], error: "Session expired" };
    }
    if (!ok) return { data: [], error: "Failed" };
    try {
      const arr = JSON.parse(text);
      return { data: Array.isArray(arr) ? arr : [], error: null };
    } catch {
      return { data: [], error: "Invalid" };
    }
  }

  async function fetchSupportContacts() {
    const { ok, text } = await apiFetch(SUPPORT_CONTACTS, { method: "GET" });
    if (!ok || !text) return null;
    try {
      const root = JSON.parse(text);
      const j = root.data || root.contacts || root;
      return {
        whatsapp: (j.whatsapp_number || j.whatsapp || j.support_whatsapp || "").trim() || null,
        telegram: (j.telegram || "").trim() || null,
        facebook: (j.facebook || j.facebook_url || "").trim() || null,
        instagram: (j.instagram || j.instagram_url || "").trim() || null,
        youtube: (j.youtube || j.youtube_url || "").trim() || null
      };
    } catch {
      return null;
    }
  }

  function gunduVirtualUrl() {
    if (isLocalDemo()) {
      return GUNDUATA_VIRTUAL;
    }
    const access = getAccess() || "";
    const refresh = getRefresh() || "";
    const json = JSON.stringify({ accessToken: access, refreshToken: refresh });
    return (
      GUNDUATA_VIRTUAL +
      (GUNDUATA_VIRTUAL.indexOf("?") >= 0 ? "&" : "?") +
      "auth=" +
      encodeURIComponent(json) +
      "&accessToken=" +
      encodeURIComponent(access) +
      "&refreshToken=" +
      encodeURIComponent(refresh)
    );
  }

  function formatRupeeBalanceForDisplay(raw) {
    if (raw == null || String(raw).trim() === "") return "₹0.00";
    const t = String(raw).trim();
    if (t.startsWith("₹")) {
      return t;
    }
    const num = parseFloat(t.replace(/,/g, ""), 10);
    if (isNaN(num)) return "₹" + t;
    return "₹" + num.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  return {
    joinUrl,
    getAccess,
    isLocalDemo,
    isAuthed,
    clearSession,
    login,
    register,
    logout,
    fetchWallet,
    fetchPaymentMethodsOnly,
    fetchProfile,
    postProfile,
    fetchBankDetails,
    postWithdrawInitiate,
    postDepositUpload,
    fetchDepositsMine,
    fetchWithdrawsMine,
    postGundataBet,
    fetchGundataBetsMine,
    postMeronWalaBet,
    fetchMeronWalaBetsMine,
    fetchSupportContacts,
    gunduVirtualUrl,
    formatRupeeBalanceForDisplay,
    COCKFIGHT_HLS,
    API_BASE_URL,
    GUNDUATA_VIRTUAL
  };
})();
