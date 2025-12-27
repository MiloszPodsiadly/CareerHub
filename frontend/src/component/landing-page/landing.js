import './landing.css';

export function initLanding(opts = {}) {
    const jobsApi     = opts.jobsApi     || '/api/jobs';
    const eventsApi   = opts.eventsApi   || '/api/events';
    const LOCALE      = opts.locale      || 'pl-PL';
    const CITY_SAMPLE = Number(opts.citySample ?? 150);

    const root = document.getElementById('view-root');
    if (!root) return;

    if (root.__landingIO && typeof root.__landingIO.disconnect === 'function') {
        root.__landingIO.disconnect();
    }
    if (root.__landingTimers && Array.isArray(root.__landingTimers)) {
        root.__landingTimers.forEach(id => clearInterval(id));
    }
    root.__landingTimers = [];

    const io = new IntersectionObserver(
        entries => entries.forEach(e => {
            if (e.isIntersecting) {
                e.target.classList.add('visible');
                io.unobserve(e.target);
            }
        }),
        { threshold: 0.15 }
    );
    root.__landingIO = io;
    root.querySelectorAll('.reveal').forEach(el => io.observe(el));

    const jobsEl   = root.querySelector('[data-stat="jobs"] .stat__num');
    const citiesEl = root.querySelector('[data-stat="companies"] .stat__num');
    const eventsEl = root.querySelector('[data-stat="events"] .stat__num');

    const fmt = n => Number(n || 0).toLocaleString(LOCALE);

    if (jobsEl) jobsEl.textContent = '0';
    if (citiesEl) citiesEl.textContent = '0';
    if (eventsEl) eventsEl.textContent = '0';

    const dataCount = el => parseInt(el?.dataset?.count || '0', 10);

    function animateCount(el, target) {
        const finalVal = Math.max(0, Number(target) || 0);

        const durationMs = 900;
        const tickMs = 16;
        const steps = Math.max(18, Math.floor(durationMs / tickMs));
        const step = Math.max(1, Math.ceil(finalVal / steps));

        let cur = 0;

        if (el.__countTimer) clearInterval(el.__countTimer);

        el.classList.remove('is-done');
        el.classList.add('is-animating');

        const id = setInterval(() => {
            cur = Math.min(cur + step, finalVal);
            el.textContent = fmt(cur);

            if (cur === finalVal) {
                clearInterval(id);
                el.__countTimer = null;
                el.classList.remove('is-animating');
                el.classList.add('is-done');

                setTimeout(() => el.classList.remove('is-done'), 650);
            }
        }, tickMs);

        el.__countTimer = id;
        root.__landingTimers.push(id);
    }

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
        if (typeof json?.total === 'number') return json.total;
        if (typeof json?.page?.totalElements === 'number') return json.page.totalElements;
        if (typeof json?.meta?.total === 'number') return json.meta.total;
        if (Array.isArray(json?.content)) return json.content.length;
        if (Array.isArray(json?.items)) return json.items.length;
        if (Array.isArray(json)) return json.length;
        return 0;
    }

    async function fetchJobsTotal(apiUrl) {
        let u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '1');
        u.searchParams.set('pageSize', '1');
        try {
            const { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            const t = extractTotal(json);
            if (t > 0) return t;
        } catch {}

        u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '0');
        u.searchParams.set('size', '1');
        try {
            const { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            return extractTotal(json);
        } catch {
            return 0;
        }
    }


    async function estimateCitiesFromJobs(apiUrl, sampleSize = CITY_SAMPLE) {
        try {
            const u = new URL(apiUrl, location.origin);
            u.searchParams.set('page', '1');
            u.searchParams.set('pageSize', '1');
            u.searchParams.set('size', '1');

            const { json } = await fetchJson(u.toString());
            const direct =
                (typeof json?.citiesCount === 'number' && json.citiesCount) ||
                (typeof json?.meta?.citiesCount === 'number' && json.meta.citiesCount) ||
                (typeof json?.stats?.cities === 'number' && json.stats.cities);

            if (direct && direct > 0) return direct;
        } catch {}

        const u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '1');
        u.searchParams.set('pageSize', String(sampleSize));
        u.searchParams.set('size', String(sampleSize));
        u.searchParams.set('sort', 'date');

        try {
            const { json } = await fetchJson(u.toString());
            const list =
                Array.isArray(json?.content) ? json.content :
                    Array.isArray(json?.items)   ? json.items   :
                        Array.isArray(json)          ? json         : [];

            const cities = new Set(
                list
                    .map(x => x?.cityName || x?.city || '')
                    .map(String)
                    .map(s => s.trim())
                    .filter(Boolean)
            );

            return cities.size;
        } catch {
            return 0;
        }
    }

    async function fetchEventsTotal(apiUrl) {
        let u = new URL(apiUrl, location.origin);
        u.searchParams.set('from', new Date().toISOString());
        u.searchParams.set('page', '0');
        u.searchParams.set('size', '1');
        u.searchParams.set('sort', 'startAt,asc');

        try {
            const { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            const t = extractTotal(json);
            if (t > 0) return t;
        } catch {}

        u = new URL(apiUrl, location.origin);
        u.searchParams.set('page', '0');
        u.searchParams.set('size', '1');
        u.searchParams.set('sort', 'startAt,asc');

        try {
            const { json, xTotal } = await fetchJson(u.toString());
            if (Number.isFinite(xTotal)) return xTotal;
            return extractTotal(json);
        } catch {
            return 0;
        }
    }

    fetchJobsTotal(jobsApi)
        .then(n => { if (jobsEl) animateCount(jobsEl, n || dataCount(jobsEl)); })
        .catch(() => { if (jobsEl) animateCount(jobsEl, dataCount(jobsEl)); });

    estimateCitiesFromJobs(jobsApi, CITY_SAMPLE)
        .then(n => { if (citiesEl) animateCount(citiesEl, n || dataCount(citiesEl)); })
        .catch(() => { if (citiesEl) animateCount(citiesEl, dataCount(citiesEl)); });

    fetchEventsTotal(eventsApi)
        .then(n => { if (eventsEl) animateCount(eventsEl, n || dataCount(eventsEl)); })
        .catch(() => { if (eventsEl) animateCount(eventsEl, dataCount(eventsEl)); });
}
