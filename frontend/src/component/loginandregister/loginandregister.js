import { authApi } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initLoginRegister(mode = 'login') {
    const mount = document.getElementById('view-root');
    if (!mount) return;

    waitForAuthBox(mount, (box) => setup(box, mode));

    function waitForAuthBox(root, cb) {
        const now = root.querySelector('.auth');
        if (now) return cb(now);

        const obs = new MutationObserver(() => {
            const el = root.querySelector('.auth');
            if (el) {
                obs.disconnect();
                cb(el);
            }
        });
        obs.observe(root, { childList: true, subtree: true });

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                const el = root.querySelector('.auth');
                if (el) {
                    obs.disconnect();
                    cb(el);
                }
            });
        });
    }

    function setup(box, initialMode) {
        ensureAuthWrapper(box);

        const title = box.querySelector('.js-title');
        const subtitle = box.querySelector('.js-subtitle');
        const alertBox = box.querySelector('.js-alert');
        const form = box.querySelector('.js-form');
        const submitBtn = box.querySelector('.js-submit');
        const tabLogin = box.querySelector('.js-tab-login');
        const tabReg = box.querySelector('.js-tab-register');

        setMode(initialMode);
        box.querySelector('input[name="email"]')?.focus();

        tabLogin?.addEventListener('click', async (e) => {
            e.preventDefault();
            if (box.dataset.mode !== 'login') {
                await navigate('/auth/login');
            }
        });

        tabReg?.addEventListener('click', async (e) => {
            e.preventDefault();
            if (box.dataset.mode !== 'register') {
                await navigate('/auth/register');
            }
        });

        form?.addEventListener('submit', async (e) => {
            e.preventDefault();
            hideAlert();
            if (submitBtn) submitBtn.disabled = true;

            const fd = new FormData(form);
            const email = (fd.get('email') || '').toString().trim();
            const password = (fd.get('password') || '').toString().trim();

            try {
                if (!email || !password) {
                    showError('Please enter email and password.');
                    return;
                }

                const isRegister = box.dataset.mode === 'register';

                if (isRegister) {
                    if (!isValidEmail(email)) {
                        showError('Please enter a valid e-mail address.');
                        return;
                    }
                    if (password.length < 8) {
                        showError('Password must be at least 8 characters.');
                        return;
                    }
                }

                if (box.dataset.mode === 'login') {
                    await authApi.login(email, password);
                    await new Promise(requestAnimationFrame);
                    await navigate('/');
                } else {
                    // ✅ rejestracja: brak auto-login (mail verification)
                    await authApi.register(email, password);

                    showSuccess('Account created. Check your email to verify your account, then sign in.');
                    setTimeout(() => navigate('/auth/login'), 900);
                }
            } catch (err) {
                showError(err?.message || 'Something went wrong.');
            } finally {
                if (submitBtn) submitBtn.disabled = false;
            }
        });

        function setMode(m) {
            box.dataset.mode = m;
            const isLogin = m === 'login';
            const resendWrap = box.querySelector('.resend-wrap');
            if (title) title.textContent = isLogin ? 'Sign in' : 'Create account';
            if (subtitle) subtitle.textContent = isLogin ? 'Enter your details to continue.' : 'Create a free account.';
            if (submitBtn) submitBtn.textContent = isLogin ? 'Sign in' : 'Sign up';
            if (resendWrap) resendWrap.style.display = (m === 'register') ? '' : 'none';

            tabLogin?.classList.toggle('active', isLogin);
            tabReg?.classList.toggle('active', !isLogin);

            const passInput = box.querySelector('input[name="password"]');
            if (passInput) {
                passInput.setAttribute('autocomplete', isLogin ? 'current-password' : 'new-password');
            }

            hideAlert();
        }

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
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}
