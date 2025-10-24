import headerHtml from './header.html?raw';

(async () => {
    const mount = document.getElementById('shared-header');
    if (!mount) return;
    mount.innerHTML = headerHtml;

    try {
        await import('./header.js');
    } catch (e) {
        console.error('Header init failed:', e);
    }
})();
