import { getUser, setUser, getAccess } from '../../shared/api.js';

const MONTHS = [
    'January','February','March','April','May','June',
    'July','August','September','October','November','December'
];

export function initProfile(){
    const form = document.getElementById('profileForm');
    if (!form) return;

    const avatarPreview = document.getElementById('avatarPreview');
    const avatarFile    = document.getElementById('avatarFile');
    const clearAvatar   = document.getElementById('clearAvatar');

    const nameInput  = document.getElementById('name');
    const emailInput = document.getElementById('email');
    const descInput  = document.getElementById('desc');

    const dobDay   = document.getElementById('dobDay');
    const dobMonth = document.getElementById('dobMonth');
    const dobYear  = document.getElementById('dobYear');

    const cvFile  = document.getElementById('cvFile');
    const cvInfo  = document.getElementById('cvInfo');
    const clearCv = document.getElementById('clearCv');

    const presetGrid = document.getElementById('presetGrid');
    const resetBtn   = document.getElementById('resetBtn');

    const user = getUser() || {};
    const original = JSON.parse(JSON.stringify(user || {}));
    let avatarBlob = null;
    let avatarPreset = null;
    let cvBlob = null;

    const makeInitials = (txt) => (txt || '?').trim().slice(0,1).toUpperCase();

    function drawAvatar(url, fallbackLetter){
        avatarPreview.innerHTML = '';
        if (url) {
            const img = document.createElement('img');
            img.src = url; img.alt = 'Avatar';
            avatarPreview.appendChild(img);
        } else {
            avatarPreview.textContent = makeInitials(fallbackLetter);
        }
    }

    const PRESETS = Array.from({ length: 5 }, (_, i) =>
        `/assets/component/profile/avatar/avatar${i + 1}.svg`
    );

    function renderPresets(activeUrl){
        presetGrid.innerHTML = '';
        PRESETS.forEach((src, idx) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.setAttribute('role','option');
            btn.setAttribute('aria-selected', String(activeUrl === src));
            btn.title = `Avatar ${idx + 1}`;
            btn.innerHTML = `<img src="${src}" alt="avatar ${idx + 1}" width="56" height="56" loading="lazy" />`;
            btn.className = (activeUrl === src) ? 'active' : '';
            btn.addEventListener('click', () => {
                avatarPreset = src; avatarBlob = null;
                [...presetGrid.children].forEach(c => { c.classList.remove('active'); c.setAttribute('aria-selected','false'); });
                btn.classList.add('active'); btn.setAttribute('aria-selected','true');
                drawAvatar(src);
            });
            presetGrid.appendChild(btn);
        });
    }

    function fillYears(){
        const now = new Date().getFullYear();
        const min = now - 70;
        const max = now - 14;
        dobYear.innerHTML = `<option value="" disabled selected>Year</option>`;
        for (let y = max; y >= min; y--) {
            const opt = document.createElement('option');
            opt.value = String(y); opt.textContent = String(y);
            dobYear.appendChild(opt);
        }
    }
    function fillMonths(){
        dobMonth.innerHTML = `<option value="" disabled selected>Month</option>`;
        MONTHS.forEach((m, idx) => {
            const opt = document.createElement('option');
            opt.value = String(idx + 1); opt.textContent = m;
            dobMonth.appendChild(opt);
        });
    }
    function daysInMonth(year, month){
        if (!year || !month) return 31;
        return new Date(Number(year), Number(month), 0).getDate();
    }
    function fillDays(){
        const y = dobYear.value; const m = dobMonth.value;
        const count = daysInMonth(y, m);
        const current = dobDay.value;
        dobDay.innerHTML = `<option value="" disabled selected>Day</option>`;
        for (let d=1; d<=count; d++){
            const opt = document.createElement('option');
            opt.value = String(d); opt.textContent = String(d);
            dobDay.appendChild(opt);
        }
        if (current && Number(current) <= count) dobDay.value = current;
    }
    dobYear.addEventListener('change', fillDays);
    dobMonth.addEventListener('change', fillDays);

    const fullName = user?.name || user?.username || '';
    const email    = user?.email || '';
    const desc     = user?.desc || user?.bio || '';

    nameInput.value  = fullName;
    emailInput.value = email;
    descInput.value  = desc;

    const startAvatar = user?.avatarUrl || null;
    drawAvatar(startAvatar, fullName);
    renderPresets(startAvatar);

    fillYears(); fillMonths(); fillDays();
    if (user?.dob) {
        const [yy, mm, dd] = String(user.dob).split('-').map(Number);
        if (yy) dobYear.value = String(yy);
        if (mm) dobMonth.value = String(mm);
        fillDays();
        if (dd) dobDay.value = String(dd);
    }

    avatarFile.addEventListener('change', () => {
        const f = avatarFile.files?.[0];
        if (!f) return;
        if (f.size > 2 * 1024 * 1024) { alert('File too large (max 2 MB).'); avatarFile.value=''; return; }
        avatarBlob = f; avatarPreset = null;
        const url = URL.createObjectURL(f);
        drawAvatar(url);
    });
    clearAvatar.addEventListener('click', () => {
        avatarBlob = null; avatarPreset = null; avatarFile.value='';
        drawAvatar(null, nameInput.value || email);
        [...presetGrid.children].forEach(c => c.classList.remove('active'));
    });

    cvFile.addEventListener('change', () => {
        const f = cvFile.files?.[0];
        if (!f) { cvInfo.textContent = 'No file.'; cvBlob = null; return; }
        if (f.size > 5 * 1024 * 1024) { alert('CV too large (max 5 MB).'); cvFile.value=''; return; }
        cvBlob = f;
        cvInfo.textContent = `${f.name} • ${(f.size/1024/1024).toFixed(2)} MB`;
    });
    clearCv.addEventListener('click', () => {
        cvFile.value = ''; cvBlob = null; cvInfo.textContent = 'No file.';
    });

    resetBtn.addEventListener('click', (e) => {
        e.preventDefault();
        nameInput.value  = original?.name || original?.username || '';
        emailInput.value = original?.email || '';
        descInput.value  = original?.desc || original?.bio || '';
        avatarBlob = null; avatarPreset = original?.avatarUrl || null; avatarFile.value='';
        drawAvatar(original?.avatarUrl || null, nameInput.value || emailInput.value);
        renderPresets(original?.avatarUrl || null);

        dobYear.value = ''; dobMonth.value = ''; fillDays(); dobDay.value = '';
        if (original?.dob) {
            const [yy, mm, dd] = String(original.dob).split('-').map(Number);
            if (yy) dobYear.value = String(yy);
            if (mm) dobMonth.value = String(mm);
            fillDays();
            if (dd) dobDay.value = String(dd);
        }

        cvFile.value=''; cvBlob=null; cvInfo.textContent='No file.';
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const fullName = nameInput.value.trim();
        const about    = descInput.value.trim();

        const y = dobYear.value, m = dobMonth.value, d = dobDay.value;
        const dob = (y && m && d) ? `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}` : '';

        const fd = new FormData();
        fd.append('name', fullName);
        fd.append('email', emailInput.value);
        fd.append('desc', about);
        if (dob) fd.append('dob', dob);

        if (avatarBlob) fd.append('avatar', avatarBlob);
        else if (avatarPreset) fd.append('avatarPreset', avatarPreset);

        if (cvBlob) fd.append('cv', cvBlob);

        try {
            const token = getAccess?.() || null;
            const res = await fetch('/api/profile', {
                method: 'PUT',
                body: fd,
                credentials: 'include',
                headers: token ? { Authorization: `Bearer ${token}` } : {}
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const updated = await res.json();

            setUser(updated);
            alert('Profile changes saved.');
        } catch (err) {
            console.error(err);
            alert('Could not save profile.');
        }
    });
}
