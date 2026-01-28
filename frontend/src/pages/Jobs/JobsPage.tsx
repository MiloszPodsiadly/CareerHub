
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import styles from './JobsPage.module.css';
import { getAccess } from '../../shared/api';

type JobSalary = { min?: number | null; max?: number | null; currency?: string; period?: string };
type JobItem = {
    id: string | number | null;
    url?: string | null;
    title: string;
    company?: string | null;
    city?: string | null;
    country?: string | null;
    datePosted?: string | null;
    keywords?: string[];
    salary?: JobSalary | null;
    remote?: boolean;
    description?: string | null;
    contract?: string | null;
    contracts?: string[];
    level?: string | null;
};

type JobApiItem = Record<string, any>;

type FavStatus = { favorited: boolean; count: number };

type Filters = {
    q: string;
    city: string;
    seniority: string;
    contract: string;
    withSalary: boolean;
    remote: boolean;
    sort: string;
    group: 'city' | 'company';
    specs: string[];
    techs: string[];
};

const PAGE_SIZE = 50;

const LEVEL_MAP: Record<string, string> = {
    '': '',
    internship: 'INTERNSHIP',
    intern: 'INTERNSHIP',
    trainee: 'INTERNSHIP',
    junior: 'JUNIOR',
    jr: 'JUNIOR',
    mid: 'MID',
    regular: 'MID',
    middle: 'MID',
    senior: 'SENIOR',
    sr: 'SENIOR',
    lead: 'LEAD',
    INTERNSHIP: 'INTERNSHIP',
    JUNIOR: 'JUNIOR',
    MID: 'MID',
    SENIOR: 'SENIOR',
    LEAD: 'LEAD',
};

const CONTRACT_MAP: Record<string, string> = {
    '': '',
    UOP: 'UOP',
    B2B: 'B2B',
    UZ: 'UZ',
    UOD: 'UOD',
    uop: 'UOP',
    b2b: 'B2B',
    uz: 'UZ',
    uod: 'UOD',
    perm: 'UOP',
    permanent: 'UOP',
    mandate: 'UZ',
    'specific-task': 'UOD',
};

const SORT_MAP: Record<string, string> = {
    date: 'date',
    newest: 'date',
    salary: 'salary',
    relevance: 'date',
    '': 'date',
};

const SPEC_CHIPS = [
    { id: 'spec-frontend', value: 'Frontend', label: 'Frontend', icon: '/assets/component/jobsoffers/specialization/Frontend.svg' },
    { id: 'spec-backend', value: 'Backend', label: 'Backend', icon: '/assets/component/jobsoffers/specialization/Backend.svg' },
    { id: 'spec-fullstack', value: 'Fullstack', label: 'Fullstack', icon: '/assets/component/jobsoffers/specialization/Full-stack.svg' },
    { id: 'spec-mobile', value: 'Mobile', label: 'Mobile', icon: '/assets/component/jobsoffers/specialization/Mobile.svg' },
    { id: 'spec-devops', value: 'DevOps', label: 'DevOps', icon: '/assets/component/jobsoffers/specialization/DevOps.svg' },
    { id: 'spec-qa', value: 'QA', label: 'QA / Test', icon: '/assets/component/jobsoffers/specialization/QA-Test.svg' },
    { id: 'spec-data', value: 'Data', label: 'Data / BI', icon: '/assets/component/jobsoffers/specialization/Data-Bi.svg' },
    { id: 'spec-security', value: 'Security', label: 'Security', icon: '/assets/component/jobsoffers/specialization/Security.svg' },
    { id: 'spec-embedded', value: 'Embedded', label: 'Embedded', icon: '/assets/component/jobsoffers/specialization/Embedded.svg' },
    { id: 'spec-aiml', value: 'AI/ML', label: 'AI / ML', icon: '/assets/component/jobsoffers/specialization/AI-ML.svg' },
    { id: 'spec-others', value: 'Others', label: 'Others', icon: '/assets/component/jobsoffers/specialization/Others.svg' },
];

