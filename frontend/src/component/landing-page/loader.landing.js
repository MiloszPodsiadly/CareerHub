import landingHtml from './landing.html?raw';
import './landing.css';
import { setView } from '../../shared/mount.js';

export function mountLanding() {
    setView(landingHtml);

    const mount = document.getElementById('view-root');

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

}
