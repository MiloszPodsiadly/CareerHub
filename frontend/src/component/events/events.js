/* ===== CONFIG (mock only for now) ===== */
const MODE = 'mock'; // when backend is ready, change to 'live'
const API_BASE = window.__API_BASE__ || (location.hostname === 'localhost' ? 'http://localhost:8080' : '');

/* ===== ISO-3166 country list ===== */
const ISO2 = "AF,AX,AL,DZ,AS,AD,AO,AI,AQ,AG,AR,AM,AW,AU,AT,AZ,BS,BH,BD,BB,BY,BE,BZ,BJ,BM,BT,BO,BQ,BA,BW,BV,BR,IO,BN,BG,BF,BI,KH,CM,CA,CV,KY,CF,TD,CL,CN,CX,CC,CO,KM,CG,CD,CK,CR,CI,HR,CU,CW,CY,CZ,DK,DJ,DM,DO,EC,EG,SV,GQ,ER,EE,SZ,ET,FK,FO,FJ,FI,FR,GF,PF,TF,GA,GM,GE,DE,GH,GI,GR,GL,GD,GP,GU,GT,GG,GN,GW,GY,HT,HM,VA,HN,HK,HU,IS,IN,ID,IR,IQ,IE,IM,IL,IT,JM,JP,JE,JO,KZ,KE,KI,KP,KR,KW,KG,LA,LV,LB,LS,LR,LY,LI,LT,LU,MO,MG,MW,MY,MV,ML,MT,MH,MQ,MR,MU,YT,MX,FM,MD,MC,MN,ME,MS,MA,MZ,MM,NA,NR,NP,NL,NC,NZ,NI,NE,NG,NU,NF,MK,MP,NO,OM,PK,PW,PS,PA,PG,PY,PE,PH,PN,PL,PT,PR,QA,RE,RO,RU,RW,BL,SH,KN,LC,MF,PM,VC,WS,SM,ST,SA,SN,RS,SC,SL,SG,SX,SK,SI,SB,SO,ZA,GS,SS,ES,LK,SD,SR,SJ,SE,CH,SY,TW,TJ,TZ,TH,TL,TG,TK,TO,TT,TN,TR,TM,TC,TV,UG,UA,AE,GB,US,UM,UY,UZ,VU,VE,VN,VG,VI,WF,EH,YE,ZM,ZW".split(",");
const REGION = new Intl.DisplayNames(["en"], { type: "region" });
const COUNTRIES = [{ code: "", name: "All countries" }, { code: "ZZ-ONLINE", name: "Online" }]
    .concat(ISO2.map(code => ({ code, name: REGION.of(code) })))
    .sort((a,b) => a.name.localeCompare(b.name));

/* ===== UI constants ===== */
const TECHS = ["javascript","typescript","react","java","python","c#","php","aws","kubernetes","devops","data","ai/ml"];
const TYPES = ["meetup","conference","hackathon","masterclass","workshop","webinar"];

/* ===== STATE ===== */
let state = { q:"", country:"", city:"", type:"", tech:"", from:"", to:"", page:0, size:25, total:0, totalPages:1 };
const $ = (sel) => document.querySelector(sel);

