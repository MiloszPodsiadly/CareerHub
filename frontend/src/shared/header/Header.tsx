import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import styles from './Header.module.css';
import { authApi, getAccess, getUser } from '../api';

type User = {
    username?: string;
    name?: string;
    email?: string;
    avatarUrl?: string | null;
    avatarPreset?: string | null;
};

const NAV = [
    { to: '/jobs', label: 'Jobs', test: /^\/jobs(\/|$)/ },
    { to: '/events', label: 'Events', test: /^\/events(\/|$)/ },
    {
        to: '/salary-calculator',
        label: 'Salary calculator',
        test: /^\/salary-calculator(\/|$)/,
        accent: true,
        chip: 'new',
    },
];

const USER_NAV = [
    { to: '/my-applications', label: 'My applications', test: /^\/my-applications(\/|$)/ },
    { to: '/my-offers', label: 'My offers', test: /^\/my-offers(\/|$)/ },
    { to: '/favorite', label: 'Favourites', test: /^\/favorite(\/|$)/ },
];

const authHeaders = (): Record<string, string> => {
    const t = getAccess?.();
    return t ? { Authorization: `Bearer ${t}` } : {};
};

const fileUrl = (id: string) => `/api/profile/file/${encodeURIComponent(id)}`;

async function loadProtectedImage(url: string) {
    const res = await fetch(url, { headers: { ...authHeaders() }, credentials: 'include' });
    if (!res.ok) throw new Error(`avatar fetch failed ${res.status}`);
    const blob = await res.blob();
    return URL.createObjectURL(blob);
}

async function fetchProfile() {
    try {
        const res = await fetch('/api/profile', { headers: { ...authHeaders() }, credentials: 'include' });
        if (!res.ok) return null;
        return await res.json();
    } catch {
        return null;
    }
}

async function resolveAvatarForHeader(user: User | null) {
    const p = await fetchProfile();
    if (p) {
        if (p.avatarFileId) {
            try {
                return await loadProtectedImage(fileUrl(p.avatarFileId));
            } catch {}
        }
        return p.avatarUrl || p.avatarPreset || null;
    }
    return user?.avatarUrl || user?.avatarPreset || null;
}

export default function Header() {
    const location = useLocation();
    const navigate = useNavigate();
    const [user, setUser] = useState<User | null>(() => getUser?.() ?? null);
    const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
    const [menuOpen, setMenuOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement | null>(null);

    const displayName = useMemo(() => {
        const name = user?.name || user?.username || 'User';
        return name.trim() || 'User';
    }, [user]);


    useEffect(() => {
        const onReady = (e: Event) => {
            const detail = (e as CustomEvent<User | null>).detail ?? null;
            setUser(detail);
        };
        const onChange = (e: Event) => {
            const detail = (e as CustomEvent<User | null>).detail ?? null;
            setUser(detail);
        };
        window.addEventListener('auth:ready', onReady);
        window.addEventListener('auth:change', onChange);
        return () => {
            window.removeEventListener('auth:ready', onReady);
            window.removeEventListener('auth:change', onChange);
        };
    }, []);

    useEffect(() => {
        let active = true;
        let blobToRevoke: string | null = null;

        resolveAvatarForHeader(user).then((url) => {
            if (!active) return;
            setAvatarUrl((prev) => {
                if (prev && prev.startsWith('blob:') && prev !== url) {
                    try {
                        URL.revokeObjectURL(prev);
                    } catch {}
                }
                return url;
            });
            if (url?.startsWith('blob:')) blobToRevoke = url;
        });

        return () => {
            active = false;
            if (blobToRevoke) {
                try {
                    URL.revokeObjectURL(blobToRevoke);
                } catch {}
            }
        };
    }, [user]);

    useEffect(() => {
        const onPreview = (e: Event) => {
            const detail = (e as CustomEvent<{ url?: string; initials?: string }>).detail || {};
            if (detail.url) setAvatarUrl(detail.url);
        };
        window.addEventListener('app:avatar-preview', onPreview);
        return () => window.removeEventListener('app:avatar-preview', onPreview);
    }, []);

    useEffect(() => {
        if (!menuOpen) return;
        const onClick = (e: MouseEvent) => {
            if (!menuRef.current) return;
            if (menuRef.current.contains(e.target as Node)) return;
            setMenuOpen(false);
        };
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') setMenuOpen(false);
        };
        document.addEventListener('click', onClick);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('click', onClick);
            document.removeEventListener('keydown', onKey);
        };
    }, [menuOpen]);

    const isActive = (test: RegExp) => test.test(location.pathname);

    const onLogout = async () => {
        try {
            await authApi.logout();
        } catch {}
        navigate('/');
    };

    return (
        <header className={styles.hdr} role="banner">
            <div className={styles.hdr__inner}>
                <Link className={styles.brand} to="/" aria-label="Home">
                    <span className={styles.brand__badge} aria-hidden="true">
                        ⚡
                    </span>
                    <span className={styles.brand__text}>CareerHub</span>
                </Link>

                <nav className={styles.nav} aria-label="Main navigation">
                    {NAV.map((item) => (
                        <Link
                            key={item.to}
                            to={item.to}
                            className={[
                                styles.nav__link,
                                item.accent ? styles['nav__link--accent'] : '',
                                isActive(item.test) ? styles['nav__link--active'] : '',
                            ]
                                .filter(Boolean)
                                .join(' ')}
                        >
                            {item.label}
                            {item.chip ? <span className={styles.chip}>{item.chip}</span> : null}
                        </Link>
                    ))}
                </nav>

                <div className={styles.hdr__actions}>
                    <Link className={[styles.btn, styles['btn--primary']].join(' ')} to="/post-job">
                        Post a job
                    </Link>

                    {!user && (
                        <div className={styles.cta}>
                            <Link className={[styles.btn, styles['btn--ghost']].join(' ')} to="/auth/login">
                                Log in
                            </Link>
                        </div>
                    )}

                    {user && (
                        <div className={[styles.cta, styles.userarea].join(' ')}>
                            {USER_NAV.map((item) => (
                                <Link
                                    key={item.to}
                                    to={item.to}
                                    className={[
                                        styles.nav__link,
                                        isActive(item.test) ? styles['nav__link--active'] : '',
                                    ]
                                        .filter(Boolean)
                                        .join(' ')}
                                >
                                    {item.label}
                                </Link>
                            ))}
                            <div className={styles.userdrop} ref={menuRef}>
                                <button
                                    className={styles.avatar}
                                    type="button"
                                    aria-haspopup="menu"
                                    aria-expanded={menuOpen ? 'true' : 'false'}
                                    aria-label="Account menu"
                                    onClick={() => setMenuOpen((v) => !v)}
                                >
                                    {avatarUrl ? <img src={avatarUrl} alt="Avatar" /> : displayName[0]?.toUpperCase()}
                                </button>
                                {menuOpen && (
                                    <div className={styles.dd} role="menu" aria-label="Account menu">
                                        <Link to="/profile" role="menuitem" className={styles.dd__item} onClick={() => setMenuOpen(false)}>
                                            Profile
                                        </Link>
                                        <button type="button" role="menuitem" className={styles.dd__item} onClick={onLogout}>
                                            Log out
                                        </button>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}
