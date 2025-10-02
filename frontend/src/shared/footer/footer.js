(() => {
    const yearEl = document.getElementById('ftr-year');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    const form = document.getElementById('ftr-newsletter');
    if (form) {
        const msg = form.parentElement.querySelector('.nl__msg');
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const email = new FormData(form).get('email')?.toString().trim();
            if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
                msg.textContent = 'Podaj poprawny adres e-mail.';
                msg.style.color = '#e11d48';
                return;
            }
            msg.textContent = 'Dzięki! Potwierdź zapis w wiadomości e-mail.';
            msg.style.color = 'inherit';

            document.dispatchEvent(new CustomEvent('footer:newsletter', { detail: { email } }));
            form.reset();
        });
    }
})();
