import pageHtml from './profile.html?raw';
import './profile.css';
import { initProfile } from './profile.js';
import { setView } from '../../shared/mount.js';

export function mountProfile() {
    setView(`<section class="profile">${pageHtml}</section>`);
    initProfile();
}