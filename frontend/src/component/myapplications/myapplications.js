import { getAccess as sharedGetAccess } from '../../shared/api.js';
import { navigate } from '../../router.js';

function resolveAccessToken(){
    try { if (typeof sharedGetAccess === 'function') { const t = sharedGetAccess(); if (t) return t; } } catch {}
    try {
        const ss = window.sessionStorage?.getItem?.('accessToken');
        if (ss) { try { return JSON.parse(ss); } catch {}
        }
        if (typeof window.getAccess === 'function') { const t = window.getAccess(); if (t) return t; }
        if (window.appAuth?.getAccess) { const t = window.appAuth.getAccess(); if (t) return t; }
        const ls = window.localStorage?.getItem?.('accessToken'); if (ls) return ls;
    } catch {}
    return '';
}
const isAuthed    = () => !!resolveAccessToken();
const authHeaders = () => { const t = resolveAccessToken(); return t ? { Authorization:`Bearer ${t}` } : {}; };

const by = (k, dir='asc') => (a,b)=>{
    const av = (a[k]??'').toString().toLowerCase();
    const bv = (b[k]??'').toString().toLowerCase();
    if (av===bv) return 0;
    return dir==='asc' ? (av>bv?1:-1) : (av>bv?-1:1);
};
const byDate = (k, dir='desc') => (a,b)=>{
    const av = a[k] ? new Date(a[k]).getTime() : 0;
    const bv = b[k] ? new Date(b[k]).getTime() : 0;
    return dir==='asc' ? (av-bv) : (bv-av);
};

