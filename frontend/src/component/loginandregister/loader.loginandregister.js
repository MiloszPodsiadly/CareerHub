import pageHtml from './loginandregister.html?raw'
import './loginandregister.css'
import { initLoginRegister } from './loginandregister.js'

export function mountLogin () {
    const root = document.getElementById('root')
    if (!root) return
    root.innerHTML = `<main class="auth-wrap">${pageHtml}</main>`
    initLoginRegister('login')
}

export function mountRegister () {
    const root = document.getElementById('root')
    if (!root) return
    root.innerHTML = `<main class="auth-wrap">${pageHtml}</main>`
    initLoginRegister('register')
}
