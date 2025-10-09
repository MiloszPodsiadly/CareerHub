export function initJobs(opts = {}) {
    // realny backend; można nadpisać przez initJobs({ apiUrl: '...' })
    const API_URL = opts.apiUrl ?? '/api/jobs';
    const PAGE_SIZE = 50;

    // Bezpieczne mapowanie -> enum backendu
    const LEVEL_MAP = {
        '': '',
        internship: 'INTERNSHIP',
        intern: 'INTERNSHIP',
        junior: 'JUNIOR',
        mid: 'MID',
        senior: 'SENIOR',
        lead: 'LEAD',
        INTERNSHIP: 'INTERNSHIP',
        JUNIOR: 'JUNIOR',
        MID: 'MID',
        SENIOR: 'SENIOR',
        LEAD: 'LEAD'
    };

    const mapLevel = v => LEVEL_MAP[String(v ?? '').trim()] ?? '';

    const CONTRACT_MAP = {
        '': '',
        UOP:'UOP', B2B:'B2B', UZ:'UZ', UOD:'UOD',
        // tolerancja na stare wartości z frontu:
        uop:'UOP', b2b:'B2B', uz:'UZ', uod:'UOD',
        perm:'UOP', mandate:'UZ'
    };

    const mapContract = v => CONTRACT_MAP[String(v ?? '').trim()] ?? '';

    const state = {
        page: 1,                 // 1-based (zgodnie z kontrolerem)
        size: PAGE_SIZE,
        totalElements: 0,
        totalPages: 0,
        items: [],
        loading: false,
        filters: {
            q:'', city:'', seniority:'', contract:'',
            minSalary:'', withSalary:false, remote:false,
            sort:'relevance', group:'city',
            specs:[], techs:[]
        }
    };

    // ---- DOM
    const $list      = byId('list');
    const $count     = byId('count');
    const $loading   = byId('loading');
    const $empty     = byId('empty');
    const $modal     = byId('modal');
    const $modalBody = byId('modalBody');
    const $pager     = byId('pager');

    const $q         = byId('q');
    const $city      = byId('city');
    const $seniority = byId('seniority');
    const $contract  = byId('contract');
    const $minSalary = byId('minSalary');
    const $sort      = byId('sort');
    const $group     = byId('group');
    const $withSalary= byId('withSalary');
    const $remote    = byId('remote');
    const $reset     = byId('reset');

    const specChecks = Array.from(document.querySelectorAll('input[name="spec[]"].chipcheck'));
    const techChecks = Array.from(document.querySelectorAll('input[name="tech[]"].chipcheck'));

    // ---- API
    async function fetchJobs(page) {
        const p = new URLSearchParams({
            page,
            pageSize: state.size,
            q: state.filters.q,
            city: state.filters.city,
            // do backendu wysyłamy 'level' w formacie ENUM
            level: mapLevel(state.filters.seniority),
            contract: mapContract(state.filters.contract),
            salaryMin: state.filters.minSalary || '',
            sort: state.filters.sort
        });
        if (state.filters.withSalary) p.append('withSalary', 'true');
        if (state.filters.remote)     p.append('remote', 'true');
        state.filters.specs.forEach(v => p.append('spec', v));
        state.filters.techs.forEach(v => p.append('tech', v));

        try {
            const res = await fetch(`${API_URL}?${p.toString()}`, { headers:{ Accept:'application/json' } });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const json = await res.json();

            // Akceptujemy Page oraz fallbacki
            let rawItems = [];
            let totalElements = 0, totalPages = 0;

            if (Array.isArray(json?.content)) {
                rawItems = json.content;
                totalElements = json.totalElements ?? rawItems.length;
                totalPages = json.totalPages ?? Math.ceil(totalElements / state.size);
            } else if (Array.isArray(json?.items)) {
                rawItems = json.items;
                totalElements = json.total ?? rawItems.length;
                totalPages = Math.ceil(totalElements / state.size);
            } else if (Array.isArray(json)) {
                rawItems = json;
                totalElements = json.length;
                totalPages = Math.ceil(totalElements / state.size);
            }

            return {
                items: rawItems.map(toUiListItem),
                totalElements,
                totalPages
            };
        } catch (e) {
            console.warn('API niedostępne – fallback DEMO', e);
            // prosta paginacja po DEMO
            const start = (page - 1) * state.size;
            const slice = DEMO_ITEMS.slice(start, start + state.size);
            return {
                items: slice.map(toUiListItem),
                totalElements: DEMO_ITEMS.length,
                totalPages: Math.ceil(DEMO_ITEMS.length / state.size)
            };
        }
    }

    async function fetchJobDetail(id) {
        try {
            const res = await fetch(`${API_URL}/${id}`, { headers:{ Accept:'application/json' } });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return toUiDetail(await res.json());
        } catch (e) {
            console.warn('Detail API fail – fallback do list item/DEMO', e);
            const found = state.items.find(x => x.id === id);
            return found ? toUiDetail(found) : null;
        }
    }

    // ---- MAPOWANIE
    function toSalary(x) {
        const min = x?.salaryMin ?? x?.salary?.min ?? null;
        const max = x?.salaryMax ?? x?.salary?.max ?? null;
        const currency = x?.currency ?? x?.salary?.currency ?? 'PLN';
        const period = x?.salary?.period ?? 'MONTH';
        if (min == null && max == null) return null;
        return { min, max, currency, period };
    }

    function toUiListItem(x) {
        return {
            id: x.id ?? x._id ?? x.externalId ?? null,
            url: x.url ?? x.applyUrl ?? null,
            title: x.title ?? '',
            company: x.companyName ?? x.company ?? null,
            city: x.cityName ?? x.city ?? null,
            country: x.country ?? null,
            datePosted: x.publishedAt ?? x.datePosted ?? null,
            keywords: Array.isArray(x.techTags) ? x.techTags
                : Array.isArray(x.keywords) ? x.keywords
                    : Array.isArray(x.techStack) ? x.techStack.map(s => s?.name).filter(Boolean)
                        : [],
            salary: toSalary(x),
            remote: !!x.remote,
            description: x.description ?? null
        };
    }

    function toUiDetail(x) {
        return {
            id: x.id ?? null,
            url: x.url ?? null,
            title: x.title ?? '',
            description: x.description ?? '',
            company: x.companyName ?? x.company ?? null,
            city: x.cityName ?? x.city ?? null,
            datePosted: x.publishedAt ?? x.datePosted ?? null,
            keywords: Array.isArray(x.techTags) ? x.techTags
                : Array.isArray(x.keywords) ? x.keywords
                    : Array.isArray(x.techStack) ? x.techStack.map(s => s?.name).filter(Boolean)
                        : [],
            salary: toSalary(x)
        };
    }

    // ---- RENDERING
    function groupKey(job) {
        return state.filters.group === 'city'
            ? (job.city || '— inne —')
            : (job.company || '— firma —');
    }

    function formatSalary(s) {
        if (!s) return '—';
        const min = s.min != null ? s.min.toLocaleString('pl-PL') : '';
        const max = s.max != null ? s.max.toLocaleString('pl-PL') : '';
        const cur = s.currency || 'PLN';
        const per = (s.period || 'MONTH').toLowerCase();
        return `${min}${max ? ' – ' + max : ''} ${cur}/${per}`;
    }

    function escapeHtml(s) {
        return String(s ?? '').replace(/[&<>"']/g, m => (
            {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]
        ));
    }

    function renderCard(job) {
        const el = document.createElement('div');
        el.className = 'card';
        el.setAttribute('role', 'listitem');
        el.innerHTML = `
      <div>
        <div class="card__title">${escapeHtml(job.title || '')}</div>
        <div class="meta">
          <span>🏢 ${escapeHtml(job.company || '—')}</span>
          <span>📍 ${escapeHtml(job.city || '')}${job.country ? ' (' + escapeHtml(job.country) + ')' : ''}</span>
          ${job.datePosted ? `<span>🗓 ${new Date(job.datePosted).toLocaleDateString('pl-PL')}</span>` : ''}
        </div>
        <div class="badges">
          ${(job.keywords || []).slice(0, 6).map(k => `<span class="badge">${escapeHtml(k)}</span>`).join('')}
        </div>
      </div>
      <div style="display:flex; align-items:center; gap:10px">
        <div class="money">${formatSalary(job.salary)}</div>
        <button class="chip" data-open="1">Podgląd</button>
        ${job.url ? `<a class="chip" href="${job.url}" target="_blank" rel="noopener">Aplikuj ↗</a>` : ''}
      </div>
    `;
        el.querySelector('[data-open]')?.addEventListener('click', async () => {
            const detail = await fetchJobDetail(job.id);
            openModal(detail ?? job);
        });
        return el;
    }

    function renderList() {
        $list.innerHTML = '';
        if (!state.items.length) {
            $empty.classList.remove('hidden');
            $count.textContent = '0 ofert';
            $pager.innerHTML = '';
            return;
        }
        $empty.classList.add('hidden');

        // grupowanie
        const groups = new Map();
        for (const job of state.items) {
            const key = groupKey(job);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(job);
        }
        for (const [g, arr] of groups) {
            const head = document.createElement('div');
            head.className = 'group';
            head.textContent = `${g} • ${arr.length}`;
            $list.appendChild(head);
            for (const job of arr) $list.appendChild(renderCard(job));
        }

        const total = state.totalElements || state.items.length;
        $count.textContent = `${total.toLocaleString('pl-PL')} ofert • strona ${state.page}/${Math.max(state.totalPages, 1)}`;

        renderPager();
    }

    function renderPager() {
        if (!$pager) return;
        const total = state.totalPages;
        const cur = state.page;

        if (!total || total <= 1) { $pager.innerHTML = ''; return; }

        const btn = (label, page, disabled = false, current = false) => {
            const a = document.createElement('button');
            a.type = 'button';
            a.className = 'pager__btn';
            a.textContent = label;
            if (disabled) a.disabled = true;
            if (current) a.setAttribute('aria-current', 'page');
            a.addEventListener('click', () => gotoPage(page));
            return a;
        };

        $pager.innerHTML = '';

        // Prev
        $pager.appendChild(btn('«', Math.max(1, cur - 1), cur === 1));

        // okno numerków (5 stron wokół aktualnej)
        let start = Math.max(1, cur - 2);
        let end = Math.min(total, start + 4);
        start = Math.max(1, end - 4);

        if (start > 1) {
            $pager.appendChild(btn('1', 1, false, cur === 1));
            if (start > 2) $pager.appendChild(ellipsis());
        }
        for (let p = start; p <= end; p++) {
            $pager.appendChild(btn(String(p), p, false, p === cur));
        }
        if (end < total) {
            if (end < total - 1) $pager.appendChild(ellipsis());
            $pager.appendChild(btn(String(total), total, false, cur === total));
        }

        // Next
        $pager.appendChild(btn('»', Math.min(total, cur + 1), cur === total));

        function ellipsis() {
            const s = document.createElement('span');
            s.className = 'pager__dots';
            s.textContent = '…';
            return s;
        }
    }

    // ---- Paginacja / ładowanie strony
    async function gotoPage(page) {
        if (state.loading) return;
        state.loading = true;
        $loading.classList.remove('hidden');
        try {
            const { items, totalElements, totalPages } = await fetchJobs(page);
            state.page = page;
            state.items = items;
            state.totalElements = totalElements;
            state.totalPages = totalPages;
            renderList();
        } catch (err) {
            console.error(err);
        } finally {
            state.loading = false;
            $loading.classList.add('hidden');
        }
    }

    // ---- Modal
    function openModal(job) {
        if (!job) return;
        $modalBody.innerHTML = `
      <header style="padding:0 6px 8px">
        <h3>${escapeHtml(job.title || '')}</h3>
        <div class="meta" style="margin-top:4px">
          <span>🏢 ${escapeHtml(job.company || '—')}</span>
          <span>📍 ${escapeHtml(job.city || '')}</span>
          <span class="money">${formatSalary(job.salary)}</span>
        </div>
      </header>
      <section style="display:grid; grid-template-columns:1.3fr .7fr; gap:16px">
        <article>
          <h4>Opis</h4>
          <div class="muted">${escapeHtml((job.description || '').slice(0, 8000)).replace(/\n/g, '<br>')}</div>
        </article>
        <aside>
          <h4>Tagi</h4>
          <div class="badges">${(job.keywords || []).map(k => `<span class="badge">${escapeHtml(k)}</span>`).join('') || '—'}</div>
          <div style="margin-top:12px; display:flex; gap:10px; flex-wrap:wrap">
            ${job.url ? `<a class="chip" href="${job.url}" target="_blank" rel="noopener">Aplikuj ↗</a>` : ''}
          </div>
        </aside>
      </section>
    `;
        if (!$modal.open) $modal.showModal();
    }
    document.querySelector('.modal__x .icon-btn')?.addEventListener('click', () => $modal.close());

    // ---- Handlery UI
    let t;
    const deb = (fn) => { clearTimeout(t); t = setTimeout(fn, 250); };

    $q.addEventListener('input',      e => { state.filters.q = e.target.value.trim(); deb(()=>gotoPage(1)); });
    $city.addEventListener('input',   e => { state.filters.city = e.target.value.trim(); deb(()=>gotoPage(1)); });
    $seniority.addEventListener('change', e => { state.filters.seniority = e.target.value; gotoPage(1); });
    $contract.addEventListener('change',  e => { state.filters.contract  = e.target.value; gotoPage(1); });
    $minSalary.addEventListener('input',  e => { state.filters.minSalary = e.target.value; deb(()=>gotoPage(1)); });
    $sort.addEventListener('change',      e => { state.filters.sort      = e.target.value; gotoPage(1); });

    $withSalary.addEventListener('click', (e) => {
        togglePressed(e.currentTarget);
        state.filters.withSalary = e.currentTarget.getAttribute('aria-pressed') === 'true';
        gotoPage(1);
    });
    $remote.addEventListener('click', (e) => {
        togglePressed(e.currentTarget);
        state.filters.remote = e.currentTarget.getAttribute('aria-pressed') === 'true';
        gotoPage(1);
    });
    $group.addEventListener('click', (e) => {
        state.filters.group = state.filters.group === 'city' ? 'company' : 'city';
        e.currentTarget.textContent = 'Grupuj: ' + (state.filters.group === 'city' ? 'miasto' : 'firma');
        renderList(); // samo przegrupowanie
    });

    specChecks.forEach(chk => chk.addEventListener('change', onChipChange));
    techChecks.forEach(chk => chk.addEventListener('change', onChipChange));
    function onChipChange() {
        state.filters.specs = specChecks.filter(x => x.checked).map(x => x.value);
        state.filters.techs = techChecks.filter(x => x.checked).map(x => x.value);
        gotoPage(1);
    }

    $reset.addEventListener('click', () => {
        Object.assign(state.filters, {
            q:'', city:'', seniority:'', contract:'', minSalary:'',
            withSalary:false, remote:false, sort:'relevance', group:'city',
            specs:[], techs:[]
        });
        [$q,$city,$seniority,$contract,$minSalary,$sort].forEach(el => { if (!el) return; el.value = (el.tagName === 'SELECT') ? '' : ''; });
        $withSalary.setAttribute('aria-pressed', 'false');
        $remote.setAttribute('aria-pressed', 'false');
        $group.textContent = 'Grupuj: miasto';
        specChecks.forEach(x => x.checked = false);
        techChecks.forEach(x => x.checked = false);
        gotoPage(1);
    });

    // start
    gotoPage(1);

    // ---- helpers
    function byId(id){ return document.getElementById(id); }
    function togglePressed(btn){
        const v = btn.getAttribute('aria-pressed') === 'true';
        btn.setAttribute('aria-pressed', String(!v));
    }
}

