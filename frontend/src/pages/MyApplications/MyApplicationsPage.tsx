import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import styles from './MyApplicationsPage.module.css';
import { getAccess } from '../../shared/api';

type ApplicationItem = {
    id: string | number;
    offerId?: string | number | null;
    offerTitle?: string | null;
    companyName?: string | null;
    cityName?: string | null;
    createdAt?: string | null;
    status?: string | null;
    cvDownloadUrl?: string | null;
};

type ApplicationGroup = {
    key: string;
    offerId: string | number | null;
    title: string;
    companyName: string;
    cityName: string;
    items: ApplicationItem[];
};

type TabKey = 'mine' | 'owned';

const PAGE_SIZE = 20;

function authHeaders(): Record<string, string> {
    try {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    } catch {
        return {};
    }
}

async function ensureAuth(navigate: ReturnType<typeof useNavigate>) {
    if (!getAccess?.()) {
        navigate('/auth/login');
        return false;
    }
    try {
        const res = await fetch('/api/auth/me', {
            headers: { Accept: 'application/json', ...authHeaders() },
            credentials: 'include',
        });
        if (res.status === 401) {
            navigate('/auth/login');
            return false;
        }
        return res.ok;
    } catch {
        return false;
    }
}

function groupByOffer(items: ApplicationItem[]): ApplicationGroup[] {
    const map = new Map<string, ApplicationGroup>();
    for (const item of items) {
        const removed = item.offerId == null;
        const key = removed ? `app-${item.id}` : String(item.offerId);

        const entry =
            map.get(key) ?? {
                key,
                offerId: removed ? null : (item.offerId ?? null),
                title: item.offerTitle || (removed ? 'Offer removed' : '—'),
                companyName: item.companyName || (removed ? '' : '—'),
                cityName: item.cityName || (removed ? '' : '—'),
                items: [],
            };
        entry.items.push(item);
        map.set(key, entry);
    }
    return [...map.values()];
}

function paginateGroups(groups: ApplicationGroup[], page: number, pageSize: number) {
    const out: ApplicationGroup[] = [];
    let acc = 0;
    let skipped = 0;
    for (const g of groups) {
        const len = g.items.length;
        if (skipped + len <= (page - 1) * pageSize) {
            skipped += len;
            continue;
        }
        if (acc >= pageSize) break;
        out.push(g);
        acc += len;
    }
    const totalApps = groups.reduce((sum, g) => sum + g.items.length, 0);
    const totalPages = Math.max(1, Math.ceil(totalApps / pageSize));
    return { groups: out, totalApps, totalPages };
}

function filterAndSort(items: ApplicationItem[], search: string, sort: string) {
    const q = search.trim().toLowerCase();
    let out = [...items];
    if (q) {
        out = out.filter(
            (a) =>
                (a.offerTitle || '').toLowerCase().includes(q) ||
                (a.companyName || '').toLowerCase().includes(q) ||
                (a.cityName || '').toLowerCase().includes(q),
        );
    }

    const by = (key: keyof ApplicationItem, dir: 'asc' | 'desc' = 'asc') => (a: ApplicationItem, b: ApplicationItem) => {
        const av = String(a[key] ?? '').toLowerCase();
        const bv = String(b[key] ?? '').toLowerCase();
        if (av === bv) return 0;
        return dir === 'asc' ? (av > bv ? 1 : -1) : (av > bv ? -1 : 1);
    };

    const byDate = (key: keyof ApplicationItem, dir: 'asc' | 'desc' = 'desc') => (a: ApplicationItem, b: ApplicationItem) => {
        const av = a[key] ? new Date(String(a[key])).getTime() : 0;
        const bv = b[key] ? new Date(String(b[key])).getTime() : 0;
        return dir === 'asc' ? av - bv : bv - av;
    };

    switch (sort) {
        case 'created_asc':
            out.sort(byDate('createdAt', 'asc'));
            break;
        case 'title_asc':
            out.sort(by('offerTitle', 'asc'));
            break;
        case 'company_asc':
            out.sort(by('companyName', 'asc'));
            break;
        case 'city_asc':
            out.sort(by('cityName', 'asc'));
            break;
        default:
            out.sort(byDate('createdAt', 'desc'));
            break;
    }

    return out;
}

