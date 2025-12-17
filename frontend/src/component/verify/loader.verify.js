import verifyHtml from './verify.html?raw';
import './verify.css';
import { initVerify } from './verify.js';
import { setView } from '../../shared/mount.js';

export function mountVerify() {
    setView(verifyHtml);
    initVerify();
}