const TECH_CHIPS = [
    { id: 'tech-angular', value: 'Angular', label: 'Angular', icon: '/assets/component/jobsoffers/technology/Angular.svg' },
    { id: 'tech-js', value: 'JavaScript', label: 'JavaScript', icon: '/assets/component/jobsoffers/technology/JavaScript.svg' },
    { id: 'tech-ts', value: 'TypeScript', label: 'TypeScript', icon: '/assets/component/jobsoffers/technology/TypeScript.svg' },
    { id: 'tech-react', value: 'React', label: 'React', icon: '/assets/component/jobsoffers/technology/React.svg' },
    { id: 'tech-flutter', value: 'Flutter', label: 'Flutter', icon: '/assets/component/jobsoffers/technology/Flutter.svg' },
    { id: 'tech-java', value: 'Java', label: 'Java', icon: '/assets/component/jobsoffers/technology/Java.svg' },
    { id: 'tech-python', value: 'Python', label: 'Python', icon: '/assets/component/jobsoffers/technology/Python.svg' },
    { id: 'tech-csharp', value: 'C#', label: 'C#', icon: '/assets/component/jobsoffers/technology/C_Sharp.svg' },
    { id: 'tech-php', value: 'PHP', label: 'PHP', icon: '/assets/component/jobsoffers/technology/Php.svg' },
    { id: 'tech-sql', value: 'SQL', label: 'SQL', icon: '/assets/component/jobsoffers/technology/Sql.svg' },
    { id: 'tech-aws', value: 'AWS', label: 'AWS', icon: '/assets/component/jobsoffers/technology/AWS.svg' },
    { id: 'tech-k8s', value: 'Kubernetes', label: 'Kubernetes', icon: '/assets/component/jobsoffers/technology/Kubernetes.svg' },
];

function mapLevel(v: string) {
    return LEVEL_MAP[String(v ?? '').trim()] ?? '';
}

function mapContract(v: string) {
    return CONTRACT_MAP[String(v ?? '').trim()] ?? '';
}

function normalizeSort(v: string) {
    return SORT_MAP[String(v ?? '').toLowerCase()] ?? 'date';
}

function toSalary(x: JobApiItem): JobSalary | null {
    const min = x?.salaryNormMonthMin ?? x?.salaryMin ?? x?.salary?.min ?? null;
    const max = x?.salaryNormMonthMax ?? x?.salaryMax ?? x?.salary?.max ?? null;
    const currency = x?.currency ?? x?.salary?.currency ?? 'PLN';
    const period =
        x?.salaryNormMonthMin != null || x?.salaryNormMonthMax != null
            ? 'MONTH'
            : (x?.salary?.period ?? x?.salaryPeriod ?? 'MONTH');
    if (min == null && max == null) return null;
    return { min, max, currency, period };
}

function ensureContracts(x: JobApiItem): string[] {
    if (Array.isArray(x?.contracts)) return x.contracts.filter(Boolean);
    if (x?.contract) return [x.contract];
    return [];
}

function toUiListItem(x: JobApiItem): JobItem {
    return {
        id: x.id ?? x._id ?? x.externalId ?? null,
        url: x.url ?? x.applyUrl ?? null,
        title: x.title ?? '',
        company: x.companyName ?? x.company ?? null,
        city: x.cityName ?? x.city ?? null,
        country: x.country ?? null,
        datePosted: x.publishedAt ?? x.datePosted ?? null,
        keywords: Array.isArray(x.techTags)
            ? x.techTags
            : Array.isArray(x.keywords)
                ? x.keywords
                : Array.isArray(x.techStack)
                    ? x.techStack.map((s: any) => s?.name).filter(Boolean)
                    : [],
        salary: toSalary(x),
        remote: !!x.remote,
        description: x.description ?? null,
        contract: x.contract ?? null,
        contracts: ensureContracts(x),
        level: x.level ?? null,
    };
}

function toUiDetail(x: JobApiItem): JobItem {
    return {
        id: x.id ?? null,
        url: x.url ?? null,
        title: x.title ?? '',
        description: x.description ?? '',
        company: x.companyName ?? x.company ?? null,
        city: x.cityName ?? x.city ?? null,
        datePosted: x.publishedAt ?? x.datePosted ?? null,
        keywords: Array.isArray(x.techTags)
            ? x.techTags
            : Array.isArray(x.keywords)
                ? x.keywords
                : Array.isArray(x.techStack)
                    ? x.techStack.map((s: any) => s?.name).filter(Boolean)
                    : [],
        salary: toSalary(x),
        contract: x.contract ?? null,
        contracts: ensureContracts(x),
        level: x.level ?? null,
        remote: !!x.remote,
        country: x.country ?? null,
    };
}

