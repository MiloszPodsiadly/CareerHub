import resetHtml from './resetpassword.html?raw';
import './resetpassword.css';
import { initResetPassword } from './resetpassword.js';
import { setView } from '../../shared/mount.js';

export function mountResetPassword() {
    setView(resetHtml);
    initResetPassword();
}
