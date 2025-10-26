import { getAccess } from '../../shared/api.js';

const FAV = (() => {
    const api = (p) => `${p}`;
    const auth = () => {
        try {
            const t = getAccess?.();
            return t ? { Authorization: `Bearer ${t}` } : {};
        } catch { return {}; }
    };
    const loggedIn = () => !!getAccess?.();

    async function status(type, id) {
        try {
            const r = await fetch(api(`/api/favorites/${type}/${id}/status`), {
                headers: { Accept: 'application/json', ...auth() },
                credentials: 'include'
            });
            if (!r.ok) throw 0;
            return r.json();
        } catch {
            return { favorited: false, count: 0 };
        }
    }

    async function toggle(type, id) {
        const r = await fetch(api(`/api/favorites/${type}/${id}/toggle`), {
            method: 'POST',
            headers: { Accept: 'application/json', ...auth() },
            credentials: 'include'
        });
        if (r.status === 401 || r.status === 403) throw new Error('Please log in');
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
    }

    function paint(el, s) {
        if (!el) return;
        const on = !!s.favorited;
        el.classList.toggle('fav--on', on);
        el.setAttribute('aria-pressed', on ? 'true' : 'false');
        el.setAttribute('aria-label', on ? 'Remove from favourites' : 'Add to favourites');
        const cnt = el.querySelector('.fav__count');
        if (cnt) cnt.textContent = String(s.count ?? 0);
    }

    function mountButton(el, { type, id, disabledWhenLoggedOut = true }) {
        if (!id) return;
        const isLogged = loggedIn();

        el.classList.add('fav');
        el.setAttribute('data-fav-type', type);
        el.setAttribute('data-fav-id', String(id));
        el.setAttribute('type', 'button');
        el.setAttribute('aria-pressed', 'false');
        el.innerHTML = `
      <svg class="fav__icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
        <path d="M8 14s-6-3.33-6-8a3.5 3.5 0 0 1 6-2.475A3.5 3.5 0 0 1 14 6c0 4.67-6 8-6 8z"></path>
      </svg>
      <span class="fav__count" aria-hidden="true">0</span>
    `;

        if (!isLogged && disabledWhenLoggedOut) {
            el.classList.add('fav--disabled');
            el.title = 'Log in to save favourites';
            return;
        }

        status(type, id).then(s => paint(el, s)).catch(() => {});
        el.addEventListener('click', async (e) => {
            e.preventDefault();
            if (!loggedIn()) return;
            try {
                const s = await toggle(type, id);
                paint(el, s);
            } catch (err) {
                console.error('fav toggle failed', err);
            }
        });
    }

    return { mountButton, loggedIn };
})();

