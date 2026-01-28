import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';

import styles from './ProfilePage.module.css';
import { getAccess, getUser, setUser } from '../../shared/api';

type Profile = {
    name?: string | null;
    email?: string | null;
    about?: string | null;
    dob?: string | null;
    avatarFileId?: string | null;
    avatarUrl?: string | null;
    avatarPreset?: string | null;
    cvFileId?: string | null;
};

type CvInfo = { name: string; size: number } | null;

const PRESETS = Array.from({ length: 5 }, (_, i) => `/assets/component/profile/avatar/avatar${i + 1}.svg`);
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];

function authHeaders(): Record<string, string> {
    const t = getAccess?.();
    const headers: Record<string, string> = {};
    if (t) headers.Authorization = `Bearer ${t}`;
    return headers;
}

function fileUrl(id: string) {
    return `/api/profile/file/${encodeURIComponent(id)}`;
}

function makeInitials(t: string) {
    return (t || '?').trim().slice(0, 1).toUpperCase();
}

async function loadProtectedImage(url: string) {
    const res = await fetch(url, { headers: { ...authHeaders() }, credentials: 'include' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();
    return URL.createObjectURL(blob);
}

async function downloadProtected(url: string, filename: string) {
    const res = await fetch(url, { headers: { ...authHeaders() }, credentials: 'include' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const blob = await res.blob();
    const obj = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = obj;
    a.download = filename || 'file';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(obj);
}

async function resolveAvatarSrc(profile: Profile) {
    if (profile?.avatarFileId) {
        try {
            return await loadProtectedImage(fileUrl(profile.avatarFileId));
        } catch {}
    }
    return profile?.avatarUrl || profile?.avatarPreset || null;
}

function broadcastHeader(url: string | null, initials: string) {
    globalThis.dispatchEvent(new CustomEvent('app:avatar-preview', { detail: { url, initials } }));
}

export default function ProfilePage() {
    const [profile, setProfileState] = useState<Profile>({});
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [about, setAbout] = useState('');
    const [dobDay, setDobDay] = useState('');
    const [dobMonth, setDobMonth] = useState('');
    const [dobYear, setDobYear] = useState('');
    const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
    const [avatarPreset, setAvatarPreset] = useState<string | null>(null);
    const [avatarBlob, setAvatarBlob] = useState<File | null>(null);
    const [cvBlob, setCvBlob] = useState<File | null>(null);
    const [cvInfo, setCvInfo] = useState<CvInfo>(null);
    const [saving, setSaving] = useState(false);

    const originalRef = useRef<Profile | null>(null);
    const previewUrlRef = useRef<string | null>(null);

    const years = useMemo(() => {
        const y = new Date().getFullYear();
        const arr: string[] = [];
        for (let yy = y - 14; yy >= y - 70; yy--) arr.push(String(yy));
        return arr;
    }, []);

    const days = useMemo(() => {
        if (!dobYear || !dobMonth) return Array.from({ length: 31 }, (_, i) => String(i + 1));
        const count = new Date(Number(dobYear), Number(dobMonth), 0).getDate();
        return Array.from({ length: count }, (_, i) => String(i + 1));
    }, [dobYear, dobMonth]);

    useEffect(() => {
        (async () => {
            try {
                const res = await fetch('/api/profile', { credentials: 'include', headers: { Accept: 'application/json', ...authHeaders() } });
                if (!res.ok) {
                    console.warn('Profile GET failed', res.status);
                    return;
                }
                const data = (await res.json()) as Profile;
                originalRef.current = data;
                setProfileState(data);

                const user = getUser?.() ?? {};
                setName(data?.name ?? user.username ?? '');
                setEmail(data?.email ?? '');
                setAbout(data?.about ?? '');

                if (data?.dob) {
                    const [yy, mm, dd] = String(data.dob).split('-').map(Number);
                    if (yy) setDobYear(String(yy));
                    if (mm) setDobMonth(String(mm));
                    if (dd) setDobDay(String(dd));
                }

                const src = await resolveAvatarSrc(data);
                setAvatarUrl(src);
                setAvatarPreset(data?.avatarPreset || data?.avatarUrl || null);
                broadcastHeader(src, makeInitials(data?.name || data?.email || ''));

                if (data?.cvFileId) {
                    setCvInfo({ name: 'CV', size: 0 });
                } else {
                    setCvInfo(null);
                }
            } catch (err) {
                console.warn('Profile GET failed', err);
            }
        })();
    }, []);

    useEffect(() => {
        return () => {
            if (previewUrlRef.current) {
                URL.revokeObjectURL(previewUrlRef.current);
                previewUrlRef.current = null;
            }
        };
    }, []);

    const handleAvatarFile = (file: File) => {
        if (file.size > 2 * 1024 * 1024) {
            alert('File too large (max 2 MB).');
            return;
        }
        if (previewUrlRef.current) {
            URL.revokeObjectURL(previewUrlRef.current);
            previewUrlRef.current = null;
        }
        const url = URL.createObjectURL(file);
        previewUrlRef.current = url;
        setAvatarUrl(url);
        setAvatarBlob(file);
        setAvatarPreset(null);
        broadcastHeader(url, makeInitials(name || email));
    };

    const clearAvatar = async () => {
        try {
            const res = await fetch('/api/profile/avatar', { method: 'DELETE', credentials: 'include', headers: authHeaders() });
            if (!res.ok) return;
        } catch {}
        if (previewUrlRef.current) {
            URL.revokeObjectURL(previewUrlRef.current);
            previewUrlRef.current = null;
        }
        setAvatarBlob(null);
        setAvatarPreset(null);
        setAvatarUrl(null);
        broadcastHeader(null, makeInitials(name || email));
    };

    const handleCvFile = (file: File) => {
        if (file.size > 5 * 1024 * 1024) {
            alert('CV too large (max 5 MB).');
            return;
        }
        setCvBlob(file);
        setCvInfo({ name: file.name, size: file.size });
    };

    const clearCv = async () => {
        try {
            await fetch('/api/profile/cv', { method: 'DELETE', credentials: 'include', headers: authHeaders() });
        } catch {}
        setCvBlob(null);
        setCvInfo(null);
        setProfileState((p) => ({ ...p, cvFileId: null }));
    };

    const onReset = async () => {
        const original = originalRef.current;
        if (!original) return;

        setName(original?.name ?? getUser()?.username ?? '');
        setEmail(original?.email ?? '');
        setAbout(original?.about ?? '');

        setDobDay('');
        setDobMonth('');
        setDobYear('');
        if (original?.dob) {
            const [yy, mm, dd] = String(original.dob).split('-').map(Number);
            if (yy) setDobYear(String(yy));
            if (mm) setDobMonth(String(mm));
            if (dd) setDobDay(String(dd));
        }

        if (previewUrlRef.current) {
            URL.revokeObjectURL(previewUrlRef.current);
            previewUrlRef.current = null;
        }
        setAvatarBlob(null);
        setAvatarPreset(original?.avatarPreset || original?.avatarUrl || null);
        const src = await resolveAvatarSrc(original);
        setAvatarUrl(src);
        broadcastHeader(src, makeInitials(original?.name || original?.email || ''));

        setCvBlob(null);
        if (original?.cvFileId) setCvInfo({ name: 'CV', size: 0 });
        else setCvInfo(null);
    };

    const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setSaving(true);

        const dob = dobYear && dobMonth && dobDay ? `${dobYear}-${String(dobMonth).padStart(2,'0')}-${String(dobDay).padStart(2,'0')}` : '';

        const fd = new FormData();
        fd.append('name', name.trim());
        fd.append('email', email);
        fd.append('about', about.trim());
        if (dob) fd.append('dob', dob);
        if (avatarBlob) fd.append('avatar', avatarBlob);
        else if (avatarPreset) fd.append('avatarPreset', avatarPreset);
        if (cvBlob) fd.append('cv', cvBlob);

        try {
            const res = await fetch('/api/profile', { method: 'PUT', body: fd, credentials: 'include', headers: authHeaders() });
            if (!res.ok) {
                console.error('Profile PUT failed', res.status);
                alert('Could not save profile.');
                return;
            }
            const updated = (await res.json()) as Profile;

            setProfileState(updated);
            const currentUser = getUser?.() ?? {};
            setUser({
                ...currentUser,
                name: updated?.name ?? currentUser.name,
                email: updated?.email ?? currentUser.email,
                avatarUrl: updated?.avatarUrl ?? updated?.avatarPreset ?? currentUser.avatarUrl ?? null,
            });
            originalRef.current = updated;

            const src = await resolveAvatarSrc(updated);
            setAvatarUrl(src);
            broadcastHeader(src, makeInitials(name || email));

            if (updated?.cvFileId) {
                setCvInfo({ name: cvBlob?.name || 'CV', size: cvBlob?.size ?? 0 });
            } else {
                setCvInfo(null);
            }
            setCvBlob(null);
            alert('Profile changes saved.');
        } catch (err) {
            console.error(err);
            alert('Could not save profile.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <section className={styles.profile} aria-labelledby="profile-title">
            <header className={styles.profile__hdr}>
                <h1 id="profile-title">My profile</h1>
                <p className={styles.muted}>Manage your account data, avatar, and documents.</p>
            </header>

            <form className={styles.profile__grid} autoComplete="off" noValidate onSubmit={onSubmit}>
                <section className={styles.profile__avatar} aria-label="Avatar and files">
                    <div className={`${styles.avatar} ${styles['avatar--xl']}`} aria-live="polite">
                        {avatarUrl ? <img src={avatarUrl} alt="Avatar" /> : makeInitials(name || email)}
                    </div>

                    <div className={styles.uploader}>
                        <input
                            id="avatarFile"
                            type="file"
                            accept="image/*"
                            hidden
                            onChange={(e) => {
                                const file = e.target.files?.[0];
                                if (file) handleAvatarFile(file);
                            }}
                        />
                        <label className={`${styles.btn} ${styles['btn--equal']}`} htmlFor="avatarFile">
                            Upload photo
                        </label>
                        <button type="button" className={`${styles.btn} ${styles['btn--ghost']}`} onClick={clearAvatar}>
                            Remove photo
                        </button>
                    </div>

                    <details className={styles.presets}>
                        <summary>…or pick a preset avatar</summary>
                        <div className={styles.presetGrid} role="listbox" aria-label="Built-in avatars">
                            {PRESETS.map((src, i) => {
                                const active = avatarPreset === src;
                                return (
                                    <button
                                        key={src}
                                        type="button"
                                        role="option"
                                        aria-selected={active}
                                        title={`Avatar ${i + 1}`}
                                        className={active ? styles.active : undefined}
                                        onClick={() => {
                                            if (previewUrlRef.current) {
                                                URL.revokeObjectURL(previewUrlRef.current);
                                                previewUrlRef.current = null;
                                            }
                                            setAvatarPreset(src);
                                            setAvatarBlob(null);
                                            setAvatarUrl(src);
                                            broadcastHeader(src, makeInitials(name || email));
                                        }}
                                    >
                                        <img src={src} alt={`avatar ${i + 1}`} width={56} height={56} loading="lazy" />
                                    </button>
                                );
                            })}
                        </div>
                    </details>

                    <small className={styles.muted}>JPG/PNG/SVG up to 2 MB. We crop it to a square.</small>

                    <hr className={styles.sep} />

                    <div className={styles.cv}>
                        <label className={styles['cv__label']}>CV (PDF/DOC/DOCX, up to 5 MB)</label>
                        <input
                            id="cvFile"
                            type="file"
                            accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            hidden
                            onChange={(e) => {
                                const file = e.target.files?.[0];
                                if (file) handleCvFile(file);
                            }}
                        />
                        <div className={styles['cv__row']}>
                            <label className={`${styles.btn} ${styles['btn--equal']}`} htmlFor="cvFile">
                                Add / replace CV
                            </label>
                            <button type="button" className={`${styles.btn} ${styles['btn--ghost']}`} onClick={clearCv}>
                                Remove CV
                            </button>
                        </div>
                        <div className={`${styles['cv__info']} ${styles.muted}`}>
                            {cvInfo ? (
                                <>
                                    <button
                                        type="button"
                                        className={`${styles['btn--ghost']}`}
                                        onClick={() => {
                                            if (profile?.cvFileId) {
                                                downloadProtected(`${fileUrl(profile.cvFileId)}/download`, cvInfo.name || 'CV');
                                            }
                                        }}
                                    >
                                        {cvInfo.name}
                                    </button>
                                    {cvInfo.size ? ` • ${(cvInfo.size / 1024 / 1024).toFixed(2)} MB` : ''}
                                </>
                            ) : (
                                'No file.'
                            )}
                        </div>
                    </div>
                </section>

                <section className={styles.profile__fields}>
                    <div className={styles.fld}>
                        <label htmlFor="name">Full name</label>
                        <input id="name" name="name" type="text" placeholder="John Doe" value={name} onChange={(e) => setName(e.target.value)} />
                    </div>

                    <div className={styles.fld}>
                        <label htmlFor="email">E-mail</label>
                        <input id="email" name="email" type="email" readOnly value={email} />
                    </div>

                    <div className={styles.fld}>
                        <label htmlFor="about">About</label>
                        <textarea id="about" name="about" rows={5} placeholder="A few words about me…" value={about} onChange={(e) => setAbout(e.target.value)} />
                    </div>

                    <div className={styles.fld}>
                        <label htmlFor="dobDay">Date of birth</label>
                        <div className={styles['dob-grid']}>
                            <select id="dobDay" name="dobDay" aria-label="Day" value={dobDay} onChange={(e) => setDobDay(e.target.value)}>
                                <option value="" disabled>
                                    Day
                                </option>
                                {days.map((d) => (
                                    <option key={d} value={d}>
                                        {d}
                                    </option>
                                ))}
                            </select>
                            <select id="dobMonth" name="dobMonth" aria-label="Month" value={dobMonth} onChange={(e) => setDobMonth(e.target.value)}>
                                <option value="" disabled>
                                    Month
                                </option>
                                {MONTHS.map((m, i) => (
                                    <option key={m} value={String(i + 1)}>
                                        {m}
                                    </option>
                                ))}
                            </select>
                            <select id="dobYear" name="dobYear" aria-label="Year" value={dobYear} onChange={(e) => setDobYear(e.target.value)}>
                                <option value="" disabled>
                                    Year
                                </option>
                                {years.map((y) => (
                                    <option key={y} value={y}>
                                        {y}
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className={styles.actions}>
                        <button className={`${styles.btn} ${styles['btn--ghost']}`} type="reset" onClick={onReset} disabled={saving}>
                            Discard changes
                        </button>
                        <button className={`${styles.btn} ${styles['btn--primary']}`} type="submit" disabled={saving}>
                            Save
                        </button>
                    </div>
                </section>
            </form>
        </section>
    );
}