// DEMO fallback
const DEMO_ITEMS = [
    { id:'1', url:'https://justjoin.it/', title:'Senior Java Developer', company:'Acme', city:'Warszawa', country:'PL',
        description:'Mikroserwisy, Spring Boot, Kafka, AWS.', salary:{currency:'PLN', min:22000, max:32000, period:'MONTH'},
        datePosted:'2025-09-29T10:00:00Z', keywords:['Java','Spring','Kafka','AWS'] },
    { id:'2', url:'https://justjoin.it/', title:'React Developer', company:'OneRail', city:'Kraków', country:'PL',
        description:'React, TypeScript, Vite, REST.', salary:{currency:'PLN', min:18000, max:25000, period:'MONTH'},
        datePosted:'2025-09-28T09:00:00Z', keywords:['React','TypeScript','Vite','REST'] },
    { id:'3', url:'https://justjoin.it/', title:'DevOps Engineer', company:'Cloudly', city:'Remote', country:'PL',
        description:'K8s, Terraform, GitHub Actions, Observability.', salary:{currency:'PLN', min:23000, max:30000, period:'MONTH'},
        datePosted:'2025-09-27T11:00:00Z', keywords:['Kubernetes','Terraform','CI/CD','Prometheus'] },
    { id:'4', url:'https://justjoin.it/', title:'QA Automation Engineer', company:'Testify', city:'Gdańsk', country:'PL',
        description:'Cypress, Playwright, API tests.', salary:{currency:'PLN', min:14000, max:20000, period:'MONTH'},
        datePosted:'2025-09-26T13:00:00Z', keywords:['Cypress','Playwright','QA','Automation'] },
    { id:'5', url:'https://justjoin.it/', title:'Python Backend Engineer', company:'DataForge', city:'Wrocław', country:'PL',
        description:'FastAPI, PostgreSQL, Kafka.', salary:{currency:'PLN', min:20000, max:27000, period:'MONTH'},
        datePosted:'2025-09-25T12:00:00Z', keywords:['Python','FastAPI','PostgreSQL','Kafka'] }
];
