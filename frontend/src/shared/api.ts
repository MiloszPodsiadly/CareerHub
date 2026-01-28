export type AuthUser = {
    username?: string;
    name?: string;
    email?: string;
    avatarUrl?: string | null;
} | null;

export type AuthToken = string | null;

type AuthCredentials = {
    email: string;
    password: string;
};

type VerifyEmailPayload = {
    token: string;
};

type ResetPasswordPayload = {
    token: string;
    newPassword: string;
};

let accessToken: AuthToken = JSON.parse(sessionStorage.getItem('accessToken') || 'null');

const saveAccess = (t: AuthToken) => {
    accessToken = t ?? null;
    if (t) sessionStorage.setItem('accessToken', JSON.stringify(t));
    else sessionStorage.removeItem('accessToken');
};

export const getAccess = (): AuthToken => accessToken;

function decodeJwtClaims(token: string) {
    try {
        const [, payload] = String(token || '').split('.');
        if (!payload) return null;
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(decodeURIComponent(escape(json)));
    } catch {
        return null;
    }
}

function userFromToken(token: string | null): AuthUser {
    const c = decodeJwtClaims(token || '');
    if (!c) return null;
    const username = c.preferred_username || c.username || c.sub || '';
    const name = c.name || c.given_name || username || 'User';
    const email = c.email || '';
    const avatar = c.avatar || c.picture || null;
    return { username, name, email, avatarUrl: avatar };
}

let refreshPromise: Promise<boolean> | null = null;
const isAuthPath = (p: string) => p.startsWith('/auth/');

async function doRefresh() {
    if (!refreshPromise) {
        refreshPromise = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
            .then(async (r) => {
                refreshPromise = null;
                if (!r.ok) throw new Error('session_expired');
                const data = await safeJson(r);
                const at = data?.accessToken ?? null;
                saveAccess(at);
                return true;
            })
            .catch((e) => {
                refreshPromise = null;
                saveAccess(null);
                throw e;
            });
    }
    return refreshPromise;
}

async function safeJson(res: Response) {
    try {
        return await res.json();
    } catch {
        return null;
    }
}

async function request<T = any>(path: string, opts: RequestInit = {}, retry = true): Promise<T | null> {
    const headers: Record<string, string> = { ...(opts.headers as Record<string, string> || {}) };
    const hasBody = opts.body && !(opts.body instanceof FormData);
    if (hasBody) headers['Content-Type'] = 'application/json';
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const res = await fetch(`/api${path}`, { ...opts, headers, credentials: 'include' });

    if (res.status === 401 && retry && !isAuthPath(path)) {
        try {
            await doRefresh();
            return request<T>(path, opts, false);
        } catch {}
    }

    if (res.status === 204) return null;

    const data = await safeJson(res);

    if (!res.ok) {
        const map: Record<number, string> = { 401: 'Incorrect username or password', 403: 'No permissions' };
        throw new Error(data?.error || data?.message || map[res.status] || `HTTP ${res.status}`);
    }

    return data as T;
}

const USER_KEY = 'ch.user';
let cachedUser: AuthUser = null;

export function getUser(): AuthUser {
    if (cachedUser) return cachedUser;
    try {
        cachedUser = JSON.parse(sessionStorage.getItem(USER_KEY) || 'null');
    } catch {
        cachedUser = null;
    }
    return cachedUser;
}

export function setUser(user: AuthUser): void {
    cachedUser = user ?? null;
    if (user) sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    else sessionStorage.removeItem(USER_KEY);
    window.dispatchEvent(new CustomEvent('auth:change', { detail: user }));
}

function emitReady() {
    window.dispatchEvent(new CustomEvent('auth:ready', { detail: getUser() }));
}

export function onAuthChange(fn: (user: AuthUser) => void): void {
    window.addEventListener('auth:change', (e) => fn((e as CustomEvent<AuthUser>).detail));
}

export function onAuthReady(fn: (user: AuthUser) => void): void {
    window.addEventListener('auth:ready', (e) => fn((e as CustomEvent<AuthUser>).detail));
}

export const authApi = {
    async login(email: AuthCredentials['email'], password: AuthCredentials['password']) {
        const data = await request<{ accessToken?: string }>(
            '/auth/login',
            { method: 'POST', body: JSON.stringify({ email, password }) },
            false,
        );

        const at = data?.accessToken ?? null;
        saveAccess(at);

        try {
            const me = await request<AuthUser>('/auth/me');
            setUser(me);
        } catch {
            setUser(userFromToken(at));
        }

        return at;
    },

    async register(email: AuthCredentials['email'], password: AuthCredentials['password']) {
        const data = await request<{ accessToken?: string }>(
            '/auth/register',
            { method: 'POST', body: JSON.stringify({ email, password }) },
            false,
        );

        const at = data?.accessToken ?? null;

        if (at) {
            saveAccess(at);
            try {
                const me = await request<AuthUser>('/auth/me');
                setUser(me);
            } catch {
                setUser(userFromToken(at));
            }
        } else {
            saveAccess(null);
            setUser(null);
        }

        return at;
    },

    async verifyEmail(token: VerifyEmailPayload['token']) {
        await request('/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) }, false);
    },

    async forgotPassword(email: AuthCredentials['email']) {
        await request('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) }, false);
    },

    async resetPassword(token: ResetPasswordPayload['token'], newPassword: ResetPasswordPayload['newPassword']) {
        await request('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, newPassword }) }, false);
    },

    async resendVerification(email: AuthCredentials['email']) {
        await request('/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email }) }, false);
    },

    me: () => request<AuthUser>('/auth/me'),

    logout: async () => {
        await request('/auth/logout', { method: 'POST' }, false);
        saveAccess(null);
        setUser(null);
    },
};

export async function bootstrapAuth() {
    if (!getAccess()) {
        setUser(null);
        emitReady();
        return null;
    }

    try {
        const me = await authApi.me();
        setUser(me);
        emitReady();
        return me;
    } catch {
        try {
            await doRefresh();
            try {
                const me2 = await authApi.me();
                setUser(me2);
                emitReady();
                return me2;
            } catch {
                const u = userFromToken(getAccess());
                setUser(u);
                emitReady();
                return u;
            }
        } catch {
            setUser(null);
            emitReady();
            return null;
        }
    }
}
