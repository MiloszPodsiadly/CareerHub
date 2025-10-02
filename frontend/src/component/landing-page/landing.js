import landingHtml from './landing.html?raw';
import './landing.css';

(() => {
    const mount = document.getElementById('landing-root');
    if (!mount) return;

    mount.innerHTML = landingHtml;

    const reveals = mount.querySelectorAll('.reveal');
    const observer = new IntersectionObserver(entries => {
        entries.forEach(e => {
            if (e.isIntersecting) {
                e.target.classList.add('visible');
                observer.unobserve(e.target);
            }
        });
    }, { threshold: 0.15 });
    reveals.forEach(el => observer.observe(el));

    const nums = mount.querySelectorAll('.stat__num');
    nums.forEach(el => {
        const target = parseInt(el.dataset.count, 10) || 0;
        let current = 0;
        const step = Math.ceil(target / 50);
        const interval = setInterval(() => {
            current += step;
            if (current >= target) {
                current = target;
                clearInterval(interval);
            }
            el.textContent = current;
        }, 30);
    });
})();
