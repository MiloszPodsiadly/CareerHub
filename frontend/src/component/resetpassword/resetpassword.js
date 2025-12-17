import { authApi } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initResetPassword() {
    const mount = document.getElementById('view-root');
    if (!mount) return;

    const box = mount.querySelector('.auth');
    if (!box) return;

    ensureAuthWrapper(box);

    const params  = new URLSearchParams(location.search);
    const token   = params.get('token');
    const form    = box.querySelector('.js-form');
    const alertBox  = box.querySelector('.js-alert');
    const submitBtn = box.querySelector('.js-submit');

    if (!token) {
        alert('Missing reset token.');
        navigate('/auth/login');
        return;
    }

    box.querySelector('input[name="password"]')?.focus();

    form?.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();
        submitBtn.disabled = true;

        const fd = new FormData(form);
        const password = (fd.get('password') || '').toString();
        const confirmPassword = (fd.get('confirmPassword') || '').toString();

        try {
            if (!password || !confirmPassword) {
                showError('Please enter the new password twice.');
                return;
            }

            if (password.trim().length < 8) {
                showError('Password must be at least 8 characters.');
                return;
            }

            if (password !== confirmPassword) {
                showError('Passwords do not match.');
                return;
            }

            await authApi.resetPassword(token, password.trim());

            showSuccess('Password changed. You can now sign in.');
            setTimeout(() => navigate('/auth/login'), 800);
        } catch (err) {
            showError(err?.message || 'Invalid or expired reset link.');
        } finally {
            submitBtn.disabled = false;
        }
    });

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

    function hideAlert() {
        if (!alertBox) return;
        alertBox.style.display = 'none';
        alertBox.textContent = '';
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