async function downloadCv(appId, explicitUrl){
    try{
        const url = explicitUrl || `/api/applications/${encodeURIComponent(appId)}/cv`;
        const res = await fetch(url, { headers: { Accept:'application/octet-stream', ...authHeaders() }, credentials:'include' });
        if (res.status === 401) { alert('Zaloguj się, aby pobrać CV.'); return; }
        if (res.status === 403) { alert('Nie masz uprawnień do tego CV.'); return; }
        if (res.status === 404) { alert('Kandydat nie dołączył CV.'); return; }
        if (!res.ok)          { alert('Nie udało się pobrać CV.'); return; }

        const cd = res.headers.get('Content-Disposition') || '';
        let filename = 'cv';
        const mStar = cd.match(/filename\*\s*=\s*[^']*'[^']*'([^;]+)/i);
        const mNorm = cd.match(/filename\s*=\s*("?)([^";]+)\1/i);
        if (mStar) filename = decodeURIComponent(mStar[1]); else if (mNorm) filename = mNorm[2];

        const blob = await res.blob();
        const href = URL.createObjectURL(blob);
        const a = document.createElement('a'); a.href = href; a.download = filename || 'cv';
        document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(href);
    }catch(e){ console.error('downloadCv error', e); alert('Błąd sieci przy pobieraniu CV.'); }
}

function wireSpaLinks(container){
    container.querySelectorAll('a[data-link]').forEach(a=>{
        a.addEventListener('click', (e)=>{
            e.preventDefault(); e.stopPropagation();
            const u = new URL(a.href, location.origin);
            navigate(u.pathname + (u.search || ''));
        });
    });
}

function groupByOffer(items){
    const map = new Map();
    for (const it of items){
        const removed = it.offerId == null;
        const key = removed ? `app-${it.id}` : String(it.offerId);

        const entry = map.get(key) || {
            offerId: removed ? null : it.offerId,
            title: it.offerTitle || (removed ? 'Offer removed' : '—'),
            companyName: it.companyName || (removed ? '' : '—'),
            cityName: it.cityName || (removed ? '' : '—'),
            items: []
        };
        entry.items.push(it);
        map.set(key, entry);
    }
    return Array.from(map.values());
}

function paginateGroups(groups, page, pageSize){
    const out = [];
    let acc = 0;
    let skipped = 0;
    for (const g of groups){
        const len = g.items.length;
        if (skipped + len <= (page-1)*pageSize) { skipped += len; continue; }
        if (acc >= pageSize) break;
        out.push(g);
        acc += len;
    }
    const totalApps = groups.reduce((s,g)=>s+g.items.length, 0);
    const totalPages = Math.max(1, Math.ceil(totalApps / pageSize));
    return { groups: out, totalApps, totalPages };
}

export function initMyApplications(root){
    const $ = (s) => root.querySelector(s);

    const tabs      = root.querySelectorAll('.js-tab');
    const search    = $('#apps-search');
    const sortSel   = $('#apps-sort');
    const expand    = $('#apps-expand');
    const collapse  = $('#apps-collapse');

    const ulMine    = $('#apps-mine');
    const ulOwned   = $('#apps-owned');
    const pagerMine = $('#pager-mine');
    const pagerOwned= $('#pager-owned');
    const pMine     = $('#panel-mine');
    const pOwned    = $('#panel-owned');

    let dataMine = [];
    let dataOwned = [];
    let pageMine = 1;
    let pageOwned = 1;
    const pageSize = 20;

    tabs.forEach(btn=>{
        btn.addEventListener('click', async ()=>{
            tabs.forEach(b=>b.setAttribute('aria-selected', b === btn ? 'true' : 'false'));
            const t = btn.dataset.tab;
            pMine.hidden  = t !== 'mine';
            pOwned.hidden = t !== 'owned';
            if (t === 'mine')  await ensureMine();
            if (t === 'owned') await ensureOwned();
        });
    });

    search?.addEventListener('input', ()=>{
        pageMine = 1; pageOwned = 1;
        renderMine(); renderOwned();
    });
    sortSel?.addEventListener('change', ()=>{
        pageMine = 1; pageOwned = 1;
        renderMine(); renderOwned();
    });
    expand?.addEventListener('click', ()=> toggleAll(true));
    collapse?.addEventListener('click', ()=> toggleAll(false));

    function toggleAll(open){
        root.querySelectorAll('.group').forEach(g=> g.setAttribute('aria-expanded', open?'true':'false'));
    }

    async function ensureAuth(){
        if (!isAuthed()) { await navigate('/auth/login'); return false; }
        try{
            const r = await fetch('/api/auth/me', { headers:{...authHeaders(), Accept:'application/json'}, credentials:'include' });
            if (r.status === 401) { await navigate('/auth/login'); return false; }
            return r.ok;
        }catch{ return false; }
    }

    async function ensureMine(){
        if (!(await ensureAuth())) return;
        if (!dataMine.length){
            const r = await fetch('/api/applications/mine', { headers:{ Accept:'application/json', ...authHeaders() }, credentials:'include' });
            dataMine = r.ok ? await r.json() : [];
        }
        renderMine();
    }
    async function ensureOwned(){
        if (!(await ensureAuth())) return;
        if (!dataOwned.length){
            const r = await fetch('/api/applications/owned', { headers:{ Accept:'application/json', ...authHeaders() }, credentials:'include' });
            dataOwned = r.ok ? await r.json() : [];
        }
        renderOwned();
    }

    function filterAndSort(items){
        const q = (search?.value || '').trim().toLowerCase();
        let out = items.slice();
        if (q){
            out = out.filter(a =>
                (a.offerTitle||'').toLowerCase().includes(q) ||
                (a.companyName||'').toLowerCase().includes(q) ||
                (a.cityName||'').toLowerCase().includes(q)
            );
        }
        switch (sortSel?.value){
            case 'created_asc':  out.sort(byDate('createdAt','asc')); break;
            case 'title_asc':    out.sort(by('offerTitle','asc'));    break;
            case 'company_asc':  out.sort(by('companyName','asc'));   break;
            case 'city_asc':     out.sort(by('cityName','asc'));      break;
            default:             out.sort(byDate('createdAt','desc'));break;
        }
        return out;
    }

    function renderGroups(ul, flatItems, pageState, pagerEl){
        if (!ul || !pagerEl) return { totalPages: 1 };

        ul.innerHTML = '';
        pagerEl.innerHTML = '';

        if (!flatItems.length){
            ul.innerHTML = `<li class="empty">No items yet.</li>`;
            pagerEl.hidden = true;
            return { totalPages: 1 };
        }

        const groups = groupByOffer(flatItems);
        const { groups:pageGroups, totalApps, totalPages } = paginateGroups(groups, pageState, pageSize);

        ul.innerHTML = pageGroups.map(g => {
            const removedGroup = !g.offerId;
            const headMeta = removedGroup
                ? 'Offer removed'
                : `${g.companyName || '—'} • ${g.cityName || '—'}`;

            const itemsHtml = g.items.map(a=>{
                const removed = a.offerId == null;
                const title   = a.offerTitle || (removed ? 'Offer removed' : '—');
                const applied = a.createdAt ? new Date(a.createdAt).toLocaleString('en-GB') : '—';

                const titleHtml = removed
                    ? `<strong>${title}</strong> <span class="badge badge--muted">Offer removed</span>`
                    : `<a href="/jobexaclyoffer?id=${encodeURIComponent(a.offerId)}" data-link><strong>${title}</strong></a>`;

                const previewHtml = removed
                    ? ''
                    : `<a href="/jobexaclyoffer?id=${encodeURIComponent(a.offerId)}" data-link>Preview ↗</a>`;

                const company = removed ? '' : (a.companyName || '—');
                const city    = removed ? '' : (a.cityName || '—');

                return `
<li class="apps__item" data-app-id="${a.id}">
  <div>
    <div>${titleHtml}</div>
    <div class="apps__meta">${removed ? '' : `Company: ${company} • City: ${city}`}</div>
    <div class="apps__meta">Applied: ${applied}</div>
  </div>
  <div class="actions">
    <span class="badge">${a.status || 'APPLIED'}</span>
    <button type="button" class="linklike js-cv" data-url="${a.cvDownloadUrl || ''}">CV ↗</button>
    ${previewHtml}
  </div>
</li>`;
            }).join('');

            return `
<li class="group" aria-expanded="true">
  <button class="group__head" type="button">
    <div class="group__title">
      <strong>${g.title}</strong>
      <span class="group__meta">${headMeta}</span>
    </div>
    <span class="group__count">${g.items.length}</span>
  </button>
  <div class="group__body">
    <ul class="apps__list">${itemsHtml}</ul>
  </div>
</li>`;
        }).join('');

        ul.querySelectorAll('.group').forEach(gr=>{
            const head = gr.querySelector('.group__head');
            head.addEventListener('click', ()=> {
                const open = gr.getAttribute('aria-expanded') !== 'true';
                gr.setAttribute('aria-expanded', open ? 'true':'false');
            });
        });

        wireSpaLinks(ul);
        ul.querySelectorAll('.js-cv').forEach(btn=>{
            btn.addEventListener('click', (e)=>{
                e.preventDefault();
                const li = btn.closest('.apps__item');
                const id = li?.getAttribute('data-app-id');
                const url= btn.getAttribute('data-url') || undefined;
                downloadCv(id, url);
            });
        });

        pagerEl.hidden = false;
        const prev = document.createElement('button');
        prev.className='pager__btn';
        prev.textContent='← Prev';
        prev.disabled = pageState<=1;

        const info = document.createElement('span');
        info.className='pager__info';
        info.textContent = `Page ${pageState} / ${totalPages} • ${totalApps} applications`;

        const next = document.createElement('button');
        next.className='pager__btn';
        next.textContent='Next →';
        next.disabled = pageState>=totalPages;

        pagerEl.append(prev, info, next);

        return { totalPages };
    }

    function renderMine(){
        const filtered = filterAndSort(dataMine);
        const r = renderGroups(ulMine, filtered, pageMine, pagerMine);
        pagerMine.onclick = (e)=>{
            if (!(e.target instanceof HTMLButtonElement)) return;
            if (e.target.textContent?.includes('Prev') && pageMine>1){ pageMine--; renderMine(); }
            if (e.target.textContent?.includes('Next') && pageMine<r.totalPages){ pageMine++; renderMine(); }
        };
    }
    function renderOwned(){
        const filtered = filterAndSort(dataOwned);
        const r = renderGroups(ulOwned, filtered, pageOwned, pagerOwned);
        pagerOwned.onclick = (e)=>{
            if (!(e.target instanceof HTMLButtonElement)) return;
            if (e.target.textContent?.includes('Prev') && pageOwned>1){ pageOwned--; renderOwned(); }
            if (e.target.textContent?.includes('Next') && pageOwned<r.totalPages){ pageOwned++; renderOwned(); }
        };
    }

    (async ()=>{ await ensureMine(); })();
}