export function initJobs(opts = {}) {
    const API_URL = opts.apiUrl ?? '/api/jobs';
    const PAGE_SIZE = 50;

    const LEVEL_MAP = {
        '': '', internship:'INTERNSHIP', intern:'INTERNSHIP', trainee:'INTERNSHIP',
        junior:'JUNIOR', jr:'JUNIOR', mid:'MID', regular:'MID', middle:'MID',
        senior:'SENIOR', sr:'SENIOR', lead:'LEAD',
        INTERNSHIP:'INTERNSHIP', JUNIOR:'JUNIOR', MID:'MID', SENIOR:'SENIOR', LEAD:'LEAD'
    };
    const mapLevel = v => LEVEL_MAP[String(v ?? '').trim()] ?? '';

    const CONTRACT_MAP = {
        '': '', UOP:'UOP', B2B:'B2B', UZ:'UZ', UOD:'UOD',
        uop:'UOP', b2b:'B2B', uz:'UZ', uod:'UOD',
        perm:'UOP', permanent:'UOP', mandate:'UZ', 'specific-task':'UOD'
    };
    const mapContract = v => CONTRACT_MAP[String(v ?? '').trim()] ?? '';

    const state = {
        page:1, size:PAGE_SIZE, totalElements:0, totalPages:0, items:[], loading:false,
        filters:{
            q:'', city:'', seniority:'',
            contract:'', contracts:[],
            withSalary:false, remote:false,
            sort:'relevance', group:'city',
            specs:[], techs:[]
        }
    };

    const $list=byId('list'), $count=byId('count'), $loading=byId('loading'), $empty=byId('empty'),
        $modal=byId('modal'), $modalBody=byId('modalBody'), $pager=byId('pager'),
        $q=byId('q'), $city=byId('city'), $seniority=byId('seniority'), $contract=byId('contract'),
        $sort=byId('sort'), $group=byId('group'), $withSalary=byId('withSalary'),
        $remote=byId('remote'), $reset=byId('reset');

    const specChecks = Array.from(document.querySelectorAll('input[name="spec[]"].chipcheck'));
    const techChecks = Array.from(document.querySelectorAll('input[name="tech[]"].chipcheck'));
    const contractChecks = Array.from(document.querySelectorAll('input[name="contract[]"].chipcheck'));

    async function fetchJobs(page){
        const p = new URLSearchParams();
        p.append('page', page);
        p.append('pageSize', state.size);

        if (state.filters.q)    p.append('q', state.filters.q);
        if (state.filters.city) p.append('city', state.filters.city);

        const lvl = mapLevel(state.filters.seniority);
        if (lvl) p.append('level', lvl);

        const chosen = new Set();
        if (state.filters.contract) chosen.add(state.filters.contract);
        (state.filters.contracts || []).forEach(c => chosen.add(c));
        for (const c of chosen){
            const norm = mapContract(c);
            if (norm) p.append('contract', norm);
        }

        if (state.filters.withSalary) p.append('withSalary', 'true');
        if (state.filters.remote)     p.append('remote', 'true');

        p.append('sort', state.filters.sort);
        state.filters.specs.forEach(v => p.append('spec', v));
        state.filters.techs.forEach(v => p.append('tech', v));

        try{
            const res = await fetch(`${API_URL}?${p.toString()}`, { headers:{Accept:'application/json'} });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const json = await res.json();

            let rawItems=[], totalElements=0, totalPages=0;
            if (Array.isArray(json?.content)){
                rawItems=json.content; totalElements=json.totalElements ?? rawItems.length; totalPages=json.totalPages ?? Math.ceil(totalElements/state.size);
            } else if (Array.isArray(json?.items)){
                rawItems=json.items; totalElements=json.total ?? rawItems.length; totalPages=Math.ceil(totalElements/state.size);
            } else if (Array.isArray(json)){
                rawItems=json; totalElements=json.length; totalPages=Math.ceil(totalElements/state.size);
            }
            return { items: rawItems.map(toUiListItem), totalElements, totalPages };
        }catch(e){
            console.warn('API unavailable – DEMO fallback', e);
            const start=(page-1)*state.size, slice=DEMO_ITEMS.slice(start, start+state.size);
            return { items: slice.map(toUiListItem), totalElements: DEMO_ITEMS.length, totalPages: Math.ceil(DEMO_ITEMS.length/state.size) };
        }
    }

    async function fetchJobDetail(id){
        try{
            const res = await fetch(`${API_URL}/${id}`, { headers:{Accept:'application/json'} });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return toUiDetail(await res.json());
        }catch(e){
            console.warn('Detail API fail – using list/DEMO', e);
            const found = state.items.find(x => x.id === id);
            return found ? toUiDetail(found) : null;
        }
    }

    function toSalary(x){
        const min=x?.salaryMin ?? x?.salary?.min ?? null;
        const max=x?.salaryMax ?? x?.salary?.max ?? null;
        const currency=x?.currency ?? x?.salary?.currency ?? 'PLN';
        const period=x?.salary?.period ?? 'MONTH';
        if (min==null && max==null) return null;
        return {min,max,currency,period};
    }
    function ensureContracts(x){
        if (Array.isArray(x?.contracts)) return x.contracts.filter(Boolean);
        if (x?.contract) return [x.contract];
        return [];
    }

    function toUiListItem(x){
        return {
            id:x.id ?? x._id ?? x.externalId ?? null,
            url:x.url ?? x.applyUrl ?? null,
            title:x.title ?? '',
            company:x.companyName ?? x.company ?? null,
            city:x.cityName ?? x.city ?? null,
            country:x.country ?? null,
            datePosted:x.publishedAt ?? x.datePosted ?? null,
            keywords: Array.isArray(x.techTags) ? x.techTags
                : Array.isArray(x.keywords) ? x.keywords
                    : Array.isArray(x.techStack) ? x.techStack.map(s => s?.name).filter(Boolean)
                        : [],
            salary: toSalary(x),
            remote: !!x.remote,
            description: x.description ?? null,
            contract: x.contract ?? null,
            contracts: ensureContracts(x),
            level: x.level ?? null
        };
    }
    function toUiDetail(x){
        return {
            id:x.id ?? null, url:x.url ?? null, title:x.title ?? '',
            description:x.description ?? '',
            company:x.companyName ?? x.company ?? null,
            city:x.cityName ?? x.city ?? null,
            datePosted:x.publishedAt ?? x.datePosted ?? null,
            keywords: Array.isArray(x.techTags) ? x.techTags
                : Array.isArray(x.keywords) ? x.keywords
                    : Array.isArray(x.techStack) ? x.techStack.map(s => s?.name).filter(Boolean)
                        : [],
            salary: toSalary(x),
            contract: x.contract ?? null,
            contracts: ensureContracts(x),
            level: x.level ?? null,
            remote: !!x.remote
        };
    }

    function groupKey(job){
        return state.filters.group === 'city'
            ? (job.city || '— other —')
            : (job.company || '— company —');
    }

    function formatSalary(s){
        if (!s) return '—';
        const min = s.min!=null ? s.min.toLocaleString('en-GB') : '';
        const max = s.max!=null ? s.max.toLocaleString('en-GB') : '';
        const cur = s.currency || 'PLN';
        const per = (s.period || 'MONTH').toLowerCase();
        return `${min}${max ? ' – ' + max : ''} ${cur}/${per}`;
    }
    function prettyContract(c){
        if (!c) return null;
        const up=String(c).toUpperCase();
        if (up==='UOP') return 'UoP';
        if (up==='UOD') return 'UoD';
        return up;
    }
    function prettyLevel(lvl){
        if (!lvl) return null;
        const m={INTERNSHIP:'Intern', JUNIOR:'Junior', MID:'Mid', SENIOR:'Senior', LEAD:'Lead'};
        return m[String(lvl).toUpperCase()] ?? lvl;
    }
    function escapeHtml(s){
        return String(s ?? '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
    }

    function renderCard(job){
        const el=document.createElement('div');
        el.className='card';
        el.setAttribute('role','listitem');

        const contracts=(job.contracts && job.contracts.length) ? job.contracts : (job.contract ? [job.contract] : []);
        const contractsBadges=contracts.map(c=>`<span class="badge">${escapeHtml(prettyContract(c))}</span>`).join('');
        const levelBadge  = job.level  ? `<span class="badge">${escapeHtml(pretyLevelSafe(job.level))}</span>` : '';
        const remoteBadge = job.remote ? `<span class="badge">Remote</span>` : '';

        el.innerHTML = `
      <div>
        <div class="card__title">${escapeHtml(job.title || '')}</div>
        <div class="meta">
          <span>🏢 ${escapeHtml(job.company || '—')}</span>
          <span>📍 ${escapeHtml(job.city || '')}${job.country ? ' ('+escapeHtml(job.country)+')' : ''}</span>
          ${job.datePosted ? `<span>🗓 ${new Date(job.datePosted).toLocaleDateString('en-GB')}</span>` : ''}
        </div>
        <div class="badges">
          ${contractsBadges}${levelBadge}${remoteBadge}
          ${(job.keywords || []).slice(0,6).map(k=>`<span class="badge">${escapeHtml(k)}</span>`).join('')}
        </div>
      </div>
      <div class="actions" style="display:flex; align-items:center; gap:10px">
        <div class="money">${formatSalary(job.salary)}</div>
        <button class="chip" data-open="1">Preview</button>
        ${job.url ? `<a class="chip" href="${job.url}" target="_blank" rel="noopener">Apply ↗</a>` : ''}
      </div>
    `;

        if (FAV.loggedIn()) {
            const actions = el.querySelector('.actions');
            if (actions) {
                const favBtn = document.createElement('button');
                actions.prepend(favBtn);
                FAV.mountButton(favBtn, { type: 'JOB', id: job.id });
            }
        }

        el.querySelector('[data-open]')?.addEventListener('click', async () => {
            const detail = await fetchJobDetail(job.id);
            openModal(detail ?? job);
        });
        return el;

        function pretyLevelSafe(l){ return prettyLevel(l); }
    }

    function renderList(){
        $list.innerHTML='';
        if (!state.items.length){
            $empty.classList.remove('hidden');
            $count.textContent='0 jobs';
            $pager.innerHTML=''; return;
        }
        $empty.classList.add('hidden');

        const groups=new Map();
        for (const job of state.items){
            const key=groupKey(job);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(job);
        }
        for (const [g, arr] of groups){
            const head=document.createElement('div');
            head.className='group'; head.textContent=`${g} • ${arr.length}`;
            $list.appendChild(head);
            for (const job of arr) $list.appendChild(renderCard(job));
        }

        const total=state.totalElements || state.items.length;
        $count.textContent=`${total.toLocaleString('en-GB')} jobs • page ${state.page}/${Math.max(state.totalPages,1)}`;

        renderPager();
    }

    function renderPager(){
        if (!$pager) return;
        const total=state.totalPages, cur=state.page;
        if (!total || total<=1){ $pager.innerHTML=''; return; }

        const btn=(label,page,disabled=false,current=false)=>{
            const a=document.createElement('button');
            a.type='button'; a.className='pager__btn'; a.textContent=label;
            if (disabled) a.disabled=true;
            if (current) a.setAttribute('aria-current','page');
            a.addEventListener('click',()=>gotoPage(page));
            return a;
        };

        $pager.innerHTML='';
        $pager.appendChild(btn('«', Math.max(1,cur-1), cur===1));

        let start=Math.max(1, cur-2);
        let end=Math.min(total, start+4);
        start=Math.max(1, end-4);

        if (start>1){
            $pager.appendChild(btn('1',1,false,cur===1));
            if (start>2) $pager.appendChild(ellipsis());
        }
        for (let p=start; p<=end; p++) $pager.appendChild(btn(String(p), p, false, p===cur));
        if (end<total){
            if (end<total-1) $pager.appendChild(ellipsis());
            $pager.appendChild(btn(String(total), total, false, cur===total));
        }
        $pager.appendChild(btn('»', Math.min(total,cur+1), cur===total));

        function ellipsis(){ const s=document.createElement('span'); s.className='pager__dots'; s.textContent='…'; return s; }
    }

    async function gotoPage(page){
        if (state.loading) return;
        state.loading=true; $loading.classList.remove('hidden');
        try{
            const {items,totalElements,totalPages}=await fetchJobs(page);
            state.page=page; state.items=items; state.totalElements=totalElements; state.totalPages=totalPages;
            renderList();
        }finally{
            state.loading=false; $loading.classList.add('hidden');
        }
    }

    function openModal(job){
        if (!job) return;

        const contracts=(job.contracts && job.contracts.length) ? job.contracts : (job.contract ? [job.contract] : []);
        const contractsBadges=contracts.map(c=>`<span class="badge">${escapeHtml(prettyContract(c))}</span>`).join('');
        const levelBadge  = job.level  ? `<span class="badge">${escapeHtml(prettyLevel(job.level))}</span>` : '';
        const remoteBadge = job.remote ? `<span class="badge">Remote</span>` : '';

        $modalBody.innerHTML = `
      <header class="modal__header">
        <h3>${escapeHtml(job.title || '')}</h3>
        <div class="modal__meta">
          <span>🏢 ${escapeHtml(job.company || '—')}</span>
          <span>📍 ${escapeHtml(job.city || '')}${job.country ? ' ('+escapeHtml(job.country)+')' : ''}</span>
          ${contractsBadges}${levelBadge}${remoteBadge}
          <span class="money">${formatSalary(job.salary)}</span>
        </div>
      </header>

      <section class="modal__grid">
        <article>
          <div class="job-desc">
            <h4>Description</h4>
            ${formatDescription(job.description || '')}
          </div>
        </article>

        <aside class="aside">
          <h4>Tags</h4>
          <div class="badges">${(job.keywords || []).map(k => `<span class="badge">${escapeHtml(k)}</span>`).join('') || '—'}</div>
          <div class="cta">
            ${job.url ? `<a class="chip" href="${job.url}" target="_blank" rel="noopener">Apply ↗</a>` : ''}
          </div>
        </aside>
      </section>
    `;
        if (!$modal.open) $modal.showModal();
    }
    document.querySelector('.modal__x .icon-btn')?.addEventListener('click', () => $modal.close());

    function formatDescription(raw){
        const text = String(raw).replace(/\r\n?/g, '\n').trim();
        if (!text) return '<p class="muted">—</p>';

        const blocks = text.split(/\n{2,}/);
        const html = blocks.map(block => {
            const lines = block.split('\n').map(l => l.trim()).filter(Boolean);
            const listLike = lines.length>1 && lines.every(l => /^[\-\*\u2022]|\d{1,2}[.)]\s/.test(l));
            if (listLike){
                const items = lines.map(l => l.replace(/^[\-\*\u2022]\s?|\d{1,2}[.)]\s/, ''))
                    .map(escapeHtml)
                    .map(i => `<li>${i}</li>`).join('');
                return `<ul>${items}</ul>`;
            }
            return `<p>${escapeHtml(lines.join(' '))}</p>`;
        }).join('');
        return html;
    }

    let t; const deb = fn => { clearTimeout(t); t=setTimeout(fn, 250); };

    $q?.addEventListener('input', e => { state.filters.q=e.target.value.trim(); deb(()=>gotoPage(1)); });
    $city?.addEventListener('input', e => { state.filters.city=e.target.value.trim(); deb(()=>gotoPage(1)); });
    $seniority?.addEventListener('change', e => { state.filters.seniority=e.target.value; gotoPage(1); });
    $contract?.addEventListener('change', e => { state.filters.contract=e.target.value; gotoPage(1); });
    contractChecks.forEach(chk => chk.addEventListener('change', () => {
        state.filters.contracts = contractChecks.filter(x=>x.checked).map(x=>x.value);
        gotoPage(1);
    }));
    $sort?.addEventListener('change', e => { state.filters.sort=e.target.value; gotoPage(1); });

    $withSalary?.addEventListener('click', e => {
        togglePressed(e.currentTarget);
        state.filters.withSalary = e.currentTarget.getAttribute('aria-pressed') === 'true';
        gotoPage(1);
    });
    $remote?.addEventListener('click', e => {
        togglePressed(e.currentTarget);
        state.filters.remote = e.currentTarget.getAttribute('aria-pressed') === 'true';
        gotoPage(1);
    });
    $group?.addEventListener('click', e => {
        state.filters.group = state.filters.group === 'city' ? 'company' : 'city';
        e.currentTarget.textContent = 'Group by: ' + (state.filters.group === 'city' ? 'city' : 'company');
        renderList();
    });

    const onChipChange = () => {
        state.filters.specs = specChecks.filter(x=>x.checked).map(x=>x.value);
        state.filters.techs = techChecks.filter(x=>x.checked).map(x=>x.value);
        gotoPage(1);
    };
    specChecks.forEach(chk => chk.addEventListener('change', onChipChange));
    techChecks.forEach(chk => chk.addEventListener('change', onChipChange));

    $reset?.addEventListener('click', () => {
        Object.assign(state.filters, {
            q:'', city:'', seniority:'',
            contract:'', contracts:[],
            withSalary:false, remote:false,
            sort:'relevance', group:'city',
            specs:[], techs:[]
        });
        [$q,$city,$seniority,$contract,$sort].forEach(el => { if (el) el.value=''; });
        contractChecks.forEach(x => x.checked=false);
        $withSalary?.setAttribute('aria-pressed','false');
        $remote?.setAttribute('aria-pressed','false');
        $group && ($group.textContent='Group by: city');
        specChecks.forEach(x => x.checked=false);
        techChecks.forEach(x => x.checked=false);
        gotoPage(1);
    });

    gotoPage(1);

    function byId(id){ return document.getElementById(id); }
    function togglePressed(btn){ const v=btn.getAttribute('aria-pressed')==='true'; btn.setAttribute('aria-pressed', String(!v)); }
}

const DEMO_ITEMS = [
    { id:'1', url:'https://example.com/apply', title:'Senior Java Developer', company:'Acme', city:'Warsaw', country:'PL',
        description:'Microservices, Spring Boot, Kafka, AWS.\n\n- Build APIs\n- Own CI/CD\n- Work with Kafka\n\nRequirements:\n1. Java 17+\n2. Spring Boot\n3. AWS',
        salary:{currency:'PLN', min:22000, max:32000, period:'MONTH'},
        datePosted:'2025-09-29T10:00:00Z', keywords:['Java','Spring','Kafka','AWS'], contract:'B2B', contracts:['B2B','UOP'], level:'SENIOR', remote:true },
    { id:'2', url:'https://example.com/apply', title:'React Developer', company:'OneRail', city:'Krakow', country:'PL',
        description:'React, TypeScript, Vite, REST.', salary:{currency:'PLN', min:18000, max:25000, period:'MONTH'},
        datePosted:'2025-09-28T09:00:00Z', keywords:['React','TypeScript','Vite','REST'], contract:'UOP', contracts:['UOP'], level:'MID' }
];
