import html from './myapplications.html?raw';
import './myapplications.css';
import { initMyApplications } from './myapplications.js';
import { setView } from '../../shared/mount.js';

export function mountMyApplications() {
    const root = setView(`<section class="apps">${html}</section>`) || document;
    initMyApplications(root);
}
