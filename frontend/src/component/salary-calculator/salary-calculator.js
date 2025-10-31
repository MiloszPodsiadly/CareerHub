const $ = (s) => document.querySelector(s);
const PLN = (n) =>
    (Number.isFinite(n) ? n : 0).toLocaleString("pl-PL", {
        style: "currency",
        currency: "PLN",
        maximumFractionDigits: 2,
    });
const debounce = (fn, ms = 120) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
const clamp = (v, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, v));
const pct1 = (v) => (Math.round(v * 10) / 10).toFixed(1);

const pct100 = (part, total) => (total ? (part / total) * 100 : 0);
const clamp100 = (v) => Math.max(0, Math.min(100, v));

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

function renderList(parts, net) {
    const rows = [
        ["Net amount", net],
        ["Pension insurance", parts.pension],
        ["Disability insurance", parts.disability],
        ["Sickness insurance", parts.sickness],
        ["Health insurance", parts.health],
        ["PIT prepayment", parts.pit],
    ];
    const box = $("#sc-list");
    if (!box) return;
    box.innerHTML = rows
        .map(([label, val]) => `<div class="sc-li"><span>${label}</span><b>${PLN(val)}</b></div>`)
        .join("");
}

function renderDonut(parts) {
    const svg = $("#sc-donut"); if (!svg) return;
    const r = 52, C = 2 * Math.PI * r;
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
  <defs><filter id="softShadow" x="-50%" y="-50%" width="200%" height="200%">
    <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="rgba(0,0,0,.15)"/>
  </filter></defs>
  <g transform="translate(60,60)">
    <circle r="${r}" fill="none" stroke="#e9eefb" stroke-width="16"></circle>
  </g>`;

    const g = document.createElementNS("http://www.w3.org/2000/svg", "g");
    g.setAttribute("transform", "translate(60,60) rotate(-90)");
    g.setAttribute("filter", "url(#softShadow)");

    segs.forEach((s) => {
        const frac = clamp((parts[s.key] || 0) / total, 0, 1);
        const path = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        path.setAttribute("r", String(r));
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", s.color);
        path.setAttribute("stroke-width", "16");
        path.setAttribute("stroke-dasharray", `${frac * C} ${C}`);
        path.setAttribute("stroke-dashoffset", `${-acc * C}`);
        acc += frac;
        g.appendChild(path);
    });
    svg.appendChild(g);

    const center = document.createElementNS("http://www.w3.org/2000/svg", "g");
    center.setAttribute("transform", "translate(60,60)");
    center.innerHTML = `
  <text x="0" y="-4" text-anchor="middle" font-size="10">Net</text>
  <text x="0" y="14" text-anchor="middle" font-size="12" font-weight="700">${PLN(parts.net)}</text>`;
    svg.appendChild(center);
}

function ensureBarsLegend() {
    const cmp = document.querySelector(".sc-compare"); if (!cmp) return;
    const headExists = cmp.querySelector(".sc-compare__head");
    const h = cmp.querySelector("h3");
    if (headExists || !h) return;
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

function adjustBarLabels(colEl, minGapPx = 22, insetPx = 8, segPadPx = 4) {
    const H = colEl.clientHeight || 480;
    const spans = Array.from(colEl.querySelectorAll(".sc-bar__seg > span"));
    if (!spans.length) return;

    const items = spans.map((sp) => {
        const cp = parseFloat(sp.dataset.cp || "0");
        const h  = parseFloat(sp.dataset.h  || "0");
        const desired = (cp / 100) * H;
        const topPct = cp - h / 2;
        const botPct = cp + h / 2;
        const tpx = (topPct / 100) * H;
        const bpx = (botPct / 100) * H;
        return { sp, desired, y: desired, h, tpx, bpx };
    }).sort((a,b) => a.desired - b.desired);

    const lo = insetPx, hi = H - insetPx;

    for (let i = 0; i < items.length; i++) {
        const prevY = i ? items[i - 1].y : lo;
        const lower = Math.max(lo, items[i].tpx + segPadPx, i ? prevY + minGapPx : lo);
        const upper = Math.min(hi, items[i].bpx - segPadPx);
        items[i].y = Math.min(Math.max(items[i].desired, lower), upper);
    }

    for (let i = items.length - 2; i >= 0; i--) {
        const nextY = items[i + 1].y;
        const upper = Math.min(items[i].bpx - segPadPx, nextY - minGapPx);
        const lower = Math.max(lo, items[i].tpx + segPadPx);
        items[i].y = Math.min(Math.max(items[i].y, lower), upper);
    }

    for (const it of items) {
        const yOff = Math.round(it.y - it.desired);
        it.sp.style.transform = `translateY(-50%) translateY(${yOff}px)`;
    }
}

async function renderBarsFromApi(_ignored, ui) {
    const box = $("#sc-bars"); if (!box) return;

    const scenarios = [
        { name: "Employment", type: "employment" },
        { name: "Mandate", type: "mandate" },
        { name: "Specific-task", type: "specific" },
        { name: "B2B", type: "b2b" },
    ];

    const payloads = scenarios.map((s) =>
        buildPayload(ui, { type: s.type, mode: ui.mode, amount: ui.amount })
    );

    const results = await Promise.all(payloads.map((p) => callCalculate(p)));

    const html = results.map((r, i) => {
        const grossR = Number(r.gross || 0);
        const netR   = Number(r.net || 0);
        const items  = r.items || {};
        const parts  = {
            pension:    Number(items.pension || 0),
            disability: Number(items.disability || 0),
            sickness:   Number(items.sickness || 0),
            health:     Number(items.health || 0),
            pit:        Number(items.pit || 0),
        };

        const stack = [
            { cls: "pit",        val: parts.pit },
            { cls: "zdrowotne",  val: parts.health },
            { cls: "chorobowe",  val: parts.sickness },
            { cls: "rentowe",    val: parts.disability },
            { cls: "emerytalne", val: parts.pension },
        ];

        let top = 0;
        const taxSegs = stack.map((p) => {
            const h = grossR ? (p.val / grossR) * 100 : 0;
            if (h <= 0) return "";
            const centerPct = top + h / 2;
            const seg = `
      <div class="sc-bar__seg sc-bar__seg--${p.cls}" style="top:${top}%;height:${h}%">
        <span data-cp="${centerPct}" data-h="${h}" title="${pct1(h)}%" style="transform:translateY(-50%)">${pct1(h)}%</span>
      </div>`;
            top += h;
            return seg;
        }).join("");

        const netPct = Math.max(0, 100 - top);
        const enteredLine = `${ui.mode === "gross" ? "gross" : "net"}: ${PLN(ui.amount)}`;
        const derivedLine = ui.mode === "net" ? `gross: ${PLN(grossR)}` : "";

        return `
    <div class="sc-bar" title="${scenarios[i].name}">
      <div class="sc-bar__col">
        ${taxSegs}
        <div class="sc-bar__seg sc-bar__seg--netto" style="bottom:0;height:${netPct}%">
          <span data-cp="${100 - netPct/2}" data-h="${netPct}" title="${pct1(netPct)}%" style="transform:translateY(-50%)">${pct1(netPct)}%</span>
        </div>
      </div>
      <div class="sc-bar__label">
        <span>${scenarios[i].name}</span>
        <strong class="sc-net-val">${PLN(netR)}</strong>
        <small>${enteredLine}</small>
        ${derivedLine ? `<small>${derivedLine}</small>` : ""}
      </div>
    </div>`;
    }).join("");

    ensureBarsLegend();
    box.innerHTML = html;

    Array.from(document.querySelectorAll("#sc-bars .sc-bar__col"))
        .forEach((col) => adjustBarLabels(col, 22, 8, 4));
}

function renderMonths(row) {
    const months = ["January","February","March","April","May","June","July","August","September","October","November","December"];
    const tbody = $("#sc-months"); if (!tbody) return;
    tbody.innerHTML = months.map((m) => `
  <tr>
    <td>${m}</td>
    <td>${PLN(row.gross)}</td>
    <td>${PLN(row.social)}</td>
    <td>${PLN(row.health)}</td>
    <td>${PLN(row.pit)}</td>
    <td>${PLN(row.net)}</td>
  </tr>`).join("");
}

async function updateUI() {
    const ui = readUi();

    try {
        const main = await callCalculate(buildPayload(ui));

        const gross = Number(main.gross || 0);
        const net   = Number(main.net || 0);
        const items = main.items || {};
        const parts = {
            pension:    Number(items.pension || 0),
            disability: Number(items.disability || 0),
            sickness:   Number(items.sickness || 0),
            health:     Number(items.health || 0),
            pit:        Number(items.pit || 0),
        };

        if ($("#sc-net"))     $("#sc-net").textContent = PLN(net);
        if ($("#sc-annual"))  $("#sc-annual").textContent = `Per year: ${PLN(net * 12)} net`;

        renderList(parts, net);
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
        console.warn("API error:", e);
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
