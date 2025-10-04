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
const clamp = (v, lo = 0, hi = 1) => Math.max(lo, Math.min(hi, v));
const pct1 = (v) => (Math.round(v * 10) / 10).toFixed(1); // 1 miejsce po przecinku

function calcUOP(brutto, { pit0, authors50 }) {
    const emerytalne = brutto * 0.0976;
    const rentowe = brutto * 0.015;
    const chorobowe = brutto * 0.0245;
    const spoleczne = emerytalne + rentowe + chorobowe;

    const podstawaZdrow = Math.max(0, brutto - spoleczne);
    const zdrowotne = podstawaZdrow * 0.09;

    const kup = authors50 ? Math.min(podstawaZdrow * 0.5, 120000 / 12) : 250;
    const podstawaPIT = Math.max(0, podstawaZdrow - kup);
    const pit = pit0 ? 0 : podstawaPIT * 0.12;

    const netto = brutto - spoleczne - zdrowotne - pit;
    return { brutto, netto, składniki: { emerytalne, rentowe, chorobowe, zdrowotne, pit } };
}

function calcUZ(brutto, { pit0, zusUZ, authors50 }) {
    const emerytalne = zusUZ ? brutto * 0.0976 : 0;
    const rentowe = zusUZ ? brutto * 0.015 : 0;
    const chorobowe = zusUZ ? brutto * 0.0245 : 0;
    const spoleczne = emerytalne + rentowe + chorobowe;

    const podstawaZdrow = Math.max(0, brutto - spoleczne);
    const zdrowotne = zusUZ ? podstawaZdrow * 0.09 : 0;

    const kup = (authors50 ? 0.5 : 0.2) * Math.max(0, brutto - spoleczne);

    const podstawaPIT = Math.max(0, podstawaZdrow - kup);
    const pit = pit0 ? 0 : podstawaPIT * 0.12;

    const netto = brutto - spoleczne - zdrowotne - pit;
    return { brutto, netto, składniki: { emerytalne, rentowe, chorobowe, zdrowotne, pit } };
}

function calcUOD(brutto, { pit0, authors50 }) {
    const emerytalne = 0,
        rentowe = 0,
        chorobowe = 0,
        zdrowotne = 0;
    const kup = (authors50 ? 0.5 : 0.2) * brutto;
    const podstawaPIT = Math.max(0, brutto - kup);
    const pit = pit0 ? 0 : podstawaPIT * 0.12;
    const netto = brutto - pit;

    return { brutto, netto, składniki: { emerytalne, rentowe, chorobowe, zdrowotne, pit } };
}

function compute(brutto, type, opts) {
    if (type === "uop") return calcUOP(brutto, opts);
    if (type === "uz") return calcUZ(brutto, opts);
    if (type === "uod") return calcUOD(brutto, opts);
    return calcUOP(brutto, opts);
}

function bruttoFromNetto(targetNetto, type, opts) {
    let lo = 0,
        hi = Math.max(5000, targetNetto * 1.8 + 2000);
    for (let i = 0; i < 40; i++) {
        const mid = (lo + hi) / 2;
        const { netto } = compute(mid, type, opts);
        if (netto < targetNetto) lo = mid;
        else hi = mid;
    }
    return hi;
}

function updateUI() {
    const amount = Math.max(0, Number($("#sc-amount")?.value || 0));
    const mode = document.querySelector('input[name="sc-mode"]:checked')?.value || "brutto";
    const type = document.querySelector('input[name="sc-type"]:checked')?.value || "uop";

    const opts = {
        pit0: $("#sc-student")?.checked || false,
        authors50: $("#sc-authors")?.checked || false,
        zusUZ: $("#sc-uz-zus")?.checked || false,
    };

    const brutto = mode === "brutto" ? amount : bruttoFromNetto(amount, type, opts);
    const { netto, składniki } = compute(brutto, type, opts);

    $("#sc-netto") && ($("#sc-netto").textContent = PLN(netto));
    $("#sc-annual") && ($("#sc-annual").textContent = `Rocznie: ${PLN(netto * 12)} netto`);

    renderList(składniki, netto);

    renderDonut({
        netto,
        emerytalne: składniki.emerytalne,
        rentowe: składniki.rentowe,
        chorobowe: składniki.chorobowe,
        zdrowotne: składniki.zdrowotne,
        pit: składniki.pit,
        brutto,
    });

    renderBars(brutto, opts);

    renderMonths({
        brutto,
        netto,
        spoleczne: składniki.emerytalne + składniki.rentowe + składniki.chorobowe,
        zdrowotne: składniki.zdrowotne,
        pit: składniki.pit,
    });
}

