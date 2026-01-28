import { useEffect, useMemo, useState } from 'react';

import styles from './FavoritesPage.module.css';
import { getAccess } from '../../shared/api';

type FavJob = {
    id: string | number | null;
    title: string;
    company?: string;
    city?: string;
    url?: string;
    salary?: { min?: number | null; max?: number | null; currency?: string; period?: string } | null;
    tags?: string[];
};

type FavEvent = {
    id: string | number | null;
    title: string;
    url?: string;
    city?: string;
    country?: string;
    startAt?: string | null;
    tags?: string[];
};

const TYPES = { JOB: 'JOB', EVENT: 'EVENT' } as const;

function authHeaders(): Record<string, string> {
    const t = getAccess?.();
    return t ? { Authorization: `Bearer ${t}` } : {};
}

const isAuthed = () => !!getAccess?.();

const fmtMoney = (s?: { min?: number | null; max?: number | null; currency?: string; period?: string } | null) => {
    if (!s) return null;
    const min = s.min != null ? s.min.toLocaleString('en-GB') : '';
    const max = s.max != null ? s.max.toLocaleString('en-GB') : '';
    const cur = s.currency || 'PLN';
    const per = (s.period || 'MONTH').toLowerCase();
    return `${min}${max ? ' – ' + max : ''} ${cur}/${per}`.trim();
};

const fmtDate = (iso?: string | null) =>
    iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : 'TBA';

function mapJobFavorite(item: any): FavJob {
    const j = item?.job || item?.payload || item?.target || item;
    const id = j?.id ?? j?._id ?? j?.jobId ?? item?.targetId ?? item?.id;
    const title = j?.title ?? j?.jobTitle ?? '';
    const company = j?.companyName ?? j?.company ?? '';
    const city = j?.cityName ?? j?.city ?? '';
    const url = j?.url ?? j?.applyUrl ?? (id ? `/jobexaclyoffer?id=${encodeURIComponent(id)}` : '#');

    const salary = (() => {
        const min = j?.salaryMin ?? j?.salary?.min ?? null;
        const max = j?.salaryMax ?? j?.salary?.max ?? null;
        const currency = j?.currency ?? j?.salary?.currency ?? 'PLN';
        const period = j?.salary?.period ?? 'MONTH';
        return min == null && max == null ? null : { min, max, currency, period };
    })();

    const tags = Array.isArray(j?.techTags)
        ? j.techTags
        : Array.isArray(j?.techStack)
            ? j.techStack.map((s: any) => s?.name).filter(Boolean)
            : Array.isArray(j?.keywords)
                ? j.keywords
                : [];

    return { id, title, company, city, url, salary, tags: tags.slice(0, 6) };
}

function mapEventFavorite(item: any): FavEvent {
    const e = item?.event || item?.payload || item?.target || item;
    const id = e?.id ?? e?._id ?? item?.targetId ?? item?.id;
    const title = e?.title ?? '';
    const url = e?.url ?? (id ? `/events/${id}` : '#');
    const city = e?.city ?? '';
    const country = e?.country ?? '';
    const startAt = e?.startAt ?? e?.start ?? null;
    const categories = e?.categories || e?.tags || [];
    return { id, title, url, city, country, startAt, tags: categories.slice(0, 6) };
}

