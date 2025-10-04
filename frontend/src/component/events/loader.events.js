import eventsHtml from './events.html?raw';
import './events.css';
import { initEvents } from './events.js';

export function mountEvents(){
    const root = document.getElementById('root');
    if (!root) return;
    root.innerHTML = `<main class="events">${eventsHtml}</main>`;
    initEvents();
}
