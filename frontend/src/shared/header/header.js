import { authApi, bootstrapAuth, getUser, onAuthChange } from "../api.js";

(function () {
    const guestEl  = document.querySelector("[data-guest]");
    const userEl   = document.querySelector("[data-user]");
    const avatarBtn= document.querySelector("[data-avatar]");
    const metaBox  = document.querySelector("[data-user-meta]");
    const dd       = document.querySelector("[data-dd]");
    const logoutBtn= document.querySelector("[data-logout]");

    const show = (el, on) => {
        if (!el) return;
        el.hidden = !on;
        el.setAttribute('aria-hidden', (!on).toString());
        el.style.display = on ? '' : 'none';   // <- dopinka „na twardo”
    };

    function renderUserUI(user){
        const isLogged = !!user;
        show(guestEl, !isLogged);
        show(userEl,  isLogged);

        if (!isLogged || !avatarBtn || !metaBox) return;

        avatarBtn.innerHTML = "";
        const photo = user?.avatarUrl;
        if (photo) {
            const img = document.createElement("img"); img.src = photo; img.alt = "Avatar";
            avatarBtn.appendChild(img);
        } else {
            avatarBtn.textContent = (user?.name || user?.username || "U").slice(0,1).toUpperCase();
        }
        metaBox.innerHTML = `<strong>${user?.name || user?.username || "Użytkownik"}</strong>${user?.email || ""}`;
    }

    if (dd && avatarBtn){
        avatarBtn.addEventListener("click", () => {
            dd.classList.toggle("open");
            avatarBtn.setAttribute("aria-expanded", dd.classList.contains("open") ? "true" : "false");
        });
        document.addEventListener("click", (e) => { if (!dd.contains(e.target)) dd.classList.remove("open"); });
    }

    if (logoutBtn){
        logoutBtn.addEventListener("click", async () => {
            try { await authApi.logout(); } catch {}
            location.href = "/";
        });
    }

    // KOLEJNOŚĆ:
    renderUserUI(getUser());
    onAuthChange(renderUserUI);
    bootstrapAuth();
})();
