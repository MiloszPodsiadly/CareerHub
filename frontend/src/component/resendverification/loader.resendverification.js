import html from './resendverification.html?raw';
import './resendverification.css';
import { initResendVerification } from './resendverification.js';
import { setView } from '../../shared/mount.js';

export function mountResendVerification() {
    setView(html);
    initResendVerification();
}
