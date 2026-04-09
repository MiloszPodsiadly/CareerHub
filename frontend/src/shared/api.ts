export type AuthUser = {
    username?: string;
    name?: string;
    email?: string;
    avatarUrl?: string | null;
} | null;

export type AuthToken = string | null;

type AuthCredentials = { email: string; password: string };
type VerifyEmailPayload = { token: string };
type ResetPasswordPayload = { token: string; newPassword: string };

const ACCESS_KEY = 'accessToken';
const USER_KEY = 'ch.user';
const PUBLIC_AUTH_PATHS = new Set([
    '/auth/forgot-password',
    '/auth/reset-password',
    '/auth/register',
    '/auth/verify-email',
    '/auth/login',
    '/auth/refresh',
    '/auth/logout',
    '/auth/resend-verification',
]);

function readSession<T>(key: string): T | null {
    const raw = sessionStorage.getItem(key);
    if (!raw) return null;
    try {
        return JSON.parse(raw) as T;
    } catch {
        return null;
    }
}

function writeSession<T>(key: string, value: T | null) {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, JSON.stringify(value));
}

let accessToken: AuthToken = readSession<AuthToken>(ACCESS_KEY) ?? null;
let cachedUser: AuthUser = null;

export const getAccess = (): AuthToken => accessToken;

const saveAccess = (t: AuthToken) => {
    accessToken = t ?? null;
    writeSession(ACCESS_KEY, accessToken);
};

function safeJson(res: Response) {
    return res.json().catch(() => null);
}

function decodeJwtClaims(token: string) {
    try {
        const parts = String(token || '').split('.');
        if (parts.length < 2) return null;

        // base64url -> base64
        const b64url = parts[1];
        const b64 = b64url.replaceAll('-', '+').replaceAll('_', '/');
        const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);

        const bin = atob(padded);
        const bytes = Uint8Array.from(bin, (c) => c.codePointAt(0) ?? 0);
        const json = new TextDecoder('utf-8', { fatal: false }).decode(bytes);

        return JSON.parse(json);
    } catch {
        return null;
    }
}

function userFromToken(token: string | null): AuthUser {
    const c = decodeJwtClaims(token || '');
    if (!c) return null;

    const subject = String(c.sub || '');
    const username = c.preferred_username || c.username || subject || '';
    const name = c.name || c.given_name || username || 'User';
    const email = c.email || subject || '';
    const avatar = c.avatar || c.picture || null;

    return { username, name, email, avatarUrl: avatar };
}

const isAuthPath = (p: string) => p.startsWith('/auth/');
const isPublicAuthPath = (p: string) => PUBLIC_AUTH_PATHS.has(p);

function isPublicRequest(path: string, method: string): boolean {
    const m = method.toUpperCase();
    if (isPublicAuthPath(path)) return true;
    if (m === 'POST' && path === '/salary/calculate') return true;
    if (m !== 'GET') return false;
    if (path.startsWith('/public/')) return true;
    if (path.startsWith('/events/')) return true;
    if (path.startsWith('/salary/report/')) return true;
    if (/^\/favorites\/[^/]+\/[^/]+\/status$/.test(path)) return true;
    if (path === '/jobs') return true;
    if (path === '/jobs/fast') return true;
    if (path === '/jobs/count') return true;
    if (path === '/jobs/all') return true;
    if (path.startsWith('/jobs/by-external/')) return true;
    return path.startsWith('/jobs/') && path !== '/jobs/mine';
}

function buildHeaders(opts: RequestInit): Record<string, string> {
    const headers: Record<string, string> = { ...(opts.headers as Record<string, string> | undefined) };
    const hasBody = opts.body && !(opts.body instanceof FormData);
    if (hasBody && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
    return headers;
}

function credentialsFor(path: string): RequestCredentials {
    return isAuthPath(path) ? 'include' : 'same-origin';
}

function errorMessage(status: number, data: any): string {
    const map: Record<number, string> = { 401: 'Unauthorized', 403: 'No permissions' };
    return data?.error || data?.message || map[status] || `HTTP ${status}`;
}

let refreshPromise: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
    refreshPromise ??= fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
        .then(async (r) => {
            if (!r.ok) throw new Error('session_expired');
            const data = await safeJson(r);
            saveAccess(data?.accessToken ?? null);
            return true;
        })
        .catch((e) => {
            saveAccess(null);
            throw e;
        })
        .finally(() => {
            refreshPromise = null;
        });

    return refreshPromise;
}