async function fetchMine(type: 'JOB' | 'EVENT') {
    if (!isAuthed()) {
        const err: any = new Error('Please log in');
        err.code = 401;
        throw err;
    }
    const r = await fetch(`/api/favorites/mine?type=${encodeURIComponent(type)}&page=0&size=50`, {
        credentials: 'include',
        headers: { Accept: 'application/json', ...authHeaders() },
    });
    if (r.status === 401 || r.status === 403) {
        const err: any = new Error('Please log in');
        err.code = r.status;
        throw err;
    }
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

async function fetchJobById(id: string | number) {
    const r = await fetch(`/api/jobs/${encodeURIComponent(String(id))}`, { headers: { Accept: 'application/json', ...authHeaders() } });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const x = await r.json();
    return mapJobFavorite({ job: x });
}

async function fetchEventById(id: string | number) {
    const r = await fetch(`/api/events/${encodeURIComponent(String(id))}`, { headers: { Accept: 'application/json', ...authHeaders() } });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const x = await r.json();
    return mapEventFavorite({ event: x });
}

async function deleteFavorite(type: 'JOB' | 'EVENT', id: string | number) {
    const r = await fetch(`/api/favorites/${type}/${encodeURIComponent(String(id))}`, {
        method: 'DELETE',
        credentials: 'include',
        headers: { ...authHeaders() },
    });
    if (r.status === 401 || r.status === 403) throw new Error('Please log in');
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
}

export default function FavoritesPage() {
    const [tab, setTab] = useState<'jobs' | 'events'>('jobs');
    const [jobs, setJobs] = useState<FavJob[]>([]);
    const [events, setEvents] = useState<FavEvent[]>([]);
    const [statusJobs, setStatusJobs] = useState<string>('');
    const [statusEvents, setStatusEvents] = useState<string>('');

    useEffect(() => {
        const start = (location.hash || '').replace('#', '');
        setTab(start === 'events' ? 'events' : 'jobs');
    }, []);

    useEffect(() => {
        const u = new URL(location.href);
        u.hash = tab;
        history.replaceState({}, '', u);
    }, [tab]);

    useEffect(() => {
        (async () => {
            try {
                setStatusJobs('Loading…');
                const page = await fetchMine(TYPES.JOB);
                const rows = page?.content || page?.items || (Array.isArray(page) ? page : []);
                const mapped = await Promise.all(
                    (rows || []).map(async (it: any) => {
                        const m = mapJobFavorite(it);
                        if (!m.id || !m.title) {
                            try {
                                return await fetchJobById(it?.targetId ?? it?.id);
                            } catch {
                                return null;
                            }
                        }
                        return m;
                    }),
                );
                const items = (mapped || []).filter(Boolean) as FavJob[];
                setJobs(items);
                setStatusJobs(items.length ? '' : 'You don’t have any saved jobs yet.');
            } catch (e: any) {
                const msg = e?.code === 401 || e?.code === 403 ? 'Please log in to use favorites.' : `Load error: ${e?.message || e}`;
                setStatusJobs(msg);
            }
        })();
    }, []);

    useEffect(() => {
        (async () => {
            try {
                setStatusEvents('Loading…');
                const page = await fetchMine(TYPES.EVENT);
                const rows = page?.content || page?.items || (Array.isArray(page) ? page : []);
                const mapped = await Promise.all(
                    (rows || []).map(async (it: any) => {
                        const m = mapEventFavorite(it);
                        if (!m.id || !m.title) {
                            try {
                                return await fetchEventById(it?.targetId ?? it?.id);
                            } catch {
                                return null;
                            }
                        }
                        return m;
                    }),
                );
                const items = (mapped || []).filter(Boolean) as FavEvent[];
                setEvents(items);
                setStatusEvents(items.length ? '' : 'You don’t have any saved events yet.');
            } catch (e: any) {
                const msg = e?.code === 401 || e?.code === 403 ? 'Please log in to use favorites.' : `Load error: ${e?.message || e}`;
                setStatusEvents(msg);
            }
        })();
    }, []);

    const onUnfav = async (type: 'JOB' | 'EVENT', id: string | number) => {
        try {
            await deleteFavorite(type, id);
            if (type === 'JOB') setJobs((prev) => prev.filter((x) => String(x.id) !== String(id)));
            else setEvents((prev) => prev.filter((x) => String(x.id) !== String(id)));
        } catch (err: any) {
            alert(err?.message || 'Could not remove from favorites');
        }
    };

    const onClear = async (type: 'JOB' | 'EVENT') => {
        const list = type === 'JOB' ? jobs : events;
        if (!list.length) return;
        if (!isAuthed()) {
            alert('Please log in to use favorites.');
            return;
        }
        try {
            await Promise.all(list.map((x) => deleteFavorite(type, x.id ?? '')));
            if (type === 'JOB') setJobs([]);
            else setEvents([]);
        } catch {
            if (type === 'JOB') setStatusJobs('Could not clear favorites.');
            else setStatusEvents('Could not clear favorites.');
        }
    };

    return (
        <section className={styles.favorite} aria-labelledby="fav-title">
            <header className={styles['fav__hdr']}>
                <h1 id="fav-title">Favorites</h1>
                <p className={styles.muted}>Your saved job offers and events.</p>
            </header>

            <nav className={styles['fav__tabs']} role="tablist" aria-label="Switch favorites">
                <button
                    className={`${styles['fav__tab']} ${tab === 'jobs' ? styles['is-active'] : ''}`}
                    role="tab"
                    aria-selected={tab === 'jobs'}
                    aria-controls="fav-jobs"
                    onClick={() => setTab('jobs')}
                >
                    Job offers
                </button>
                <button
                    className={`${styles['fav__tab']} ${tab === 'events' ? styles['is-active'] : ''}`}
                    role="tab"
                    aria-selected={tab === 'events'}
                    aria-controls="fav-events"
                    onClick={() => setTab('events')}
                >
                    Events
                </button>
            </nav>

            <section
                id="fav-jobs"
                className={`${styles['fav__panel']} ${tab === 'jobs' ? styles['is-active'] : ''}`}
                role="tabpanel"
                aria-labelledby="tab-jobs"
                hidden={tab !== 'jobs'}
            >
                <div className={styles['fav__toolbar']}>
                    <div className={styles.left}>
                        <span className={styles.muted}>Sort by:</span>
                        <select className={styles.sel} aria-label="Sort jobs" disabled>
                            <option>Newest</option>
                        </select>
                    </div>
                    <div className={styles.right}>
                        <button className={`${styles.btn} ${styles['btn--ghost']}`} onClick={() => onClear('JOB')}>
                            Clear
                        </button>
                    </div>
                </div>

                <div className={styles['fav__grid']}>
                    {jobs.map((j) => (
                        <article key={String(j.id)} className={styles['fav-card']} data-id={String(j.id)}>
                            <div className={styles['fav-card__head']}>
                                <h3 className={styles['fav-card__title']}>{j.title || ''}</h3>
                                <button
                                    className={styles['icon-btn']}
                                    title="Remove from favorites"
                                    aria-label="Remove from favorites"
                                    onClick={() => onUnfav('JOB', j.id ?? '')}
                                >
                                    <svg viewBox="0 0 24 24" aria-hidden="true">
                                        <path d="M12 21s-6.5-4.35-9.33-7.18A5.91 5.91 0 1 1 12 5.66a5.91 5.91 0 1 1 9.33 8.16C18.5 16.65 12 21 12 21z" />
                                    </svg>
                                </button>
                            </div>
                            <p className={styles.muted}>
                                {j.company || '—'}
                                {j.city ? ` • ${j.city}` : ''}
                            </p>
                            <div className={styles.badges}>
                                {(j.tags || []).map((t) => (
                                    <span key={t} className={styles.badge}>
                                        {t}
                                    </span>
                                ))}
                            </div>
                            <div className={styles['fav-card__meta']}>
                                {j.salary ? <span className={styles.money}>{fmtMoney(j.salary)}</span> : <span />}
                                <a className={styles.link} href={j.url} target="_blank" rel="noopener">
                                    View job
                                </a>
                            </div>
                        </article>
                    ))}
                </div>

                <div className={`${styles['fav__empty']} ${jobs.length ? styles.hidden : ''}`}>
                    <p>{statusJobs || 'You don’t have any saved jobs yet.'}</p>
                    <a href="/jobs" className={styles.btn}>
                        Browse jobs
                    </a>
                </div>
            </section>

            <section
                id="fav-events"
                className={`${styles['fav__panel']} ${tab === 'events' ? styles['is-active'] : ''}`}
                role="tabpanel"
                aria-labelledby="tab-events"
                hidden={tab !== 'events'}
            >
                <div className={styles['fav__toolbar']}>
                    <div className={styles.left}>
                        <span className={styles.muted}>Sort by:</span>
                        <select className={styles.sel} aria-label="Sort events" disabled>
                            <option>Upcoming</option>
                        </select>
                    </div>
                    <div className={styles.right}>
                        <button className={`${styles.btn} ${styles['btn--ghost']}`} onClick={() => onClear('EVENT')}>
                            Clear
                        </button>
                    </div>
                </div>

                <div className={styles['fav__grid']}>
                    {events.map((e) => {
                        const place = `${e.city ? `${e.city} • ` : ''}${e.country || '—'} • ${fmtDate(e.startAt)}`;
                        return (
                            <article key={String(e.id)} className={styles['fav-card']} data-id={String(e.id)}>
                                <div className={styles['fav-card__head']}>
                                    <h3 className={styles['fav-card__title']}>{e.title || ''}</h3>
                                    <button
                                        className={styles['icon-btn']}
                                        title="Remove from favorites"
                                        aria-label="Remove from favorites"
                                        onClick={() => onUnfav('EVENT', e.id ?? '')}
                                    >
                                        <svg viewBox="0 0 24 24" aria-hidden="true">
                                            <path d="M12 21s-6.5-4.35-9.33-7.18A5.91 5.91 0 1 1 12 5.66a5.91 5.91 0 1 1 9.33 8.16C18.5 16.65 12 21 12 21z" />
                                        </svg>
                                    </button>
                                </div>
                                <p className={styles.muted}>{place}</p>
                                <div className={styles.badges}>
                                    {(e.tags || []).map((t) => (
                                        <span key={t} className={styles.badge}>
                                            {t}
                                        </span>
                                    ))}
                                </div>
                                <div className={styles['fav-card__meta']}>
                                    <span className={styles.pill}>{e.city ? 'On-site' : 'Online'}</span>
                                    <a className={styles.link} href={e.url} target="_blank" rel="noopener">
                                        View event
                                    </a>
                                </div>
                            </article>
                        );
                    })}
                </div>

                <div className={`${styles['fav__empty']} ${events.length ? styles.hidden : ''}`}>
                    <p>{statusEvents || 'You don’t have any saved events yet.'}</p>
                    <a href="/events" className={styles.btn}>
                        See events
                    </a>
                </div>
            </section>
        </section>
    );
}
