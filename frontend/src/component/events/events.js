import { getAccess } from '../../shared/api.js';

const FAV = (() => {
    const api = (p) => `${p}`;
    const auth = () => { try { const t = getAccess?.(); return t ? { Authorization:`Bearer ${t}` } : {}; } catch { return {}; } };
    const loggedIn = () => !!getAccess?.();

    async function status(type, id){
        try{
            const r = await fetch(api(`/api/favorites/${type}/${id}/status`), { headers:{ Accept:'application/json', ...auth() }, credentials:'include' });
            if(!r.ok) throw 0; return r.json();
        }catch{ return { favorited:false, count:0 }; }
    }
    async function toggle(type, id){
        const r = await fetch(api(`/api/favorites/${type}/${id}/toggle`), { method:'POST', headers:{ Accept:'application/json', ...auth() }, credentials:'include' });
        if (r.status===401 || r.status===403) throw new Error('Please log in');
        if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json();
    }
    function paint(el,s){
        if(!el) return; const on=!!s.favorited;
        el.classList.toggle('fav--on', on);
        el.setAttribute('aria-pressed', on?'true':'false');
        el.setAttribute('aria-label', on?'Remove from favourites':'Add to favourites');
        const cnt = el.querySelector('.fav__count'); if (cnt) cnt.textContent = String(s.count ?? 0);
    }
    function mountButton(el, { type, id, disabledWhenLoggedOut=true }){
        if(!id) return; const isLogged = loggedIn();
        el.classList.add('fav'); el.setAttribute('data-fav-type', type); el.setAttribute('data-fav-id', String(id));
        el.type='button'; el.setAttribute('aria-pressed','false');
        el.innerHTML = `
      <svg class="fav__icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
        <path d="M8 14s-6-3.33-6-8a3.5 3.5 0 0 1 6-2.475A3.5 3.5 0 0 1 14 6c0 4.67-6 8-6 8z"></path>
      </svg>
      <span class="fav__count" aria-hidden="true">0</span>`;
        if(!isLogged && disabledWhenLoggedOut){ el.classList.add('fav--disabled'); el.title='Log in to save favourites'; return; }
        status(type,id).then(s=>paint(el,s)).catch(()=>{});
        el.addEventListener('click', async (e)=>{ e.preventDefault(); if(!loggedIn()) return; try{ paint(el, await toggle(type,id)); }catch(err){ console.error(err);} });
    }
    return { mountButton, loggedIn };
})();

