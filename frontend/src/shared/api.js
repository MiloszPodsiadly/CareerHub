// — token z gatewaya —
let accessToken = JSON.parse(sessionStorage.getItem('accessToken') || 'null');
const saveAccess = (t) => { accessToken = t ?? null;
    if (t) sessionStorage.setItem('accessToken', JSON.stringify(t));
    else sessionStorage.removeItem('accessToken'); };
export const getAccess = () => accessToken;

// — prosty request z auto-refresh —
let refreshPromise = null;
const isAuthPath = (p) => p.startsWith('/auth/');
async function doRefresh(){
    if (!refreshPromise) {
        refreshPromise = fetch('/api/auth/refresh', { method:'POST', credentials:'include' })
            .then(async r => { refreshPromise=null; if(!r.ok) throw new Error('session_expired');
                const { accessToken: at } = await r.json(); saveAccess(at); return true; })
            .catch(e => { refreshPromise=null; saveAccess(null); throw e; });
    }
    return refreshPromise;
}
async function request(path, opts={}, retry=true){
    const headers = { ...(opts.headers||{}) };
    const hasBody = opts.body && !(opts.body instanceof FormData);
    if (hasBody) headers['Content-Type'] = 'application/json';
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

    const res = await fetch(`/api${path}`, { ...opts, headers, credentials:'include' });
    if (res.status === 401 && retry && !isAuthPath(path)) { try{ await doRefresh(); return request(path,opts,false);}catch{} }
    if (res.status === 204) return null;
    let data=null; try{ data = await res.json(); }catch{}
    if(!res.ok){ const map={401:'Incorrect username or password',403:'No permissions'};
        throw new Error(data?.error||data?.message||map[res.status]||`HTTP ${res.status}`); }
    return data;
}

// — store użytkownika + event —
const USER_KEY='ch.user'; let cachedUser=null;
export function getUser(){ if(cachedUser) return cachedUser;
    try{ cachedUser = JSON.parse(sessionStorage.getItem(USER_KEY)||'null'); }catch{ cachedUser=null; }
    return cachedUser; }
export function setUser(user){
    cachedUser = user ?? null;
    if(user) sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    else sessionStorage.removeItem(USER_KEY);
    window.dispatchEvent(new CustomEvent('auth:change', { detail:user }));
}
export function onAuthChange(fn){ window.addEventListener('auth:change', e=>fn(e.detail)); }

// — auth API —
export const authApi = {
    async login(username, password){
        const { accessToken: at } = await request('/auth/login',
            { method:'POST', body: JSON.stringify({ username, password }) }, false);
        saveAccess(at);
        try { const me = await authApi.me(); setUser(me); } catch { setUser(null); }
        return at;
    },
    async register(username, password){
        const { accessToken: at } = await request('/auth/register',
            { method:'POST', body: JSON.stringify({ username, password }) }, false);
        saveAccess(at);
        try { const me = await authApi.me(); setUser(me); } catch { setUser(null); }
        return at;
    },
    me: () => request('/auth/me'),
    logout: async () => { await request('/auth/logout', { method:'POST' }, false); saveAccess(null); setUser(null); },
};

// — bootstrap przy starcie (po F5) —
export async function bootstrapAuth(){
    if (!getAccess()) { setUser(null); return null; }
    try { const me = await authApi.me(); setUser(me); return me; }
    catch { setUser(null); return null; }
}
