import html from './myoffers.html?raw';
import './myoffers.css';
import { initMyOffers } from './myoffers.js';
import { setView } from '../../shared/mount.js';

export function mountMyOffers() {
    setView(`<section class="myoffers">${html}</section>`);
    initMyOffers();
}