function renderList(s, netto) {
    const rows = [
        ["Kwota netto", netto],
        ["Ubezp. emerytalne", s.emerytalne],
        ["Ubezp. rentowe", s.rentowe],
        ["Ubezp. chorobowe", s.chorobowe],
        ["Ubezp. zdrowotne", s.zdrowotne],
        ["Zaliczka na PIT", s.pit],
    ];
    const box = $("#sc-list");
    if (!box) return;
    box.innerHTML = rows
        .map(
            ([label, val]) => `
    <div class="sc-li"><span>${label}</span><b>${PLN(val)}</b></div>
  `
        )
        .join("");
}

function renderDonut(parts) {
    const svg = $("#sc-donut");
    if (!svg) return;
    const r = 52,
        C = 2 * Math.PI * r;

    const segs = [
        { key: "pit", color: "#ff6b8b" },
        { key: "zdrowotne", color: "#f9b54c" },
        { key: "chorobowe", color: "#7cc4ff" },
        { key: "rentowe", color: "#b7d1ff" },
        { key: "emerytalne", color: "#9aaeff" },
    ];

    const total = parts.brutto > 0 ? parts.brutto : 1;
    let acc = 0;

    svg.innerHTML = `
    <defs>
      <filter id="softShadow" x="-50%" y="-50%" width="200%" height="200%">
        <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="rgba(0,0,0,.15)"/>
      </filter>
    </defs>
    <g transform="translate(60,60)">
      <circle r="${r}" fill="none" stroke="#e9eefb" stroke-width="16"></circle>
    </g>
  `;

    const g = document.createElementNS("http://www.w3.org/2000/svg", "g");
    g.setAttribute("transform", "translate(60,60) rotate(-90)");
    g.setAttribute("filter", "url(#softShadow)");

    segs.forEach((s) => {
        const frac = clamp((parts[s.key] || 0) / total);
        const path = document.createElementNS("http://www.w3.org/2000/svg", "circle");
        path.setAttribute("r", String(r));
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", s.color);
        path.setAttribute("stroke-width", "16");
        path.setAttribute("stroke-linecap", "butt");
        path.setAttribute("stroke-dasharray", `${frac * C} ${C}`);
        path.setAttribute("stroke-dashoffset", `${-acc * C}`);
        acc += frac;
        g.appendChild(path);
    });
    svg.appendChild(g);

    const center = document.createElementNS("http://www.w3.org/2000/svg", "g");
    center.setAttribute("transform", "translate(60,60)");
    center.innerHTML = `
    <text x="0" y="-4" text-anchor="middle" font-size="10" fill="currentColor">Netto</text>
    <text x="0" y="14" text-anchor="middle" font-size="12" font-weight="700" fill="currentColor">${PLN(parts.netto)}</text>
  `;
    svg.appendChild(center);
}

function ensureBarsLegend() {
    const cmp = document.querySelector(".sc-compare");
    if (!cmp) return;

    const headExists = cmp.querySelector(".sc-compare__head");
    const h = cmp.querySelector("h3");
    if (headExists || !h) return;

    const head = document.createElement("div");
    head.className = "sc-compare__head";
    head.innerHTML = `
    <h3>${h.textContent}</h3>
    <div class="sc-legend">
      <span><i class="sw netto"></i>Netto</span>
      <span><i class="sw pit"></i>PIT</span>
      <span><i class="sw zdrow"></i>Zdrowotne</span>
      <span><i class="sw chor"></i>Chorobowe</span>
      <span><i class="sw rent"></i>Rentowe</span>
      <span><i class="sw emer"></i>Emerytalne</span>
    </div>`;
    h.replaceWith(head);
}

