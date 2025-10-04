let accessToken = JSON.parse(sessionStorage.getItem('accessToken') || 'null');

const saveAccess = (t) => {
    accessToken = t ?? null;
    if (t) sessionStorage.setItem('accessToken', JSON.stringify(t));
    else sessionStorage.removeItem('accessToken');
};

export const setAccess   = saveAccess;
export const clearAccess = () => saveAccess(null);
export const getAccess   = () => accessToken;

let refreshPromise = null;
const isAuthPath = (path) => path.startsWith('/auth/');

async function doRefresh() {
    if (!refreshPromise) {
        refreshPromise = fetch('/api/auth/refresh', {
            method: 'POST',
            credentials: 'include'
        }).then(async r => {
            refreshPromise = null;
            if (!r.ok) throw new Error('session_expired');
            const { accessToken: at } = await r.json();
            saveAccess(at);
            return true;
        }).catch(err => {
            refreshPromise = null;
            saveAccess(null);
            throw err;
        });
    }
    return refreshPromise;
}

async function request(path, opts = {}, retry = true) {
    const headers = { ...(opts.headers || {}) };

    const hasBody = opts.body && !(opts.body instanceof FormData);
    if (hasBody) headers['Content-Type'] = 'application/json';

    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const res = await fetch(`/api${path}`, {
        ...opts,
        headers,
        credentials: 'include',
    });

    if (res.status === 401 && retry && !isAuthPath(path)) {
        try {
            await doRefresh();
            return request(path, opts, false);
        } catch {}
    }

    if (res.status === 204) return null;

    let data = null;
    try { data = await res.json(); } catch (_) {}

    if (!res.ok) {
        const map = { 401: 'Incorrect username or password', 403: 'No permissions' };
        const msg = data?.error || data?.message || map[res.status] || `HTTP ${res.status}`;
        throw new Error(msg);
    }

    return data;
}

export const authApi = {
    async login(username, password) {
        const { accessToken: at } = await request(
            '/auth/login',
            { method: 'POST', body: JSON.stringify({ username, password }) },
            false
        );
        saveAccess(at);
        return at;
    },

    async register(username, password) {
        const { accessToken: at } = await request(
            '/auth/register',
            { method: 'POST', body: JSON.stringify({ username, password }) },
            false
        );
        saveAccess(at);
        return at;
    },

    me: () => request('/auth/me'),

    logout: async () => {
        await request('/auth/logout', { method: 'POST' }, false);
        clearAccess();
    },
};