async function rawFetch(path: string, opts: RequestInit, headers: Record<string, string>) {
    const method = String(opts.method || 'GET').toUpperCase();
    return fetch(`/api${path}`, { ...opts, method, headers, credentials: credentialsFor(path) });
}

async function handleResponse<T>(res: Response): Promise<T | null> {
    if (res.status === 204) return null;
    const data = await safeJson(res);
    if (!res.ok) throw new Error(errorMessage(res.status, data));
    return data as T;
}

async function request<T = any>(path: string, opts: RequestInit = {}, retry = true): Promise<T | null> {
    const method = String(opts.method || 'GET').toUpperCase();
    const headers = buildHeaders(opts);

    const publicReq = isPublicRequest(path, method);
    const sentAuth = !publicReq && Boolean(accessToken);

    if (sentAuth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const res = await rawFetch(path, opts, headers);
    if (res.status === 401 && retry && !isPublicAuthPath(path) && sentAuth) {
        try {
            await doRefresh();
            return request<T>(path, opts, false);
        } catch {
        }
    }

    return handleResponse<T>(res);
}

export function getUser(): AuthUser {
    cachedUser ??= readSession<AuthUser>(USER_KEY);
    return cachedUser;
}

export function setUser(user: AuthUser): void {
    cachedUser = user ?? null;
    writeSession(USER_KEY, cachedUser);
    globalThis.dispatchEvent(new CustomEvent('auth:change', { detail: cachedUser }));
}

function emitReady() {
    globalThis.dispatchEvent(new CustomEvent('auth:ready', { detail: getUser() }));
}

export function onAuthChange(fn: (user: AuthUser) => void): void {
    globalThis.addEventListener('auth:change', (e) => fn((e as CustomEvent<AuthUser>).detail));
}

export function onAuthReady(fn: (user: AuthUser) => void): void {
    globalThis.addEventListener('auth:ready', (e) => fn((e as CustomEvent<AuthUser>).detail));
}

export const authApi = {
    async login(email: AuthCredentials['email'], password: AuthCredentials['password']) {
        const normEmail = String(email || '').trim().toLowerCase();

        const data = await request<{ accessToken?: string }>(
            '/auth/login',
            { method: 'POST', body: JSON.stringify({ email: normEmail, password }) },
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
        const normEmail = String(email || '').trim().toLowerCase();

        const data = await request<{ accessToken?: string }>(
            '/auth/register',
            { method: 'POST', body: JSON.stringify({ email: normEmail, password }) },
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
        const normEmail = String(email || '').trim().toLowerCase();
        await request('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email: normEmail }) }, false);
    },

    async resetPassword(token: ResetPasswordPayload['token'], newPassword: ResetPasswordPayload['newPassword']) {
        await request('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, newPassword }) }, false);
    },

    async resendVerification(email: AuthCredentials['email']) {
        const normEmail = String(email || '').trim().toLowerCase();
        await request('/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email: normEmail }) }, false);
    },

    me: () => request<AuthUser>('/auth/me'),

    logout: async () => {
        await request('/auth/logout', { method: 'POST' }, false);
        saveAccess(null);
        setUser(null);
    },
};

// ✅ KLUCZ: zawsze spróbuj refresh cookie przy starcie,
// nawet jeśli accessToken zniknął z sessionStorage.
export async function bootstrapAuth() {
    // 1) Jeśli jest access — spróbuj /me
    if (getAccess()) {
        try {
            const me = await authApi.me();
            setUser(me);
            emitReady();
            return me;
        } catch {
            // idziemy do refresh
        }
    }

    // 2) Spróbuj refresh (cookie)
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
        saveAccess(null);
        setUser(null);
        emitReady();
        return null;
    }
}
