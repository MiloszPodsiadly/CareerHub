import landingHtml from './landing.html?raw';
import './landing.css';
import { setView } from '../../shared/mount.js';

export async function mountLanding(opts = {}) {
    const jobsApi   = opts.jobsApi   || '/api/jobs';
    const eventsApi = opts.eventsApi || '/api/events';
    const LOCALE    = opts.locale    || 'pl-PL';

    setView(landingHtml);
    const mount = document.getElementById('view-root');

    const io = new IntersectionObserver((entries) => {
        entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('visible'); io.unobserve(e.target); } });
    }, { threshold: 0.15 });
    mount.querySelectorAll('.reveal').forEach(el => io.observe(el));

    const fmt = (n) => Number(n || 0).toLocaleString(LOCALE);
    const animateCount = (el, target) => {
        const finalVal = Math.max(0, Number(target) || 0);
        const step = Math.max(1, Math.ceil(finalVal / 50));
        let cur = 0;
        const id = setInterval(() => {
            cur = Math.min(cur + step, finalVal);
            el.textContent = fmt(cur);
            if (cur === finalVal) clearInterval(id);
        }, 30);
    };

    mount.querySelectorAll('.stat__num').forEach(el => {
        animateCount(el, parseInt(el.dataset.count || '0', 10));
    });

    const jobsEl      = mount.querySelector('[data-stat="jobs"] .stat__num');
    const companiesEl = mount.querySelector('[data-stat="companies"] .stat__num');
    const eventsEl    = mount.querySelector('[data-stat="events"] .stat__num');

    if (jobsEl) {
        try { const total = await fetchJobsTotal(jobsApi); if (Number.isFinite(total)) animateCount(jobsEl, total); } catch {}
    }
    if (companiesEl) {
        try {
            const totalCompanies =
                (await fetchCompaniesTotal({ companiesApi: '/api/companies', jobsApi })) ??
                (await estimateCompaniesFromJobs(jobsApi));
            if (Number.isFinite(totalCompanies)) animateCount(companiesEl, totalCompanies);
        } catch {}
    }
    if (eventsEl) {
        try {
            const { from, to } = monthBounds(new Date());
            const total = await fetchEventsTotal(eventsApi, { from, to });
            if (Number.isFinite(total)) animateCount(eventsEl, total);
        } catch {}
    }

    async function fetchJobsTotal(apiUrl) {
        const url = new URL(apiUrl, location.origin);
        url.searchParams.set('page', '1');      // 1-based
        url.searchParams.set('pageSize', '1');  // tylko total
        const res = await fetch(url.toString(), { headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error('Jobs API error: ' + res.status);

        const headerTotal = res.headers.get('X-Total-Count');
        if (headerTotal) return parseInt(headerTotal, 10);

        const json = await res.json();
        if (Array.isArray(json?.content)) return typeof json.totalElements === 'number' ? json.totalElements : json.content.length;
        if (Array.isArray(json?.items))   return typeof json.total === 'number' ? json.total : json.items.length;
        if (Array.isArray(json))          return json.length;
        return 0;
    }

    async function fetchEventsTotal(apiUrl, { from, to, type } = {}) {
        const url = new URL(apiUrl, location.origin);
        if (from) url.searchParams.set('from', `${from}T00:00:00Z`);
        if (to)   url.searchParams.set('to',   `${to}T23:59:59Z`);
        if (type) url.searchParams.set('type', String(type).toUpperCase());
        url.searchParams.set('page', '0');
        url.searchParams.set('size', '1');
        url.searchParams.set('sort', 'startAt,asc');

        const res = await fetch(url.toString(), { headers: { Accept: 'application/json' }, mode: 'cors' });
        if (!res.ok) throw new Error('Events API error: ' + res.status);

        const json = await res.json();
        if (Array.isArray(json?.content)) return typeof json.totalElements === 'number' ? json.totalElements : json.content.length;
        if (Array.isArray(json?.items))   return typeof json.total === 'number' ? json.total : json.items.length;
        if (Array.isArray(json))          return json.length;
        return 0;
    }

    async function fetchCompaniesTotal({ companiesApi = '/api/companies', jobsApi }) {
        try {
            const url = new URL(companiesApi, location.origin);
            url.searchParams.set('page', '0');
            url.searchParams.set('size', '1');
            const res = await fetch(url.toString(), { headers: { Accept: 'application/json' } });
            if (!res.ok) throw new Error('Companies API error');
            const json = await res.json();
            if (Array.isArray(json?.content)) return json.totalElements ?? json.content.length;
            if (Array.isArray(json?.items))   return json.total ?? json.items.length;
            if (Array.isArray(json))          return json.length;
            return undefined;
        } catch { return undefined; }
    }

    async function estimateCompaniesFromJobs(apiUrl) {
        const url = new URL(apiUrl, location.origin);
        url.searchParams.set('page', '1');
        url.searchParams.set('pageSize', '500');
        url.searchParams.set('sort', 'company,asc');

        const res = await fetch(url.toString(), { headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error('Jobs list error: ' + res.status);

        const json = await res.json();
        const list = Array.isArray(json?.content) ? json.content
            : Array.isArray(json?.items)   ? json.items
                : Array.isArray(json)          ? json
                    : [];
        const names = new Set(
            list.map(x => x.companyName || x.company || '')
                .map(String).map(s => s.trim()).filter(Boolean)
        );
        return names.size;
    }

    function monthBounds(d) {
        const y = d.getFullYear(), m = d.getMonth();
        const from = new Date(y, m, 1).toISOString().slice(0, 10);
        const to   = new Date(y, m + 1, 0).toISOString().slice(0, 10);
        return { from, to };
    }
}
