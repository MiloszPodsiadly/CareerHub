import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import styles from './MyOffersPage.module.css';
import { getAccess } from '../../shared/api';

type JobOffer = {
    id: string | number;
    title?: string | null;
    companyName?: string | null;
    cityName?: string | null;
    level?: string | null;
    contract?: string | null;
    publishedAt?: string | null;
};

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

export default function MyOffersPage() {
    const navigate = useNavigate();
    const dialogRef = useRef<HTMLDialogElement | null>(null);

    const [items, setItems] = useState<JobOffer[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [pendingId, setPendingId] = useState<string | number | null>(null);
    const [password, setPassword] = useState('');
    const [deleteMsg, setDeleteMsg] = useState('');
    const [deleting, setDeleting] = useState(false);

    const load = async () => {
        setLoading(true);
        setError(null);
        const ok = await ensureAuth(navigate);
        if (!ok) {
            setLoading(false);
            return;
        }

        try {
            const res = await fetch('/api/jobs/mine', {
                headers: { Accept: 'application/json', ...authHeaders() },
                credentials: 'include',
            });
            if (res.status === 401) {
                navigate('/auth/login');
                return;
            }
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setItems(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Failed to load offers', err);
            setError('Could not load your offers.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    const openDelete = (id: string | number) => {
        setPendingId(id);
        setPassword('');
        setDeleteMsg('');
        if (dialogRef.current?.showModal) dialogRef.current.showModal();
        else dialogRef.current?.setAttribute('open', '');
    };

    const closeDelete = () => {
        dialogRef.current?.close?.('cancel');
        dialogRef.current?.removeAttribute?.('open');
        setPendingId(null);
        setDeleteMsg('');
        setPassword('');
    };

    const confirmDelete = async () => {
        if (!pendingId) return;
        if (!password.trim()) {
            setDeleteMsg('Please enter your password.');
            return;
        }
        setDeleting(true);
        setDeleteMsg('');
        try {
            const ok = await ensureAuth(navigate);
            if (!ok) {
                setDeleting(false);
                return;
            }

            const url = new URL(`/api/jobs/${pendingId}`, window.location.origin);
            url.searchParams.set('password', password);
            const res = await fetch(url.toString().replace(window.location.origin, ''), {
                method: 'DELETE',
                headers: {
                    Accept: 'text/plain, application/json',
                    'X-Account-Password': password,
                    ...authHeaders(),
                },
                credentials: 'include',
                body: JSON.stringify({ password }),
            });
            const raw = await res.text();
            if (res.status === 401) {
                navigate('/auth/login');
                return;
            }
            if (!res.ok) {
                setDeleteMsg(raw || 'Delete failed.');
                return;
            }
            closeDelete();
            await load();
        } catch (err) {
            console.error('Delete offer failed', err);
            setDeleteMsg('Network error. Try again.');
        } finally {
            setDeleting(false);
        }
    };

    const skeletons = Array.from({ length: 2 }).map((_, idx) => (
        <article key={`sk-${idx}`} className={`${styles.offer} ${styles['offer--skeleton']}`} aria-hidden="true">
            <div className={styles.offer__main}>
                <h3 className={`${styles.offer__title} ${styles.skeleton}`} style={{ width: '12rem' }}></h3>
                <div className={styles.offer__meta}>
                    <span className={`${styles.pill} ${styles.skeleton}`} style={{ width: '4rem' }}></span>
                    <span className={`${styles.pill} ${styles.skeleton}`} style={{ width: '3.5rem' }}></span>
                    <span className={`${styles.pill} ${styles.skeleton}`} style={{ width: '2.5rem' }}></span>
                    <span className={`${styles.pill} ${styles.skeleton}`} style={{ width: '3rem' }}></span>
                    <span className={`${styles.pill} ${styles.skeleton}`} style={{ width: '4.5rem' }}></span>
                </div>
            </div>
            <div className={styles.offer__actions}>
                <span className={`${styles.btn} ${styles.skeleton}`} style={{ width: '5rem' }} aria-hidden="true"></span>
                <span className={`${styles.btn} ${styles.skeleton}`} style={{ width: '4rem' }} aria-hidden="true"></span>
            </div>
        </article>
    ));

    return (
        <section className={styles.myoffers} aria-labelledby="myoffers-title">
            <header className={styles.myoffers__hero}>
                <h1 id="myoffers-title">My offers</h1>
                <p className={styles.muted}>Manage the jobs you’ve published.</p>
            </header>

            <div className={styles.myoffers__list} aria-live="polite" aria-busy={loading ? 'true' : 'false'}>
                {loading && skeletons}
                {!loading && error && <p className={styles.muted}>{error}</p>}
                {!loading && !error && items.length === 0 && (
                    <div className={styles.empty}>
                        You haven’t published anything yet.
                        <Link className={styles.btn} to="/post-job">
                            Post a job
                        </Link>
                    </div>
                )}
                {!loading &&
                    !error &&
                    items.map((offer) => {
                        const previewUrl = `/jobexaclyoffer?id=${encodeURIComponent(String(offer.id))}`;
                        const date = offer.publishedAt ? String(offer.publishedAt).slice(0, 10) : '';
                        return (
                            <article key={String(offer.id)} className={styles.offer} data-id={offer.id}>
                                <div className={styles.offer__main}>
                                    <h3 className={styles.offer__title}>{offer.title || '(no title)'}</h3>
                                    <div className={styles.offer__meta}>
                                        {offer.companyName ? <span className={styles.pill}>{offer.companyName}</span> : null}
                                        {offer.cityName ? <span className={styles.pill}>{offer.cityName}</span> : null}
                                        {offer.level ? <span className={styles.pill}>{offer.level}</span> : null}
                                        {offer.contract ? <span className={styles.pill}>{offer.contract}</span> : null}
                                        {date ? <span className={styles.pill}>{date}</span> : null}
                                    </div>
                                </div>
                                <div className={styles.offer__actions}>
                                    <Link className={`${styles.btn} ${styles['btn--ghost']}`} to={previewUrl}>
                                        Preview
                                    </Link>
                                    <button
                                        className={`${styles.btn} ${styles['btn--danger']}`}
                                        type="button"
                                        onClick={() => openDelete(offer.id)}
                                    >
                                        Delete
                                    </button>
                                </div>
                            </article>
                        );
                    })}
            </div>

            <dialog ref={dialogRef}>
                <div className={styles.dlg}>
                    <h4>Delete offer</h4>
                    <p className={styles.muted}>Enter your account password to confirm.</p>

                    <input
                        type="password"
                        className={styles.dlg__pwd}
                        placeholder="Password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                    />

                    <div className={styles.dlg__actions}>
                        <button type="button" className={styles.btn} onClick={closeDelete}>
                            Cancel
                        </button>
                        <button
                            type="button"
                            className={`${styles.btn} ${styles['btn--danger']}`}
                            onClick={confirmDelete}
                            disabled={deleting}
                        >
                            {deleting ? 'Deleting…' : 'Delete'}
                        </button>
                    </div>

                    <p className={styles.dlg__msg} aria-live="polite">
                        {deleteMsg}
                    </p>
                </div>
            </dialog>
        </section>
    );
}
