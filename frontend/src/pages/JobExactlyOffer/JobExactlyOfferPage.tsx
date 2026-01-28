import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import styles from './JobExactlyOfferPage.module.css';
import { authApi, getAccess } from '../../shared/api';

type JobOffer = {
    id?: string | number | null;
    title?: string | null;
    companyName?: string | null;
    company?: string | null;
    cityName?: string | null;
    city?: string | null;
    country?: string | null;
    contract?: string | null;
    contracts?: string[] | null;
    level?: string | null;
    remote?: boolean;
    salaryMin?: number | null;
    salaryMax?: number | null;
    currency?: string | null;
    publishedAt?: string | null;
    description?: string | null;
    techTags?: string[] | null;
    keywords?: string[] | null;
    techStack?: Array<{ name?: string | null }> | null;
};

type StatusKind = 'ok' | 'warn' | 'err' | 'info';

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

function prettyLevel(level?: string | null) {
    const map: Record<string, string> = { INTERNSHIP: 'Intern', JUNIOR: 'Junior', MID: 'Mid', SENIOR: 'Senior', LEAD: 'Lead' };
    return level ? map[String(level).toUpperCase()] ?? level : '';
}

function prettyContract(c?: string | null) {
    if (!c) return '';
    const up = String(c).toUpperCase();
    if (up === 'UOP') return 'UoP';
    if (up === 'UOD') return 'UoD';
    return up;
}

function salaryToText(offer: JobOffer) {
    const min = offer.salaryMin;
    const max = offer.salaryMax;
    if (min == null && max == null) return '';
    const a = min != null ? min.toLocaleString('en-GB') : '';
    const b = max != null ? ` - ${max.toLocaleString('en-GB')}` : '';
    return `${a}${b} ${(offer.currency || 'PLN')}/month`;
}

function authHeaders(): Record<string, string> {
    try {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    } catch {
        return {};
    }
}

async function ensureAuthOrLogin(navigate: ReturnType<typeof useNavigate>) {
    if (!getAccess?.()) {
        navigate(`/auth/login?next=${encodeURIComponent(window.location.href)}`);
        return false;
    }
    try {
        await authApi.me();
        return true;
    } catch {
        navigate(`/auth/login?next=${encodeURIComponent(window.location.href)}`);
        return false;
    }
}

