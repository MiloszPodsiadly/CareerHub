import jobsHtml from './jobs.html?raw';
import './jobs.css';
import { initJobs } from './jobs.js';
import { setView } from '../../shared/mount.js';

export function mountJobs() {
    setView(`<section class="jobs">${jobsHtml}</section>`);
    initJobs();
}
