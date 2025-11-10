import pageHtml from './jobexaclyoffer.html?raw';
import './jobexaclyoffer.css';
import { initJobExactlyOffer } from './jobexaclyoffer.js';
import { setView } from '../../shared/mount.js';

export function mountJobExactlyOffer() {
    const id = new URLSearchParams(location.search).get('id');
    let root = setView(pageHtml);

    if (!root || !root.matches?.('.jobx')) {
        root = document.querySelector('.jobx') || document.body;
    }

    initJobExactlyOffer(root, { id });
}
