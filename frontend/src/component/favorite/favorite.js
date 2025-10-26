// favorite.js
// ---------------------------------------------------------
// Ulubione: lista ofert pracy i eventów + akcje (unfav/clear)
// ---------------------------------------------------------

// Jeśli bundlujesz moduły – import działa (tak jak w jobs.js)
import { getAccess as sharedGetAccess } from '../../shared/api.js';

export function initFavorites() {
    /* ====== config & endpoints ====== */
    // Jeśli front i API lecą przez ten sam host (Nginx proxy /api -> backend), zostaw pusty string.
    // Jeśli rozdzielasz – ustaw np. const API_BASE = 'https://api.example.com';
    const API_BASE = '';

    const API = {
        mine  : (type, page = 0, size = 50) =>
            `${API_BASE}/api/favorites/mine?type=${encodeURIComponent(type)}&page=${page}&size=${size}`,
        del   : (type, id) =>
            `${API_BASE}/api/favorites/${encodeURIComponent(type)}/${encodeURIComponent(id)}`,
        toggle: (type, id) =>
            `${API_BASE}/api/favorites/${encodeURIComponent(type)}/${encodeURIComponent(id)}/toggle`,
        status: (type, id) =>
            `${API_BASE}/api/favorites/${encodeURIComponent(type)}/${encodeURIComponent(id)}/status`,
        jobById  : (id) => `${API_BASE}/api/jobs/${encodeURIComponent(id)}`,
        eventById: (id) => `${API_BASE}/api/events/${encodeURIComponent(id)}`
    };

    const TYPES = { JOB: 'JOB', EVENT: 'EVENT' };

    /* ====== auth helpers (solidny resolver tokenu) ====== */
    function resolveAccessToken() {
        try {
            if (typeof sharedGetAccess === 'function') {
                const t = sharedGetAccess();
                if (t) return t;
            }
        } catch {}

        try {
            if (typeof window !== 'undefined') {
                if (typeof window.getAccess === 'function') {
                    const t = window.getAccess();
                    if (t) return t;
                }
                if (window.appAuth?.getAccess) {
                    const t = window.appAuth.getAccess();
                    if (t) return t;
                }
                const ls = window.localStorage?.getItem?.('accessToken');
                if (ls) return ls;
            }
        } catch {}

        return '';
    }

    const authHeader = () => {
        const t = resolveAccessToken();
        return t ? { Authorization: `Bearer ${t}` } : {};
    };
    const isAuthed = () => !!resolveAccessToken();

    /* ====== dom helpers ====== */
    const $ = (sel, root = document) => root.querySelector(sel);
    const byId = (id) => document.getElementById(id);

    const panels = { jobs: byId('fav-jobs'), events: byId('fav-events') };
    const tabs   = Array.from(document.querySelectorAll('.fav__tab'));

    /* ====== state ====== */
    const state = {
        jobs  : { page: 0, size: 50, total: 0, items: [], loading: false },
        events: { page: 0, size: 50, total: 0, items: [], loading: false }
    };

    /* ====== tabs ====== */
    const switchTo = (key) => {
        tabs.forEach(t => {
            const active = t.dataset.tab === key;
            t.classList.toggle('is-active', active);
            t.setAttribute('aria-selected', String(active));
            t.tabIndex = active ? 0 : -1;
        });
        Object.entries(panels).forEach(([k, el]) => {
            const on = (k === key);
            if (!el) return;
            el.classList.toggle('is-active', on);
            if (on) el.removeAttribute('hidden');
            else    el.setAttribute('hidden','');
        });
        const u = new URL(location.href); u.hash = key; history.replaceState({},'',u);
    };

    const start = (location.hash || '').replace('#','');
    switchTo(start === 'events' ? 'events' : 'jobs');
    tabs.forEach(btn => btn.addEventListener('click', () => switchTo(btn.dataset.tab)));

    /* ====== UI helpers ====== */
    function setPanelStatus(panelEl, text) {
        if (!panelEl) return;
        let s = panelEl.querySelector('.fav__status');
        if (!s) {
            s = document.createElement('div');
            s.className = 'fav__status muted';
            panelEl.prepend(s);
        }
        s.textContent = text || '';
        s.hidden = !text;
    }

    const fmtMoney = (s) => {
        if (!s) return null;
        const min = s.min != null ? s.min.toLocaleString('en-GB') : '';
        const max = s.max != null ? s.max.toLocaleString('en-GB') : '';
        const cur = s.currency || 'PLN';
        const per = (s.period || 'MONTH').toLowerCase();
        return `${min}${max ? ' – ' + max : ''} ${cur}/${per}`.trim();
    };

    const fmtDate = (iso) =>
        iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : 'TBA';

    const escapeHtml = (s) =>
        String(s ?? '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));

    /* ====== mapping ====== */
    function mapJobFavorite(item) {
        const j = item?.job || item?.payload || item?.target || item;
        const id = j?.id ?? j?._id ?? j?.jobId ?? item?.targetId ?? item?.id;
        const title   = j?.title ?? j?.jobTitle ?? '';
        const company = j?.companyName ?? j?.company ?? '';
        const city    = j?.cityName ?? j?.city ?? '';
        const url     = j?.url ?? j?.applyUrl ?? (id ? `/jobs/${id}` : '#');

        const salary = (() => {
            const min = j?.salaryMin ?? j?.salary?.min ?? null;
            const max = j?.salaryMax ?? j?.salary?.max ?? null;
            const currency = j?.currency ?? j?.salary?.currency ?? 'PLN';
            const period = j?.salary?.period ?? 'MONTH';
            return (min == null && max == null) ? null : ({ min, max, currency, period });
        })();

        const keywords = Array.isArray(j?.techTags) ? j.techTags
            : Array.isArray(j?.techStack) ? j.techStack.map(s => s?.name).filter(Boolean)
                : Array.isArray(j?.keywords)  ? j.keywords
                    : [];

        return { id, title, company, city, url, salary, tags: keywords.slice(0, 6) };
    }

    function mapEventFavorite(item) {
        const e = item?.event || item?.payload || item?.target || item;
        const id   = e?.id ?? e?._id ?? item?.targetId ?? item?.id;
        const title = e?.title ?? '';
        const url   = e?.url ?? (id ? `/events/${id}` : '#');
        const city  = e?.city ?? '';
        const country = e?.country ?? '';
        const startAt = e?.startAt ?? e?.start ?? null;
        const categories = e?.categories || e?.tags || [];
        return { id, title, url, city, country, startAt, tags: categories.slice(0, 6) };
    }

    /* ====== public API (dla list jobs/events) ====== */
    async function statusFavorite(type, id) {
        const r = await fetch(API.status(type, id), {
            credentials: 'include',
            headers: { Accept: 'application/json', ...authHeader() }
        });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json(); // { favorited, count }
    }

    async function toggleFavorite(type, id) {
        if (!isAuthed()) throw new Error('Please log in');
        const r = await fetch(API.toggle(type, id), {
            method: 'POST',
            credentials: 'include',
            headers: { Accept: 'application/json', ...authHeader() }
        });
        if (r.status === 401 || r.status === 403) throw new Error('Please log in');
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json(); // { favorited, count }
    }

    // Wystaw do globala:
    window.favorites = Object.assign(window.favorites || {}, { statusFavorite, toggleFavorite, TYPES });

    /* ====== fetchers ====== */
    async function fetchMine(type, page = 0, size = 50) {
        if (!isAuthed()) {
            const err = new Error('Please log in'); err.code = 401; throw err;
        }
        const r = await fetch(API.mine(type, page, size), {
            credentials: 'include',
            headers: { Accept: 'application/json', ...authHeader() }
        });
        if (r.status === 401 || r.status === 403) {
            const err = new Error('Please log in'); err.code = r.status; throw err;
        }
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
    }

    async function fetchJobById(id) {
        const r = await fetch(API.jobById(id), { headers: { Accept: 'application/json', ...authHeader() } });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const x = await r.json();
        return mapJobFavorite({ job: x });
    }

    async function fetchEventById(id) {
        const r = await fetch(API.eventById(id), { headers: { Accept: 'application/json', ...authHeader() } });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const x = await r.json();
        return mapEventFavorite({ event: x });
    }

    /* ====== renderers ====== */
    function renderJobs(items) {
        const panel = panels.jobs; if (!panel) return;
        const grid  = panel.querySelector('.fav__grid');
        const empty = panel.querySelector('[data-empty="jobs"]');
        if (!grid) return;

        grid.innerHTML = '';
        if (!items.length) { empty?.classList.remove('hidden'); return; }
        empty?.classList.add('hidden');

        for (const j of items) {
            const card = document.createElement('article');
            card.className = 'fav-card job';
            card.dataset.id = String(j.id ?? '');
            card.innerHTML = `
        <div class="fav-card__head">
          <h3 class="fav-card__title">${escapeHtml(j.title || '')}</h3>
          <button class="icon-btn" title="Remove from favorites" aria-label="Remove from favorites" data-unfav>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6.5-4.35-9.33-7.18A5.91 5.91 0 1 1 12 5.66a5.91 5.91 0 1 1 9.33 8.16C18.5 16.65 12 21 12 21z"/></svg>
          </button>
        </div>
        <p class="muted">${escapeHtml(j.company || '—')}${j.city ? ' • ' + escapeHtml(j.city) : ''}</p>
        <div class="badges">${(j.tags || []).map(t => `<span class="badge">${escapeHtml(t)}</span>`).join('')}</div>
        <div class="fav-card__meta">
          ${j.salary ? `<span class="money">${escapeHtml(fmtMoney(j.salary))}</span>` : '<span></span>'}
          <a class="link" href="${j.url}" target="_blank" rel="noopener">View job</a>
        </div>`;
            grid.appendChild(card);
        }
    }

    function renderEvents(items) {
        const panel = panels.events; if (!panel) return;
        const grid  = panel.querySelector('.fav__grid');
        const empty = panel.querySelector('[data-empty="events"]');
        if (!grid) return;

        grid.innerHTML = '';
        if (!items.length) { empty?.classList.remove('hidden'); return; }
        empty?.classList.add('hidden');

        for (const e of items) {
            const when  = fmtDate(e.startAt);
            const place = `${e.city ? escapeHtml(e.city) + ' • ' : ''}${escapeHtml(e.country || '—')} • ${escapeHtml(when)}`;

            const card = document.createElement('article');
            card.className = 'fav-card event';
            card.dataset.id = String(e.id ?? '');
            card.innerHTML = `
        <div class="fav-card__head">
          <h3 class="fav-card__title">${escapeHtml(e.title || '')}</h3>
          <button class="icon-btn" title="Remove from favorites" aria-label="Remove from favorites" data-unfav>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-6.5-4.35-9.33-7.18A5.91 5.91 0 1 1 12 5.66a5.91 5.91 0 1 1 9.33 8.16C18.5 16.65 12 21 12 21z"/></svg>
          </button>
        </div>
        <p class="muted">${place}</p>
        <div class="badges">${(e.tags || []).map(t => `<span class="badge">${escapeHtml(t)}</span>`).join('')}</div>
        <div class="fav-card__meta">
          <span class="pill">${e.city ? 'On-site' : 'Online'}</span>
          <a class="link" href="${e.url}" target="_blank" rel="noopener">View event</a>
        </div>`;
            grid.appendChild(card);
        }
    }

    /* ====== loaders ====== */
    async function loadJobs() {
        const panel = panels.jobs;
        try{
            state.jobs.loading = true;
            setPanelStatus(panel, 'Loading…');

            const page = await fetchMine(TYPES.JOB, state.jobs.page, state.jobs.size);
            const rows  = page?.content || page?.items || (Array.isArray(page) ? page : []);
            const total = page?.totalElements ?? page?.total ?? rows.length;
            state.jobs.total = total;

            const mapped = await Promise.all((rows || []).map(async (it) => {
                const m = mapJobFavorite(it);
                if (!m.id || !m.title) {
                    try { return await fetchJobById(it?.targetId ?? it?.id); }
                    catch { return null; }
                }
                return m;
            }));
            const items = (mapped || []).filter(Boolean);
            state.jobs.items = items;

            renderJobs(items);
            setPanelStatus(panel, items.length ? '' : 'You don’t have any saved jobs yet.');
        } catch (e) {
            const msg = (e && (e.code === 401 || e.code === 403))
                ? 'Please log in to use favorites.'
                : `Load error: ${e.message || e}`;
            setPanelStatus(panel, msg);
        } finally {
            state.jobs.loading = false;
        }
    }

    async function loadEvents() {
        const panel = panels.events;
        try{
            state.events.loading = true;
            setPanelStatus(panel, 'Loading…');

            const page = await fetchMine(TYPES.EVENT, state.events.page, state.events.size);
            const rows  = page?.content || page?.items || (Array.isArray(page) ? page : []);
            const total = page?.totalElements ?? page?.total ?? rows.length;
            state.events.total = total;

            const mapped = await Promise.all((rows || []).map(async (it) => {
                const m = mapEventFavorite(it);
                if (!m.id || !m.title) {
                    try { return await fetchEventById(it?.targetId ?? it?.id); }
                    catch { return null; }
                }
                return m;
            }));
            const items = (mapped || []).filter(Boolean);
            state.events.items = items;

            renderEvents(items);
            setPanelStatus(panel, items.length ? '' : 'You don’t have any saved events yet.');
        } catch (e) {
            const msg = (e && (e.code === 401 || e.code === 403))
                ? 'Please log in to use favorites.'
                : `Load error: ${e.message || e}`;
            setPanelStatus(panel, msg);
        } finally {
            state.events.loading = false;
        }
    }

    /* ====== actions ====== */
    async function handleUnfavClick(e) {
        const btn = e.target.closest?.('[data-unfav]');
        if (!btn) return;

        const panel = btn.closest('.fav__panel');
        const type  = panel?.id === 'fav-jobs' ? TYPES.JOB : TYPES.EVENT;
        const card  = btn.closest('.fav-card');
        const id    = card?.dataset?.id;
        if (!id) return;

        if (!isAuthed()) {
            alert('Please log in to use favorites.');
            return;
        }

        // optimistic remove
        animateRemove(card);

        try{
            const r = await fetch(API.del(type, id), {
                method: 'DELETE',
                credentials: 'include',
                headers: { ...authHeader() }
            });
            if (r.status === 401 || r.status === 403) throw new Error('Please log in');
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
        } catch (err) {
            await (type === TYPES.JOB ? loadJobs() : loadEvents());
            alert(err.message || 'Could not remove from favorites');
        }
    }

    function animateRemove(card) {
        card.style.opacity = '0';
        card.style.transform = 'translateY(4px)';
        setTimeout(() => {
            const panel = card.closest('.fav__panel');
            card.remove();
            if (!panel.querySelector('.fav-card')) {
                const type = panel.id === 'fav-jobs' ? 'jobs' : 'events';
                panel.querySelector(`[data-empty="${type}"]`)?.classList.remove('hidden');
            }
        }, 150);
    }

    async function handleClear(typeKey) {
        const panel = typeKey === 'jobs' ? panels.jobs : panels.events;
        const type  = typeKey === 'jobs' ? TYPES.JOB : TYPES.EVENT;
        const cards = Array.from(panel.querySelectorAll('.fav-card'));
        if (!cards.length) return;

        if (!isAuthed()) {
            alert('Please log in to use favorites.');
            return;
        }

        // optimistic UI
        cards.forEach(c => animateRemove(c));

        try{
            await Promise.all(cards.map(c => {
                const id = c.dataset.id;
                return fetch(API.del(type, id), {
                    method: 'DELETE',
                    credentials: 'include',
                    headers: { ...authHeader() }
                });
            }));
        } catch {
            await (type === TYPES.JOB ? loadJobs() : loadEvents());
        }
    }

    /* ====== wire up ====== */
    document.addEventListener('click', handleUnfavClick);
    document.querySelectorAll('[data-clear]').forEach(btn => {
        btn.addEventListener('click', () => handleClear(btn.getAttribute('data-clear')));
    });

    // initial loads
    loadJobs();
    loadEvents();
}
