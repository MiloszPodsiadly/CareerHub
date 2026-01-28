import { useCallback, useEffect, useMemo, useState } from 'react';

import styles from './EventsPage.module.css';
import { getAccess } from '../../shared/api';

type EventItem = {
    id: string | number | null;
    title: string;
    url: string;
    city?: string | null;
    country?: string | null;
    date: string;
    categories: string[];
};

type FavStatus = { favorited: boolean; count: number };

const PAGE_SIZE = 25;

const ICONS: Record<string, string> = {
    javascript: '/assets/component/jobsoffers/technology/JavaScript.svg',
    typescript: '/assets/component/jobsoffers/technology/TypeScript.svg',
    react: '/assets/component/jobsoffers/technology/React.svg',
    java: '/assets/component/jobsoffers/technology/Java.svg',
    python: '/assets/component/jobsoffers/technology/Python.svg',
    'c#': '/assets/component/jobsoffers/technology/C_Sharp.svg',
    php: '/assets/component/jobsoffers/technology/Php.svg',
    aws: '/assets/component/jobsoffers/technology/AWS.svg',
    kubernetes: '/assets/component/jobsoffers/technology/Kubernetes.svg',
    devops: '/assets/component/jobsoffers/specialization/DevOps.svg',
    data: '/assets/component/jobsoffers/specialization/Data-Bi.svg',
    'ai/ml': '/assets/component/jobsoffers/specialization/AI-ML.svg',
};

const TECHS = ['javascript', 'typescript', 'react', 'java', 'python', 'c#', 'php', 'aws', 'kubernetes', 'devops', 'data', 'ai/ml'];

const ISO2 = 'AF,AX,AL,DZ,AS,AD,AO,AI,AQ,AG,AR,AM,AW,AU,AT,AZ,BS,BH,BD,BB,BY,BE,BZ,BJ,BM,BT,BO,BQ,BA,BW,BV,BR,IO,BN,BG,BF,BI,KH,CM,CA,CV,KY,CF,TD,CL,CN,CX,CC,CO,KM,CG,CD,CK,CR,CI,HR,CU,CW,CY,CZ,DK,DJ,DM,DO,EC,EG,SV,GQ,ER,EE,SZ,ET,FK,FO,FJ,FI,FR,GF,PF,TF,GA,GM,GE,DE,GH,GI,GR,GL,GD,GP,GU,GT,GG,GN,GW,GY,HT,HM,VA,HN,HK,HU,IS,IN,ID,IR,IQ,IE,IM,IL,IT,JM,JP,JE,JO,KZ,KE,KI,KP,KR,KW,KG,LA,LV,LB,LS,LR,LY,LI,LT,LU,MO,MG,MW,MY,MV,ML,MT,MH,MQ,MR,MU,YT,MX,FM,MD,MC,MN,ME,MS,MA,MZ,MM,NA,NR,NP,NL,NC,NZ,NI,NE,NG,NU,NF,MK,MP,NO,OM,PK,PW,PS,PA,PG,PY,PE,PH,PN,PL,PT,PR,QA,RE,RO,RU,RW,BL,SH,KN,LC,MF,PM,VC,WS,SM,ST,SA,SN,RS,SC,SL,SG,SX,SK,SI,SB,SO,ZA,GS,SS,ES,LK,SD,SR,SJ,SE,CH,SY,TW,TJ,TZ,TH,TL,TG,TK,TO,TT,TN,TR,TM,TC,TV,UG,UA,AE,GB,US,UM,UY,UZ,VU,VE,VN,VG,VI,WF,EH,YE,ZM,ZW'.split(',');

const REGION =
    typeof Intl !== 'undefined' && 'DisplayNames' in Intl
        ? new Intl.DisplayNames(['en'], { type: 'region' })
        : { of: (code: string) => code };

const countriesBase = [
    { code: '', name: 'All countries' },
    { code: 'ZZ-ONLINE', name: 'Online' },
];

function iconFor(t: string) {
    return t ? ICONS[String(t).toLowerCase()] || null : null;
}

function displayTechLabel(t: string) {
    const key = String(t || '').toLowerCase();
    const map: Record<string, string> = {
        javascript: 'JavaScript',
        typescript: 'TypeScript',
        react: 'React',
        java: 'Java',
        python: 'Python',
        'c#': 'C#',
        php: 'PHP',
        aws: 'AWS',
        kubernetes: 'Kubernetes',
        devops: 'DevOps',
        data: 'Data',
        'ai/ml': 'AI/ML',
    };
    return map[key] || (key ? key.charAt(0).toUpperCase() + key.slice(1) : '');
}

function authHeaders(): Record<string, string> {
    try {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    } catch {
        return {};
    }
}