function prettyContract(c?: string | null) {
    if (!c) return null;
    const up = String(c).toUpperCase();
    if (up === 'UOP') return 'UoP';
    if (up === 'UOD') return 'UoD';
    return up;
}

function prettyLevel(l?: string | null) {
    if (!l) return null;
    const m: Record<string, string> = { INTERNSHIP: 'Intern', JUNIOR: 'Junior', MID: 'Mid', SENIOR: 'Senior', LEAD: 'Lead' };
    return m[String(l).toUpperCase()] ?? l;
}

function escapeHtml(s: string) {
    return String(s ?? '').replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]!));
}

function mdSanitize(html: string) {
    const tpl = document.createElement('template');
    tpl.innerHTML = html || '';
    const ALLOWED = new Set(['p', 'br', 'strong', 'em', 'u', 'h2', 'h3', 'ul', 'ol', 'li', 'blockquote', 'a']);

    (function walk(node: ParentNode) {
        [...node.childNodes].forEach((ch) => {
            if (ch instanceof Element) {
                const tag = (ch as HTMLElement).tagName.toLowerCase();
                if (!ALLOWED.has(tag)) {
                    const frag = document.createDocumentFragment();
                    while (ch.firstChild) frag.appendChild(ch.firstChild);
                    ch.replaceWith(frag);
                    return;
                }
                if (tag === 'a') {
                    [...(ch as HTMLElement).attributes].forEach((a) => {
                        if (a.name.toLowerCase() !== 'href') (ch as HTMLElement).removeAttribute(a.name);
                    });
                    const href = (ch as HTMLElement).getAttribute('href') || '';
                    if (!/^https?:\/\//i.test(href)) (ch as HTMLElement).removeAttribute('href');
                    else {
                        (ch as HTMLElement).setAttribute('rel', 'noopener noreferrer');
                        (ch as HTMLElement).setAttribute('target', '_blank');
                    }
                } else {
                    [...(ch as HTMLElement).attributes].forEach((a) => (ch as HTMLElement).removeAttribute(a.name));
                }
                walk(ch);
            }
        });
    })(tpl.content);

    return tpl.innerHTML.replace(/<p>\s*<\/p>/g, '');
}

function mdToHtml(md: string) {
    if (!md) return '';
    let html = String(md).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    html = html
        .replace(/^\s*###\s+(.*)$/gm, '<h3>$1</h3>')
        .replace(/^\s*##\s+(.*)$/gm, '<h2>$1</h2>');
    html = html.replace(/(^|\n)>\s?(.*)/g, (m, lead, line) => `${lead}<blockquote>${line}</blockquote>`);
    html = html.replace(/(^|\n)[\*\-]\s+(.*)/g, (m, lead, item) => `${lead}<ul><li>${item}</li></ul>`);
    html = html
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/__(.*?)__/g, '<u>$1</u>');

    html = html
        .split(/\n{2,}/)
        .map((b) => {
            const t = b.trim();
            if (!t) return '';
            if (/^<h[23]|^<ul>|^<blockquote>/.test(t)) return t;
            return `<p>${t.replace(/\n/g, '<br>')}</p>`;
        })
        .join('')
        .replace(/<\/ul>\s*<ul>/g, '');

    return mdSanitize(html);
}

function platformRouteFromUrl(url: string | null | undefined, numericId: string | number | null | undefined) {
    if (!numericId && numericId !== 0) {
        return null;
    }

    if (url && /[?&]id=platform-/i.test(url)) {
        return `/jobexaclyoffer?id=${encodeURIComponent(String(numericId))}`;
    }

    if (url && url.startsWith('/jobexaclyoffer')) {
        return url;
    }

    try {
        if (!url) return null;
        const u = new URL(url, location.origin);
        const path = u.pathname.replace(/\/+$/, '');
        if (path === '/jobexaclyoffer') {
            return `/jobexaclyoffer?id=${encodeURIComponent(String(numericId))}`;
        }
    } catch {}

    return null;
}

function formatSalary(s?: JobSalary | null) {
    if (!s) return '';
    const min = s.min != null ? s.min.toLocaleString('en-GB') : '';
    const max = s.max != null ? s.max.toLocaleString('en-GB') : '';
    const cur = s.currency || 'PLN';
    const per = (s.period || 'MONTH').toLowerCase();
    return `${min}${max ? ' – ' + max : ''} ${cur}/${per}`;
}

function authHeaders(): Record<string, string> {
    try {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    } catch {
        return {};
    }
}

async function favStatus(type: 'JOB', id: JobItem['id']): Promise<FavStatus> {
    try {
        const r = await fetch(`/api/favorites/${type}/${id}/status`, {
            headers: { Accept: 'application/json', ...authHeaders() },
            credentials: 'include',
        });
        if (!r.ok) throw 0;
        return r.json();
    } catch {
        return { favorited: false, count: 0 };
    }
}

async function favToggle(type: 'JOB', id: JobItem['id']): Promise<FavStatus> {
    const r = await fetch(`/api/favorites/${type}/${id}/toggle`, {
        method: 'POST',
        headers: { Accept: 'application/json', ...authHeaders() },
        credentials: 'include',
    });
    if (r.status === 401 || r.status === 403) throw new Error('Please log in');
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

const DEFAULT_FILTERS: Filters = {
    q: '',
    city: '',
    seniority: '',
    contract: '',
    withSalary: false,
    remote: false,
    sort: 'date',
    group: 'city',
    specs: [],
    techs: [],
};

function FavButton({ id }: { id: JobItem['id'] }) {
    const [status, setStatus] = useState<FavStatus>({ favorited: false, count: 0 });
    const [disabled, setDisabled] = useState(false);

    const loggedIn = !!getAccess?.();

    useEffect(() => {
        if (!loggedIn || !id) return;
        favStatus('JOB', id).then(setStatus).catch(() => {});
    }, [id, loggedIn]);

    const onClick = async () => {
        if (!loggedIn || !id) return;
        setDisabled(true);
        try {
            const next = await favToggle('JOB', id);
            setStatus(next);
        } catch (err) {
            console.error('fav toggle failed', err);
        } finally {
            setDisabled(false);
        }
    };

    if (!loggedIn) return null;

    return (
        <button
            className={styles.fav}
            type="button"
            aria-pressed={status.favorited ? 'true' : 'false'}
            aria-label={status.favorited ? 'Remove from favourites' : 'Add to favourites'}
            onClick={onClick}
            disabled={disabled}
        >
            <svg className={styles['fav__icon']} viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                <path d="M8 14s-6-3.33-6-8a3.5 3.5 0 0 1 6-2.475A3.5 3.5 0 0 1 14 6c0 4.67-6 8-6 8z"></path>
            </svg>
            <span className={styles['fav__count']} aria-hidden="true">
                {status.count ?? 0}
            </span>
        </button>
    );
}

export default function JobsPage() {
    const [items, setItems] = useState<JobItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [loadedOnce, setLoadedOnce] = useState(false);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [page, setPage] = useState(1);
    const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS);

    const modalRef = useRef<HTMLDialogElement | null>(null);
    const modalBodyRef = useRef<HTMLElement | null>(null);
    const lastCtrl = useRef<AbortController | null>(null);
    const loadingRef = useRef(false);

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        const pageParam = parseInt(params.get('page') || '1', 10);
        if (Number.isFinite(pageParam) && pageParam > 0) setPage(pageParam);

        const next = { ...DEFAULT_FILTERS };
        const q = params.get('q');
        if (q) next.q = q;
        const city = params.get('city');
        if (city) next.city = city;
        const seniority = params.get('seniority');
        if (seniority) next.seniority = seniority;
        const contract = params.get('contract');
        if (contract) next.contract = contract;
        const sort = params.get('sort');
        if (sort) next.sort = normalizeSort(sort);
        if (params.get('withSalary') === 'true') next.withSalary = true;
        if (params.get('remote') === 'true') next.remote = true;
        const specs = params.getAll('spec');
        if (specs?.length) next.specs = specs;
        const techs = params.getAll('tech');
        if (techs?.length) next.techs = techs;
        setFilters(next);
    }, []);

    const grouped = useMemo(() => {
        const groups = new Map<string, JobItem[]>();
        for (const job of items) {
            const key = filters.group === 'city' ? job.city || 'Other' : job.company || 'Company';
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key)!.push(job);
        }
        return groups;
    }, [items, filters.group]);

    const fetchJobs = useCallback(async (nextPage: number) => {
        if (lastCtrl.current) lastCtrl.current.abort();
        lastCtrl.current = new AbortController();

        const p = new URLSearchParams();
        p.append('page', String(nextPage));
        p.append('pageSize', String(PAGE_SIZE));
        if (filters.q) p.append('q', filters.q);
        if (filters.city) p.append('city', filters.city);
        const lvl = mapLevel(filters.seniority);
        if (lvl) p.append('level', lvl);
        const normContract = mapContract(filters.contract);
        if (normContract) p.append('contract', normContract);
        if (filters.withSalary) p.append('withSalary', 'true');
        if (filters.remote) p.append('remote', 'true');
        p.append('sort', normalizeSort(filters.sort));
        filters.specs.forEach((v) => p.append('spec', v));
        filters.techs.forEach((v) => p.append('tech', v));

        const res = await fetch(`/api/jobs?${p.toString()}`, {
            headers: { Accept: 'application/json' },
            signal: lastCtrl.current.signal,
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const json = await res.json();

        let rawItems: JobApiItem[] = [],
            total = 0,
            pages = 0;

        if (Array.isArray(json?.content)) {
            rawItems = json.content;
            total = json.totalElements ?? rawItems.length;
            pages = json.totalPages ?? Math.ceil(total / PAGE_SIZE);
        } else if (Array.isArray(json?.items)) {
            rawItems = json.items;
            total = json.total ?? rawItems.length;
            pages = Math.ceil(total / PAGE_SIZE);
        } else if (Array.isArray(json)) {
            rawItems = json;
            total = json.length;
            pages = Math.ceil(total / PAGE_SIZE);
        }

        return { items: rawItems.map(toUiListItem), total, pages };
    }, [filters]);

    const fetchJobDetail = async (id: JobItem['id']) => {
        const res = await fetch(`/api/jobs/${id}`, { headers: { Accept: 'application/json' } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return toUiDetail(await res.json());
    };

    const refresh = useCallback(async (nextPage: number, { scroll = true } = {}) => {
        if (loadingRef.current) return;
        loadingRef.current = true;
        setLoading(true);
        setError(null);
        try {
            const res = await fetchJobs(nextPage);
            setPage(nextPage);
            setItems(res.items);
            setTotalElements(res.total);
            setTotalPages(res.pages);
            setLoadedOnce(true);

            if (scroll) document.getElementById('list')?.scrollIntoView({ behavior: 'smooth', block: 'start' });

            const params = new URLSearchParams();
            params.set('page', String(nextPage));
            Object.entries(filters).forEach(([k, v]) => {
                if (!v || (Array.isArray(v) && !v.length)) return;
                if (k === 'sort') {
                    params.set('sort', normalizeSort(String(v)));
                    return;
                }
                if (Array.isArray(v)) v.forEach((x) => params.append(k, String(x)));
                else params.set(k, String(v));
            });
            history.replaceState(null, '', `?${params.toString()}`);
        } catch (err: any) {
            const isAbort = err?.name === 'AbortError' || /aborted/i.test(err?.message || '');
            if (isAbort) return;
            setItems([]);
            setTotalElements(0);
            setTotalPages(0);
            setError(err?.message || 'Failed to load jobs.');
        } finally {
            setLoading(false);
            loadingRef.current = false;
        }
    }, [fetchJobs, filters]);

    useEffect(() => {
        const q = filters.q;
        const city = filters.city;
        const t = setTimeout(() => refresh(page, { scroll: false }), q || city ? 300 : 0);
        return () => clearTimeout(t);
    }, [filters, page, refresh]);

    const openModal = async (job: JobItem) => {
        try {
            const detail = await fetchJobDetail(job.id);
            const j = detail ?? job;
            const contracts = j.contracts && j.contracts.length ? j.contracts : j.contract ? [j.contract] : [];
            const contractsBadges = contracts
                .map((c) => `<span class="${styles.badge}">${escapeHtml(prettyContract(c) || '')}</span>`)
                .join('');
            const levelBadge = j.level ? `<span class="${styles.badge}">${escapeHtml(prettyLevel(j.level) || '')}</span>` : '';
            const remoteBadge = j.remote ? `<span class="${styles.badge}">Remote</span>` : '';

            const internal = platformRouteFromUrl(j.url ?? null, j.id);
            const applyCta = internal
                ? `<a class="${styles.chip} ${styles['chip--apply']}" href="${internal}">Apply ↗</a>`
                : j.url
                    ? `<a class="${styles.chip} ${styles['chip--apply']}" href="${j.url}" target="_blank" rel="noopener">Apply ↗</a>`
                    : '';

            if (modalBodyRef.current) {
                modalBodyRef.current.innerHTML = `
      <header class="${styles['modal__header']}">
        <h3>${escapeHtml(j.title || '')}</h3>
        <div class="${styles['modal__meta']}">
          <span>🏢 ${escapeHtml(j.company || '—')}</span>
          <span>📍 ${escapeHtml(j.city || '')}${j.country ? ' (' + escapeHtml(j.country) + ')' : ''}</span>
          ${contractsBadges}${levelBadge}${remoteBadge}
          <span class="${styles.money}">${escapeHtml(formatSalary(j.salary))}</span>
        </div>
      </header>
      <section class="${styles['modal__grid']}">
        <article>
          <div class="${styles['job-desc']}">
            <h4>Description</h4>
            <div class="${styles.prose}">${mdToHtml(j.description || '')}</div>
          </div>
        </article>
        <aside class="${styles.aside}">
          <h4>Tags</h4>
          <div class="${styles.badges}">${(j.keywords || []).map((k) => `<span class="${styles.badge}">${escapeHtml(k)}</span>`).join('') || '—'}</div>
          <div class="cta">${applyCta}</div>
        </aside>
      </section>`;
            }

            if (modalRef.current && !modalRef.current.open) modalRef.current.showModal();
        } catch (err) {
            console.error('Failed to load job detail', err);
        }
    };

    const togglePressed = (key: 'withSalary' | 'remote') => {
        setFilters((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    const onReset = () => {
        setFilters(DEFAULT_FILTERS);
        setPage(1);
    };

    const onSpecToggle = (value: string) => {
        setFilters((prev) => {
            const set = new Set(prev.specs);
            if (set.has(value)) set.delete(value);
            else set.add(value);
            return { ...prev, specs: [...set] };
        });
    };

    const onTechToggle = (value: string) => {
        setFilters((prev) => {
            const set = new Set(prev.techs);
            if (set.has(value)) set.delete(value);
            else set.add(value);
            return { ...prev, techs: [...set] };
        });
    };

    const pager = useMemo(() => {
        if (!totalPages || totalPages <= 1) return [] as Array<number | 'dots'>;
        const cur = page;
        const nums: Array<number | 'dots'> = [];
        const start = Math.max(1, cur - 2);
        const end = Math.min(totalPages, start + 4);
        const s = Math.max(1, end - 4);

        nums.push(1);
        if (s > 2) nums.push('dots');
        for (let p = s; p <= end; p++) {
            if (p === 1 || p === totalPages) continue;
            nums.push(p);
        }
        if (end < totalPages - 1) nums.push('dots');
        if (totalPages > 1) nums.push(totalPages);
        return nums;
    }, [page, totalPages]);

    return (
        <section className={styles.jobs}>
            <section className={styles.toolbar} aria-label="Primary filters">
                <div className={styles['toolbar__row']}>
                    <input
                        id="q"
                        className={styles.inp}
                        placeholder="Search: title, company, tech…"
                        autoComplete="off"
                        value={filters.q}
                        onChange={(e) => setFilters((prev) => ({ ...prev, q: e.target.value.trim() }))}
                    />
                    <input
                        id="city"
                        className={styles.inp}
                        placeholder="City (e.g. Warsaw)"
                        autoComplete="off"
                        value={filters.city}
                        onChange={(e) => setFilters((prev) => ({ ...prev, city: e.target.value.trim() }))}
                    />

                    <select
                        id="seniority"
                        className={styles.inp}
                        aria-label="Seniority"
                        value={filters.seniority}
                        onChange={(e) => setFilters((prev) => ({ ...prev, seniority: e.target.value }))}
                    >
                        <option value="">Level</option>
                        <option value="INTERNSHIP">intern</option>
                        <option value="JUNIOR">junior</option>
                        <option value="MID">mid</option>
                        <option value="SENIOR">senior</option>
                        <option value="LEAD">lead</option>
                    </select>

                    <select
                        id="contract"
                        className={styles.inp}
                        aria-label="Contract type"
                        value={filters.contract}
                        onChange={(e) => setFilters((prev) => ({ ...prev, contract: e.target.value }))}
                    >
                        <option value="">Contract</option>
                        <option value="UOP">UoP</option>
                        <option value="B2B">B2B</option>
                        <option value="UZ">Mandate (UZ)</option>
                        <option value="UOD">Specific-task (UoD)</option>
                    </select>

                    <select
                        id="sort"
                        className={styles.inp}
                        aria-label="Sorting"
                        value={filters.sort}
                        onChange={(e) => setFilters((prev) => ({ ...prev, sort: e.target.value }))}
                    >
                        <option value="date">Newest</option>
                        <option value="salary">Highest salaries</option>
                    </select>

                    <button
                        id="group"
                        type="button"
                        className={styles.btn}
                        aria-pressed={filters.group === 'company'}
                        title="Toggle grouping"
                        onClick={() =>
                            setFilters((prev) => ({ ...prev, group: prev.group === 'city' ? 'company' : 'city' }))
                        }
                    >
                        Group by: {filters.group === 'company' ? 'company' : 'city'}
                    </button>
                </div>

                <div className={styles['toolbar__meta']}>
                    <span id="count" aria-live="polite">
                        {totalElements.toLocaleString('en-GB')} jobs • page {page}/{Math.max(totalPages, 1)}
                    </span>
                    <span className={styles.muted} aria-hidden="true">
                        •
                    </span>
                    <button
                        id="withSalary"
                        type="button"
                        className={styles.chip}
                        aria-pressed={filters.withSalary}
                        onClick={() => togglePressed('withSalary')}
                    >
                        Only with salary ranges
                    </button>
                    <button
                        id="remote"
                        type="button"
                        className={styles.chip}
                        aria-pressed={filters.remote}
                        onClick={() => togglePressed('remote')}
                    >
                        Remote only
                    </button>
                    <button id="reset" type="button" className={`${styles.chip} ${styles.danger}`} onClick={onReset}>
                        Reset filters
                    </button>
                </div>
            </section>

            <section className={styles.filters} aria-labelledby="filters-title">
                <header className={styles['filters__header']}>
                    <h2 id="filters-title">Quick filters</h2>
                    <p className={styles['filters__hint']}>Select your specializations and technologies, then refine above.</p>
                </header>

                <div className={styles['filters__grid']}>
                    <fieldset className={styles.fg} aria-labelledby="fg-spec">
                        <legend id="fg-spec" className={styles['fg__title']}>
                            Specializations
                        </legend>
                        <div className={styles.chips}>
                            {SPEC_CHIPS.map((chip) => (
                                <div key={chip.id}>
                                    <input
                                        id={chip.id}
                                        className={styles.chipcheck}
                                        type="checkbox"
                                        name="spec[]"
                                        value={chip.value}
                                        checked={filters.specs.includes(chip.value)}
                                        onChange={() => onSpecToggle(chip.value)}
                                    />
                                    <label htmlFor={chip.id} className={`${styles.chip} ${styles['chip--svg']}`}>
                                        <span className={styles.i}>
                                            <img src={chip.icon} alt="" loading="lazy" decoding="async" />
                                        </span>
                                        {chip.label}
                                    </label>
                                </div>
                            ))}
                        </div>
                    </fieldset>

                    <fieldset className={styles.fg} aria-labelledby="fg-tech">
                        <legend id="fg-tech" className={styles['fg__title']}>
                            Technologies
                        </legend>
                        <div className={styles.chips}>
                            {TECH_CHIPS.map((chip) => (
                                <div key={chip.id}>
                                    <input
                                        id={chip.id}
                                        className={styles.chipcheck}
                                        type="checkbox"
                                        name="tech[]"
                                        value={chip.value}
                                        checked={filters.techs.includes(chip.value)}
                                        onChange={() => onTechToggle(chip.value)}
                                    />
                                    <label htmlFor={chip.id} className={`${styles.chip} ${styles['chip--svg']}`}>
                                        <span className={styles.i}>
                                            <img src={chip.icon} alt="" loading="lazy" decoding="async" />
                                        </span>
                                        {chip.label}
                                    </label>
                                </div>
                            ))}
                        </div>
                    </fieldset>
                </div>
            </section>

            <section className={styles.results} aria-live="polite">
                <div id="list" className={styles.list} role="list">
                    {[...grouped.entries()].map(([group, jobs]) => (
                        <div key={group}>
                            <div className={styles.group}>
                                <span className={styles['group__name']}>{group}</span>
                                <span className={styles['group__count']}>{jobs.length}</span>
                            </div>
                            <div className={styles.grid}>
                                {jobs.map((job) => {
                                    const contracts = job.contracts && job.contracts.length ? job.contracts : job.contract ? [job.contract] : [];
                                    const contractsBadges = contracts.map((c) => (
                                        <span key={c} className={styles.badge}>
                                            {prettyContract(c)}
                                        </span>
                                    ));
                                    const levelBadge = job.level ? (
                                        <span className={styles.badge}>{prettyLevel(job.level)}</span>
                                    ) : null;
                                    const remoteBadge = job.remote ? <span className={styles.badge}>Remote</span> : null;

                                    const internal = platformRouteFromUrl(job.url ?? null, job.id);
                                    const applyBtn = internal ? (
                                        <Link className={styles.chip} to={internal}>
                                            Apply ↗
                                        </Link>
                                    ) : job.url ? (
                                        <a className={styles.chip} href={job.url} target="_blank" rel="noopener">
                                            Apply ↗
                                        </a>
                                    ) : null;

                                    return (
                                        <div key={String(job.id)} className={styles.card} role="listitem">
                                            <div className={styles['card__title']}>{job.title}</div>
                                            <div className={styles.meta}>
                                                <span>🏢 {job.company || '—'}</span>
                                                <span>
                                                    📍 {job.city || ''}{job.country ? ` (${job.country})` : ''}
                                                </span>
                                                {job.datePosted ? (
                                                    <span>🗓 {new Date(job.datePosted).toLocaleDateString('en-GB')}</span>
                                                ) : null}
                                            </div>
                                            <div className={styles.badges}>
                                                {contractsBadges}
                                                {levelBadge}
                                                {remoteBadge}
                                                {(job.keywords || []).slice(0, 6).map((k) => (
                                                    <span key={k} className={styles.badge}>
                                                        {k}
                                                    </span>
                                                ))}
                                            </div>
                                            <div className={styles.actions}>
                                                <div className={styles.money}>{formatSalary(job.salary)}</div>
                                                <div className={styles['actions__btns']}>
                                                    <FavButton id={job.id} />
                                                    <button className={styles.chip} onClick={() => openModal(job)}>
                                                        Preview
                                                    </button>
                                                    {applyBtn}
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    ))}
                </div>
                {loading && (
                    <div id="loading" className={styles.loading}>
                        Loading…
                    </div>
                )}
                {!loading && error && (
                    <div id="empty" className={styles.empty}>
                        {error}
                    </div>
                )}
                {!loading && !error && loadedOnce && !items.length && (
                    <div id="empty" className={styles.empty}>
                        No results for these filters.
                    </div>
                )}
                <nav id="pager" className={styles.pager} aria-label="Pagination">
                    {totalPages > 1 ? (
                        <>
                            <button
                                className={styles['pager__btn']}
                                onClick={() => setPage(Math.max(1, page - 1))}
                                disabled={page === 1}
                            >
                                «
                            </button>
                            {pager.map((p, idx) =>
                                p === 'dots' ? (
                                    <span key={`dots-${idx}`} className={styles['pager__dots']}>
                                        …
                                    </span>
                                ) : (
                                    <button
                                        key={p}
                                        className={styles['pager__btn']}
                                        aria-current={p === page ? 'page' : undefined}
                                        onClick={() => setPage(p)}
                                    >
                                        {p}
                                    </button>
                                ),
                            )}
                            <button
                                className={styles['pager__btn']}
                                onClick={() => setPage(Math.min(totalPages, page + 1))}
                                disabled={page === totalPages}
                            >
                                »
                            </button>
                        </>
                    ) : null}
                </nav>
            </section>

            <dialog id="modal" className={styles.modal} ref={modalRef}>
                <form method="dialog" className={styles['modal__x']}>
                    <button className={styles['icon-btn']} type="submit" aria-label="Close">
                        ✕
                    </button>
                </form>
                <article id="modalBody" className={styles['modal__body']} ref={modalBodyRef}></article>
            </dialog>
        </section>
    );
}
