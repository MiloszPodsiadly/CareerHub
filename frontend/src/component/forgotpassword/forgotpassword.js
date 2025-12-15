import { authApi } from '../../shared/api.js';

export function initForgotPassword() {
    const mount = document.getElementById('view-root');
    if (!mount) return;

    const box = mount.querySelector('.auth');
    if (!box) return;

    ensureAuthWrapper(box);

    const form      = box.querySelector('.js-form');
    const alertBox  = box.querySelector('.js-alert');
    const submitBtn = box.querySelector('.js-submit');

    box.querySelector('input[name="email"]')?.focus();

    form?.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();
        submitBtn.disabled = true;

        const fd    = new FormData(form);
        const email = (fd.get('email') || '').toString().trim();

        try {
            if (!email) {
                showError('Please enter your e-mail.');
                return;
            }

            await authApi.forgotPassword(email);

            showSuccess('If this e-mail exists in our system, a reset link has been sent.');
        } catch (err) {
            showError(err?.message || 'Something went wrong.');
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