function renderBars(brutto, opts) {
    const uop = compute(brutto, "uop", opts);
    const uz  = compute(brutto, "uz",  opts);
    const uod = compute(brutto, "uod", opts);

    const items = [
        { name: "UoP",      model: uop },
        { name: "Zlecenie", model: uz  },
        { name: "Dzieło",   model: uod },
        { name: "B2B",      model: null },
    ];

    const box = $("#sc-bars");
    if (!box) return;

    box.innerHTML = items.map((it) => {
        if (!it.model) {
            return `
        <div class="sc-bar">
          <div class="sc-bar__col">
            <div class="sc-bar__seg sc-bar__seg--pit"        style="top:0;height:10%"><span>—</span></div>
            <div class="sc-bar__seg sc-bar__seg--zdrowotne"  style="top:10%;height:8%"><span>—</span></div>
            <div class="sc-bar__seg sc-bar__seg--chorobowe"  style="top:18%;height:5%"><span>—</span></div>
            <div class="sc-bar__seg sc-bar__seg--rentowe"    style="top:23%;height:4%"><span>—</span></div>
            <div class="sc-bar__seg sc-bar__seg--emerytalne" style="top:27%;height:6%"><span>—</span></div>
            <div class="sc-bar__seg sc-bar__seg--netto"      style="bottom:0;height:67%"><span>—</span></div>
          </div>
          <div class="sc-bar__label"><span>${it.name}</span><span>–</span></div>
        </div>`;
        }

        const s = it.model.składniki;
        const nettoPct = clamp(it.model.netto / brutto) * 100;

        const parts = [
            { cls: "pit",        val: s.pit },
            { cls: "zdrowotne",  val: s.zdrowotne },
            { cls: "chorobowe",  val: s.chorobowe },
            { cls: "rentowe",    val: s.rentowe },
            { cls: "emerytalne", val: s.emerytalne },
        ];

        let top = 0;
        let tinyIndex = 0;

        const taxSegs = parts.map(p => {
            const h = clamp(p.val / brutto) * 100;
            if (h < 0.5) return "";

            const isTiny = h < 3;
            const tinyCls = isTiny ? ` tiny ${ (tinyIndex++ % 2 ? 'even' : 'odd') }` : "";

            const seg = `<div class="sc-bar__seg sc-bar__seg--${p.cls}${tinyCls}"
                     style="top:${top}%;height:${h}%">
                 <span>${pct1(h)}%</span>
               </div>`;
            top += h;
            return seg;
        }).join("");

        return `
      <div class="sc-bar" title="${it.name}">
        <div class="sc-bar__col">
          ${taxSegs}
          <div class="sc-bar__seg sc-bar__seg--netto" style="bottom:0;height:${nettoPct}%">
            <span>${pct1(nettoPct)}%</span>
          </div>
        </div>
        <div class="sc-bar__label">
          <span>${it.name}</span>
          <span>${PLN(it.model.netto)}</span>
        </div>
      </div>`;
    }).join("");
}

function renderMonths(row) {
    const months = [
        "Styczeń",
        "Luty",
        "Marzec",
        "Kwiecień",
        "Maj",
        "Czerwiec",
        "Lipiec",
        "Sierpień",
        "Wrzesień",
        "Październik",
        "Listopad",
        "Grudzień",
    ];
    const tbody = $("#sc-months");
    if (!tbody) return;
    tbody.innerHTML = months
        .map(
            (m) => `
    <tr>
      <td>${m}</td>
      <td>${PLN(row.brutto)}</td>
      <td>${PLN(row.spoleczne)}</td>
      <td>${PLN(row.zdrowotne)}</td>
      <td>${PLN(row.pit)}</td>
      <td>${PLN(row.netto)}</td>
    </tr>`
        )
        .join("");
}

export function initSalaryCalculator() {
    $("#sc-amount") && $("#sc-amount").addEventListener("input", debounce(updateUI, 120));
    document
        .querySelectorAll('input[name="sc-mode"], input[name="sc-type"]')
        .forEach((el) => el.addEventListener("change", updateUI));
    ["#sc-student", "#sc-authors", "#sc-uz-zus"].forEach((id) => {
        const el = document.querySelector(id);
        if (el) el.addEventListener("change", updateUI);
    });

    ensureBarsLegend();
    updateUI();
}
