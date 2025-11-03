import './landing.css';


export async function initLanding(opts = {}) {
    const jobsApi     = opts.jobsApi     || '/api/jobs';
    const eventsApi   = opts.eventsApi   || '/api/events';
    const LOCALE      = opts.locale      || 'pl-PL';
    const CITY_SAMPLE = opts.citySample  || 500;

    const root = document.getElementById('view-root');

    const io = new IntersectionObserver(
        es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add('visible'); io.unobserve(e.target); } }),
        { threshold: 0.15 }
    );
    root.querySelectorAll('.reveal').forEach(el => io.observe(el));

    const fmt = n => Number(n || 0).toLocaleString(LOCALE);
    function animateCount(el, target) {
        const finalVal = Math.max(0, Number(target) || 0);
        const step = Math.max(1, Math.ceil(finalVal / 50));
        let cur = 0;
        const id = setInterval(() => {
            cur = Math.min(cur + step, finalVal);
            el.textContent = fmt(cur);
            if (cur === finalVal) clearInterval(id);
        }, 30);
    }

    const jobsEl   = root.querySelector('[data-stat="jobs"] .stat__num');
    const citiesEl = root.querySelector('[data-stat="companies"] .stat__num');
    const eventsEl = root.querySelector('[data-stat="events"] .stat__num');

    if (jobsEl)   jobsEl.textContent   = '0';
    if (citiesEl) citiesEl.textContent = '0';
    if (eventsEl) eventsEl.textContent = '0';

    async function fetchJson(url) {
        const r = await fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store' });
        if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
        const xTotal = r.headers.get('X-Total-Count');
        let json = null;
        try { json = await r.json(); } catch {}
        return { json, xTotal: xTotal ? parseInt(xTotal, 10) : null };
    }

    function extractTotal(json) {
        if (typeof json?.totalElements === 'number') return json.totalElements;
        if (typeof json?.total === 'number')         return json.total;
        if (typeof json?.page?.totalElements === 'number') return json.page.totalElements;
        if (Array.isArray(json?.content)) return json.content.length;
        if (Array.isArray(json?.items))   return json.items.length;
        if (Array.isArray(json))          return json.length;
        return 0;
    }

    async function fetchJobsTotal(apiUrl) {
        // A) 1-based
        let u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '1');
        u.searchParams.set('pageSize', '1');
        try {
            let { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            const t = extractTotal(json);
            if (t > 0) return t;
        } catch {}

        u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '0');
        u.searchParams.set('size', '1');
        try {
            let { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            return extractTotal(json);
        } catch { return 0; }
    }

    async function estimateCitiesFromJobs(apiUrl, sampleSize = CITY_SAMPLE) {
        const u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '1');
        u.searchParams.set('pageSize', String(sampleSize));
        u.searchParams.set('size',     String(sampleSize));
        u.searchParams.set('sort', 'date');

        try {
            const { json } = await fetchJson(u.toString());
            const list =
                Array.isArray(json?.content) ? json.content :
                    Array.isArray(json?.items)   ? json.items   :
                        Array.isArray(json)          ? json         : [];

            const cities = new Set(
                list.map(x => x?.cityName || x?.city || '')
                    .map(String).map(s => s.trim()).filter(Boolean)
            );
            return cities.size;
        } catch { return 0; }
    }

    async function fetchEventsTotal(apiUrl) {
        let u = new URL(apiUrl, location.origin);
        u.searchParams.set('from', new Date().toISOString());
        u.searchParams.set('page', '0');
        u.searchParams.set('size', '1');
        u.searchParams.set('sort', 'startAt,asc');
        try {
            let { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            const t = extractTotal(json);
            if (t > 0) return t;
        } catch {}

        u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '0');
        u.searchParams.set('size', '1');
        u.searchParams.set('sort', 'startAt,asc');
        try {
            let { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            return extractTotal(json);
        } catch { return 0; }
    }

    const [jobsTotal, citiesTotal, eventsTotal] = await Promise.all([
        fetchJobsTotal(jobsApi).catch(() => 0),
        estimateCitiesFromJobs(jobsApi, CITY_SAMPLE).catch(() => 0),
        fetchEventsTotal(eventsApi).catch(() => 0),
    ]);

    const dataCount = el => parseInt(el?.dataset.count || '0', 10);

    if (jobsEl)   animateCount(jobsEl,   jobsTotal   || dataCount(jobsEl));
    if (citiesEl) animateCount(citiesEl, citiesTotal || dataCount(citiesEl));
    if (eventsEl) animateCount(eventsEl, eventsTotal || dataCount(eventsEl));
}