async function favStatus(type: 'EVENT', id: EventItem['id']): Promise<FavStatus> {
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

async function favToggle(type: 'EVENT', id: EventItem['id']): Promise<FavStatus> {
    const r = await fetch(`/api/favorites/${type}/${id}/toggle`, {
        method: 'POST',
        headers: { Accept: 'application/json', ...authHeaders() },
        credentials: 'include',
    });
    if (r.status === 401 || r.status === 403) throw new Error('Please log in');
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

function FavButton({ id }: { id: EventItem['id'] }) {
    const [status, setStatus] = useState<FavStatus>({ favorited: false, count: 0 });
    const [disabled, setDisabled] = useState(false);
    const loggedIn = !!getAccess?.();

    useEffect(() => {
        if (!loggedIn || !id) return;
        favStatus('EVENT', id).then(setStatus).catch(() => {});
    }, [id, loggedIn]);

    const onClick = async () => {
        if (!loggedIn || !id) return;
        setDisabled(true);
        try {
            const next = await favToggle('EVENT', id);
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

export default function EventsPage() {
    const [q, setQ] = useState('');
    const [country, setCountry] = useState('');
    const [countrySearch, setCountrySearch] = useState('');
    const [city, setCity] = useState('');
    const [tech, setTech] = useState('');
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [page, setPage] = useState(0);
    const [total, setTotal] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [items, setItems] = useState<EventItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const countries = useMemo(() => {
        const list = ISO2.map((code) => ({ code, name: REGION.of(code) || code }));
        return countriesBase.concat(list).sort((a, b) => a.name.localeCompare(b.name));
    }, []);

    const filteredCountries = useMemo(() => {
        const qv = countrySearch.trim().toLowerCase();
        if (!qv) return countries;
        const base = countries.filter((c, i) => i <= 1 || c.name.toLowerCase().includes(qv));
        if (country && !base.find((c) => c.code === country)) {
            const current = countries.find((c) => c.code === country);
            return current ? [current, ...base] : base;
        }
        return base;
    }, [countries, countrySearch, country]);

    useEffect(() => {
        if (!from) setFrom(new Date().toISOString().slice(0, 10));
    }, [from]);

    useEffect(() => {
        if (!country || country === 'ZZ-ONLINE') {
            setCity('');
        }
    }, [country]);

    useEffect(() => {
        setPage(0);
    }, [q, country, city, tech, from, to]);

    const fetchEvents = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            const p = new URLSearchParams({
                q: q.trim(),
                city: city.trim(),
                tag: tech || '',
                page: String(page),
                size: String(PAGE_SIZE),
                sort: 'startAt,asc',
            });

            if (country && country !== 'ZZ-ONLINE') {
                p.set('country', REGION.of(country) || country);
            }

            if (country === 'ZZ-ONLINE') p.set('online', 'true');
            if (from) p.set('from', `${from}T00:00:00Z`);
            if (to) p.set('to', `${to}T23:59:59Z`);

            [...p.keys()].forEach((k) => {
                if (!p.get(k)) p.delete(k);
            });

            const url = `/api/events?${p.toString()}`;
            const r = await fetch(url, { headers: { Accept: 'application/json' } });
            if (!r.ok) throw new Error(`HTTP ${r.status}`);
            const json = await r.json();

            const totalElements = json.totalElements ?? 0;
            const pages = json.totalPages ?? Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
            const content = Array.isArray(json.content) ? json.content : [];

            const mapped = content.map((e: any) => {
                const d = e.startAt ? new Date(e.startAt) : null;
                return {
                    id: e.id ?? e._id ?? e.externalId ?? null,
                    title: e.title,
                    url: e.url,
                    city: e.city,
                    country: e.country || e.countryCode,
                    date: d ? d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : 'TBA',
                    categories: e.categories || [],
                } as EventItem;
            });

            setItems(mapped);
            setTotal(totalElements);
            setTotalPages(pages);
        } catch (err: any) {
            setItems([]);
            setTotal(0);
            setTotalPages(1);
            setError(err?.message || 'Failed to load events.');
        } finally {
            setLoading(false);
        }
    }, [q, country, city, tech, from, to, page]);

    useEffect(() => {
        const t = setTimeout(fetchEvents, q ? 300 : 0);
        return () => clearTimeout(t);
    }, [q, fetchEvents]);

    const pageNumbers = useMemo(() => {
        const windowSize = 2;
        const current = page;
        const totalPagesCount = totalPages;
        const nums: Array<number | 'dots'> = [];

        if (totalPagesCount <= 1) return nums;

        nums.push(0);
        let start = Math.max(1, current - windowSize);
        let end = Math.min(totalPagesCount - 2, current + windowSize);
        if (start > 1) nums.push('dots');
        for (let i = start; i <= end; i++) nums.push(i);
        if (end < totalPagesCount - 2) nums.push('dots');
        if (totalPagesCount > 1) nums.push(totalPagesCount - 1);
        return nums;
    }, [page, totalPages]);

    const countryDisabled = !country || country === 'ZZ-ONLINE';

    return (
        <section className={styles.events}>
            <section className={styles.toolbar} aria-label="Events filters">
                <div className={styles['toolbar__row']}>
                    <input
                        id="q"
                        className={styles.inp}
                        placeholder="Search (e.g. javascript, AI, cloud…)"
                        value={q}
                        onChange={(e) => setQ(e.target.value)}
                    />

                    <div className={styles.countryGrid}>
                        <input
                            id="country-search"
                            className={styles.inp}
                            placeholder="Search country…"
                            aria-label="Search country"
                            value={countrySearch}
                            onChange={(e) => setCountrySearch(e.target.value)}
                        />
                        <select
                            id="country"
                            className={styles.inp}
                            aria-label="Country"
                            value={country}
                            onChange={(e) => setCountry(e.target.value)}
                        >
                            {filteredCountries.map((c) => (
                                <option key={c.code || 'all'} value={c.code}>
                                    {c.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <input
                        id="city"
                        className={styles.inp}
                        placeholder="City"
                        disabled={countryDisabled}
                        value={city}
                        onChange={(e) => setCity(e.target.value)}
                    />

                    <input id="from" className={styles.inp} type="date" aria-label="From date" value={from} onChange={(e) => setFrom(e.target.value)} />
                    <input id="to" className={styles.inp} type="date" aria-label="To date" value={to} onChange={(e) => setTo(e.target.value)} />

                    <button id="btn-reset" className={styles.btn} onClick={() => {
                        setQ('');
                        setCountry('');
                        setCity('');
                        setTech('');
                        setFrom(new Date().toISOString().slice(0, 10));
                        setTo('');
                        setPage(0);
                    }}>
                        Reset
                    </button>
                </div>
                <div className={styles.meta} id="meta">
                    {total} results
                </div>
            </section>

            <section className={styles.filters}>
                <div className={`${styles['filters__grid']} ${styles['filters__grid--single']}`}>
                    <div className={styles.fg}>
                        <h3>Technologies</h3>
                        <div className={styles.chips} id="chips-tech">
                            {TECHS.map((t) => {
                                const icon = iconFor(t);
                                const active = tech === t;
                                return (
                                    <button
                                        key={t}
                                        className={styles.chip}
                                        data-tech={t}
                                        aria-pressed={active ? 'true' : 'false'}
                                        onClick={() => setTech(active ? '' : t)}
                                    >
                                        {icon ? <img className={styles.chip__icon} src={icon} alt="" loading="lazy" decoding="async" /> : null}
                                        {displayTechLabel(t)}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                </div>
            </section>

            <section className={styles.results}>
                <div className={styles.list}>
                    {loading && <div id="status" className={styles.loading}>Loading…</div>}
                    {!loading && error && <div id="status" className={styles.error}>{error}</div>}
                    {!loading && !error && items.length === 0 && <div id="status" className={styles.empty}>No events match your filters.</div>}
                    <div id="grid" className={styles.grid} hidden={loading || !!error || items.length === 0}>
                        {items.map((ev) => (
                            <article key={String(ev.id)} className={styles.card}>
                                <div className={styles.title}>{ev.title || ''}</div>
                                <div className={styles.where}>
                                    {ev.city ? `${ev.city}, ` : ''}{ev.country || '—'} • {ev.date}
                                </div>
                                <div className={styles.tags} style={!ev.categories?.length ? { display: 'none' } : undefined}>
                                    {(ev.categories || []).map((tag) => {
                                        const icon = iconFor(tag);
                                        return (
                                            <span key={tag} className={styles.tag}>
                                                {icon ? <img className={styles.tag__icon} src={icon} alt="" loading="lazy" decoding="async" /> : null}
                                                {tag}
                                            </span>
                                        );
                                    })}
                                </div>
                                <div className={styles.cardActions}>
                                    <a className={styles.btn} href={ev.url} target="_blank" rel="noreferrer">
                                        Details
                                    </a>
                                    <FavButton id={ev.id} />
                                </div>
                            </article>
                        ))}
                    </div>
                </div>

                <div className={styles.pagination}>
                    <button id="btn-prev" className={styles.btn} disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                        Previous
                    </button>
                    {pageNumbers.map((n, idx) =>
                        n === 'dots' ? (
                            <span key={`dots-${idx}`} className={styles.pagination__ellipsis}>…</span>
                        ) : (
                            <button
                                key={n}
                                className={`${styles.pagination__num} ${n === page ? styles['is-current'] : ''}`}
                                onClick={() => setPage(n)}
                                aria-current={n === page ? 'page' : undefined}
                                disabled={n === page}
                            >
                                {n + 1}
                            </button>
                        ),
                    )}
                    <button id="btn-next" className={styles.btn} disabled={page + 1 >= totalPages} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}>
                        Next
                    </button>
                </div>
            </section>
        </section>
    );
}
