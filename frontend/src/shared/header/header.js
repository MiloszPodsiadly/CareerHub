import { authApi, bootstrapAuth, getUser, getAccess, onAuthChange, onAuthReady } from "../api.js";

(function () {
    var guestEl   = document.querySelector("[data-guest]");
    var userEl    = document.querySelector("[data-user]");
    var avatarBtn = document.querySelector("[data-avatar]");
    var metaBox   = document.querySelector("[data-user-meta]");
    var dd        = document.querySelector("[data-dd]");
    var logoutBtn = document.querySelector("[data-logout]");

    function show(el, on) {
        if (!el) return;
        el.hidden = !on;
        el.setAttribute('aria-hidden', (!on).toString());
        el.style.display = on ? '' : 'none';
    }
    function getSafe(prop, fallback) { return (prop === undefined || prop === null) ? fallback : prop; }

    const authHeaders = () => { const t = getAccess?.(); return t ? { Authorization: `Bearer ${t}` } : {}; };
    const fileUrl = (id) => `/api/profile/file/${encodeURIComponent(id)}`;

    async function loadProtectedImage(url) {
        const res = await fetch(url, { headers: { ...authHeaders() }, credentials: 'include' });
        if (!res.ok) throw new Error(`avatar fetch failed ${res.status}`);
        const blob = await res.blob();
        return URL.createObjectURL(blob);
    }

    let headerBlobUrl = null;

    function drawHeaderAvatar(url, initials) {
        if (!avatarBtn) return;
        avatarBtn.innerHTML = '';

        if (headerBlobUrl && headerBlobUrl !== url) {
            URL.revokeObjectURL(headerBlobUrl);
            headerBlobUrl = null;
        }

        if (url) {
            const img = document.createElement('img');
            img.src = url;
            img.alt = 'Avatar';
            avatarBtn.appendChild(img);
            if (url.startsWith('blob:')) headerBlobUrl = url;
        } else {
            const letter = (initials || 'U').toString().charAt(0).toUpperCase();
            avatarBtn.textContent = letter;
        }
    }

    async function fetchProfile() {
        try {
            const res = await fetch('/api/profile', { headers: { ...authHeaders() }, credentials: 'include' });
            if (!res.ok) return null;
            return await res.json();
        } catch { return null; }
    }

    async function resolveAvatarForHeader(user) {
        const p = await fetchProfile();
        if (p) {
            if (p.avatarFileId) {
                try { return await loadProtectedImage(fileUrl(p.avatarFileId)); } catch {}
            }
            return p.avatarUrl || p.avatarPreset || null;
        }
        return user?.avatarUrl || user?.avatarPreset || null;
    }

    async function renderUserUI(user) {
        const isLogged = !!user;
        show(guestEl, !isLogged);
        show(userEl,  isLogged);
        if (!isLogged || !avatarBtn || !metaBox) return;

        const name  = getSafe(user && (user.name || user.username), 'User');
        const email = getSafe(user && user.email, '');

        const url = await resolveAvatarForHeader(user);
        drawHeaderAvatar(url, name);

        metaBox.innerHTML = '<strong>' + name + '</strong>' + email;
    }

    if (dd && avatarBtn) {
        avatarBtn.addEventListener('click', function () {
            dd.classList.toggle('open');
            avatarBtn.setAttribute('aria-expanded', dd.classList.contains('open') ? 'true' : 'false');
        });
        document.addEventListener('click', function (e) {
            if (!dd.contains(e.target)) dd.classList.remove('open');
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', async function () {
            try { await authApi.logout(); } catch {}
            if (headerBlobUrl) { URL.revokeObjectURL(headerBlobUrl); headerBlobUrl = null; }
            location.href = '/';
        });
    }

    window.addEventListener('app:avatar-preview', function (e) {
        const d = e.detail || {};
        drawHeaderAvatar(d.url || null, d.initials || 'U');
    });

    (async () => { await renderUserUI(getUser()); })();

    onAuthReady(renderUserUI);
    onAuthChange(async (user) => {
        if (!user) {
            if (headerBlobUrl) { URL.revokeObjectURL(headerBlobUrl); headerBlobUrl = null; }
            show(guestEl, true); show(userEl, false);
            return;
        }
        await renderUserUI(user);
    });

    bootstrapAuth();
})();
