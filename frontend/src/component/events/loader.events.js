import eventsHtml from './events.html?raw';
import './events.css';
import { initEvents } from './events.js';
import { setView } from '../../shared/mount.js';

export function mountEvents() {
    setView(`<section class="events">${eventsHtml}</section>`);
    initEvents();
}
