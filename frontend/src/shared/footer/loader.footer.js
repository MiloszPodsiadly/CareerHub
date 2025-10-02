import footerHtml from './footer.html?raw';
import './footer.css';

(() => {
    const mountId = 'shared-footer';
    let mount = document.getElementById(mountId);
    if (!mount) {
        mount = document.createElement('div');
        mount.id = mountId;
        document.body.appendChild(mount);
    }
    mount.innerHTML = footerHtml;

    import('./footer.js');
})();
