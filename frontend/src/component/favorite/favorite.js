export function initFavorites(){
    const tabs = Array.from(document.querySelectorAll('.fav__tab'));
    const panels = {
        jobs:   document.getElementById('fav-jobs'),
        events: document.getElementById('fav-events'),
    };

    const switchTo = (key) => {
        tabs.forEach(t => {
            const active = t.dataset.tab === key;
            t.classList.toggle('is-active', active);
            t.setAttribute('aria-selected', String(active));
        });
        Object.entries(panels).forEach(([k, el]) => {
            const on = k === key;
            el.classList.toggle('is-active', on);
            if (on) { el.removeAttribute('hidden'); el.focus(); }
            else    { el.setAttribute('hidden',''); }
        });
        const u = new URL(location.href);
        u.hash = key;
        history.replaceState({}, '', u);
    };

    const start = (location.hash || '').replace('#','');
    switchTo(start === 'events' ? 'events' : 'jobs');

    tabs.forEach(btn => btn.addEventListener('click', () => switchTo(btn.dataset.tab)));

    document.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-unfav]');
        if (!btn) return;
        const card = btn.closest('.fav-card');
        if (card) {
            card.style.opacity = '0';
            card.style.transform = 'translateY(4px)';
            setTimeout(() => {
                const panel = card.closest('.fav__panel');
                card.remove();
                if (!panel.querySelector('.fav-card')) {
                    const type = panel.id === 'fav-jobs' ? 'jobs' : 'events';
                    panel.querySelector(`[data-empty="${type}"]`)?.classList.remove('hidden');
                }
            }, 150);
        }
    });

    document.querySelectorAll('[data-clear]').forEach(btn => {
        btn.addEventListener('click', () => {
            const type = btn.getAttribute('data-clear');
            const panel = type === 'jobs' ? panels.jobs : panels.events;
            panel.querySelectorAll('.fav-card').forEach(c => c.remove());
            panel.querySelector(`[data-empty="${type}"]`)?.classList.remove('hidden');
        });
    });
}
