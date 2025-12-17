import { authApi } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initVerify() {
    const mount = document.getElementById('view-root');
    if (!mount) return;

    const box = mount.querySelector('.auth');
    if (!box) return;

    ensureAuthWrapper(box);

    const alertBox = box.querySelector('.js-alert');
    const params = new URLSearchParams(location.search);
    const token = params.get('token');

    if (!token) {
        showError('Missing verification token.');
        return;
    }

    (async () => {
        try {
            await authApi.verifyEmail(token);
            showSuccess('Email verified! You can now sign in.');
            setTimeout(() => navigate('/auth/login'), 900);
        } catch (err) {
            showError(err?.message || 'Invalid or expired verification link.');
        }
    })();

    function showError(msg) {
        if (!alertBox) return;
        alertBox.style.display = '';
        alertBox.className = 'error auth js-alert';
        alertBox.textContent = msg;
    }

    function showSuccess(msg) {
        if (!alertBox) return;
        alertBox.style.display = '';
        alertBox.className = 'success auth js-alert';
        alertBox.textContent = msg;
    }
}

function ensureAuthWrapper(box) {
    let wrapper = box.parentElement;
    if (!wrapper || !wrapper.classList.contains('auth-wrap')) {
        wrapper = document.createElement('div');
        wrapper.className = 'auth-wrap';
        box.parentElement?.insertBefore(wrapper, box);
        wrapper.appendChild(box);
    }

    wrapper.style.setProperty('margin-top', '0px', 'important');

    if (!wrapper.style.padding) wrapper.style.padding = '24px 16px';
    if (!wrapper.style.display) wrapper.style.display = 'grid';
    if (!wrapper.style.justifyContent) wrapper.style.justifyContent = 'center';
}