async function hasCv() {
    try {
        const res = await fetch('/api/profile', {
            headers: { Accept: 'application/json', ...authHeaders() },
            credentials: 'include',
        });
        if (!res.ok) return 'unknown';
        const p = await res.json();
        if (p?.hasCv === true) return 'yes';
        if (p?.hasCv === false) return 'no';
        if (typeof p?.cvFileId !== 'undefined') return p.cvFileId ? 'yes' : 'no';
        const cvObj = p?.cv || p?.resume || p?.files?.cv || p?.files?.resume;
        if (cvObj) {
            if (typeof cvObj === 'string' && cvObj.trim()) return 'yes';
            if (typeof cvObj?.id === 'string' && cvObj.id.trim()) return 'yes';
            if (typeof cvObj?.fileId === 'string' && cvObj.fileId.trim()) return 'yes';
            if (typeof cvObj?.url === 'string' && /^https?:\/\//i.test(cvObj.url)) return 'yes';
        }
        return 'unknown';
    } catch {
        return 'unknown';
    }
}

export default function JobExactlyOfferPage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const idParam = params.get('id')?.trim() ?? '';

    const [offer, setOffer] = useState<JobOffer | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [status, setStatus] = useState<{ text: string; kind: StatusKind; link?: string } | null>(null);
    const [applying, setApplying] = useState(false);
    const [applied, setApplied] = useState(false);

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            setError(null);
            setOffer(null);

            if (!idParam) {
                setError('Offer not found.');
                setLoading(false);
                return;
            }

            try {
                const isNumericId = /^\d+$/.test(idParam);
                const url = isNumericId
                    ? `/api/jobs/${encodeURIComponent(idParam)}`
                    : `/api/jobs/by-external/${encodeURIComponent(idParam)}?source=PLATFORM`;
                const res = await fetch(url, { headers: { Accept: 'application/json' } });
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const data = (await res.json()) as JobOffer;
                setOffer(data);
            } catch (err) {
                console.error('Failed to load offer', err);
                setError('Offer not found.');
            } finally {
                setLoading(false);
            }
        };
        load();
    }, [idParam]);

    const tags = useMemo(() => {
        if (!offer) return [];
        if (Array.isArray(offer.techTags)) return offer.techTags.filter(Boolean) as string[];
        if (Array.isArray(offer.keywords)) return offer.keywords.filter(Boolean) as string[];
        if (Array.isArray(offer.techStack)) return offer.techStack.map((t) => t?.name).filter(Boolean) as string[];
        return [];
    }, [offer]);

    const badges = useMemo(() => {
        if (!offer) return [];
        const out: string[] = [];
        if (offer.contract) out.push(prettyContract(offer.contract));
        if (Array.isArray(offer.contracts)) offer.contracts.forEach((c) => c && out.push(prettyContract(c)));
        if (offer.remote) out.push('Remote');
        if (offer.level) out.push(prettyLevel(offer.level));
        const salary = salaryToText(offer);
        if (salary) out.push(salary);
        return out.filter(Boolean);
    }, [offer]);

    const onApply = async () => {
        if (!offer?.id || applying || applied) return;
        setStatus(null);
        const ok = await ensureAuthOrLogin(navigate);
        if (!ok) return;

        const cvState = await hasCv();
        if (cvState === 'no') {
            setStatus({ text: 'Add your CV in profile before applying.', kind: 'warn', link: '/profile#cv' });
            return;
        }

        setApplying(true);
        try {
            const res = await fetch('/api/applications', {
                method: 'POST',
                headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...authHeaders() },
                credentials: 'include',
                body: JSON.stringify({ offerId: offer.id }),
            });
            if (res.status === 401 || res.status === 403) {
                setStatus({ text: 'Please sign in to apply.', kind: 'warn' });
                navigate(`/auth/login?next=${encodeURIComponent(window.location.href)}`);
                return;
            }
            if (res.status === 409) {
                setApplied(true);
                setStatus({ text: 'You have applied for this role.', kind: 'warn' });
                return;
            }
            if (!res.ok) {
                let msg = '';
                try {
                    const j = await res.json();
                    msg = j?.message || j?.error || '';
                } catch {
                    msg = await res.text();
                }
                setStatus({ text: msg || 'Unexpected error. Please try again later.', kind: 'err' });
                return;
            }
            setApplied(true);
            setStatus({ text: 'Applied for job.', kind: 'ok' });
        } catch (err) {
            console.error('Apply failed', err);
            setStatus({ text: 'Network error. Please try again.', kind: 'err' });
        } finally {
            setApplying(false);
        }
    };

    if (loading) {
        return (
            <section className={styles.jobx}>
                <p className={styles.jobx__meta}>Loading…</p>
            </section>
        );
    }

    if (error || !offer) {
        return (
            <section className={styles.jobx}>
                <p className={styles.jobx__meta}>{error || 'Offer not found.'}</p>
            </section>
        );
    }

    const company = offer.companyName || offer.company || '-';
    const city = offer.cityName || offer.city || '-';

    return (
        <section className={styles.jobx} aria-labelledby="jobx-title">
            <header className={styles.jobx__hero}>
                <nav className={styles.jobx__crumbs}>
                    <Link to="/jobs">Jobs</Link>
                    <span aria-hidden="true">/</span>
                    <span>{offer.title || 'Offer'}</span>
                </nav>

                <h1 id="jobx-title">{offer.title || 'Job offer'}</h1>
                <p className={styles.jobx__meta}>
                    Company: {company} - City: {city} {offer.country ? `(${offer.country})` : ''}
                </p>

                <div className={styles.jobx__badges}>
                    {badges.map((b) => (
                        <span key={b} className={styles.badge}>
                            {b}
                        </span>
                    ))}
                </div>

                <div className={styles.jobx__cta}>
                    <button
                        className={`${styles.chip} ${styles['chip--apply']}`}
                        type="button"
                        onClick={onApply}
                        disabled={applying || applied}
                    >
                        {applied ? 'APPLIED' : applying ? 'Applying...' : 'Apply ->'}
                    </button>
                    {status ? (
                        <span className={styles.jobx__status} data-kind={status.kind}>
                            {status.text}
                            {status.link ? (
                                <Link to={status.link} style={{ marginLeft: 6 }}>
                                    Go to profile
                                </Link>
                            ) : null}
                        </span>
                    ) : null}
                </div>
            </header>

            <div className={styles.jobx__grid}>
                <article className={`${styles.jobx__desc} ${styles.prose}`} dangerouslySetInnerHTML={{ __html: mdToHtml(offer.description || '') }} />

                <aside className={styles.jobx__aside}>
                    <h3>Tags</h3>
                    <div className={styles.jobx__tags}>
                        {tags.length
                            ? tags.map((t) => (
                                  <span key={t} className={styles.badge}>
                                      {t}
                                  </span>
                              ))
                            : '-'}
                    </div>

                    <h3>Details</h3>
                    <ul className={styles.jobx__details}>
                        {offer.publishedAt ? (
                            <li>
                                <strong>Published:</strong> {new Date(offer.publishedAt).toLocaleDateString('en-GB')}
                            </li>
                        ) : (
                            <li className={styles.jobx__meta}>-</li>
                        )}
                    </ul>
                </aside>
            </div>
        </section>
    );
}
