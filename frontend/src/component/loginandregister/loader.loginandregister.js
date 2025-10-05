import pageHtml from './loginandregister.html?raw';
import './loginandregister.css';
import { initLoginRegister } from './loginandregister.js';
import { setView } from '../../shared/mount.js';

function mount(mode) {
    setView(`<section class="auth-wrap">${pageHtml}</section>`);
    initLoginRegister(mode);
}

export function mountLogin()    { mount('login'); }
export function mountRegister() { mount('register'); }