/* ===== MOCK DATA ===== */
const MOCK = (() => {
    const CITY_BY = {
        PL:["Warsaw","Kraków","Wrocław","Gdańsk","Poznań"],
        US:["San Francisco","New York","Seattle","Austin","Boston"],
        GB:["London","Manchester","Birmingham","Edinburgh","Bristol"],
        DE:["Berlin","Munich","Hamburg","Frankfurt","Cologne"],
        FR:["Paris","Lyon","Lille","Marseille","Nantes"],
        ES:["Madrid","Barcelona","Valencia","Seville","Malaga"],
        NL:["Amsterdam","Rotterdam","Utrecht","Eindhoven","The Hague"],
        SE:["Stockholm","Gothenburg","Malmö"],
        CA:["Toronto","Vancouver","Montreal"],
        AU:["Sydney","Melbourne","Brisbane"]
    };
    const pick = (arr) => arr[Math.floor(Math.random()*arr.length)];
    const now = new Date(), days = (n) => new Date(now.getTime() + n*864e5);

    const items = [];
    const baseCountries = Object.keys(CITY_BY);
    for (let i=0;i<200;i++){
        const cc = pick(baseCountries);
        const country = REGION.of(cc);
        const city = pick(CITY_BY[cc]);
        const type = pick(TYPES);
        const tech = pick(TECHS);
        const start = days((Math.random()*150|0)+1);
        const title = `${tech.toUpperCase()} ${type} • ${city}`;
        const url = `https://example.com/events/${cc.toLowerCase()}-${city.toLowerCase().replace(/\s+/g,'-')}-${i}`;
        items.push({
            id:`mock-${i}`, source:"mock", title, url,
            city, country, startAt: start.toISOString(), categories: [type, tech]
        });
    }
    for (let i=0;i<20;i++){
        const tech = pick(TECHS), type = pick(TYPES);
        items.push({
            id:`mock-online-${i}`, source:"mock",
            title:`${tech.toUpperCase()} ${type} • Online`,
            url:`https://example.com/events/online-${i}`,
            city:null, country:"Online",
            startAt: days((Math.random()*90|0)+1).toISOString(),
            categories:[type, tech, "online"]
        });
    }

    function search(params){
        const { q, country, city, category, tech, from, to, page=0, size=25 } = params;
        let arr = items.slice();
        if (country && country !== "ZZ-ONLINE") arr = arr.filter(e => e.country === REGION.of(country));
        if (country === "ZZ-ONLINE") arr = arr.filter(e => e.country === "Online" || (e.categories||[]).includes("online"));
        if (city) arr = arr.filter(e => (e.city||"").toLowerCase().includes(city.toLowerCase()));
        if (category) arr = arr.filter(e => (e.categories||[]).map(s=>s.toLowerCase()).includes(category.toLowerCase()));
        if (tech) arr = arr.filter(e => (e.categories||[]).map(s=>s.toLowerCase()).includes(tech.toLowerCase()));
        if (q){
            const qq = q.toLowerCase();
            arr = arr.filter(e => (e.title||"").toLowerCase().includes(qq) || (e.city||"").toLowerCase().includes(qq) || (e.country||"").toLowerCase().includes(qq));
        }
        if (from) arr = arr.filter(e => e.startAt && e.startAt >= `${from}T00:00:00Z`);
        if (to)   arr = arr.filter(e => e.startAt && e.startAt <= `${to}T23:59:59Z`);

        arr.sort((a,b) => new Date(a.startAt) - new Date(b.startAt));
        const total = arr.length;
        const totalPages = Math.max(1, Math.ceil(total / size));
        const start = page * size, end = start + size;
        return { content: arr.slice(start, end), totalElements: total, totalPages };
    }

    return { search };
})();

