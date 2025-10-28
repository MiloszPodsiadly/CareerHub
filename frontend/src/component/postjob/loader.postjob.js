import postjobHtml from './postjob.html?raw';
import './postjob.css';
import { initPostJob } from './postjob.js';
import { setView } from '../../shared/mount.js';

export function mountPostJob() {
    setView(`<section class="postjob">${postjobHtml}</section>`);
    initPostJob();
}