export function initEvents(opts = {}) {
    const API_URL   = opts.apiUrl ?? '/api/events';
    const PAGE_SIZE = 25;

    const ISO2 = "AF,AX,AL,DZ,AS,AD,AO,AI,AQ,AG,AR,AM,AW,AU,AT,AZ,BS,BH,BD,BB,BY,BE,BZ,BJ,BM,BT,BO,BQ,BA,BW,BV,BR,IO,BN,BG,BF,BI,KH,CM,CA,CV,KY,CF,TD,CL,CN,CX,CC,CO,KM,CG,CD,CK,CR,CI,HR,CU,CW,CY,CZ,DK,DJ,DM,DO,EC,EG,SV,GQ,ER,EE,SZ,ET,FK,FO,FJ,FI,FR,GF,PF,TF,GA,GM,GE,DE,GH,GI,GR,GL,GD,GP,GU,GT,GG,GN,GW,GY,HT,HM,VA,HN,HK,HU,IS,IN,ID,IR,IQ,IE,IM,IL,IT,JM,JP,JE,JO,KZ,KE,KI,KP,KR,KW,KG,LA,LV,LB,LS,LR,LY,LI,LT,LU,MO,MG,MW,MY,MV,ML,MT,MH,MQ,MR,MU,YT,MX,FM,MD,MC,MN,ME,MS,MA,MZ,MM,NA,NR,NP,NL,NC,NZ,NI,NE,NG,NU,NF,MK,MP,NO,OM,PK,PW,PS,PA,PG,PY,PE,PH,PN,PL,PT,PR,QA,RE,RO,RU,RW,BL,SH,KN,LC,MF,PM,VC,WS,SM,ST,SA,SN,RS,SC,SL,SG,SX,SK,SI,SB,SO,ZA,GS,SS,ES,LK,SD,SR,SJ,SE,CH,SY,TW,TJ,TZ,TH,TL,TG,TK,TO,TT,TN,TR,TM,TC,TV,UG,UA,AE,GB,US,UM,UY,UZ,VU,VE,VN,VG,VI,WF,EH,YE,ZM,ZW".split(",");
    const REGION = new Intl.DisplayNames(['en'], { type: 'region' });

    const COUNTRIES = [
        { code: '',          name: 'All countries' },
        { code: 'ZZ-ONLINE', name: 'Online' }
    ].concat(ISO2.map(code => ({ code, name: REGION.of(code) }))).sort((a,b)=>a.name.localeCompare(b.name));

    const TECHS = ['javascript','typescript','react','java','python','c#','php','aws','kubernetes','devops','data','ai/ml'];

    const state = {
        page: 0, size: PAGE_SIZE, total: 0, totalPages: 1,
        q:'', country:'', city:'', tech:'', from:'', to:''
    };

    const $ = (sel) => document.querySelector(sel);
    const $grid = $('#grid'), $status = $('#status'), $meta = $('#meta'),
        $pageInfo = $('#page-info'), $btnPrev = $('#btn-prev'), $btnNext = $('#btn-next');

    const countrySel    = $('#country');
    const countrySearch = document.getElementById('country-search');

    const $pager = $btnPrev?.parentElement || null;
    let $nums = null;
    if ($pager){
        if ($btnPrev) $btnPrev.textContent = 'Previous';
        if ($btnNext) $btnNext.textContent = 'Next';
        if ($pageInfo) $pageInfo.remove();
        $nums = document.createElement('div');
        $nums.id = 'page-numbers';
        $pager.classList.add('pagination');
        $pager.insertBefore($nums, $btnNext);
    }

    if (countrySel) {
        countrySel.innerHTML = COUNTRIES.map(c => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join('');
        countrySel.value = '';
    }

    const chipsTech = $('#chips-tech');
    if (chipsTech) {
        chipsTech.innerHTML = TECHS.map(t => `<button class="chip" data-tech="${t}">${t}</button>`).join('');
        chipsTech.querySelectorAll('.chip').forEach(b => b.addEventListener('click', () => {
            state.tech = (state.tech === b.dataset.tech) ? '' : b.dataset.tech;
            highlightTech(); onChange();
        }));
    }

    $('#q')?.addEventListener('input', debounce(onChange, 300));
    $('#city')?.addEventListener('input', debounce(onChange, 0));
    $('#from')?.addEventListener('change', onChange);
    $('#to')?.addEventListener('change', onChange);

    $('#btn-reset')?.addEventListener('click', () => { reset(); onChange(); });

    $('#country')?.addEventListener('change', () => {
        const disableCity = !countrySel.value || countrySel.value === 'ZZ-ONLINE';
        const cityEl = $('#city');
        if (cityEl) { cityEl.disabled = disableCity; if (disableCity) cityEl.value = ''; }
        onChange();
    });

    if (countrySearch && countrySel) {
        countrySearch.addEventListener('input', debounce(() => {
            const q = countrySearch.value.trim().toLowerCase();
            const filtered = COUNTRIES.filter((c, i) => (i <= 1) || c.name.toLowerCase().includes(q));
            countrySel.innerHTML = filtered.map(c => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join('');
            const exact = filtered.find(c => c.name.toLowerCase() === q);
            countrySel.value = (exact?.code ?? filtered[0]?.code ?? '');
            const disableCity = !countrySel.value || countrySel.value === 'ZZ-ONLINE';
            const cityEl = $('#city');
            if (cityEl) { cityEl.disabled = disableCity; if (disableCity) cityEl.value = ''; }
            onChange();
        }, 200));
    }

    $btnPrev?.addEventListener('click', () => { if (state.page > 0){ state.page--; fetchAndRender(false); } });
    $btnNext?.addEventListener('click', () => { if (state.page + 1 < state.totalPages){ state.page++; fetchAndRender(false); } });

    if ($('#from') && !$('#from').value) $('#from').value = new Date().toISOString().slice(0,10);
    if ($('#city')) $('#city').disabled = !countrySel.value || countrySel.value === 'ZZ-ONLINE';

    const toolbarEl = document.querySelector('.events .toolbar');
    const onScr = () => toolbarEl?.classList.toggle('is-raised', window.scrollY > 4);
    window.addEventListener('scroll', onScr, { passive:true });
    onScr();

    onChange();

    function onChange(){
        state.q       = $('#q')?.value.trim() || '';
        state.country = countrySel?.value.trim() || '';
        state.city    = $('#city')?.value.trim() || '';
        state.from    = $('#from')?.value || '';
        state.to      = $('#to')?.value   || '';
        state.page    = 0;
        fetchAndRender();
    }

    async function fetchAndRender(){
        setStatus('Loading…');
        try{
            const res = await fetchLive({
                q: state.q, country: state.country, city: state.city,
                tech: state.tech, from: state.from, to: state.to,
                page: state.page, size: state.size
            });

            state.total = res.totalElements ?? 0;
            state.totalPages = res.totalPages ?? Math.max(1, Math.ceil(state.total / state.size));
            const items = (res.content || []).map(toVM);

            renderCards(items);
            if ($meta)     $meta.textContent     = `${state.total} results`;
            if ($btnPrev)  $btnPrev.disabled     = state.page === 0;
            if ($btnNext)  $btnNext.disabled     = (state.page+1) >= state.totalPages;
            renderPageNumbers();
            setStatus(items.length === 0 ? 'No events match your filters.' : '');
        }catch(err){
            console.warn('Events API unavailable – DEMO fallback', err);
            const demo = MOCK.search({
                q: state.q, country: state.country, city: state.city,
                tech: state.tech, page: state.page, size: state.size
            });
            state.total = demo.totalElements; state.totalPages = demo.totalPages;
            const items = (demo.content || []).map(toVM);
            renderCards(items);
            if ($meta)     $meta.textContent     = `${state.total} results`;
            if ($btnPrev)  $btnPrev.disabled     = state.page === 0;
            if ($btnNext)  $btnNext.disabled     = (state.page+1) >= state.totalPages;
            renderPageNumbers();
            setStatus(items.length === 0 ? 'No events match your filters.' : '');
        }
    }

    async function fetchLive(params){
        const p = new URLSearchParams({
            q: params.q,
            ...(params.country && params.country !== 'ZZ-ONLINE' ? { country: REGION.of(params.country) } : {}),
            city: params.city,
            tag:  params.tech || '',
            from: params.from ? `${params.from}T00:00:00Z` : '',
            to:   params.to   ? `${params.to}T23:59:59Z` : '',
            page: params.page,
            size: params.size,
            sort: 'startAt,asc'
        });
        if (params.country === 'ZZ-ONLINE') p.set('online', 'true');
        [...p.keys()].forEach(k => { if (!p.get(k)) p.delete(k); });

        const url = `${API_URL}?${p.toString()}`;
        const r = await fetch(url, { headers:{ Accept:'application/json' } });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
    }

    function renderCards(items){
        if (!$grid) return;
        $grid.hidden = false;
        $grid.innerHTML = '';
        for (const ev of items){
            const card  = document.createElement('article'); card.className = 'card';
            const title = document.createElement('div');    title.className = 'title'; title.textContent = ev.title || '';
            const where = document.createElement('div');    where.className = 'where';
            where.textContent = `${ev.city ? ev.city + ', ' : ''}${ev.country || '—'} • ${ev.date}`;

            const tags = document.createElement('div');
            tags.className = 'tags';
            tags.innerHTML = (ev.categories?.length ? ev.categories.map(c => `<span class="tag">${escapeHtml(c)}</span>`).join('') : '');
            if (!ev.categories?.length) tags.style.display='none';

            const link = document.createElement('a');
            link.className = 'btn'; link.href = ev.url; link.target = '_blank'; link.rel = 'noreferrer'; link.textContent = 'Details';

            const row = document.createElement('div');
            row.style.display='flex'; row.style.gap='10px'; row.style.flexWrap='wrap';
            row.appendChild(link);

            if (FAV.loggedIn()) {
                const favBtn = document.createElement('button'); favBtn.type = 'button';
                row.appendChild(favBtn); FAV.mountButton(favBtn, { type: 'EVENT', id: ev.id });
            }

            card.appendChild(title); card.appendChild(where); card.appendChild(tags); card.appendChild(row);
            $grid.appendChild(card);
        }
    }

    function renderPageNumbers(){
        if (!$nums) return;
        const total = state.totalPages;
        const current = state.page;

        const makeNum = (idx, label = (idx+1), isCurrent = false) => {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = isCurrent ? 'pagination__num is-current' : 'pagination__num';
            b.textContent = String(label);
            if (!isCurrent){
                b.addEventListener('click', () => {
                    if (idx === state.page) return;
                    state.page = idx;
                    fetchAndRender(false);
                });
            } else {
                b.setAttribute('aria-current', 'page');
                b.disabled = true;
            }
            return b;
        };
        const ellipsis = () => {
            const s = document.createElement('span');
            s.className = 'pagination__ellipsis';
            s.textContent = '…';
            return s;
        };

        const windowSize = 2;
        let start = Math.max(1, current - windowSize) ;
        let end   = Math.min(total - 2, current + windowSize);

        $nums.innerHTML = '';

        if (total <= 1) return;

        $nums.appendChild(makeNum(0, 1, current === 0));

        if (start > 1) $nums.appendChild(ellipsis());

        for (let i = start; i <= end; i++){
            $nums.appendChild(makeNum(i, i+1, current === i));
        }

        if (end < total - 2) $nums.appendChild(ellipsis());

        if (total > 1){
            const lastIdx = total - 1;
            $nums.appendChild(makeNum(lastIdx, total, current === lastIdx));
        }
    }

    const MOCK = (() => {
        const CITY_BY = {
            PL:['Warsaw','Kraków','Wrocław','Gdańsk','Poznań'],
            US:['San Francisco','New York','Seattle','Austin','Boston'],
            GB:['London','Manchester','Birmingham','Edinburgh','Bristol'],
            DE:['Berlin','Munich','Hamburg','Frankfurt','Cologne'],
            FR:['Paris','Lyon','Lille','Marseille','Nantes']
        };
        const TYPES = ['meetup','conference','hackathon','masterclass','workshop','webinar'];
        const pick = (arr) => arr[Math.floor(Math.random()*arr.length)];
        const now = new Date(), days = (n) => new Date(now.getTime() + n*864e5);
        const items = []; const baseCountries = Object.keys(CITY_BY);
        for (let i=0;i<150;i++){
            const cc = pick(baseCountries);
            const country = REGION.of(cc);
            const city = pick(CITY_BY[cc]);
            const type = pick(TYPES);
            const tech = pick(['javascript','typescript','react','java','python','c#','php','aws','kubernetes','devops','data','ai/ml']);
            const start = days((Math.random()*150|0)+1);
            const title = `${tech.toUpperCase()} ${type} • ${city}`;
            const url = `https://example.com/events/${cc.toLowerCase()}-${city.toLowerCase().replace(/\s+/g,'-')}-${i}`;
            items.push({ id:`mock-${i}`, title, url, city, country, startAt: start.toISOString(), categories:[type, tech] });
        }
        function search(params){
            const { q, country, city, tech, page=0, size=25 } = params;
            let arr = items.slice();
            if (country && country !== 'ZZ-ONLINE') arr = arr.filter(e => e.country === REGION.of(country));
            if (city) arr = arr.filter(e => (e.city||'').toLowerCase().includes(city.toLowerCase()));
            if (tech) arr = arr.filter(e => (e.categories||[]).map(s=>s.toLowerCase()).includes(tech.toLowerCase()));
            if (q){
                const qq = q.toLowerCase();
                arr = arr.filter(e =>
                    (e.title||'').toLowerCase().includes(qq) ||
                    (e.city||'').toLowerCase().includes(qq)  ||
                    (e.country||'').toLowerCase().includes(qq)
                );
            }
            arr.sort((a,b) => new Date(a.startAt) - new Date(b.startAt));
            const total = arr.length; const totalPages = Math.max(1, Math.ceil(total / size));
            const start = page * size, end = start + size;
            return { content: arr.slice(start, end), totalElements: total, totalPages };
        }
        return { search };
    })();

    const toCountryName = (v) => (v && v.length === 2 ? REGION.of(v) : v);
    function toVM(e){
        const d = e.startAt ? new Date(e.startAt) : null;
        return {
            id: e.id ?? e._id ?? e.externalId ?? null,
            title: e.title, url: e.url, city: e.city,
            country: toCountryName(e.country || e.countryCode),
            date: d ? d.toLocaleString(undefined,{ dateStyle:'medium', timeStyle:'short' }) : 'TBA',
            categories: e.categories || []
        };
    }
    function setStatus(txt){ const el=$status; if(!el) return; if(!txt){ el.hidden=true; el.textContent=''; } else { el.hidden=false; el.textContent=txt; } }
    function reset(){
        ['q','country','country-search','city','from','to'].forEach(id => { const el=document.getElementById(id); if(el) el.value=''; });
        if (countrySel) {
            countrySel.innerHTML = COUNTRIES.map(c => `<option value="${c.code}">${escapeHtml(c.name)}</option>`).join('');
            countrySel.value = '';
        }
        state.tech=''; highlightTech();
    }
    function highlightTech(){
        document.querySelectorAll('#chips-tech .chip').forEach(b => {
            const active = b.dataset.tech === state.tech;
            b.setAttribute('aria-pressed', active ? 'true' : 'false');
            b.style.outline = active ? '2px solid var(--acc1)' : 'none';
        });
    }
    function debounce(fn, ms=300){ let t; return (...a)=>{ clearTimeout(t); t=setTimeout(()=>fn(...a), ms); }; }
    function escapeHtml(s){ return String(s ?? '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m])); }
}
