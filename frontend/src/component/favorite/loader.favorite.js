import pageHtml from './favorite.html?raw';
import './favorite.css';
import { initFavorites } from './favorite.js';
import { setView } from '../../shared/mount.js';

export function mountFavorite() {
    setView(pageHtml);
    initFavorites();
}
