(function () {
  const K = window.KokorokoApi;
  if (!K) {
    console.error("KokorokoApi missing — include api.js before app.js");
  }
  const TAB_IDS = ["home", "promotion", "wallet", "profile"];
  const ALL_SCREENS = [...TAB_IDS, "cockfight", "gundu", "login", "register"];
  const NAV_HASHES = ALL_SCREENS;
  let lastWalletData = null;
  let cockHls = null;
  let lastBankWithdraw = { upi: "", bankAcc: "", bankIfsc: "" };

  function balanceNumOnly(s) {
    if (!K) return s == null ? "—" : String(s);
    return K.formatRupeeBalanceForDisplay(s).replace(/^₹\s?/, "");
  }
  function setBalanceDisplays(mainBal, wdrBal) {
    const hb = document.getElementById("header-balance");
    const wWithdraw = document.getElementById("wallet-withdrawable-balance");
    const cfbal = document.getElementById("cockfight-balance");
    const gbal = document.getElementById("gundu-balance");
    if (mainBal !== undefined && hb) {
      if (mainBal === "—") hb.textContent = "—";
      else hb.textContent = balanceNumOnly(mainBal);
    }
    if (wdrBal !== undefined && wWithdraw) wWithdraw.textContent = wdrBal === "—" ? "—" : K ? K.formatRupeeBalanceForDisplay(wdrBal) : wdrBal;
    if (mainBal !== undefined && cfbal) cfbal.textContent = mainBal === "—" ? "—" : K ? K.formatRupeeBalanceForDisplay(mainBal) : mainBal;
    if (mainBal !== undefined && gbal) gbal.textContent = mainBal === "—" ? "—" : K ? K.formatRupeeBalanceForDisplay(mainBal) : mainBal;
  }
  function refreshHeaderAuth() {
    const sign = document.getElementById("header-signin-pill");
    const wall = document.getElementById("header-wallet-pill");
    if (!K) return;
    const ok = K.isAuthed();
    if (sign) sign.hidden = ok;
    if (wall) wall.hidden = !ok;
  }
  async function refreshAllBalances() {
    refreshHeaderAuth();
    if (!K) return;
    if (!K.isAuthed() || K.isLocalDemo()) {
      if (K.isAuthed() && K.isLocalDemo()) setBalanceDisplays("0", "0");
      else {
        setBalanceDisplays("—", "—");
        refreshHeaderAuth();
      }
      return;
    }
    const { data, error } = await K.fetchWallet();
    if (error) {
      setBalanceDisplays("—", "—");
      return;
    }
    lastWalletData = data;
    const b = (data && data.balance) || "0";
    const w = (data && (data.withdrawableBalance || data.balance)) || b;
    setBalanceDisplays(b, w);
  }
  async function loadWalletFromApi() {
    if (!K || !K.isAuthed() || K.isLocalDemo()) {
      if (K && K.isAuthed() && K.isLocalDemo()) {
        const wEl = document.getElementById("wallet-withdrawable-balance");
        if (wEl) wEl.textContent = "₹0.00";
      }
      return;
    }
    let { data } = await K.fetchWallet();
    if (!data) {
      const r2 = await K.fetchPaymentMethodsOnly();
      if (r2.data) data = r2.data;
    }
    if (data) {
      lastWalletData = data;
      const w = data.withdrawableBalance || data.balance || "0";
      const b = data.balance || "0";
      setBalanceDisplays(b, w);
    }
    const bdet = await K.fetchBankDetails();
    if (bdet && bdet.data) {
      const u = bdet.data.upiId || "";
      const b = bdet.data.bank;
      lastBankWithdraw.upi = u;
      if (b) {
        lastBankWithdraw.bankAcc = b.accountNumber || "";
        lastBankWithdraw.bankIfsc = b.ifsc || "";
        const t = document.getElementById("wallet-saved-bank");
        if (t) t.textContent = b.accountNumber ? b.bankName + " · · · " + b.accountNumber.slice(-4) : t.textContent;
      }
      const uEl = document.getElementById("wallet-saved-upi");
      if (uEl && u) uEl.textContent = u;
    }
  }
  function pickPaymentMethodId() {
    const m = lastWalletData && lastWalletData.paymentMethods;
    if (!m || !m.length) return 1;
    for (const x of m) {
      if (x.id > 0) return x.id;
    }
    return m[0].id || 1;
  }
  function attachCockfightHls() {
    const video = document.getElementById("cockfight-video");
    if (!video) return;
    const url = K ? K.COCKFIGHT_HLS() : "https://fight.pravoo.in/hls/live/stream/index.m3u8";
    const HlsRef = typeof Hls !== "undefined" ? Hls : window.Hls;
    if (HlsRef && HlsRef.isSupported && HlsRef.isSupported()) {
      if (cockHls) {
        cockHls.destroy();
        cockHls = null;
      }
      cockHls = new HlsRef();
      cockHls.loadSource(url);
      cockHls.attachMedia(video);
      if (location.hash === "#cockfight") video.play().catch(() => {});
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = url;
      if (location.hash === "#cockfight") video.play().catch(() => {});
    } else {
      const mp4 = video.querySelector('source[type="video/mp4"]');
      if (mp4 && mp4.getAttribute("src")) {
        video.src = mp4.getAttribute("src");
      }
    }
  }
  function goTab(name) {
    const key = NAV_HASHES.includes(name) ? name : "home";
    location.hash = key;
  }

  function showProfileView(name) {
    const root = document.getElementById("profile-panel");
    if (!root) return;
    root.querySelectorAll(".profile-subview").forEach((el) => {
      const sub = el.getAttribute("data-profile-sub");
      el.hidden = sub !== name;
    });
  }

  function onHash() {
    const h = (location.hash || "#home").replace("#", "").toLowerCase();
    if (h === "auth") {
      location.replace("#login");
      return;
    }
    if (K && (h === "wallet" || h === "profile") && !K.isAuthed()) {
      if (h !== "login" && h !== "register") {
        location.replace("#login");
        return;
      }
    }
    const key = ALL_SCREENS.includes(h) ? h : "home";
    if (h !== key && !ALL_SCREENS.includes(h)) {
      location.replace("#" + key);
      return;
    }
    document.documentElement.dataset.tab = key;
    if (K) {
      document.documentElement.dataset.auth = K.isAuthed() ? "1" : "0";
    }
    if (key === "profile") {
      showProfileView("main");
    }
    if (key === "wallet" && K) {
      loadWalletFromApi();
    }
    document.querySelectorAll(".panel").forEach((p) => {
      const isMatch = p.getAttribute("data-panel") === key;
      p.hidden = !isMatch;
      p.classList.toggle("panel--active", isMatch);
    });
    document.querySelectorAll(".bottom-nav [data-nav]").forEach((a) => {
      const n = a.getAttribute("data-nav");
      const on = n === key;
      a.classList.toggle("bottom-nav__item--on", on);
      if (on) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
    document.getElementById("main-scroll")?.scrollTo(0, 0);
    document.getElementById("gundu-scroll")?.scrollTo(0, 0);

    const v = document.getElementById("live-video");
    const liveChk = document.getElementById("live-on");
    if (v) {
      if (key === "home" && liveChk?.checked) {
        v.play().catch(() => {});
      } else {
        v.pause();
      }
    }
    const vCf = document.getElementById("cockfight-video");
    if (vCf) {
      if (key === "cockfight") {
        vCf.play().catch(() => {});
      } else {
        vCf.pause();
      }
    }
    const vGu = document.getElementById("gundu-video");
    if (vGu) {
      if (key === "gundu") {
        vGu.play().catch(() => {});
      } else {
        vGu.pause();
      }
    }
    if (key === "cockfight") {
      setTimeout(attachCockfightHls, 0);
    }
    refreshHeaderAuth();
    refreshAllBalances();
  }

  document.querySelectorAll(".bottom-nav [data-nav]").forEach((el) => {
    el.addEventListener("click", (e) => {
      e.preventDefault();
      const t = el.getAttribute("data-nav");
      if (t) {
        if (location.hash === "#" + t) onHash();
        else location.hash = t;
      }
    });
  });

  document.querySelectorAll(".wallet-pill[data-nav], .game-tile[data-nav]").forEach((el) => {
    const n = el.getAttribute("data-nav");
    if (n && NAV_HASHES.includes(n)) {
      el.addEventListener("click", (e) => {
        e.preventDefault();
        goTab(n);
      });
    }
  });

  const search = document.querySelector(".search__input");
  const clear = document.querySelector(".search__clear");
  if (search && clear) {
    search.addEventListener("input", () => {
      clear.hidden = !search.value;
    });
    clear.addEventListener("click", () => {
      search.value = "";
      clear.hidden = true;
      search.focus();
    });
  }

  const live = document.getElementById("live-on");
  const offMsg = document.getElementById("live-off");
  const liveCard = document.getElementById("live-card");
  const liveVideo = document.getElementById("live-video");
  if (live && offMsg && liveCard) {
    const sync = () => {
      const on = live.checked;
      offMsg.hidden = on;
      liveCard.hidden = !on;
      if (liveVideo) {
        if (on) {
          liveVideo.play().catch(() => {});
        } else {
          liveVideo.pause();
        }
      }
    };
    live.addEventListener("change", sync);
    sync();
  }

  window.addEventListener("kokoroko:auth", () => {
    refreshAllBalances();
  });

  (function setupWalletUI() {
    const panel = document.getElementById("wallet-panel");
    const walletApp = document.getElementById("wallet-app-root");
    if (!panel) return;
    const tabs = panel.querySelectorAll(".wallet-tabs__tab");
    const amt = document.getElementById("wallet-amount-input");
    const chips = document.getElementById("wallet-chips");
    const payCards = panel.querySelectorAll("[data-wallet-pay]");
    const btnDep = document.getElementById("wallet-btn-deposit");
    const btnWdr = document.getElementById("wallet-btn-withdraw");
    const infoDep = panel.querySelector("[data-wallet-info-deposit]");
    const infoWdr = panel.querySelector("[data-wallet-info-withdraw]");
    const howtoQ = panel.querySelector("[data-wallet-howto-q]");
    const onlyWdr = panel.querySelectorAll("[data-wallet-only-withdraw]");
    const destBank = panel.querySelector("[data-wallet-dest-bank]");
    const destUpi = panel.querySelector("[data-wallet-dest-upi]");

    let mode = "deposit";
    let pay = "upi";

    function syncPayDest() {
      if (destBank && destUpi) {
        const isBank = pay === "bank";
        destBank.hidden = !isBank;
        destUpi.hidden = isBank;
      }
    }

    function setMode(m) {
      mode = m;
      const isDep = m === "deposit";
      if (walletApp) walletApp.dataset.walletMode = m;
      tabs.forEach((t) => {
        const on = t.getAttribute("data-wallet-mode") === m;
        t.classList.toggle("is-on", on);
        t.setAttribute("aria-selected", on ? "true" : "false");
      });
      if (infoDep && infoWdr) {
        infoDep.hidden = !isDep;
        infoWdr.hidden = isDep;
      }
      onlyWdr.forEach((el) => {
        el.hidden = isDep;
      });
      if (howtoQ) howtoQ.textContent = isDep ? "How to Deposit" : "How to Withdraw";
      if (chips) chips.setAttribute("data-wallet-hidden", isDep ? "0" : "1");
      const ctaDepBlock = document.getElementById("wallet-cta-deposit-block");
      const ctaWdrBlock = document.getElementById("wallet-cta-withdraw-block");
      if (ctaDepBlock && ctaWdrBlock) {
        ctaDepBlock.hidden = !isDep;
        ctaWdrBlock.hidden = isDep;
      }
      if (amt) {
        const phDep = amt.getAttribute("data-placeholder-deposit") || "e.g. 500";
        const phWdr = amt.getAttribute("data-placeholder-withdraw") || "e.g. 200";
        amt.placeholder = isDep ? phDep : phWdr;
        if (isDep) {
          if (!amt.value.trim()) amt.value = "1000";
        } else {
          amt.value = "";
        }
      }
      if (isDep) {
        const sel = chips?.querySelector(".wallet-chip.is-on");
        if (sel && amt) amt.value = sel.getAttribute("data-amt") || "1000";
      }
      syncPayDest();
    }

    function setPay(p) {
      pay = p;
      payCards.forEach((c) => {
        const on = c.getAttribute("data-wallet-pay") === p;
        c.classList.toggle("is-selected", on);
        c.setAttribute("aria-pressed", on ? "true" : "false");
      });
      syncPayDest();
    }

    tabs.forEach((t) => {
      t.addEventListener("click", () => {
        const m = t.getAttribute("data-wallet-mode");
        if (m) setMode(m);
      });
    });
    payCards.forEach((c) => {
      c.addEventListener("click", () => {
        const p = c.getAttribute("data-wallet-pay");
        if (p) setPay(p);
      });
    });
    if (chips) {
      chips.addEventListener("click", (e) => {
        const chip = e.target.closest(".wallet-chip");
        if (!chip || !chips.contains(chip)) return;
        chips.querySelectorAll(".wallet-chip").forEach((x) => x.classList.toggle("is-on", x === chip));
        if (amt) amt.value = chip.getAttribute("data-amt") || "";
      });
    }
    if (amt) {
      amt.addEventListener("input", () => {
        const d = amt.value.replace(/\D/g, "").slice(0, 9);
        if (amt.value !== d) amt.value = d;
      });
    }
    const fileIn = document.getElementById("wallet-deposit-screenshot");
    btnDep?.addEventListener("click", async () => {
      if (!K || !K.isAuthed()) {
        location.hash = "login";
        return;
      }
      if (K.isLocalDemo()) {
        window.alert("Sign in with a real account to deposit (demo user cannot).");
        return;
      }
      const amount = parseInt(amt && amt.value ? amt.value.replace(/\D/g, "") : "0", 10) || 0;
      if (amount < 100) {
        window.alert("Minimum deposit is ₹100.");
        return;
      }
      if (!fileIn) {
        window.alert("File input missing");
        return;
      }
      fileIn.value = "";
      fileIn.onchange = async () => {
        const f = fileIn.files && fileIn.files[0];
        if (!f) return;
        const mid = pickPaymentMethodId();
        const { data, error } = await K.postDepositUpload(f, amount, mid);
        if (error) window.alert(error);
        else {
          window.alert("Deposit proof submitted. Status: " + (data && data.status ? data.status : "PENDING"));
          await refreshAllBalances();
        }
        fileIn.value = "";
      };
      fileIn.click();
    });
    btnWdr?.addEventListener("click", async () => {
      if (!K || !K.isAuthed()) {
        location.hash = "login";
        return;
      }
      if (K.isLocalDemo()) {
        window.alert("Demo account cannot withdraw.");
        return;
      }
      const n = parseInt(amt && amt.value ? amt.value.replace(/\D/g, "") : "0", 10) || 0;
      if (n <= 0) {
        window.alert("Enter a valid amount");
        return;
      }
      let details;
      if (pay === "upi") {
        details = (lastBankWithdraw.upi || "").trim();
      } else {
        const a = (lastBankWithdraw.bankAcc || "").trim();
        const i = (lastBankWithdraw.bankIfsc || "").trim();
        if (!a) {
          window.alert("No saved bank. Add in app or when API returns details.");
          return;
        }
        details = a + (i ? " | " + i : "");
      }
      if (!details) {
        window.alert("Add a UPI or bank for this method in your account first.");
        return;
      }
      const { data, error } = await K.postWithdrawInitiate(n, pay === "upi" ? "UPI" : "BANK", details);
      if (error) window.alert(error);
      else window.alert("Withdrawal #" + (data && data.id) + " — " + (data && data.status) + " (₹" + (data && data.amount) + ")");
      await refreshAllBalances();
    });
    panel.querySelectorAll(".wallet-outlined-btn").forEach((b) => {
      b.addEventListener("click", () => {
        const k = b.getAttribute("data-add");
        window.alert(
          k === "bank"
            ? "Add bank account (web preview; connect API for saved withdrawal details)."
            : "Add UPI ID (web preview; connect API for saved withdrawal details)."
        );
      });
    });

    setMode("deposit");
    setPay("upi");
  })();

  async function loadProfileForm() {
    if (!K) return;
    const hint = document.getElementById("profile-form-hint");
    const { data, error } = await K.fetchProfile();
    if (error) {
      if (hint) hint.textContent = error;
      return;
    }
    if (!data) return;
    if (hint) hint.textContent = "Edit your details.";
    const u = document.getElementById("pf-username");
    const ph = document.getElementById("pf-phone");
    const em = document.getElementById("pf-email");
    if (u) u.value = data.username || "";
    if (ph) ph.value = data.phoneNumber || "";
    if (em) em.value = data.email || "";
    if (data.gender) {
      document.querySelectorAll('input[name="gender"]').forEach((r) => {
        r.checked = r.value === data.gender;
      });
    }
  }
  const txUi = {
    kind: "deposit",
    filter: "all",
    deposits: [],
    withdraws: [],
    depositErr: null,
    withdrawErr: null
  };

  function txEscapeHtml(s) {
    if (s == null) return "";
    const d = document.createElement("div");
    d.textContent = String(s);
    return d.innerHTML;
  }

  function formatIsoDateTimeShort(iso) {
    if (!iso || !String(iso).trim()) return "—";
    try {
      let s = String(iso).trim();
      if (s.includes("T") && !/Z$/i.test(s) && !/[+-]\d\d:?\d\d$/.test(s)) {
        s = s.replace(/\.\d+/, "") + "Z";
      }
      const d = new Date(s);
      if (isNaN(d.getTime())) return iso;
      const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
      const dd = String(d.getDate()).padStart(2, "0");
      const mon = months[d.getMonth()];
      const y = d.getFullYear();
      const hh = String(d.getHours()).padStart(2, "0");
      const mm = String(d.getMinutes()).padStart(2, "0");
      return dd + " " + mon + " " + y + ", " + hh + ":" + mm;
    } catch {
      return iso;
    }
  }

  function matchesTxStatusFilter(status, filterKey) {
    if (filterKey === "all") return true;
    const s = String(status || "")
      .trim()
      .toUpperCase();
    const isSuccess =
      ["SUCCESS", "COMPLETED", "COMPLETE", "APPROVED", "SUCCESSFUL", "DONE", "CONFIRMED", "PAID", "PROCESSED"].indexOf(s) >= 0;
    const isFailed =
      ["FAILED", "FAILURE", "REJECTED", "CANCELLED", "CANCELED", "DECLINED"].indexOf(s) >= 0;
    if (filterKey === "success") return isSuccess;
    if (filterKey === "failed") return isFailed;
    return true;
  }

  function txStatusTier(status) {
    const s = String(status || "")
      .trim()
      .toUpperCase();
    if (
      ["SUCCESS", "COMPLETED", "COMPLETE", "APPROVED", "SUCCESSFUL", "DONE", "CONFIRMED", "PAID", "PROCESSED"].indexOf(s) >= 0
    )
      return "success";
    if (["FAILED", "FAILURE", "REJECTED", "CANCELLED", "CANCELED", "DECLINED"].indexOf(s) >= 0) return "failed";
    return "pending";
  }

  function txDepositScreenshotUrl(d) {
    if (!d || typeof d !== "object") return "";
    const u = d.screenshot_url || d.screenshot || d.proof_url || "";
    return String(u).trim();
  }

  function syncTxRecordPills() {
    document.querySelectorAll(".tx-kind-pill").forEach((b) => {
      const on = b.getAttribute("data-tx-kind") === txUi.kind;
      b.classList.toggle("tx-kind-pill--on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    document.querySelectorAll(".tx-filter-pill").forEach((b) => {
      const on = b.getAttribute("data-tx-filter") === txUi.filter;
      b.classList.toggle("tx-filter-pill--on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    const statusLabelEl = document.getElementById("tx-status-section-label");
    if (statusLabelEl) {
      statusLabelEl.textContent = txUi.kind === "deposit" ? "Deposit status" : "Withdraw status";
    }
  }

  function renderTxRecordsUi() {
    const loadingEl = document.getElementById("tx-loading");
    const errBox = document.getElementById("tx-error");
    const errMsg = document.getElementById("tx-error-msg");
    const emptyEl = document.getElementById("tx-empty");
    const cardsEl = document.getElementById("tx-cards");
    if (!cardsEl) return;

    syncTxRecordPills();

    if (loadingEl && !loadingEl.hidden) {
      return;
    }

    const err = txUi.kind === "deposit" ? txUi.depositErr : txUi.withdrawErr;
    if (err) {
      if (errBox) errBox.hidden = false;
      if (errMsg) errMsg.textContent = err;
      if (emptyEl) emptyEl.hidden = true;
      cardsEl.innerHTML = "";
      return;
    }
    if (errBox) errBox.hidden = true;

    const raw = txUi.kind === "deposit" ? txUi.deposits : txUi.withdraws;
    const rows = (raw || []).filter((r) => matchesTxStatusFilter(r && r.status, txUi.filter));

    const statusPhrase =
      txUi.filter === "all" ? "all statuses" : txUi.filter === "success" ? "successful" : "failed";
    const kindWord = txUi.kind === "deposit" ? "deposit" : "withdraw";

    if (!rows.length) {
      if (emptyEl) {
        emptyEl.hidden = false;
        emptyEl.textContent = "No " + kindWord + " transactions for " + statusPhrase + " yet.";
      }
      cardsEl.innerHTML = "";
      return;
    }
    if (emptyEl) emptyEl.hidden = true;

    const html = rows
      .map((r) => {
        const st = (r && r.status) || "";
        const tier = txStatusTier(st);
        const amt =
          txUi.kind === "deposit"
            ? r.amount != null
              ? String(r.amount)
              : ""
            : r.amount != null
              ? String(r.amount)
              : "";
        let block =
          '<article class="tx-card"><div class="tx-card__row"><span class="tx-card__amt">₹' +
          txEscapeHtml(amt) +
          '</span><span class="tx-card__status tx-card__status--' +
          tier +
          '">' +
          txEscapeHtml(st || "—") +
          "</span></div>";
        block += '<div class="tx-card__date">' + txEscapeHtml(formatIsoDateTimeShort(r.created_at)) + "</div>";
        if (txUi.kind === "deposit") {
          if (r.payment_method != null && r.payment_method !== "") {
            block +=
              '<p class="tx-card__meta">Payment method #' + txEscapeHtml(String(r.payment_method)) + "</p>";
          }
          const shot = txDepositScreenshotUrl(r);
          if (shot) {
            block +=
              '<a class="tx-card__shot" href="' +
              txEscapeHtml(shot) +
              '" target="_blank" rel="noopener noreferrer">View payment screenshot</a>';
          }
        } else {
          const wm = (r.withdrawal_method && String(r.withdrawal_method).trim()) || "";
          const wd = (r.withdrawal_details && String(r.withdrawal_details).trim()) || "";
          if (wm || wd) {
            const line = wm + (wm && wd ? " · " : "") + wd;
            block += '<p class="tx-card__dest">' + txEscapeHtml(line) + "</p>";
          }
          const proc = r.processed_by_name || r.processed_by;
          if (proc) {
            block += '<p class="tx-card__proc">Processed by: ' + txEscapeHtml(proc) + "</p>";
          }
        }
        if (r.admin_note && String(r.admin_note).trim()) {
          block += '<p class="tx-card__note">Note: ' + txEscapeHtml(String(r.admin_note).trim()) + "</p>";
        }
        block += "</article>";
        return block;
      })
      .join("");
    cardsEl.innerHTML = html;
  }

  async function loadTxRecords() {
    if (!K || !K.isAuthed()) return;
    txUi.kind = "deposit";
    txUi.filter = "all";
    txUi.deposits = [];
    txUi.withdraws = [];
    txUi.depositErr = null;
    txUi.withdrawErr = null;
    const loadingEl = document.getElementById("tx-loading");
    const errBox = document.getElementById("tx-error");
    const emptyEl = document.getElementById("tx-empty");
    const cardsEl = document.getElementById("tx-cards");
    const showSpinner = !K.isLocalDemo();

    syncTxRecordPills();
    if (cardsEl) cardsEl.innerHTML = "";
    if (errBox) errBox.hidden = true;
    if (emptyEl) emptyEl.hidden = true;
    if (loadingEl) loadingEl.hidden = !showSpinner;

    const { data: deps, error: eD } = await K.fetchDepositsMine();
    const { data: wdrs, error: eW } = await K.fetchWithdrawsMine();

    txUi.deposits = Array.isArray(deps) ? deps : [];
    txUi.withdraws = Array.isArray(wdrs) ? wdrs : [];
    txUi.depositErr = eD || null;
    txUi.withdrawErr = eW || null;

    if (loadingEl) loadingEl.hidden = true;
    renderTxRecordsUi();
  }
  (async function applySupportContacts() {
    if (!K) return;
    const c = await K.fetchSupportContacts();
    if (!c) return;
    const w = c.whatsapp
      ? "https://wa.me/" + String(c.whatsapp).replace(/\D/g, "")
      : null;
    const tg = c.telegram
      ? c.telegram.startsWith("http")
        ? c.telegram
        : "https://t.me/" + c.telegram.replace(/^@/, "")
      : null;
    const wa = document.getElementById("profile-link-wa");
    const tgel = document.getElementById("profile-link-tg");
    if (wa && w) wa.href = w;
    if (tgel && tg) tgel.href = tg;
    if (c.facebook) {
      const el = document.getElementById("profile-link-fb");
      if (el) el.href = c.facebook;
    }
    if (c.instagram) {
      const el = document.getElementById("profile-link-ig");
      if (el) el.href = c.instagram;
    }
    if (c.youtube) {
      const el = document.getElementById("profile-link-yt");
      if (el) el.href = c.youtube;
    }
  })();

  const profilePanel = document.getElementById("profile-panel");
  if (profilePanel) {
    profilePanel.addEventListener("click", (e) => {
      const back = e.target.closest("[data-profile-back]");
      if (back) {
        e.preventDefault();
        const name = back.getAttribute("data-profile-back");
        if (name) showProfileView(name);
        return;
      }
      const open = e.target.closest("[data-open-profile]");
      if (open) {
        e.preventDefault();
        const name = open.getAttribute("data-open-profile");
        if (name) {
          showProfileView(name);
          if (name === "details") loadProfileForm();
          if (name === "tx") loadTxRecords();
        }
        return;
      }
      const kindPill = e.target.closest(".tx-kind-pill");
      if (kindPill && e.target.closest("#tx-records-root")) {
        const kind = kindPill.getAttribute("data-tx-kind");
        if (kind) {
          txUi.kind = kind;
          renderTxRecordsUi();
        }
        return;
      }
      const filterPill = e.target.closest(".tx-filter-pill");
      if (filterPill && e.target.closest("#tx-records-root")) {
        const f = filterPill.getAttribute("data-tx-filter");
        if (f) {
          txUi.filter = f;
          renderTxRecordsUi();
        }
      }
    });
  }

  document.getElementById("tx-retry-btn")?.addEventListener("click", () => {
    loadTxRecords();
  });

  document.getElementById("profile-logout-btn")?.addEventListener("click", async () => {
    if (!window.confirm("Log out?")) return;
    if (K) await K.logout();
    location.hash = "home";
    refreshAllBalances();
  });

  document.getElementById("profile-update-btn")?.addEventListener("click", async () => {
    if (!K) return;
    const g = document.querySelector('input[name="gender"]:checked');
    const { ok, error } = await K.postProfile({
      username: (document.getElementById("pf-username") && document.getElementById("pf-username").value) || "",
      phoneNumber: (document.getElementById("pf-phone") && document.getElementById("pf-phone").value) || "",
      email: (document.getElementById("pf-email") && document.getElementById("pf-email").value) || "",
      gender: g ? g.value : null
    });
    if (ok) window.alert("Profile updated.");
    else window.alert(error || "Update failed");
  });

  const copyBtn = document.getElementById("referral-copy-btn");
  if (copyBtn) {
    copyBtn.addEventListener("click", async () => {
      const t = copyBtn.getAttribute("data-copy") || "AGHMU545";
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(t);
        } else {
          throw new Error("no clipboard");
        }
      } catch {
        window.prompt("Copy:", t);
      }
    });
  }

  (function initGundu() {
    const panel = document.getElementById("gundu-panel");
    if (!panel) return;

    const stakes = Object.create(null);
    let tapStack = [];
    let focusedFace = null;
    let selectedChip = 100;
    let placing = false;
    let regionTab = 0;

    function syncCard(face) {
      const btn = panel.querySelector(`.gundu-dice[data-gundu-face="${face}"]`);
      if (!btn) return;
      const amt = stakes[face] | 0;
      const main = btn.querySelector(".gundu-dice__main");
      const pip = main.querySelector(".gundu-pip");
      const st = main.querySelector(".gundu-dice__stake");
      if (amt > 0) {
        pip.hidden = true;
        st.hidden = false;
        st.textContent = String(amt);
        const fs = amt >= 10000 ? 10 : amt >= 1000 ? 12 : amt >= 100 ? 14 : 16;
        st.style.fontSize = fs + "px";
      } else {
        pip.hidden = false;
        st.hidden = true;
      }
      btn.classList.toggle("gundu-dice--focus", focusedFace === face);
    }

    function syncChips() {
      panel.querySelectorAll(".gundu-chip").forEach((c) => {
        const v = +c.getAttribute("data-amt");
        c.classList.toggle("is-on", v === selectedChip);
      });
    }

    function hasAnyStake() {
      for (let f = 1; f <= 6; f++) {
        if ((stakes[f] | 0) > 0) return true;
      }
      return false;
    }

    function syncPlace() {
      const has = hasAnyStake();
      const btn = document.getElementById("gundu-place-btn");
      if (btn) {
        btn.disabled = !has || placing;
      }
      const undo = document.getElementById("gundu-undo");
      if (undo) {
        const ok = tapStack.length > 0 && !placing;
        undo.disabled = !ok;
        undo.setAttribute("aria-disabled", ok ? "false" : "true");
      }
    }

    function syncAllCards() {
      for (let f = 1; f <= 6; f++) syncCard(f);
    }

    function buildLastStrip() {
      const host = document.getElementById("gundu-last-inner");
      if (!host) return;
      host.replaceChildren();
      const pips9 = {
        1: [0, 0, 0, 0, 1, 0, 0, 0, 0],
        2: [1, 0, 0, 0, 0, 0, 0, 0, 1],
        3: [1, 0, 0, 0, 1, 0, 0, 0, 1],
        4: [1, 0, 1, 0, 0, 0, 1, 0, 1],
        5: [1, 0, 1, 0, 1, 0, 1, 0, 1],
        6: [1, 0, 1, 1, 0, 1, 1, 0, 1]
      };
      for (let r = 1; r <= 20; r++) {
        const col = document.createElement("div");
        col.className = "gundu-lastcol";
        const h = document.createElement("div");
        h.className = "gundu-lastcol__h";
        h.textContent = String(r);
        col.appendChild(h);
        const stack = document.createElement("div");
        stack.className = "gundu-lastcol__stack";
        for (let v = 1; v <= 6; v++) {
          const d = document.createElement("div");
          d.className = "gundu-mini";
          const pat = pips9[v] || pips9[1];
          const wrap = document.createElement("div");
          wrap.className = "gundu-mini__pip";
          for (let i = 0; i < 9; i++) {
            const s = document.createElement("span");
            if (pat[i]) s.className = "on";
            wrap.appendChild(s);
          }
          d.appendChild(wrap);
          stack.appendChild(d);
        }
        col.appendChild(stack);
        host.appendChild(col);
      }
    }

    function setRegion(i) {
      regionTab = i;
      panel.querySelectorAll(".gundu-pill").forEach((p) => {
        const idx = +p.getAttribute("data-gundu-region");
        p.classList.toggle("is-on", idx === i);
        p.setAttribute("aria-pressed", idx === i ? "true" : "false");
      });
    }

    const histModal = document.getElementById("gundu-bet-history");
    const fsRoot = document.getElementById("gundu-fullscreen");
    const virt = document.getElementById("gundu-virt");
    const videoInline = document.getElementById("gundu-video");
    const videoFs = document.getElementById("gundu-video-fs");

    const streamBox = document.getElementById("gundu-stream-box");
    function openFs() {
      if (fsRoot) {
        fsRoot.hidden = false;
        streamBox?.classList.add("gundu-stream-box--fs");
        videoInline?.pause();
        const src = videoInline?.querySelector("source");
        if (videoFs && videoInline) {
          videoFs.poster = videoInline.poster || "";
          if (src?.src) {
            let s = videoFs.querySelector("source");
            if (!s) {
              s = document.createElement("source");
              videoFs.appendChild(s);
            }
            s.src = src.src;
            s.type = src.type || "video/mp4";
            videoFs.load();
          }
          videoFs.currentTime = videoInline.currentTime || 0;
          videoFs.play().catch(() => {});
        }
        document.body.style.overflow = "hidden";
      }
    }
    function closeFs() {
      if (fsRoot) {
        fsRoot.hidden = true;
        streamBox?.classList.remove("gundu-stream-box--fs");
        if (videoFs && videoInline) {
          videoInline.currentTime = videoFs.currentTime || 0;
          if (document.documentElement.dataset.tab === "gundu") {
            videoInline.play().catch(() => {});
          }
        }
        document.body.style.overflow = "";
      }
    }

    panel.addEventListener("click", (e) => {
      const dice = e.target.closest(".gundu-dice");
      if (dice && panel.contains(dice) && !placing) {
        const face = +dice.getAttribute("data-gundu-face");
        focusedFace = face;
        const cur = stakes[face] | 0;
        const add = selectedChip;
        stakes[face] = cur + add;
        tapStack.push([face, add]);
        syncCard(face);
        syncPlace();
        return;
      }
      const chip = e.target.closest(".gundu-chip");
      if (chip && panel.querySelector("#gundu-chips")?.contains(chip)) {
        const v = +chip.getAttribute("data-amt");
        if (v) {
          selectedChip = v;
          syncChips();
        }
        return;
      }
      const pill = e.target.closest(".gundu-pill");
      if (pill) {
        const idx = +pill.getAttribute("data-gundu-region");
        setRegion(idx);
        if (idx === 1) {
          const f = document.getElementById("gundu-virt-frame");
          if (f) f.setAttribute("src", K ? K.gunduVirtualUrl() : "https://gunduata.club/game/index.html");
          virt && (virt.hidden = false);
          document.body.style.overflow = "hidden";
        }
        return;
      }
    });

    document.getElementById("gundu-undo")?.addEventListener("click", () => {
      if (tapStack.length === 0 || placing) return;
      const [face, amt] = tapStack.pop();
      const cur = (stakes[face] | 0) - amt;
      if (cur <= 0) delete stakes[face];
      else stakes[face] = cur;
      const last = tapStack.length ? tapStack[tapStack.length - 1][0] : null;
      focusedFace = last;
      syncAllCards();
      syncPlace();
    });

    document.getElementById("gundu-place-btn")?.addEventListener("click", async () => {
      if (placing || !hasAnyStake()) return;
      if (!K || !K.isAuthed()) {
        location.hash = "login";
        return;
      }
      if (K.isLocalDemo()) {
        window.alert("Demo account cannot place bets.");
        return;
      }
      const lineItems = [];
      for (let n = 1; n <= 6; n++) {
        const a = stakes[n] | 0;
        if (a > 0) lineItems.push([n, a]);
      }
      lineItems.sort((x, y) => x[0] - y[0]);
      placing = true;
      syncPlace();
      let lastBal = null;
      let errMsg = null;
      for (const [n, amount] of lineItems) {
        const { data, error } = await K.postGundataBet(n, amount);
        if (data && data.walletBalance) lastBal = data.walletBalance;
        if (error) {
          errMsg = error;
          break;
        }
      }
      for (let n = 1; n <= 6; n++) delete stakes[n];
      tapStack = [];
      focusedFace = null;
      syncAllCards();
      placing = false;
      syncPlace();
      if (lastBal) setBalanceDisplays(lastBal, lastBal);
      else await refreshAllBalances();
      if (errMsg) window.alert(errMsg);
      else window.alert(lineItems.length === 1 ? "Bet placed" : "Bets placed");
    });

    document.getElementById("gundu-open-bet-history")?.addEventListener("click", async () => {
      if (histModal) histModal.hidden = false;
      const list = document.getElementById("gundu-bet-history-list");
      const empty = document.getElementById("gundu-bet-history-empty");
      if (!K || !K.isAuthed() || K.isLocalDemo()) {
        if (empty) {
          empty.hidden = false;
          empty.textContent = "Sign in with a real account to see your bets.";
        }
        if (list) {
          list.hidden = true;
          list.innerHTML = "";
        }
        return;
      }
      if (empty) empty.textContent = "Loading…";
      const { data, error } = await K.fetchGundataBetsMine();
      if (error) {
        if (empty) empty.textContent = error;
        if (list) {
          list.hidden = true;
        }
        return;
      }
      if (!data || !data.length) {
        if (empty) {
          empty.hidden = false;
          empty.textContent = "No bets yet.";
        }
        if (list) {
          list.hidden = true;
          list.innerHTML = "";
        }
        return;
      }
      if (empty) empty.hidden = true;
      if (list) {
        list.hidden = false;
        list.innerHTML = data
          .map((b) => {
            return (
              "<li>#" +
              (b.id != null ? b.id : "") +
              " · Face " +
              (b.number != null ? b.number : "") +
              " · " +
              (b.chip_amount || "") +
              " · " +
              (b.status || "") +
              " · " +
              (b.created_at || "").replace("T", " ").slice(0, 16) +
              "</li>"
            );
          })
          .join("");
      }
    });
    document.getElementById("gundu-bet-history-close")?.addEventListener("click", () => {
      if (histModal) histModal.hidden = true;
    });
    document.getElementById("gundu-bet-history-backdrop")?.addEventListener("click", () => {
      if (histModal) histModal.hidden = true;
    });
    document.getElementById("gundu-fs-open")?.addEventListener("click", openFs);
    document.getElementById("gundu-fs-close")?.addEventListener("click", closeFs);
    document.getElementById("gundu-virt-close")?.addEventListener("click", () => {
      if (virt) virt.hidden = true;
      document.body.style.overflow = "";
      setRegion(0);
    });
    document.getElementById("gundu-virt-backdrop")?.addEventListener("click", () => {
      if (virt) virt.hidden = true;
      document.body.style.overflow = "";
      setRegion(0);
    });

    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      if (fsRoot && !fsRoot.hidden) {
        e.preventDefault();
        closeFs();
      }
      if (histModal && !histModal.hidden) histModal.hidden = true;
      if (virt && !virt.hidden) {
        virt.hidden = true;
        document.body.style.overflow = "";
        setRegion(0);
      }
    });

    buildLastStrip();
    syncChips();
    syncAllCards();
    syncPlace();
    setRegion(0);
  })();
  (function initCockfight() {
    const odds = document.getElementById("cockfight-odds");
    const chips = document.getElementById("cock-chips");
    if (odds) {
      odds.addEventListener("click", (e) => {
        const btn = e.target.closest(".cock-portrait");
        if (!btn || !odds.contains(btn)) return;
        odds.querySelectorAll(".cock-portrait").forEach((b) => {
          b.classList.toggle("is-selected", b === btn);
          b.setAttribute("aria-pressed", b === btn ? "true" : "false");
        });
      });
    }
    if (chips) {
      chips.addEventListener("click", (e) => {
        const c = e.target.closest(".cock-chip");
        if (!c || !chips.contains(c)) return;
        chips.querySelectorAll(".cock-chip").forEach((x) => x.classList.toggle("is-on", x === c));
      });
    }
    const sideMap = { Meron: "MERON", Draw: "DRAW", Wala: "WALA" };
    document.getElementById("cock-place-btn")?.addEventListener("click", async () => {
      const sideEl = document.querySelector("#cockfight-odds .cock-portrait.is-selected");
      if (!sideEl) {
        window.alert("Pick Meron, Draw, or Wala");
        return;
      }
      if (!K || !K.isAuthed()) {
        location.hash = "login";
        return;
      }
      if (K.isLocalDemo()) {
        window.alert("Demo account cannot place bets.");
        return;
      }
      const lab = sideEl.getAttribute("data-cf-side");
      const apiSide = sideMap[lab] || "MERON";
      const chip = document.querySelector("#cock-chips .cock-chip.is-on");
      const amt = parseInt(chip ? chip.getAttribute("data-amt") : "100", 10) || 100;
      const { data, error } = await K.postMeronWalaBet(apiSide, amt);
      if (error) window.alert(error);
      else {
        if (data && data.walletBalance) setBalanceDisplays(data.walletBalance, data.walletBalance);
        else await refreshAllBalances();
        window.alert("Bet placed");
      }
    });
    const hModal = document.getElementById("cf-history");
    const openH = async () => {
      if (hModal) hModal.hidden = false;
      const list = document.getElementById("cf-history-list");
      const empty = document.getElementById("cf-history-empty");
      if (!K || !K.isAuthed() || K.isLocalDemo()) {
        if (empty) {
          empty.hidden = false;
          empty.querySelector("p").textContent = "Sign in with a real account to see bet history.";
        }
        if (list) {
          list.hidden = true;
          list.innerHTML = "";
        }
        return;
      }
      const { data, error } = await K.fetchMeronWalaBetsMine();
      if (error) {
        if (empty) {
          empty.hidden = false;
          empty.querySelector("p").textContent = error;
        }
        if (list) list.hidden = true;
        return;
      }
      if (!data || !data.length) {
        if (empty) {
          empty.hidden = false;
          empty.querySelector("p").textContent = "No bets placed yet";
        }
        if (list) {
          list.hidden = true;
          list.innerHTML = "";
        }
        return;
      }
      if (empty) empty.hidden = true;
      if (list) {
        list.hidden = false;
        list.innerHTML = data
          .map(
            (b) =>
              "<li>" +
              (b.side || "") +
              " · ₹" +
              (b.stake != null ? b.stake : "") +
              " · " +
              (b.status || "") +
              " · " +
              String(b.created_at || "")
                .replace("T", " ")
                .slice(0, 19) +
              "</li>"
          )
          .join("");
      }
    };
    const closeH = () => hModal && (hModal.hidden = true);
    document.getElementById("cock-open-history")?.addEventListener("click", openH);
    document.getElementById("cf-history-close")?.addEventListener("click", closeH);
    document.getElementById("cf-history-backdrop")?.addEventListener("click", closeH);
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && hModal && !hModal.hidden) closeH();
    });
  })();

  document.getElementById("referral-share-row")?.addEventListener("click", () => {
    const code = "AGHMU545";
    const text = `Join me on Kokoroko! Code: ${code}`;
    if (navigator.share) {
      navigator.share({ title: "Kokoroko", text }).catch(() => {});
    } else {
      window.alert(text);
    }
  });

  function setPwToggle(input, btn) {
    if (!input || !btn) return;
    btn.addEventListener("click", () => {
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      btn.setAttribute("aria-label", show ? "Hide password" : "Show password");
      btn.setAttribute("title", show ? "Hide password" : "Show password");
    });
  }
  setPwToggle(document.getElementById("login-pass"), document.getElementById("login-pass-toggle"));
  setPwToggle(document.getElementById("reg-pass"), document.getElementById("reg-pass-toggle"));

  let otpSendCount = 0;
  document.getElementById("reg-otp-btn")?.addEventListener("click", () => {
    const first = otpSendCount === 0;
    otpSendCount++;
    const btn = document.getElementById("reg-otp-btn");
    if (btn) btn.textContent = "Resend";
    window.alert(first ? "Verification code sent (demo)" : "Code resent (demo)");
  });
  document.getElementById("reg-phone")?.addEventListener("input", (e) => {
    e.target.value = e.target.value.replace(/\D/g, "").slice(0, 15);
  });
  document.getElementById("reg-otp")?.addEventListener("input", (e) => {
    e.target.value = e.target.value.replace(/\D/g, "").slice(0, 6);
  });

  document.getElementById("login-form")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const u = document.getElementById("login-user");
    const p = document.getElementById("login-pass");
    const err = document.getElementById("login-err");
    const btn = document.getElementById("login-submit");
    if (!K || !u || !p) return;
    if (!u.value.trim()) {
      if (err) {
        err.textContent = "Please enter your phone number.";
        err.hidden = false;
      }
      return;
    }
    if (!p.value) {
      if (err) {
        err.textContent = "Please enter your password.";
        err.hidden = false;
      }
      return;
    }
    if (err) err.hidden = true;
    if (btn) {
      btn.disabled = true;
      btn.setAttribute("aria-busy", "true");
    }
    const res = await K.login(u.value, p.value);
    if (btn) {
      btn.disabled = false;
      btn.setAttribute("aria-busy", "false");
    }
    if (res.ok) {
      location.hash = "home";
      u.value = "";
      p.value = "";
    } else if (err) {
      err.textContent = res.error || "Sign in failed";
      err.hidden = false;
    }
  });

  document.getElementById("register-form")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const elU = document.getElementById("reg-username");
    const elP = document.getElementById("reg-phone");
    const elPw = document.getElementById("reg-pass");
    const elO = document.getElementById("reg-otp");
    const err = document.getElementById("register-err");
    if (!K || !elU || !elP || !elPw) return;
    if (err) err.hidden = true;
    const res = await K.register({
      username: elU.value,
      phone: elP.value,
      password: elPw.value,
      otp: elO && elO.value
    });
    if (res.ok) {
      window.alert("Account created. Please log in.");
      location.hash = "login";
      if (elO) elO.value = "";
    } else if (res.apksuccess) {
      window.alert("Account created. Please log in.");
      location.hash = "login";
    } else {
      if (err) {
        err.textContent = res.error || "Could not create account";
        err.hidden = false;
      } else {
        window.alert(res.error || "Could not create account");
      }
    }
  });

  window.addEventListener("hashchange", onHash);
  onHash();
})();
