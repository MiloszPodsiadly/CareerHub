import forgotHtml from './forgotpassword.html?raw';
import './forgotpassword.css';
import { initForgotPassword } from './forgotpassword.js';
import { setView } from '../../shared/mount.js';

export function mountForgotPassword() {
    setView(forgotHtml);
    initForgotPassword();
}
