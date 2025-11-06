const $ = (s) => document.querySelector(s);
const PLN = (n) =>
    (Number.isFinite(n) ? n : 0).toLocaleString("pl-PL", {
        style: "currency",
        currency: "PLN",
        maximumFractionDigits: 2,
    });

const debounce = (fn, ms = 120) => {
    let t;
    return (...a) => {
        clearTimeout(t);
        t = setTimeout(() => fn(...a), ms);
    };
};

const API_BASE = window.__API_BASE__ || "";

const TYPE_ENUM = {
    employment: "EMPLOYMENT",
    mandate: "MANDATE",
    specific: "SPECIFIC_TASK",
    b2b: "B2B",
};

function readUi() {
    const amount = Math.max(0, Number($("#sc-amount")?.value || 0));
    const mode = document.querySelector('input[name="sc-mode"]:checked')?.value || "gross";
    const type = document.querySelector('input[name="sc-type"]:checked')?.value || "employment";
    const pit0 = $("#sc-pit0")?.checked || false;
    return { amount, mode, type, pit0 };
}

function buildPayload(ui, overrides = {}) {
    return {
        amount: overrides.amount ?? ui.amount,
        amountMode: (overrides.mode ?? ui.mode) === "net" ? "NET" : "GROSS",
        contractType: TYPE_ENUM[overrides.type ?? ui.type] || "EMPLOYMENT",
        year: 2025,
        pit0: overrides.pit0 ?? ui.pit0,
    };
}