/* ===== PUBLIC ===== */
export function initEvents(){
    // fill countries
    const sel = $("#country");
    sel.innerHTML = COUNTRIES.map(c => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join("");
    sel.value = ""; // All countries

    // searchable country
    $("#country-search").addEventListener("input", () => filterCountry($("#country-search").value));

    // enable city after country
    $("#country").addEventListener("change", () => {
        const isChosen = !!$("#country").value;
        $("#city").disabled = !isChosen || $("#country").value === "";
        if (!isChosen) $("#city").value = "";
        onChange();
    });

    // tech chips
    $("#chips-tech").innerHTML = TECHS.map(t=>`<button class="chip" data-tech="${t}">${t}</button>`).join("");
    document.querySelectorAll("#chips-tech .chip").forEach(b => b.addEventListener("click", () => {
        state.tech = (state.tech===b.dataset.tech ? "" : b.dataset.tech);
        highlightTech(); onChange();
    }));

    // quick type chips
    document.querySelectorAll(".chip[data-type]").forEach(b => b.addEventListener("click", () => {
        $("#type").value = b.dataset.type; onChange();
    }));

    // inputs
    $("#q").addEventListener("input", debounce(onChange, 300));
    $("#city").addEventListener("input", debounce(onChange, 0));
    $("#type").addEventListener("change", onChange);
    $("#from").addEventListener("change", onChange);
    $("#to").addEventListener("change", onChange);
    $("#btn-reset").addEventListener("click", () => { reset(); onChange(); });

    // pagination buttons
    $("#btn-prev").addEventListener("click", () => { if (state.page>0){ state.page--; fetchAndRender(false); } });
    $("#btn-next").addEventListener("click", () => { if (state.page+1<state.totalPages){ state.page++; fetchAndRender(false); } });

    // init from URL
    readQueryToUI();
    $("#city").disabled = !$("#country").value;
    onChange();
}

/* ===== Filtering + rendering ===== */
function filterCountry(query){
    const q = (query || "").trim().toLowerCase();
    const sel = $("#country");
    const visible = COUNTRIES.filter(c => !q || c.name.toLowerCase().includes(q) || c.code.toLowerCase() === q);
    sel.innerHTML = visible.map(c => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join("");
    if (![...sel.options].some(o => o.value === state.country)) {
        sel.value = ""; state.country = ""; $("#city").disabled = true; $("#city").value = "";
    } else { sel.value = state.country; }
}

function onChange(){
    state = {
        ...state,
        q: $("#q").value.trim(),
        country: $("#country").value.trim(), // "" or ISO2 or "ZZ-ONLINE"
        city: $("#city").value.trim(),
        type: $("#type").value,
        from: $("#from").value,
        to: $("#to").value,
        page: 0
    };
    writeQueryFromState();
    fetchAndRender(false);
}

async function fetchAndRender(){
    setStatus("Loading…");
    const params = {
        q: state.q,
        country: state.country,
        city: state.city,
        category: state.type,
        tech: state.tech,
        from: state.from,
        to: state.to,
        page: state.page,
        size: state.size
    };

    try{
        const res = MODE === 'mock'
            ? MOCK.search(params)
            : await fetchLive(params);

        state.total = res.totalElements ?? 0;
        state.totalPages = res.totalPages ?? Math.max(1, Math.ceil(state.total / state.size));

        const items = (res.content || []).map(toViewModel);
        renderCards(items);
        $("#meta").textContent = `${state.total} results`;

        // pagination UI
        $("#page-info").textContent = `Page ${state.page+1} of ${state.totalPages}`;
        $("#btn-prev").disabled = state.page === 0;
        $("#btn-next").disabled = (state.page+1) >= state.totalPages;

        setStatus(items.length === 0 ? "No events match your filters." : "");
    }catch(e){
        setStatus(""); $("#grid").hidden = true;
        const container = document.querySelector(".results .list");
        if (!container.querySelector(".error")) container.insertAdjacentHTML("afterbegin", `<div class="error">Load error: ${e.message}</div>`);
    }
}

async function fetchLive(params){
    const p = new URLSearchParams({
        q: params.q,
        ...(params.country === "ZZ-ONLINE" ? { format:"online" } : { country: params.country }),
        city: params.city,
        category: params.category,
        tech: params.tech,
        from: params.from ? `${params.from}T00:00:00Z` : "",
        to: params.to ? `${params.to}T23:59:59Z` : "",
        page: params.page, size: params.size, sort: "startAt,asc"
    });
    [...p.keys()].forEach(k=>{ if(!p.get(k)) p.delete(k); });
    const url = `${API_BASE}/api/events?${p.toString()}`;
    const r = await fetch(url, { mode: 'cors' });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

function renderCards(items){
    const grid = $("#grid");
    grid.hidden = false;
    grid.innerHTML = items.map(ev => `
    <article class="card">
      <div class="title">${escapeHtml(ev.title)}</div>
      <div class="where">${ev.city ? ev.city+", " : ""}${ev.country || "—"} • ${ev.date}</div>
      ${ev.categories?.length ? `<div class="tags">${ev.categories.map(c=>`<span class="tag">${escapeHtml(c)}</span>`).join("")}</div>` : ""}
      <div><a class="btn" href="${ev.url}" target="_blank" rel="noreferrer">Details</a></div>
    </article>
  `).join("");
}

function toViewModel(e){
    const d = e.startAt ? new Date(e.startAt) : null;
    return {
        title: e.title, url: e.url, city: e.city, country: e.country,
        date: d ? d.toLocaleString(undefined,{ dateStyle:"medium", timeStyle:"short" }) : "TBA",
        categories: e.categories || []
    };
}

function setStatus(txt){ const s = $("#status"); if(!txt){ s.hidden=true; s.textContent=""; } else { s.hidden=false; s.textContent=txt; } }
function reset(){
    ["q","country-search","city","type","from","to"].forEach(id => { const el = document.getElementById(id); if(el){ el.value=""; } });
    $("#country").value = ""; state.country = ""; $("#city").disabled = true;
    state.tech=""; highlightTech(); filterCountry("");
}

/* ===== helpers ===== */
function writeQueryFromState(){
    const p = new URLSearchParams();
    const mapCountry = (c) => c === "ZZ-ONLINE" ? "online" : c;
    Object.entries({ q:state.q, country: mapCountry(state.country), city:state.city, type:state.type, tech:state.tech, from:state.from, to:state.to, page: state.page+1 })
        .forEach(([k,v]) => { if (v || v===0) p.set(k,v); });
    history.replaceState({}, "", `${location.pathname}?${p.toString()}`);
}
function readQueryToUI(){
    const p = new URLSearchParams(location.search);
    const get = (k) => p.get(k) || "";
    $("#q").value = get("q");
    const rawCountry = get("country");
    state.country = rawCountry === "online" ? "ZZ-ONLINE" : rawCountry;
    $("#country").value = state.country;
    $("#city").value = get("city");
    $("#type").value = get("type");
    $("#from").value = get("from");
    $("#to").value = get("to");
    state.tech = get("tech");
    const pageFromUrl = parseInt(get("page"),10);
    if (!isNaN(pageFromUrl) && pageFromUrl > 0) state.page = pageFromUrl - 1;
    highlightTech(); filterCountry($("#country-search").value || "");
}
function highlightTech(){
    document.querySelectorAll("#chips-tech .chip").forEach(b => {
        const active = b.dataset.tech === state.tech;
        b.setAttribute("aria-pressed", active ? "true" : "false");
        b.style.outline = active ? `2px solid var(--acc1)` : "none";
    });
}
function debounce(fn, ms=300){ let t; return (...a)=>{ clearTimeout(t); t=setTimeout(()=>fn(...a), ms); }; }
function escapeHtml(s){ return (s||"").replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m])); }
