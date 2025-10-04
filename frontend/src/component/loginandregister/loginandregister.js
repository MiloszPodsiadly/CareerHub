import { authApi } from '../../shared/api.js'
import { navigate } from '../../router.js'

export function initLoginRegister (mode = 'login') {
    const root = document.getElementById('root')
    const box  = root.querySelector('.auth')
    if (!box) return

    const title     = box.querySelector('.js-title')
    const subtitle  = box.querySelector('.js-subtitle')
    const alertBox  = box.querySelector('.js-alert')
    const form      = box.querySelector('.js-form')
    const submitBtn = box.querySelector('.js-submit')
    const tabLogin  = box.querySelector('.js-tab-login')
    const tabReg    = box.querySelector('.js-tab-register')

    setMode(mode)

    tabLogin.addEventListener('click', async (e) => {
        e.preventDefault(); await navigate('/auth/login')
    })
    tabReg.addEventListener('click', async (e) => {
        e.preventDefault(); await navigate('/auth/register')
    })

    form.addEventListener('submit', async (e) => {
        e.preventDefault()
        hideAlert(); submitBtn.disabled = true

        const fd = new FormData(form)
        const username = (fd.get('username') || '').trim()
        const password = (fd.get('password') || '').trim()

        try {
            if (box.dataset.mode === 'login') {
                await authApi.login(username, password)
                await navigate('/')
            } else {
                await authApi.register(username, password)
                showSuccess('Konto utworzone. Zalogowano automatycznie.')
                setTimeout(() => navigate('/'), 600)
            }
        } catch (err) {
            showError(err.message || 'Wystąpił błąd.')
        } finally {
            submitBtn.disabled = false
        }
    })

    function setMode (m) {
        box.dataset.mode = m
        const isLogin = m === 'login'
        title.textContent = isLogin ? 'Zaloguj się' : 'Załóż konto'
        subtitle.textContent = isLogin ? 'Wprowadź dane, aby kontynuować.' : 'Utwórz bezpłatne konto.'
        submitBtn.textContent = isLogin ? 'Zaloguj' : 'Zarejestruj'
        tabLogin.classList.toggle('active', isLogin)
        tabReg.classList.toggle('active', !isLogin)
        hideAlert()
    }
    function showError (msg){ alertBox.style.display=''; alertBox.className='error auth js-alert'; alertBox.textContent=msg }
    function showSuccess (msg){ alertBox.style.display=''; alertBox.className='success auth js-alert'; alertBox.textContent=msg }
    function hideAlert (){ alertBox.style.display='none'; alertBox.textContent='' }
}
