import landingHtml from './landing.html?raw';
import './landing.css';

function mountLanding() {
    const mount = document.getElementById('landing-root');
    if (!mount) return false;

    mount.innerHTML = landingHtml;

    const reveals = mount.querySelectorAll('.reveal');
    const io = new IntersectionObserver((entries) => {
        entries.forEach((e) => {
            if (e.isIntersecting) { e.target.classList.add('visible'); io.unobserve(e.target); }
        });
    }, { threshold: 0.15 });
    reveals.forEach((el) => io.observe(el));

    const nums = mount.querySelectorAll('.stat__num');
    nums.forEach((el) => {
        const target = parseInt(el.dataset.count || '0', 10);
        let current = 0;
        const step = Math.max(1, Math.ceil(target / 50));
        const id = setInterval(() => {
            current += step;
            if (current >= target) { current = target; clearInterval(id); }
            el.textContent = current;
        }, 30);
    });

    return true;
}

if (!mountLanding()) {
    const mo = new MutationObserver(() => {
        if (mountLanding()) mo.disconnect();
    });
    mo.observe(document.body, { childList: true, subtree: true });
}
