(function () {
  const K = window.KokorokoApi;
  if (!K) {
    console.error("KokorokoApi missing — include api.js before app.js");
  }
  const TAB_IDS = ["home", "promotion", "wallet", "profile"];
  const ALL_SCREENS = [...TAB_IDS, "cockfight", "gundu", "login", "register"];
  const NAV_HASHES = ALL_SCREENS;
  let lastWalletData = null;
  let cockfightLiveBound = false;
  let cockfightMaxTime = 0;
  let cockfightDialogOpen = false;
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
    const authPills = document.getElementById("header-auth-pills");
    const wall = document.getElementById("header-wallet-pill");
    const bottomNavWallet = document.getElementById("bottom-nav-wallet");
    const authed = !!(K && K.isAuthed());
    /* Header + bottom nav wallet only for a real server session, not unauthenticated or local demo (svs/svs). */
    const showWalletPill = authed && K && !K.isLocalDemo();
    if (authPills) authPills.hidden = showWalletPill;
    if (wall) wall.hidden = !showWalletPill;
    if (bottomNavWallet) bottomNavWallet.hidden = !showWalletPill;
    document.documentElement.dataset.auth = authed ? "1" : "0";
    document.documentElement.dataset.headerWallet = showWalletPill ? "1" : "0";
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
  /** #cockfight only: local MP4 (assets/cockfight_live_stream.mp4) — no HLS, no controls, no forward seek, no pause. */
  function setupCockfightLiveStream() {
    const video = document.getElementById("cockfight-video");
    if (!video) return;
    const src = "assets/cockfight_live_stream.mp4";
    if (video.getAttribute("data-cf-wired") !== "1") {
      video.replaceChildren();
      video.src = src;
      video.setAttribute("data-cf-wired", "1");
    }
    if (!cockfightLiveBound) {
      cockfightLiveBound = true;
      video.addEventListener("timeupdate", () => {
        if (location.hash !== "#cockfight") return;
        if (video.currentTime + 0.3 < cockfightMaxTime) {
          cockfightMaxTime = 0;
        }
        cockfightMaxTime = Math.max(cockfightMaxTime, video.currentTime);
      });
      video.addEventListener("seeking", () => {
        if (location.hash !== "#cockfight") return;
        if (video.currentTime > cockfightMaxTime + 0.2) {
          try {
            video.currentTime = cockfightMaxTime;
          } catch {}
        }
      });
      video.addEventListener("pause", () => {
        if (location.hash !== "#cockfight") return;
        if (cockfightDialogOpen) return;
        if (!document.getElementById("cockfight-panel") || document.getElementById("cockfight-panel").hidden) return;
        video.play().catch(() => {});
      });
      video.addEventListener("stalled", () => {
        if (location.hash === "#cockfight") video.play().catch(() => {});
      });
      video.addEventListener("contextmenu", (e) => e.preventDefault());
    }
    if (location.hash === "#cockfight") {
      video.muted = true;
      video.play().catch(() => {});
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
    /* Wallet needs auth for real money; Profile & Settings UI matches APK and is viewable for layout (API actions still need sign-in). */
    if (K && h === "wallet" && !K.isAuthed()) {
      location.replace("#login");
      return;
    }
    const key = ALL_SCREENS.includes(h) ? h : "home";
    if (h !== key && !ALL_SCREENS.includes(h)) {
      location.replace("#" + key);
      return;
    }
    document.documentElement.dataset.tab = key;
    if (key !== "home") {
      closeLiveVideoFullscreen();
    }
    if (key !== "cockfight") {
      closeCockfightFullscreen();
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
      setTimeout(setupCockfightLiveStream, 0);
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

  document
    .querySelectorAll(
      ".wallet-pill[data-nav], .game-tile[data-nav], a.brand--home[data-nav], a.cockfight-tap-home[data-nav]"
    )
    .forEach((el) => {
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
  const liveCardWrap = document.getElementById("live-card-wrap");
  const liveVideo = document.getElementById("live-video");
  if (live && offMsg && liveCardWrap) {
    const sync = () => {
      const on = live.checked;
      offMsg.hidden = on;
      liveCardWrap.hidden = !on;
      if (!on) closeLiveVideoFullscreen();
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

  function closeLiveVideoFullscreen() {
    const fsRoot = document.getElementById("live-fullscreen");
    const vIn = document.getElementById("live-video");
    const vFs = document.getElementById("live-video-fs");
    if (!fsRoot || fsRoot.hidden) return;
    fsRoot.hidden = true;
    if (vIn && vFs) {
      vIn.currentTime = vFs.currentTime || 0;
      vFs.pause();
    }
    document.body.style.overflow = "";
    const on = document.getElementById("live-on")?.checked;
    if (on && document.documentElement.dataset.tab === "home" && vIn) {
      vIn.play().catch(() => {});
    }
  }

  function openLiveVideoFullscreen() {
    const fsRoot = document.getElementById("live-fullscreen");
    const vIn = document.getElementById("live-video");
    const vFs = document.getElementById("live-video-fs");
    if (!fsRoot || !vIn || !vFs) return;
    if (!document.getElementById("live-on")?.checked) return;
    fsRoot.hidden = false;
    vIn.pause();
    const src = vIn.querySelector("source");
    vFs.muted = true;
    vFs.loop = true;
    vFs.poster = vIn.poster || "";
    if (src && src.src) {
      let s = vFs.querySelector("source");
      if (!s) {
        s = document.createElement("source");
        vFs.appendChild(s);
      }
      s.src = src.src;
      s.type = src.type || "video/mp4";
      vFs.load();
    }
    vFs.currentTime = vIn.currentTime || 0;
    vFs.play().catch(() => {});
    document.body.style.overflow = "hidden";
  }

  function closeCockfightFullscreen() {
    /* Exit native fullscreen if active */
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      (document.exitFullscreen || document.webkitExitFullscreen || (() => {})).call(document);
    }
    if (typeof window.__closeCfFsBetSheet === "function") window.__closeCfFsBetSheet();
    if (typeof window.__cockfightSyncMainFromFs === "function") window.__cockfightSyncMainFromFs();
    const fsRoot = document.getElementById("cockfight-fullscreen");
    const vFs = document.getElementById("cockfight-video-fs");
    const vIn = document.getElementById("cockfight-video");
    cockfightDialogOpen = false;
    if (fsRoot && !fsRoot.hidden) {
      fsRoot.hidden = true;
      if (vFs) {
        vFs.pause();
        vFs.removeAttribute("src");
        vFs.replaceChildren();
      }
      document.body.style.overflow = "";
    }
    if (document.documentElement.dataset.tab === "cockfight" && vIn) {
      vIn.muted = true;
      vIn.play().catch(() => {});
    }
  }

  function openCockfightFullscreen() {
    const vIn = document.getElementById("cockfight-video");
    if (!vIn) return;
    if (document.documentElement.dataset.tab !== "cockfight") return;
    /* Always use overlay dialog so betting strip (APK-style) stays visible */
    openCockfightFsDialog();
  }

  function openCockfightFsDialog() {
    const fsRoot = document.getElementById("cockfight-fullscreen");
    const vIn  = document.getElementById("cockfight-video");
    const vFs  = document.getElementById("cockfight-video-fs");
    if (!fsRoot || !vIn || !vFs) return;

    cockfightDialogOpen = true;
    const url = (vIn.currentSrc || "").trim() || "assets/cockfight_live_stream.mp4";
    /* Only reload if src changed */
    if (vFs.src !== url) {
      vFs.replaceChildren();
      vFs.src = url;
      vFs.muted = true;
      vFs.loop = true;
      vFs.poster = vIn.poster || "";
    }
    const syncAndShow = () => {
      try { vFs.currentTime = vIn.currentTime || 0; } catch {}
      vIn.pause();
      fsRoot.hidden = false;
      document.body.style.overflow = "hidden";
      vFs.play().catch(() => {});
      if (typeof window.__closeCfMainBetSheet === "function") window.__closeCfMainBetSheet();
      if (typeof window.__cockfightSyncFsFromMain === "function") window.__cockfightSyncFsFromMain();
    };
    if (vFs.readyState >= 1) {
      syncAndShow();
    } else {
      vFs.addEventListener("loadedmetadata", syncAndShow, { once: true });
      vFs.load();
    }
  }

  document.getElementById("live-video-max")?.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    openLiveVideoFullscreen();
  });
  document.getElementById("live-fs-close")?.addEventListener("click", () => {
    closeLiveVideoFullscreen();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key !== "Escape") return;
    const lfs = document.getElementById("live-fullscreen");
    if (lfs && !lfs.hidden) closeLiveVideoFullscreen();
    const cfs = document.getElementById("cockfight-fullscreen");
    if (cfs && !cfs.hidden) closeCockfightFullscreen();
  });

  document.getElementById("cockfight-video-max")?.addEventListener("click", (e) => {
    e.preventDefault();
    e.stopPropagation();
    openCockfightFullscreen();
  });
  document.getElementById("cockfight-fs-close")?.addEventListener("click", () => {
    closeCockfightFullscreen();
  });
  /* Resume main video when native fullscreen exits */
  document.addEventListener("fullscreenchange", () => {
    if (!document.fullscreenElement) {
      cockfightDialogOpen = false;
      const vIn = document.getElementById("cockfight-video");
      if (document.documentElement.dataset.tab === "cockfight" && vIn) {
        vIn.muted = true;
        vIn.play().catch(() => {});
      }
    }
  });
  document.addEventListener("webkitfullscreenchange", () => {
    if (!document.webkitFullscreenElement) {
      cockfightDialogOpen = false;
      const vIn = document.getElementById("cockfight-video");
      if (document.documentElement.dataset.tab === "cockfight" && vIn) {
        vIn.muted = true;
        vIn.play().catch(() => {});
      }
    }
  });

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

    /* ── Deposit Flow Dialog ── */
    (function setupDepositDialog() {
      const dialog = document.getElementById("dep-dialog");
      const closeBtn = document.getElementById("dep-dialog-close");
      const amountDisplay = document.getElementById("dep-amount-display");
      const timerEl = document.getElementById("dep-timer");
      const loadingEl = document.getElementById("dep-loading");
      const methodsWrap = document.getElementById("dep-methods-wrap");
      const methodsRow = document.getElementById("dep-methods-row");
      const detailsCard = document.getElementById("dep-details-card");
      const upiBlock = document.getElementById("dep-upi-block");
      const upiIdEl = document.getElementById("dep-upi-id");
      const copyUpiBtn = document.getElementById("dep-copy-upi");
      const qrWrap = document.getElementById("dep-qr-wrap");
      const qrImg = document.getElementById("dep-qr-img");
      const bankBlock = document.getElementById("dep-bank-block");
      const bankGrid = document.getElementById("dep-bank-grid");
      const uploadSection = document.getElementById("dep-upload-section");
      const uploadBtn = document.getElementById("dep-upload-btn");
      const filePreview = document.getElementById("dep-file-preview");
      const fileNameEl = document.getElementById("dep-file-name");
      const fileRemove = document.getElementById("dep-file-remove");
      const errEl = document.getElementById("dep-err");
      const submitBtn = document.getElementById("dep-submit-btn");
      const submitTxt = document.getElementById("dep-submit-txt");
      const depFileIn = document.getElementById("dep-screenshot-input");
      const upiLabel = document.getElementById("dep-upi-label");

      let methods = [];
      let selectedMethod = null;
      let selectedFile = null;
      let depositAmount = 0;
      let timerInterval = null;

      function startTimer() {
        let secs = 600;
        function fmt(s) {
          return String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0");
        }
        if (timerEl) timerEl.textContent = fmt(secs);
        clearInterval(timerInterval);
        timerInterval = setInterval(() => {
          secs = Math.max(0, secs - 1);
          if (timerEl) timerEl.textContent = fmt(secs);
          if (secs === 0) clearInterval(timerInterval);
        }, 1000);
      }
      function stopTimer() { clearInterval(timerInterval); }

      function showErr(msg) {
        if (!errEl) return;
        errEl.textContent = msg;
        errEl.hidden = !msg;
      }

      function copyText(text) {
        if (navigator.clipboard) {
          navigator.clipboard.writeText(text).catch(() => {});
        } else {
          const el = document.createElement("textarea");
          el.value = text;
          document.body.appendChild(el);
          el.select();
          document.execCommand("copy");
          document.body.removeChild(el);
        }
      }

      function buildUpiDeepLink(m) {
        // Build standard UPI intent URL for PhonePe / GPay / Paytm / any UPI app
        const upi = m.upiId || "";
        if (!upi) return m.deepLink || null;
        const t = (m.type || "").toUpperCase();
        const params = new URLSearchParams({
          pa: upi,
          pn: "Kokoroko",
          am: String(depositAmount),
          cu: "INR",
          tn: "Deposit to Kokoroko"
        });
        // Use app-specific scheme when possible, fallback to generic upi://
        if (t.includes("PHONEPE")) return "phonepe://pay?" + params.toString();
        if (t.includes("GPAY"))    return "tez://upi/pay?" + params.toString();
        if (t.includes("PAYTM"))   return "paytmmp://upi/pay?" + params.toString();
        return "upi://pay?" + params.toString();
      }

      function launchPayment(m) {
        const link = buildUpiDeepLink(m);
        if (link) {
          // Try app deep link; fallback to upi:// which browsers/Android handle
          window.location.href = link;
        }
        // Show upload section immediately so user can upload after paying
        if (uploadSection) uploadSection.hidden = false;
      }

      function renderMethod(m) {
        // Hide UPI ID card — payment goes direct via deep link
        if (detailsCard) detailsCard.hidden = true;
        if (upiBlock) upiBlock.hidden = true;
        if (bankBlock) bankBlock.hidden = true;

        // Bank accounts (no deep link): show details card
        const t = (m.type || "").toUpperCase();
        const isBank = t.includes("BANK");
        if (isBank && detailsCard && bankGrid) {
          const rows = [
            ["Account Name", m.accountHolder],
            ["Bank Name", m.bankName],
            ["Account Number", m.accountNumber],
            ["IFSC Code", m.ifsc]
          ].filter(([, v]) => v);
          if (rows.length) {
            bankGrid.innerHTML = rows.map(([label, val]) =>
              `<div class="dep-bank-row">
                <span class="dep-bank-row__label">${label}</span>
                <span class="dep-bank-row__val">${val}
                  <button type="button" class="dep-bank-copy" onclick="navigator.clipboard&&navigator.clipboard.writeText('${val}')" aria-label="Copy ${label}">
                    <svg viewBox="0 0 24 24" width="14" height="14"><path fill="currentColor" d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
                  </button>
                </span>
              </div>`
            ).join("");
            if (bankBlock) bankBlock.hidden = false;
            if (detailsCard) detailsCard.hidden = false;
          }
        }
        if (uploadSection) uploadSection.hidden = false;
      }

      function selectMethod(m) {
        selectedMethod = m;
        if (methodsRow) {
          methodsRow.querySelectorAll(".dep-method-btn").forEach((b) => {
            b.classList.toggle("is-active", Number(b.dataset.mid) === m.id);
          });
        }
        renderMethod(m);
        // Launch payment app immediately on tap
        launchPayment(m);
      }

      function updateSubmit() {
        if (submitBtn) submitBtn.disabled = !selectedFile || !selectedMethod;
      }

      function openDialog(amount) {
        if (!dialog) return;
        depositAmount = amount;
        selectedFile = null;
        selectedMethod = null;
        methods = [];
        if (amountDisplay) amountDisplay.textContent = "\u20B9" + amount.toLocaleString("en-IN");
        if (loadingEl) loadingEl.hidden = false;
        if (methodsWrap) methodsWrap.hidden = true;
        if (detailsCard) detailsCard.hidden = true;
        if (uploadSection) uploadSection.hidden = true;
        if (filePreview) filePreview.hidden = true;
        if (errEl) errEl.hidden = true;
        if (submitBtn) submitBtn.disabled = true;
        dialog.hidden = false;
        document.body.style.overflow = "hidden";
        startTimer();

        K.fetchPaymentMethodsDetails().then(({ data, error }) => {
          if (loadingEl) loadingEl.hidden = true;
          if (error || !data || !data.length) {
            showErr(error || "No payment methods available. Please contact support.");
            return;
          }
          methods = data;
          if (methodsRow) {
            const iconColors = {
              PHONEPE: "#5f259f", GPAY: "#4285f4", PAYTM: "#002970",
              UPI: "#ff6b00", BANK: "#1565c0", QR: "#388e3c"
            };
            methodsRow.innerHTML = methods.map((m) => {
              const t = (m.type || "UPI").toUpperCase();
              const color = iconColors[Object.keys(iconColors).find((k) => t.includes(k)) || "UPI"] || "#ff6b00";
              return `<button type="button" class="dep-method-btn" data-mid="${m.id}">
                <span class="dep-method-btn__icon" style="background:${color}18;">
                  <svg viewBox="0 0 24 24" width="22" height="22"><path fill="${color}" d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z"/></svg>
                </span>
                <span class="dep-method-btn__name">${m.name || m.type}</span>
                <span class="dep-method-btn__arrow">
                  <svg viewBox="0 0 24 24" width="22" height="22"><path fill="${color}" d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z"/></svg>
                </span>
              </button>`;
            }).join("");
            methodsRow.querySelectorAll(".dep-method-btn").forEach((b) => {
              b.addEventListener("click", () => {
                const m = methods.find((x) => x.id === Number(b.dataset.mid));
                if (m) selectMethod(m);
              });
            });
          }
          if (methodsWrap) methodsWrap.hidden = false;
          selectMethod(methods[0]);
          updateSubmit();
        });
      }

      function closeDialog() {
        if (dialog) dialog.hidden = true;
        document.body.style.overflow = "";
        stopTimer();
        if (depFileIn) depFileIn.value = "";
        selectedFile = null;
        if (filePreview) filePreview.hidden = true;
        updateSubmit();
      }

      if (closeBtn) closeBtn.addEventListener("click", closeDialog);

      if (uploadBtn && depFileIn) {
        uploadBtn.addEventListener("click", () => { depFileIn.value = ""; depFileIn.click(); });
        depFileIn.addEventListener("change", () => {
          const f = depFileIn.files && depFileIn.files[0];
          if (!f) return;
          selectedFile = f;
          if (fileNameEl) fileNameEl.textContent = f.name;
          if (filePreview) filePreview.hidden = false;
          showErr("");
          updateSubmit();
        });
      }

      if (fileRemove) {
        fileRemove.addEventListener("click", () => {
          selectedFile = null;
          if (depFileIn) depFileIn.value = "";
          if (filePreview) filePreview.hidden = true;
          updateSubmit();
        });
      }

      if (submitBtn) {
        submitBtn.addEventListener("click", async () => {
          if (!selectedFile || !selectedMethod || !K) return;
          showErr("");
          submitBtn.disabled = true;
          if (submitTxt) submitTxt.textContent = "Submitting…";
          const { data, error } = await K.postDepositUpload(selectedFile, depositAmount, selectedMethod.id);
          submitBtn.disabled = false;
          if (submitTxt) submitTxt.textContent = "Submit Payment Proof";
          if (error) {
            showErr(error);
          } else {
            closeDialog();
            await refreshAllBalances();
            window.alert("Deposit submitted! Status: " + (data && data.status ? data.status : "PENDING") + "\nYour account will be credited once approved.");
          }
        });
      }

      /* Hook into Proceed to Deposit */
      window.__openDepositDialog = openDialog;
    })();

    btnDep?.addEventListener("click", async () => {
      if (!K || !K.isAuthed()) {
        location.hash = "login";
        return;
      }
      if (K.isLocalDemo()) {
        window.alert("Sign in with a real account to deposit.");
        return;
      }
      const amount = parseInt(amt && amt.value ? amt.value.replace(/\D/g, "") : "0", 10) || 0;
      if (amount < 100) {
        window.alert("Minimum deposit is ₹100.");
        return;
      }
      if (window.__openDepositDialog) window.__openDepositDialog(amount);
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
        }
        return;
      }
    });
  }

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
      if (!fsRoot) return;
      fsRoot.hidden = false;
      streamBox?.classList.add("gundu-stream-box--fs");
      document.body.style.overflow = "hidden";
      videoInline?.pause();
      if (!videoFs) return;
      videoFs.poster = videoInline?.poster || "assets/banner_gundu.png";
      const srcEl = videoInline?.querySelector("source");
      const srcUrl = srcEl?.src || "assets/gunduata_live.mp4";
      const savedTime = videoInline?.currentTime || 0;
      let s = videoFs.querySelector("source");
      if (!s) { s = document.createElement("source"); videoFs.appendChild(s); }
      if (s.src !== srcUrl) {
        s.src = srcUrl;
        s.type = "video/mp4";
        videoFs.load();
        videoFs.addEventListener("canplay", function onReady() {
          videoFs.removeEventListener("canplay", onReady);
          videoFs.currentTime = savedTime;
          videoFs.play().catch(() => {});
        });
      } else {
        videoFs.currentTime = savedTime;
        videoFs.play().catch(() => {});
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
  (function initHomeBanner() {
    const wrap = document.getElementById("home-banner");
    if (!wrap) return;
    const slides = Array.from(wrap.querySelectorAll(".banner__link"));
    const dots = Array.from(wrap.querySelectorAll(".banner__dot"));
    if (slides.length < 2) return;
    let cur = 0;
    function goTo(idx) {
      slides[cur].classList.remove("banner__link--active");
      slides[cur].setAttribute("aria-hidden", "true");
      slides[cur].setAttribute("tabindex", "-1");
      dots[cur].classList.remove("banner__dot--on");
      cur = (idx + slides.length) % slides.length;
      slides[cur].classList.add("banner__link--active");
      slides[cur].removeAttribute("aria-hidden");
      slides[cur].removeAttribute("tabindex");
      dots[cur].classList.add("banner__dot--on");
    }
    dots.forEach((d, i) => d.addEventListener("click", () => { goTo(i); clearInterval(timer); timer = setInterval(() => goTo(cur + 1), 3500); }));
    let timer = setInterval(() => goTo(cur + 1), 3500);
    wrap.addEventListener("mouseenter", () => clearInterval(timer));
    wrap.addEventListener("mouseleave", () => { timer = setInterval(() => goTo(cur + 1), 3500); });
  })();
  (function initCockfight() {
    function setCfSide(side) {
      ["cockfight-side-bar", "cockfight-fs-side-bar"].forEach((id) => {
        const bar = document.getElementById(id);
        if (!bar) return;
        bar.querySelectorAll(".cockfight-side-btn").forEach((b) => {
          const on = b.getAttribute("data-cf-side") === side;
          b.classList.toggle("is-selected", on);
          b.setAttribute("aria-pressed", on ? "true" : "false");
        });
      });
    }
    function setCfAmt(amtStr) {
      ["cock-chips", "cockfight-fs-chips"].forEach((id) => {
        const root = document.getElementById(id);
        if (!root) return;
        root.querySelectorAll(".cock-chip").forEach((c) => {
          const on = c.getAttribute("data-amt") === String(amtStr);
          c.classList.toggle("is-on", on);
        });
      });
    }
    function syncBodyScrollLock() {
      const fsDlg = document.getElementById("cockfight-fullscreen");
      const mainSheet = document.getElementById("cf-bet-sheet-main");
      const fsSheet = document.getElementById("cf-bet-sheet-fs");
      const fsUi = fsDlg && !fsDlg.hidden;
      const msOpen = mainSheet && !mainSheet.hidden;
      const fssOpen = fsSheet && !fsSheet.hidden;
      if (fsUi || msOpen || fssOpen) document.body.style.overflow = "hidden";
      else document.body.style.overflow = "";
    }
    function closeCfBetSheet(context) {
      const sheet =
        context === "fs"
          ? document.getElementById("cf-bet-sheet-fs")
          : document.getElementById("cf-bet-sheet-main");
      if (sheet) sheet.hidden = true;
      syncBodyScrollLock();
    }
    window.__closeCfMainBetSheet = () => closeCfBetSheet("main");
    window.__closeCfFsBetSheet = () => closeCfBetSheet("fs");

    function syncCfFsFromMain() {
      const mainBar = document.getElementById("cockfight-side-bar");
      const sel = mainBar && mainBar.querySelector(".cockfight-side-btn.is-selected");
      const side = sel ? sel.getAttribute("data-cf-side") : "Meron";
      setCfSide(side || "Meron");
      const mainChips = document.getElementById("cock-chips");
      const chipOn = mainChips && mainChips.querySelector(".cock-chip.is-on");
      const amt = chipOn ? chipOn.getAttribute("data-amt") : "100";
      setCfAmt(amt || "100");
    }
    function syncCfMainFromFs() {
      const fsBar = document.getElementById("cockfight-fs-side-bar");
      const sel = fsBar && fsBar.querySelector(".cockfight-side-btn.is-selected");
      const side = sel ? sel.getAttribute("data-cf-side") : "Meron";
      setCfSide(side || "Meron");
      const fsChips = document.getElementById("cockfight-fs-chips");
      const chipOn = fsChips && fsChips.querySelector(".cock-chip.is-on");
      const amt = chipOn ? chipOn.getAttribute("data-amt") : "100";
      setCfAmt(amt || "100");
    }
    window.__cockfightSyncFsFromMain = syncCfFsFromMain;
    window.__cockfightSyncMainFromFs = syncCfMainFromFs;

    function updateBetSheetTitle(btn, titleEl) {
      const label =
        (btn && (btn.getAttribute("data-cf-display") || btn.getAttribute("data-cf-side"))) || "";
      const odd = (btn && btn.getAttribute("data-cf-odd")) || "";
      if (titleEl) titleEl.textContent = label + " · " + odd + "×";
    }
    function openCfBetSheet(context, triggerBtn) {
      const sheet =
        context === "fs"
          ? document.getElementById("cf-bet-sheet-fs")
          : document.getElementById("cf-bet-sheet-main");
      const titleEl =
        context === "fs"
          ? document.getElementById("cf-bet-sheet-fs-title")
          : document.getElementById("cf-bet-sheet-main-title");
      if (!sheet || !triggerBtn) return;
      updateBetSheetTitle(triggerBtn, titleEl);
      sheet.hidden = false;
      syncBodyScrollLock();
    }

    ["cockfight-side-bar", "cockfight-fs-side-bar"].forEach((barId) => {
      const bar = document.getElementById(barId);
      if (!bar) return;
      bar.addEventListener("click", (e) => {
        const btn = e.target.closest(".cockfight-side-btn");
        if (!btn || !bar.contains(btn)) return;
        const side = btn.getAttribute("data-cf-side");
        if (side) setCfSide(side);
        /* Only open sheet for fullscreen mode; main layout has inline chips */
        if (barId.includes("fs")) {
          openCfBetSheet("fs", btn);
        }
      });
    });
    ["cock-chips", "cockfight-fs-chips"].forEach((id) => {
      const chips = document.getElementById(id);
      if (!chips) return;
      chips.addEventListener("click", (e) => {
        const c = e.target.closest(".cock-chip");
        if (!c || !chips.contains(c)) return;
        const amt = c.getAttribute("data-amt");
        if (amt) setCfAmt(amt);
      });
    });

    document.getElementById("cf-bet-sheet-main-bd")?.addEventListener("click", () => closeCfBetSheet("main"));
    document.getElementById("cf-bet-sheet-main-close")?.addEventListener("click", () => closeCfBetSheet("main"));
    document.getElementById("cf-bet-sheet-fs-bd")?.addEventListener("click", () => closeCfBetSheet("fs"));
    document.getElementById("cf-bet-sheet-fs-close")?.addEventListener("click", () => closeCfBetSheet("fs"));

    const sideMap = { Meron: "MERON", Draw: "DRAW", Wala: "WALA" };
    function getCfBetContext() {
      const fsDlg = document.getElementById("cockfight-fullscreen");
      const fsSheet = document.getElementById("cf-bet-sheet-fs");
      if (fsDlg && !fsDlg.hidden && fsSheet && !fsSheet.hidden) return "fs";
      /* Main layout now always visible — always return main */
      return "main";
    }
    async function placeCockfightBet() {
      const ctx = getCfBetContext();
      const bar =
        ctx === "fs"
          ? document.getElementById("cockfight-fs-side-bar")
          : document.getElementById("cockfight-side-bar");
      const chipRoot =
        ctx === "fs" ? document.getElementById("cockfight-fs-chips") : document.getElementById("cock-chips");
      const sideEl = bar && bar.querySelector(".cockfight-side-btn.is-selected");
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
      const chip = chipRoot && chipRoot.querySelector(".cock-chip.is-on");
      const amt = parseInt(chip ? chip.getAttribute("data-amt") : "100", 10) || 100;
      const { data, error } = await K.postMeronWalaBet(apiSide, amt);
      if (error) window.alert(error);
      else {
        if (data && data.walletBalance) setBalanceDisplays(data.walletBalance, data.walletBalance);
        else await refreshAllBalances();
        window.alert("Bet placed");
        closeCfBetSheet(ctx);
      }
    }
    document.getElementById("cock-place-btn")?.addEventListener("click", placeCockfightBet);
    document.getElementById("cockfight-fs-place-btn")?.addEventListener("click", placeCockfightBet);

    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      const ms = document.getElementById("cf-bet-sheet-main");
      const fss = document.getElementById("cf-bet-sheet-fs");
      if (fss && !fss.hidden) {
        e.preventDefault();
        closeCfBetSheet("fs");
        return;
      }
      if (ms && !ms.hidden) {
        e.preventDefault();
        closeCfBetSheet("main");
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
    document.getElementById("cockfight-fs-open-history")?.addEventListener("click", openH);
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

  document.getElementById("reg-phone")?.addEventListener("input", (e) => {
    e.target.value = e.target.value.replace(/\D/g, "").slice(0, 15);
  });

  document.getElementById("login-form")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const u = document.getElementById("login-user");
    const p = document.getElementById("login-pass");
    const err = document.getElementById("login-err");
    const btn = document.getElementById("login-submit");
    const btnTxt = btn && btn.querySelector(".ap-pill__txt");
    function showErr(msg) {
      if (err) { err.textContent = msg; err.hidden = false; }
    }
    function setBusy(busy) {
      if (!btn) return;
      btn.disabled = busy;
      btn.setAttribute("aria-busy", busy ? "true" : "false");
      if (btnTxt) btnTxt.textContent = busy ? "Logging in…" : "Login";
    }
    if (!K) { showErr("Page error — please refresh the page."); return; }
    if (!u || !p) return;
    if (!u.value.trim()) { showErr("Please enter your phone number or username."); return; }
    if (!p.value) { showErr("Please enter your password."); return; }
    if (err) err.hidden = true;
    setBusy(true);
    try {
      const res = await K.login(u.value, p.value);
      setBusy(false);
      if (res.ok) {
        location.hash = "home";
        u.value = "";
        p.value = "";
      } else {
        showErr(res.error || "Sign in failed. Check your credentials.");
      }
    } catch (ex) {
      setBusy(false);
      showErr("Network error — please try again.");
    }
  });

  document.getElementById("register-form")?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const elU = document.getElementById("reg-username");
    const elP = document.getElementById("reg-phone");
    const elPw = document.getElementById("reg-pass");
    const err = document.getElementById("register-err");
    const regBtn = document.getElementById("register-submit");
    const regBtnTxt = regBtn && regBtn.querySelector(".ap-pill__txt--solo");
    if (!K || !elU || !elP || !elPw) return;
    if (err) err.hidden = true;
    if (regBtn) { regBtn.disabled = true; if (regBtnTxt) regBtnTxt.textContent = "Creating…"; }
    const res = await K.register({
      username: elU.value,
      phone: elP.value,
      password: elPw.value
    });
    if (regBtn) { regBtn.disabled = false; if (regBtnTxt) regBtnTxt.textContent = "Create account"; }
    if (res.ok) {
      if (elO) elO.value = "";
      if (res.autologin) {
        /* Backend issued tokens -- go straight to home */
        location.hash = "home";
      } else {
        window.alert("Account created. Please log in.");
        location.hash = "login";
      }
    } else {
      if (err) {
        err.style.color = "";
        err.textContent = res.error || "Could not create account";
        err.hidden = false;
      } else {
        window.alert(res.error || "Could not create account");
      }
    }
  });

  window.addEventListener("hashchange", onHash);
  onHash();

  /* APK download banner — Android only, show after 3 s on every page load */
  (function setupApkBanner() {
    const banner = document.getElementById("apk-banner");
    const closeBtn = document.getElementById("apk-banner-close");
    if (!banner) return;
    /* Hide entirely on Apple devices — APK doesn't work on iOS/iPadOS */
    const ua = navigator.userAgent || "";
    const isApple = /iPhone|iPad|iPod|Macintosh/i.test(ua);
    if (isApple) { banner.hidden = true; return; }
    setTimeout(() => {
      banner.classList.add("apk-banner--visible");
    }, 3000);
    if (closeBtn) {
      closeBtn.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        banner.hidden = true;
      });
    }
  })();
})();
