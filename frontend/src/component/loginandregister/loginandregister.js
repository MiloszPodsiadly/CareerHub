import { authApi } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initLoginRegister(mode = 'login') {
    const mount = document.getElementById('view-root');
    if (!mount) return;

    const box = mount.querySelector('.auth');
    if (!box) return;

    const title     = box.querySelector('.js-title');
    const subtitle  = box.querySelector('.js-subtitle');
    const alertBox  = box.querySelector('.js-alert');
    const form      = box.querySelector('.js-form');
    const submitBtn = box.querySelector('.js-submit');
    const tabLogin  = box.querySelector('.js-tab-login');
    const tabReg    = box.querySelector('.js-tab-register');

    setMode(mode);

    tabLogin?.addEventListener('click', async (e) => {
        e.preventDefault();
        if (box.dataset.mode !== 'login') await navigate('/auth/login');
    });

    tabReg?.addEventListener('click', async (e) => {
        e.preventDefault();
        if (box.dataset.mode !== 'register') await navigate('/auth/register');
    });

    form?.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();
        submitBtn.disabled = true;

        const fd = new FormData(form);
        const username = (fd.get('username') || '').toString().trim();
        const password = (fd.get('password') || '').toString().trim();

        try {
            if (!username || !password) {
                showError('Please enter username and password.');
                return;
            }

            if (box.dataset.mode === 'login') {
                await authApi.login(username, password);
                await new Promise(requestAnimationFrame);
                await navigate('/');
            } else {
                await authApi.register(username, password);
                showSuccess('Account created. You are now signed in.');
                setTimeout(() => { navigate('/'); }, 200);
            }
        } catch (err) {
            showError(err?.message || 'Something went wrong.');
        } finally {
            submitBtn.disabled = false;
        }
    });

    function setMode(m) {
        box.dataset.mode = m;
        const isLogin = m === 'login';
        if (title)     title.textContent     = isLogin ? 'Sign in' : 'Create account';
        if (subtitle)  subtitle.textContent  = isLogin ? 'Enter your details to continue.' : 'Create a free account.';
        if (submitBtn) submitBtn.textContent = isLogin ? 'Sign in' : 'Sign up';
        tabLogin?.classList.toggle('active', isLogin);
        tabReg?.classList.toggle('active', !isLogin);
        hideAlert();
    }

    function showError (msg){
        if (!alertBox) return;
        alertBox.style.display = '';
        alertBox.className = 'error auth js-alert';
        alertBox.textContent = msg;
    }
    function showSuccess(msg){
        if (!alertBox) return;
        alertBox.style.display = '';
        alertBox.className = 'success auth js-alert';
        alertBox.textContent = msg;
    }
    function hideAlert(){
        if (!alertBox) return;
        alertBox.style.display = 'none';
        alertBox.textContent = '';
    }
}