async function callCalculate(payload) {
    const res = await fetch(`${API_BASE}/api/salary/calculate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(`API ${res.status}: ${await res.text().catch(() => "")}`);
    return await res.json();
}

function renderList(parts, net, grossForBars) {
    const gross = Math.max(1, Number(grossForBars || 0));
    const C = {
        net: getComputedStyle(document.querySelector(".sc")).getPropertyValue("--netto").trim() || "#5b7fff",
        pit: "#ff6b8b",
        health: "#f9b54c",
        sickness: "#7cc4ff",
        disability: "#b7d1ff",
        pension: "#9aaeff",
    };

    const rows = [
        { key: "net",        label: "Net amount",           val: net,            color: C.net },
        { key: "pension",    label: "Pension insurance",    val: parts.pension,  color: C.pension },
        { key: "disability", label: "Disability insurance", val: parts.disability, color: C.disability },
        { key: "sickness",   label: "Sickness insurance",   val: parts.sickness, color: C.sickness },
        { key: "health",     label: "Health insurance",     val: parts.health,   color: C.health },
        { key: "pit",        label: "PIT prepayment",       val: parts.pit,      color: C.pit },
    ];

    const box = $("#sc-list");
    if (!box) return;

    box.innerHTML = rows
        .map((r) => {
            const pct = Math.max(0, Math.min(100, (Number(r.val || 0) / gross) * 100));
            return `
      <div class="sc-li" style="--p:${pct}; --c:${r.color}">
        <div class="sc-li__bar"></div>
        <div class="sc-li__content">
          <span class="sc-li__label"><i class="sc-li__dot"></i>${r.label}</span>
          <b>${PLN(r.val)}</b>
        </div>
      </div>`;
        })
        .join("");
}

function renderDonut(parts) {
    const svg = $("#sc-donut");
    if (!svg) return;
    const r = 52, C = 2 * Math.PI * r;
    const STROKE = 16;
    const EPS = 0.001;

    const segs = [
        { key: "pit",        color: "#ff6b8b" },
        { key: "health",     color: "#f9b54c" },
        { key: "sickness",   color: "#7cc4ff" },
        { key: "disability", color: "#b7d1ff" },
        { key: "pension",    color: "#9aaeff" },
    ];

    const total = parts.gross > 0 ? parts.gross : 1;
    let acc = 0;

    svg.innerHTML = `
    <defs>
      <linearGradient id="donutBg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%"  stop-color="var(--grad1)" />
        <stop offset="100%" stop-color="var(--grad2)" />
      </linearGradient>
      <filter id="softShadow" x="-50%" y="-50%" width="200%" height="200%">
        <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="rgba(0,0,0,.12)"/>
      </filter>
    </defs>
    <g transform="translate(60,60)">
      <circle r="${r}" fill="none" stroke="url(#donutBg)" stroke-width="${STROKE}" opacity=".15"></circle>
    </g>
  `;

    const g = document.createElementNS("http://www.w3.org/2000/svg", "g");
    g.setAttribute("transform", "translate(60,60) rotate(-90)");
    g.setAttribute("filter", "url(#softShadow)");

    segs.forEach((s) => {
        const rawFrac = Math.max(0, Math.min(1, (parts[s.key] || 0) / total));
        if (rawFrac <= 0) return;

        const frac = Math.min(1 - acc, rawFrac + EPS);
        const dash = frac * C;

        const path = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        path.setAttribute("r", String(r));
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", s.color);
        path.setAttribute("stroke-width", String(STROKE));
        path.setAttribute("stroke-linecap", "butt");
        path.setAttribute("stroke-dasharray", `${dash} ${C}`);
        path.setAttribute("stroke-dashoffset", `${-acc * C}`);

        acc += rawFrac;
        g.appendChild(path);
    });

    svg.appendChild(g);

    const center = document.createElementNS("http://www.w3.org/2000/svg", "g");
    center.setAttribute("transform", "translate(60,60)");
    center.innerHTML = `
    <text x="0" y="-4" text-anchor="middle" font-size="12" fill="var(--muted)">Net</text>
    <text x="0" y="14" text-anchor="middle" font-size="14" font-weight="800">${PLN(parts.net)}</text>`;
    svg.appendChild(center);
}

function ensureBarsLegend() {
    const cmp = document.querySelector(".sc-compare");
    if (!cmp) return;
    if (cmp.querySelector(".sc-compare__head")) return;
    const h = cmp.querySelector("h3");
    if (!h) return;
    const head = document.createElement("div");
    head.className = "sc-compare__head";
    head.innerHTML = `
    <h3>${h.textContent}</h3>
    <div class="sc-legend">
      <span><i class="sw netto"></i>Net</span>
      <span><i class="sw pit"></i>PIT</span>
      <span><i class="sw zdrow"></i>Health</span>
      <span><i class="sw chor"></i>Sickness</span>
      <span><i class="sw rent"></i>Disability</span>
      <span><i class="sw emer"></i>Pension</span>
    </div>`;
    h.replaceWith(head);
}

function layoutBarLabels(colEl, insetPx = 26, segPadPx = 8, vGapPx = 3) {
    const H = colEl.clientHeight || 480;
    const spans = Array.from(colEl.querySelectorAll(".sc-bar__seg > span"));
    if (!spans.length) return;

    const items = spans
        .map((sp) => {
            sp.style.transform = "translateY(-50%)";

            const cp   = parseFloat(sp.dataset.cp || "0");
            const hSeg = parseFloat(sp.dataset.h  || "0");

            sp.style.setProperty("--lane", 0);

            const desired = (cp / 100) * H;
            const tpx = ((cp - hSeg / 2) / 100) * H;
            const bpx = ((cp + hSeg / 2) / 100) * H;

            const half = (sp.offsetHeight || 18) / 2;
            const lo = Math.max(insetPx, tpx + segPadPx + half);
            const hi = Math.min(H - insetPx, bpx - segPadPx - half);

            const segPx = (hSeg / 100) * H;
            const bias = Math.min(4, Math.max(0, half - segPx / 2));

            let y = Math.min(Math.max(desired + bias, lo), hi);
            return { sp, desired, y, half, tpx, bpx, lo, hi, hSeg };
        })
        .sort((a, b) => a.y - b.y);

    let changed = true, iter = 0;
    while (changed && iter++ < 40) {
        changed = false;

        for (let i = 1; i < items.length; i++) {
            const prev = items[i - 1];
            const cur  = items[i];

            const need = (prev.half + cur.half + vGapPx) - (cur.y - prev.y);
            if (need > 0) {
                const curDown = cur.hi - cur.y;
                const prevUp  = prev.y - prev.lo;

                if (curDown >= prevUp) {
                    const mv = Math.min(need, curDown);
                    if (mv > 0) { cur.y += mv; changed = true; }
                } else {
                    const mv = Math.min(need, prevUp);
                    if (mv > 0) { prev.y -= mv; changed = true; }
                }
            }

            prev.y = Math.min(Math.max(prev.y, prev.lo), prev.hi);
            cur.y  = Math.min(Math.max(cur.y,  cur.lo),  cur.hi);
        }
    }

    for (let i = 1; i < items.length; i++) {
        const a = items[i - 1];
        const b = items[i];

        const aMicro = a.hSeg < 2;
        const bMicro = b.hSeg < 2;
        const aThin  = !aMicro && a.hSeg < 3.2;
        const bThin  = !bMicro && b.hSeg < 3.2;

        const close = (b.y - a.y) < (a.half + b.half + vGapPx + 2);
        if (!close) continue;

        if ((aMicro && bThin) || (aThin && bMicro)) {
            const micro = aMicro ? a : b;
            const thin  = aThin  ? a : b;
            micro.sp.style.setProperty("--lane", 0);
            thin.sp.style.setProperty("--lane", 1);
            continue;
        }

        if (aThin && bThin) {
            const smaller = a.hSeg <= b.hSeg ? a : b;
            const larger  = smaller === a ? b : a;
            smaller.sp.style.setProperty("--lane", 0);
            larger.sp.style.setProperty("--lane", 1);
            continue;
        }

        if (aMicro && bMicro) {
            a.sp.style.setProperty("--lane", 0);
            b.sp.style.setProperty("--lane", 1);
        }
    }

    items.forEach((it) => {
        it.sp.style.transform = `translateY(-50%) translateY(${it.y - it.desired}px)`;
        it.sp.style.willChange = "transform";
    });
}

async function renderBarsFromApi(_ignored, ui) {
    const box = $("#sc-bars");
    if (!box) return;

    const scenarios = [
        { name: "Employment",    type: "employment" },
        { name: "Mandate",       type: "mandate" },
        { name: "Specific-task", type: "specific" },
        { name: "B2B",           type: "b2b" },
    ];

    const results = await Promise.all(
        scenarios.map((s) => callCalculate(buildPayload(ui, { type: s.type, mode: ui.mode, amount: ui.amount })))
    );

    const html = results
        .map((r, i) => {
            const grossR = Number(r.gross || 0);
            const netR   = Number(r.net || 0);
            const it     = r.items || {};
            const parts  = {
                pension:    Number(it.pension || 0),
                disability: Number(it.disability || 0),
                sickness:   Number(it.sickness || 0),
                health:     Number(it.health || 0),
                pit:        Number(it.pit || 0),
            };

            const stack = [
                { cls: "pit",        val: parts.pit },
                { cls: "zdrowotne",  val: parts.health },
                { cls: "chorobowe",  val: parts.sickness },
                { cls: "rentowe",    val: parts.disability },
                { cls: "emerytalne", val: parts.pension },
            ];

            let top = 0;
            const taxSegs = stack
                .map((p) => {
                    const h = grossR ? (p.val / grossR) * 100 : 0;
                    if (h <= 0) return "";
                    const centerPct = top + h / 2;

                    const seg = `
            <div class="sc-bar__seg sc-bar__seg--${p.cls}" style="top:${top}%;height:${h}%">
              <span data-cp="${centerPct}" data-h="${h}" title="${h.toFixed(1)}%" style="transform:translateY(-50%); --lane:0">${h.toFixed(1)}%</span>
            </div>`;
                    top += h;
                    return seg;
                })
                .join("");

            const netPct = Math.max(0, 100 - top);
            const enteredLine = ui.mode === "gross" ? `gross: ${PLN(ui.amount)}` : "";
            const derivedLine = ui.mode === "net"   ? `gross: ${PLN(grossR)}` : "";

            return `
        <div class="sc-bar" title="${scenarios[i].name}">
          <div class="sc-bar__col">
            ${taxSegs}
            <div class="sc-bar__seg sc-bar__seg--netto" style="bottom:0;height:${netPct}%">
              <span data-cp="${100 - netPct / 2}" data-h="${netPct}" title="${netPct.toFixed(1)}%">${netPct.toFixed(1)}%</span>
            </div>
          </div>
          <div class="sc-bar__label">
            <span>${scenarios[i].name}</span>
            <strong class="sc-net-val">${PLN(netR)}</strong>
            <small>${enteredLine}</small>
            ${derivedLine ? `<small>${derivedLine}</small>` : ""}
          </div>
        </div>`;
        })
        .join("");

    ensureBarsLegend();
    box.innerHTML = html;

    const relayout = () => {
        const root = document.querySelector(".sc");
        const cssInset = parseFloat(getComputedStyle(root).getPropertyValue("--label-vert-inset")) || 26;

        document
            .querySelectorAll("#sc-bars .sc-bar__col")
            .forEach((col) => layoutBarLabels(col, /* inset */ cssInset, /* segPad */ -3.1, /* vGap */ 3));
    };
    relayout();

    if (!window.__scBarsResizeBound) {
        window.addEventListener("resize", relayout, { passive: true });
        window.__scBarsResizeBound = true;
    }
}

function renderMonths(row) {
    const months = ["January","February","March","April","May","June","July","August","September","October","November","December"];
    const tbody = $("#sc-months");
    if (!tbody) return;
    tbody.innerHTML = months
        .map(
            (m) => `
        <tr>
          <td>${m}</td>
          <td>${PLN(row.gross)}</td>
          <td>${PLN(row.social)}</td>
          <td>${PLN(row.health)}</td>
          <td>${PLN(row.pit)}</td>
          <td>${PLN(row.net)}</td>
        </tr>`
        )
        .join("");
}

function ensureAnnualFooter() {
    const tableCard = document.querySelector(".sc-card.sc-table");
    if (!tableCard) return null;
    let el = tableCard.querySelector("#sc-annual-bottom");
    if (!el) {
        el = document.createElement("p");
        el.id = "sc-annual-bottom";
        el.className = "sc-annual";
        tableCard.appendChild(el);
    }
    return el;
}


async function updateUI() {
    const ui = readUi();
    try {
        const main = await callCalculate(buildPayload(ui));
        const annualEl = ensureAnnualFooter();
        const gross = Number(main.gross || 0);
        const net   = Number(main.net || 0);
        const it    = main.items || {};
        const parts = {
            pension:    Number(it.pension || 0),
            disability: Number(it.disability || 0),
            sickness:   Number(it.sickness || 0),
            health:     Number(it.health || 0),
            pit:        Number(it.pit || 0),
        };

        if ($("#sc-net")) $("#sc-net").textContent = PLN(net);
        if (annualEl) {
            annualEl.textContent = `Year total (net): ${PLN(net * 12)}`;
        }

        renderList(parts, net, gross);
        renderDonut({ net, gross, ...parts });
        await renderBarsFromApi(null, ui);

        renderMonths({
            gross,
            net,
            social: parts.pension + parts.disability + parts.sickness,
            health: parts.health,
            pit: parts.pit,
        });
    } catch (e) {
        console.warn("Salary API error:", e);
    }
}

export function initSalaryCalculator() {
    $("#sc-amount") && $("#sc-amount").addEventListener("input", debounce(updateUI, 120));
    document.querySelectorAll('input[name="sc-mode"], input[name="sc-type"]').forEach((el) =>
        el.addEventListener("change", updateUI)
    );
    $("#sc-pit0") && $("#sc-pit0").addEventListener("change", updateUI);

    ensureBarsLegend();
    updateUI();
}

window.initSalaryCalculator = initSalaryCalculator;
