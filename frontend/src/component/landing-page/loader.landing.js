import landingHtml from './landing.html?raw';
import './landing.css';
import { navigate } from '../../router.js';

export function mountLanding() {
    const root = document.getElementById('root');
    if (!root) return;

    root.innerHTML = `<div id="landing-root"></div>`;
    const mount = document.getElementById('landing-root');
    mount.innerHTML = landingHtml;

    const reveals = mount.querySelectorAll('.reveal');
    const io = new IntersectionObserver((entries) => {
        entries.forEach(e => {
            if (e.isIntersecting) { e.target.classList.add('visible'); io.unobserve(e.target); }
        });
    }, { threshold: 0.15 });
    reveals.forEach(el => io.observe(el));

    mount.querySelectorAll('.stat__num').forEach(el => {
        const target = parseInt(el.dataset.count || '0', 10);
        let cur = 0, step = Math.max(1, Math.ceil(target / 50));
        const id = setInterval(() => {
            cur = Math.min(cur + step, target);
            el.textContent = cur;
            if (cur === target) clearInterval(id);
        }, 30);
    });

    mount.querySelectorAll('a[href="/jobs"]').forEach(a => {
        a.addEventListener('click', (e) => { e.preventDefault(); navigate('/jobs'); });
    });
}