async function downloadCv(appId: string | number, explicitUrl?: string | null) {
    try {
        const url = explicitUrl || `/api/applications/${encodeURIComponent(String(appId))}/cv`;
        const res = await fetch(url, {
            headers: { Accept: 'application/octet-stream', ...authHeaders() },
            credentials: 'include',
        });
        if (res.status === 401) {
            window.alert('Please log in to download the CV.');
            return;
        }
        if (res.status === 403) {
            window.alert('You do not have access to this CV.');
            return;
        }
        if (res.status === 404) {
            window.alert('The candidate did not attach a CV.');
            return;
        }
        if (!res.ok) {
            window.alert('Could not download CV.');
            return;
        }

        const cd = res.headers.get('Content-Disposition') || '';
        let filename = 'cv';
        const mStar = cd.match(/filename\*\s*=\s*[^']*'[^']*'([^;]+)/i);
        const mNorm = cd.match(/filename\s*=\s*("?)([^";]+)\1/i);
        if (mStar) filename = decodeURIComponent(mStar[1]);
        else if (mNorm) filename = mNorm[2];

        const blob = await res.blob();
        const href = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = href;
        a.download = filename || 'cv';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(href);
    } catch (err) {
        console.error('downloadCv error', err);
        window.alert('Network error while downloading CV.');
    }
}

export default function MyApplicationsPage() {
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState<TabKey>('mine');
    const [search, setSearch] = useState('');
    const [sort, setSort] = useState('created_desc');

    const [mineItems, setMineItems] = useState<ApplicationItem[]>([]);
    const [ownedItems, setOwnedItems] = useState<ApplicationItem[]>([]);
    const [mineLoaded, setMineLoaded] = useState(false);
    const [ownedLoaded, setOwnedLoaded] = useState(false);
    const [mineError, setMineError] = useState<string | null>(null);
    const [ownedError, setOwnedError] = useState<string | null>(null);
    const [mineLoading, setMineLoading] = useState(false);
    const [ownedLoading, setOwnedLoading] = useState(false);

    const [pageMine, setPageMine] = useState(1);
    const [pageOwned, setPageOwned] = useState(1);
    const [collapsedMine, setCollapsedMine] = useState<string[]>([]);
    const [collapsedOwned, setCollapsedOwned] = useState<string[]>([]);

    useEffect(() => {
        setPageMine(1);
        setPageOwned(1);
    }, [search, sort]);

    const filteredMine = useMemo(() => filterAndSort(mineItems, search, sort), [mineItems, search, sort]);
    const filteredOwned = useMemo(() => filterAndSort(ownedItems, search, sort), [ownedItems, search, sort]);
    const mineGroups = useMemo(() => groupByOffer(filteredMine), [filteredMine]);
    const ownedGroups = useMemo(() => groupByOffer(filteredOwned), [filteredOwned]);

    const minePage = useMemo(() => paginateGroups(mineGroups, pageMine, PAGE_SIZE), [mineGroups, pageMine]);
    const ownedPage = useMemo(() => paginateGroups(ownedGroups, pageOwned, PAGE_SIZE), [ownedGroups, pageOwned]);

    useEffect(() => {
        const run = async () => {
            if (activeTab === 'mine' && !mineLoaded && !mineLoading) {
                setMineLoading(true);
                setMineError(null);
                const ok = await ensureAuth(navigate);
                if (!ok) {
                    setMineLoading(false);
                    return;
                }
                try {
                    const res = await fetch('/api/applications/mine', {
                        headers: { Accept: 'application/json', ...authHeaders() },
                        credentials: 'include',
                    });
                    if (res.status === 401) {
                        navigate('/auth/login');
                        return;
                    }
                    if (!res.ok) throw new Error(`HTTP ${res.status}`);
                    const data = await res.json();
                    setMineItems(Array.isArray(data) ? data : []);
                    setMineLoaded(true);
                } catch (err) {
                    console.error('Failed to load applications (mine)', err);
                    setMineError('Could not load your applications.');
                } finally {
                    setMineLoading(false);
                }
            }
            if (activeTab === 'owned' && !ownedLoaded && !ownedLoading) {
                setOwnedLoading(true);
                setOwnedError(null);
                const ok = await ensureAuth(navigate);
                if (!ok) {
                    setOwnedLoading(false);
                    return;
                }
                try {
                    const res = await fetch('/api/applications/owned', {
                        headers: { Accept: 'application/json', ...authHeaders() },
                        credentials: 'include',
                    });
                    if (res.status === 401) {
                        navigate('/auth/login');
                        return;
                    }
                    if (!res.ok) throw new Error(`HTTP ${res.status}`);
                    const data = await res.json();
                    setOwnedItems(Array.isArray(data) ? data : []);
                    setOwnedLoaded(true);
                } catch (err) {
                    console.error('Failed to load applications (owned)', err);
                    setOwnedError('Could not load applications to your offers.');
                } finally {
                    setOwnedLoading(false);
                }
            }
        };
        run();
    }, [activeTab, mineLoaded, ownedLoaded, mineLoading, ownedLoading, navigate]);

    const toggleGroup = (tab: TabKey, key: string) => {
        if (tab === 'mine') {
            setCollapsedMine((prev) =>
                prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key],
            );
            return;
        }
        setCollapsedOwned((prev) =>
            prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key],
        );
    };

    const expandAll = () => {
        if (activeTab === 'mine') setCollapsedMine([]);
        else setCollapsedOwned([]);
    };

    const collapseAll = () => {
        if (activeTab === 'mine') setCollapsedMine(mineGroups.map((g) => g.key));
        else setCollapsedOwned(ownedGroups.map((g) => g.key));
    };

    const renderGroupItems = (items: ApplicationItem[]) =>
        items.map((app) => {
            const removed = app.offerId == null;
            const title = app.offerTitle || (removed ? 'Offer removed' : '—');
            const applied = app.createdAt ? new Date(app.createdAt).toLocaleString('en-GB') : '—';
            const link = removed ? null : `/jobexaclyoffer?id=${encodeURIComponent(String(app.offerId))}`;

            return (
                <li key={String(app.id)} className={styles.apps__item}>
                    <div>
                        <div>
                            {removed ? (
                                <strong>{title}</strong>
                            ) : (
                                <Link to={link ?? '/'} replace={false}>
                                    <strong>{title}</strong>
                                </Link>
                            )}
                            {removed ? <span className={styles.badge}>Offer removed</span> : null}
                        </div>
                        <div className={styles.apps__meta}>
                            {removed ? '' : `Company: ${app.companyName || '—'} • City: ${app.cityName || '—'}`}
                        </div>
                        <div className={styles.apps__meta}>Applied: {applied}</div>
                    </div>
                    <div className={styles.actions}>
                        <span className={styles.badge}>{app.status || 'APPLIED'}</span>
                        <button type="button" className={styles.linklike} onClick={() => downloadCv(app.id, app.cvDownloadUrl)}>
                            CV ↗
                        </button>
                        {removed ? null : (
                            <Link to={link ?? '/'} className={styles.linklike}>
                                Preview ↗
                            </Link>
                        )}
                    </div>
                </li>
            );
        });

    const renderGroups = (tab: TabKey, groups: ApplicationGroup[], collapsed: string[], pageGroups: ApplicationGroup[], loading: boolean, error: string | null) => {
        const collapsedSet = new Set(collapsed);

        if (loading) {
            return <li className={styles.empty}>Loading…</li>;
        }

        if (error) {
            return <li className={styles.empty}>{error}</li>;
        }

        if (!groups.length) {
            return <li className={styles.empty}>No items yet.</li>;
        }

        return pageGroups.map((group) => {
            const removedGroup = group.offerId == null;
            const headMeta = removedGroup ? 'Offer removed' : `${group.companyName || '—'} • ${group.cityName || '—'}`;
            const isCollapsed = collapsedSet.has(group.key);
            return (
                <li
                    key={group.key}
                    className={`${styles.group} ${isCollapsed ? styles.groupClosed : ''}`}
                    aria-expanded={isCollapsed ? 'false' : 'true'}
                >
                    <button type="button" className={styles.group__head} onClick={() => toggleGroup(tab, group.key)}>
                        <div className={styles.group__title}>
                            <strong>{group.title}</strong>
                            <span className={styles.group__meta}>{headMeta}</span>
                        </div>
                        <span className={styles.group__count}>{group.items.length}</span>
                    </button>
                    <div className={styles.group__body}>
                        <ul className={styles.apps__list}>{renderGroupItems(group.items)}</ul>
                    </div>
                </li>
            );
        });
    };

    return (
        <section className={styles.apps} id="my-apps-root">
            <header className={styles.apps__hero}>
                <h1>My applications</h1>
                <p className={styles.muted}>Track jobs you applied for — and applications sent to your own offers.</p>

                <nav className={styles.apps__tabs} role="tablist" aria-label="Applications tabs">
                    <button
                        className={`${styles.tab} ${activeTab === 'mine' ? styles.tabActive : ''}`}
                        type="button"
                        role="tab"
                        aria-selected={activeTab === 'mine' ? 'true' : 'false'}
                        onClick={() => setActiveTab('mine')}
                    >
                        Applied by me
                    </button>
                    <button
                        className={`${styles.tab} ${activeTab === 'owned' ? styles.tabActive : ''}`}
                        type="button"
                        role="tab"
                        aria-selected={activeTab === 'owned' ? 'true' : 'false'}
                        onClick={() => setActiveTab('owned')}
                    >
                        To my offers
                    </button>
                </nav>

                <div className={styles.apps__toolbar} aria-label="Filters">
                    <div className={styles.apps__search}>
                        <input
                            id="apps-search"
                            type="search"
                            placeholder="Search by title, company, city…"
                            autoComplete="off"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                        />
                    </div>
                    <div className={styles.apps__controls}>
                        <label className={styles.apps__select}>
                            <span>Sort</span>
                            <select id="apps-sort" value={sort} onChange={(e) => setSort(e.target.value)}>
                                <option value="created_desc">Newest first</option>
                                <option value="created_asc">Oldest first</option>
                                <option value="title_asc">Title A → Z</option>
                                <option value="company_asc">Company A → Z</option>
                                <option value="city_asc">City A → Z</option>
                            </select>
                        </label>
                        <div className={styles.apps__toggles}>
                            <button type="button" onClick={expandAll}>
                                Expand all
                            </button>
                            <button type="button" onClick={collapseAll}>
                                Collapse all
                            </button>
                        </div>
                    </div>
                </div>
            </header>

            <div className={styles.apps__panel} hidden={activeTab !== 'mine'}>
                <ul className={styles.apps__groups} aria-live="polite">
                    {renderGroups(
                        'mine',
                        mineGroups,
                        collapsedMine,
                        minePage.groups,
                        mineLoading,
                        mineError,
                    )}
                </ul>
                <div className={styles.apps__pager} hidden={minePage.totalPages <= 1}>
                    <button
                        className={styles.pager__btn}
                        onClick={() => setPageMine(Math.max(1, pageMine - 1))}
                        disabled={pageMine <= 1}
                    >
                        ← Prev
                    </button>
                    <span className={styles.pager__info}>
                        Page {pageMine} / {minePage.totalPages} • {minePage.totalApps} applications
                    </span>
                    <button
                        className={styles.pager__btn}
                        onClick={() => setPageMine(Math.min(minePage.totalPages, pageMine + 1))}
                        disabled={pageMine >= minePage.totalPages}
                    >
                        Next →
                    </button>
                </div>
            </div>

            <div className={styles.apps__panel} hidden={activeTab !== 'owned'}>
                <ul className={styles.apps__groups} aria-live="polite">
                    {renderGroups(
                        'owned',
                        ownedGroups,
                        collapsedOwned,
                        ownedPage.groups,
                        ownedLoading,
                        ownedError,
                    )}
                </ul>
                <div className={styles.apps__pager} hidden={ownedPage.totalPages <= 1}>
                    <button
                        className={styles.pager__btn}
                        onClick={() => setPageOwned(Math.max(1, pageOwned - 1))}
                        disabled={pageOwned <= 1}
                    >
                        ← Prev
                    </button>
                    <span className={styles.pager__info}>
                        Page {pageOwned} / {ownedPage.totalPages} • {ownedPage.totalApps} applications
                    </span>
                    <button
                        className={styles.pager__btn}
                        onClick={() => setPageOwned(Math.min(ownedPage.totalPages, pageOwned + 1))}
                        disabled={pageOwned >= ownedPage.totalPages}
                    >
                        Next →
                    </button>
                </div>
            </div>
        </section>
    );
}
