export function initJobs(opts = {}) {
    const API_URL = opts.apiUrl ?? 'java/api/jobs';
    const PAGE_SIZE = 50;

    const state = {
        page: 1,
        items: [],
        total: 0,
        loading: false,
        end: false,
        filters: {
            q: '', city: '', seniority: '', contract: '',
            minSalary: '', withSalary: false, remote: false,
            sort: 'relevance', group: 'city',
            specs: [],
            techs: []
        }
    };

    const $list      = byId('list');
    const $count     = byId('count');
    const $loading   = byId('loading');
    const $empty     = byId('empty');
    const $sentinel  = byId('sentinel');
    const $modal     = byId('modal');
    const $modalBody = byId('modalBody');

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

    async function fetchJobs(page) {
        const p = new URLSearchParams({
            page, pageSize: PAGE_SIZE,
            q: state.filters.q,
            city: state.filters.city,
            seniority: state.filters.seniority,
            contract: state.filters.contract,
            salaryMin: state.filters.minSalary || '',
            sort: state.filters.sort
        });
        if (state.filters.withSalary) p.append('withSalary', '1');
        if (state.filters.remote)     p.append('remote', 'yes');

        state.filters.specs.forEach(v => p.append('spec', v));
        state.filters.techs .forEach(v => p.append('tech', v));

        try {
            const res = await fetch(`${API_URL}?${p.toString()}`, { headers: { Accept: 'application/json' } });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return await res.json();
        } catch (e) {
            console.warn('API niedostępne – używam danych DEMO.', e);
            const start = (page - 1) * PAGE_SIZE;
            const slice = DEMO_ITEMS.slice(start, start + PAGE_SIZE);
            return { items: slice, pagination: { hasNext: start + PAGE_SIZE < DEMO_ITEMS.length } };
        }
    }

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

    function escapeHtml(s){
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
        el.querySelector('[data-open]')?.addEventListener('click', () => openModal(job));
        return el;
    }

    function renderList() {
        $list.innerHTML = '';
        if (!state.items.length) {
            $empty.classList.remove('hidden');
            $count.textContent = '0 ofert';
            return;
        }
        $empty.classList.add('hidden');

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

        $count.textContent = `${state.items.length.toLocaleString('pl-PL')} ofert`;
    }

    const io = new IntersectionObserver(async (entries) => {
        for (const e of entries) {
            if (e.isIntersecting && !state.loading && !state.end) await loadNext();
        }
    });
    io.observe($sentinel);

    async function loadNext(reset = false) {
        try {
            state.loading = true;
            $loading.classList.remove('hidden');
            if (reset) { state.page = 1; state.items = []; state.end = false; }

            const data = await fetchJobs(state.page);
            const items = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : [];
            state.items.push(...items);
            state.page += 1;

            const hasNext = data?.pagination?.hasNext ?? (items.length >= PAGE_SIZE);
            if (!hasNext || items.length === 0) state.end = true;

            renderList();
        } catch (err) {
            console.error(err);
        } finally {
            state.loading = false;
            $loading.classList.add('hidden');
        }
    }

    function openModal(job) {
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
          <div class="muted">${escapeHtml((job.description || '').slice(0, 4000)).replace(/\n/g, '<br>')}</div>
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

    let t;
    const deb = (fn) => { clearTimeout(t); t = setTimeout(fn, 250); };

    $q.addEventListener('input',      e => { state.filters.q = e.target.value.trim(); deb(()=>loadNext(true)); });
    $city.addEventListener('input',   e => { state.filters.city = e.target.value.trim(); deb(()=>loadNext(true)); });
    $seniority.addEventListener('change', e => { state.filters.seniority = e.target.value; loadNext(true); });
    $contract.addEventListener('change',  e => { state.filters.contract  = e.target.value; loadNext(true); });
    $minSalary.addEventListener('input',  e => { state.filters.minSalary = e.target.value; deb(()=>loadNext(true)); });
    $sort.addEventListener('change',      e => { state.filters.sort      = e.target.value; loadNext(true); });

    $withSalary.addEventListener('click', (e) => {
        togglePressed(e.currentTarget);
        state.filters.withSalary = e.currentTarget.getAttribute('aria-pressed') === 'true';
        loadNext(true);
    });
    $remote.addEventListener('click', (e) => {
        togglePressed(e.currentTarget);
        state.filters.remote = e.currentTarget.getAttribute('aria-pressed') === 'true';
        loadNext(true);
    });
    $group.addEventListener('click', (e) => {
        state.filters.group = state.filters.group === 'city' ? 'company' : 'city';
        e.currentTarget.textContent = 'Grupuj: ' + (state.filters.group === 'city' ? 'miasto' : 'firma');
        loadNext(true);
    });

    specChecks.forEach(chk => chk.addEventListener('change', onChipChange));
    techChecks.forEach(chk => chk.addEventListener('change', onChipChange));

    function onChipChange() {
        state.filters.specs = specChecks.filter(x => x.checked).map(x => x.value);
        state.filters.techs = techChecks.filter(x => x.checked).map(x => x.value);
        loadNext(true);
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
        loadNext(true);
    });

    loadNext(true);

    function byId(id){ return document.getElementById(id); }
    function togglePressed(btn){
        const v = btn.getAttribute('aria-pressed') === 'true';
        btn.setAttribute('aria-pressed', String(!v));
    }
}

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
