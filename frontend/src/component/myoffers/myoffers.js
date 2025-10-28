import { getAccess as sharedGetAccess } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initMyOffers() {
    const listRoot = document.getElementById('myoffers-list');
    const tpl = document.getElementById('myoffer-item');
    const dlg = document.getElementById('confirm-del');

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

    const authHeaders = () => {
        const t = resolveAccessToken();
        return t ? { Authorization: `Bearer ${t}` } : {};
    };
    const isAuthed = () => !!resolveAccessToken();
    const show = (el, on) => { if (!el) return; el.hidden = !on; if (!on) el.textContent = ''; };

    async function authMe() {
        try {
            const r = await fetch('/api/auth/me', {
                headers: { ...authHeaders(), Accept: 'application/json' },
                credentials: 'include'
            });
            if (r.status === 401) return false;
            return r.ok;
        } catch {
            return false;
        }
    }

    async function load() {
        listRoot.innerHTML = '<p class="muted">Loading…</p>';
        try {
            if (!isAuthed()) { await navigate('/auth/login'); return; }

            const ok = await authMe();
            if (!ok) { await navigate('/auth/login'); return; }

            const res = await fetch('/api/jobs/mine', {
                headers: { Accept: 'application/json', ...authHeaders() },
                credentials: 'include'
            });

            if (res.status === 401) { await navigate('/auth/login'); return; }
            const raw = await res.text();
            if (!res.ok) throw new Error(`HTTP ${res.status} - ${raw || 'no-body'}`);

            const items = raw ? JSON.parse(raw) : [];
            render(Array.isArray(items) ? items : []);
        } catch (e) {
            console.error('[myoffers] load error:', e);
            listRoot.innerHTML = '<p class="muted">Could not load your offers.</p>';
        }
    }

    function render(items) {
        listRoot.innerHTML = '';
        if (!items.length) {
            listRoot.innerHTML = `
        <div class="empty">
          You haven’t published anything yet.
          <a class="btn" href="/post-job">Post a job</a>
        </div>`;
            return;
        }

        items.forEach(o => {
            const el = tpl.content.firstElementChild.cloneNode(true);
            el.dataset.id = o.id;

            el.querySelector('.offer__title').textContent = o.title || '(no title)';

            const c   = el.querySelector('.pill--company');  c.textContent   = o.companyName || ''; show(c, !!o.companyName);
            const city= el.querySelector('.pill--city');     city.textContent= o.cityName    || ''; show(city, !!o.cityName);
            const lvl = el.querySelector('.pill--level');    lvl.textContent = o.level       || ''; show(lvl, !!o.level);
            const ct  = el.querySelector('.pill--contract'); ct.textContent  = o.contract    || ''; show(ct, !!o.contract);
            const dt  = el.querySelector('.pill--date');     dt.textContent  = (o.publishedAt || '').slice(0,10); show(dt, !!o.publishedAt);

            const previewEl = el.querySelector('.js-preview') || el.querySelector('.js-view');
            if (previewEl) {
                const url = `/jobs/${o.id}`;
                previewEl.setAttribute('href', url);
                previewEl.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    window.location.assign(url);
                });
            }

            el.querySelector('.js-delete')?.addEventListener('click', () => confirmDelete(o.id));

            listRoot.appendChild(el);
        });
    }

    async function confirmDelete(id) {
        const pwdInput = dlg.querySelector('.dlg__pwd');
        const msg = dlg.querySelector('.dlg__msg');
        pwdInput.value = '';
        msg.textContent = '';

        const onKey = (e) => { if (e.key === 'Escape') dlg.close('cancel'); };
        dlg.addEventListener('keydown', onKey, { once: true });

        if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open','');

        const proceed = await new Promise(resolve => {
            function onClose() { dlg.removeEventListener('close', onClose); resolve(dlg.returnValue === 'ok'); }
            dlg.addEventListener('close', onClose, { once:true });
        });
        if (!proceed) return;

        try {
            if (!isAuthed()) { await navigate('/auth/login'); return; }

            const res = await fetch(`/api/jobs/${id}`, {
                method: 'DELETE',
                headers: { 'Content-Type':'application/json', Accept:'text/plain, application/json', ...authHeaders() },
                credentials: 'include',
                body: JSON.stringify({ password: pwdInput.value })
            });
            const raw = await res.text();

            if (res.status === 401) { await navigate('/auth/login'); return; }
            if (!res.ok) {
                msg.textContent = raw || 'Delete failed.';
                if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open','');
                return;
            }
            await load();
        } catch (e) {
            console.error('[myoffers] delete error:', e);
            msg.textContent = 'Network error. Try again.';
            if (typeof dlg.showModal === 'function') dlg.showModal(); else dlg.setAttribute('open','');
        }
    }

    load();
}
