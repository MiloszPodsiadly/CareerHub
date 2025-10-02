import jobsHtml from './jobs.html?raw';
import './jobs.css';
import { initJobs } from './jobs.js';

export function mountJobs() {
    const root = document.getElementById('root');
    if (!root) return;

    root.innerHTML = `<main class="jobs">${jobsHtml}</main>`;
    initJobs();
}
