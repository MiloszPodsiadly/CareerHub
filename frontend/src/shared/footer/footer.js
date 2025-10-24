(function () {
    var yearEl = document.getElementById('ftr-year');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    var form = document.getElementById('ftr-newsletter');
    if (!form) return;

    var msg = form.parentNode ? form.parentNode.querySelector('.nl__msg') : null;

    form.addEventListener('submit', function (e) {
        e.preventDefault();

        var fd = new FormData(form);
        var email = (fd.get('email') || '').toString().trim();
        var valid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);

        if (!valid) {
            if (msg) {
                msg.textContent = 'Please enter a valid email address.';
                msg.style.color = '#e11d48';
            }
            return;
        }

        if (msg) {
            msg.textContent = 'Thanks! Please confirm your subscription via email.';
            msg.style.color = '';
        }

        var ev;
        try {
            ev = new CustomEvent('footer:newsletter', { detail: { email: email } });
        } catch (err) {
            ev = document.createEvent('CustomEvent');
            ev.initCustomEvent('footer:newsletter', true, true, { email: email });
        }
        document.dispatchEvent(ev);

        form.reset();
    });
})();
