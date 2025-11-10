import { authApi, bootstrapAuth, getUser, getAccess, onAuthChange, onAuthReady } from "../api.js";

(function headerBoot() {
    const guestEl   = document.querySelector("[data-guest]");
    const userEl    = document.querySelector("[data-user]");
    const avatarBtn = document.querySelector("[data-avatar]");
    const metaBox   = document.querySelector("[data-user-meta]");
    const dd        = document.querySelector("[data-dd]");
    const logoutBtn = document.querySelector("[data-logout]");

    function show(el, on) {
        if (!el) return;
        el.hidden = !on;
        el.setAttribute("aria-hidden", (!on).toString());
        el.style.display = on ? "" : "none";
    }
    const getSafe = (prop, fallback) => (prop === undefined || prop === null ? fallback : prop);
    const authHeaders = () => {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    };
    const fileUrl = (id) => `/api/profile/file/${encodeURIComponent(id)}`;

    async function loadProtectedImage(url) {
        const res = await fetch(url, { headers: { ...authHeaders() }, credentials: "include" });
        if (!res.ok) throw new Error(`avatar fetch failed ${res.status}`);
        const blob = await res.blob();
        return URL.createObjectURL(blob);
    }

    let headerBlobUrl = null;
    function drawHeaderAvatar(url, initials) {
        if (!avatarBtn) return;
        avatarBtn.innerHTML = "";

        if (headerBlobUrl && headerBlobUrl !== url) {
            try { URL.revokeObjectURL(headerBlobUrl); } catch {}
            headerBlobUrl = null;
        }

        if (url) {
            const img = document.createElement("img");
            img.src = url;
            img.alt = "Avatar";
            avatarBtn.appendChild(img);
            if (url.startsWith("blob:")) headerBlobUrl = url;
        } else {
            const letter = (initials || "U").toString().charAt(0).toUpperCase();
            avatarBtn.textContent = letter;
        }
    }

    async function fetchProfile() {
        try {
            const res = await fetch("/api/profile", { headers: { ...authHeaders() }, credentials: "include" });
            if (!res.ok) return null;
            return await res.json();
        } catch {
            return null;
        }
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

        const name  = getSafe(user && (user.name || user.username), "User");
        const email = getSafe(user && user.email, "");

        const url = await resolveAvatarForHeader(user);
        drawHeaderAvatar(url, name);

        metaBox.innerHTML = email
            ? `<strong>${name}</strong><br>${email}`
            : `<strong>${name}</strong>`;
    }

    if (dd && avatarBtn) {
        const closeDd = () => { dd.classList.remove("open"); avatarBtn.setAttribute("aria-expanded", "false"); };
        avatarBtn.addEventListener("click", function () {
            const on = !dd.classList.contains("open");
            dd.classList.toggle("open", on);
            avatarBtn.setAttribute("aria-expanded", on ? "true" : "false");
        });
        document.addEventListener("click", function (e) {
            if (!dd.contains(e.target)) closeDd();
        });
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") closeDd();
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener("click", async function () {
            try { await authApi.logout(); } catch {}
            if (headerBlobUrl) { try { URL.revokeObjectURL(headerBlobUrl); } catch {} headerBlobUrl = null; }
            location.href = "/";
        });
    }

    window.addEventListener("app:avatar-preview", function (e) {
        const d = e.detail || {};
        drawHeaderAvatar(d.url || null, d.initials || "U");
    });

    const NAV_MAP = [
        { test: /^\/jobs(\/|$)/,               sel: '[data-nav="jobs"]' },
        { test: /^\/events(\/|$)/,             sel: '[data-nav="events"]' },
        { test: /^\/salary-calculator(\/|$)/,  sel: '[data-nav="salary"]' },
        { test: /^\/my-offers(\/|$)/,          sel: '[data-nav="myoffers"]' },
        { test: /^\/favorite(\/|$)/,           sel: '[data-nav="fav"]' },
        { test: /^\/my-applications(\/|$)/,    sel: '[data-nav="apps"]' },
    ];
    function updateActiveNav() {
        const path = location.pathname;
        NAV_MAP.forEach(m => {
            const el = document.querySelector(m.sel);
            if (el) el.classList.toggle("is-active", m.test.test(path));
        });
    }
    updateActiveNav();
    window.addEventListener("popstate", updateActiveNav);
    window.addEventListener("app:navigate", updateActiveNav);

    (async () => { await renderUserUI(getUser()); })();
    onAuthReady(renderUserUI);
    onAuthChange(async (user) => {
        if (!user) {
            if (headerBlobUrl) { try { URL.revokeObjectURL(headerBlobUrl); } catch {} headerBlobUrl = null; }
            show(guestEl, true);
            show(userEl, false);
            return;
        }
        await renderUserUI(user);
    });

    bootstrapAuth();
})();
