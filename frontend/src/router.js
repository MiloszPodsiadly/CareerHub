const routes = {
    '/':     () => import('./component/landing-page/loader.landing.js')
        .then(m => m.mountLanding()),
    '/jobs': () => import('./component/jobsoffers/loader.jobs.js')
        .then(m => m.mountJobs()),
    '/events': () => import('./component/events/loader.events.js')
        .then(m => m.mountEvents()),
    '/salary-calculator': () => import('./component/salary-calculator/loader.salary-calculator.js')
        .then(m => m.mountSalaryCalculator()),
};

export async function render() {
    const path = Object.prototype.hasOwnProperty.call(routes, location.pathname)
        ? location.pathname
        : '/';
    await routes[path]();
}

export async function navigate(path) {
    if (location.pathname === path) return;
    history.pushState({}, '', path);
    await render();
}

document.addEventListener('click', async (e) => {
    const a = e.target.closest('a[href^="/"]');
    if (!a) return;
    const url = new URL(a.href);
    const sameOrigin = url.origin === location.origin;
    const handled = Object.prototype.hasOwnProperty.call(routes, url.pathname);
    if (sameOrigin && handled) {
        e.preventDefault();
        await navigate(url.pathname);
    }
});

window.addEventListener('popstate', () => { render(); });

render();
