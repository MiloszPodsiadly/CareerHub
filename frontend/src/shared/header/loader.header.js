import headerHtml from './header.html?raw';

(() => {
    const mount = document.getElementById('shared-header');
    if (!mount) return;

    mount.innerHTML = headerHtml;

    import('./header.js');
})();
