import { bootstrapAuth } from './shared/api.js';
import { clearView } from './shared/mount.js';
import {mountJobExactlyOffer} from "./component/jobexaclyoffer/loader.jobexaclyoffer.js";
import {mountMyApplications} from "./component/myapplications/loader.myapplications.js";

const routes = {
    '/':     () => import('./component/landing-page/loader.landing.js').then(m => m.mountLanding()),
    '/jobs': () => import('./component/jobsoffers/loader.jobs.js').then(m => m.mountJobs()),
    '/events': () => import('./component/events/loader.events.js').then(m => m.mountEvents()),
    '/salary-calculator': () => import('./component/salary-calculator/loader.salary-calculator.js').then(m => m.mountSalaryCalculator()),
    '/auth/login':    () => import('./component/loginandregister/loader.loginandregister.js').then(m => m.mountLogin()),
    '/auth/register': () => import('./component/loginandregister/loader.loginandregister.js').then(m => m.mountRegister()),
    '/profile': () => import('./component/profile/loader.profile.js').then(m => m.mountProfile()),
    '/favorite': () => import('./component/favorite/loader.favorite.js').then(m => m.mountFavorite()),
    '/post-job': () => import('./component/postjob/loader.postjob.js').then(m => m.mountPostJob()),
    '/my-offers': () => import('./component/myoffers/loader.myoffers.js').then(m => m.mountMyOffers()),
    '/jobexaclyoffer': () => import('./component/jobexaclyoffer/loader.jobexaclyoffer.js').then(m => m.mountJobExactlyOffer()),
    '/my-applications': () => import('./component/myapplications/loader.myapplications.js').then(m => m.mountMyApplications()),
};

const FALLBACK = '/';

export async function render() {
    clearView();
    const path = Object.prototype.hasOwnProperty.call(routes, location.pathname)
        ? location.pathname
        : FALLBACK;
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

await bootstrapAuth();
render();
