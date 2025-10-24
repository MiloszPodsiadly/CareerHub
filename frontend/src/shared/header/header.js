import { authApi, bootstrapAuth, getUser, onAuthChange, onAuthReady } from "../api.js";

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

    function renderUserUI(user) {
        var isLogged = !!user;
        show(guestEl, !isLogged);
        show(userEl,  isLogged);
        if (!isLogged || !avatarBtn || !metaBox) return;

        avatarBtn.innerHTML = '';
        var photo = user && user.avatarUrl;
        if (photo) {
            var img = document.createElement('img');
            img.src = photo; img.alt = 'Avatar';
            avatarBtn.appendChild(img);
        } else {
            var letter = (getSafe(user && (user.name || user.username), 'U'))
                .toString().charAt(0).toUpperCase();
            avatarBtn.textContent = letter;
        }

        var name  = getSafe(user && (user.name || user.username), 'User');
        var email = getSafe(user && user.email, '');
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
            try { await authApi.logout(); } catch (e) {}
            location.href = '/';
        });
    }

    renderUserUI(getUser());
    onAuthReady(renderUserUI);
    onAuthChange(renderUserUI);
    bootstrapAuth();
})();
