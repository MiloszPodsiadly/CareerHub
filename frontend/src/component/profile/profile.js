import { getUser, setUser, getAccess } from '../../shared/api.js';

export async function initProfile(opts = {}) {
    const API_BASE = opts.apiBase ?? '';

    const form = document.getElementById('profileForm');
    if (!form) return;

    const avatarPreview = document.getElementById('avatarPreview');
    const avatarFile    = document.getElementById('avatarFile');
    const clearAvatar   = document.getElementById('clearAvatar');

    const nameInput   = document.getElementById('name');
    const emailInput  = document.getElementById('email');
    const aboutInput  = document.getElementById('about');

    const dobDay   = document.getElementById('dobDay');
    const dobMonth = document.getElementById('dobMonth');
    const dobYear  = document.getElementById('dobYear');

    const cvFile  = document.getElementById('cvFile');
    const cvInfo  = document.getElementById('cvInfo');
    const clearCv = document.getElementById('clearCv');

    const presetGrid = document.getElementById('presetGrid');
    const resetBtn   = document.getElementById('resetBtn');

    const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
    const makeInitials = (t) => (t||'?').trim().slice(0,1).toUpperCase();
    const api  = (p) => `${API_BASE}${p}`;
    const fileUrl = (id) => api(`/api/profile/file/${encodeURIComponent(id)}`);
    const auth = () => { const t = getAccess?.(); return t ? { Authorization: `Bearer ${t}` } : {}; };

    // helpers
    async function loadProtectedImage(url) {
        const res = await fetch(url, { headers: { ...auth() }, credentials: 'include' });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const blob = await res.blob();
        return URL.createObjectURL(blob);
    }
    async function downloadProtected(url, filename) {
        const res = await fetch(url, { headers: { ...auth() }, credentials: 'include' });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const blob = await res.blob();
        const obj = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = obj; a.download = filename || 'file';
        document.body.appendChild(a); a.click(); a.remove();
        URL.revokeObjectURL(obj);
    }

    async function resolveAvatarSrc(profile) {
        if (profile?.avatarFileId) {
            try { return await loadProtectedImage(fileUrl(profile.avatarFileId)); } catch {}
        }
        return profile?.avatarUrl || profile?.avatarPreset || null;
    }

    let previewObjectUrl = null;
    function drawAvatarUrl(url, fallback) {
        avatarPreview.innerHTML = '';
        if (url) {
            const img = document.createElement('img');
            img.src = url; img.alt = 'Avatar';
            avatarPreview.appendChild(img);
        } else {
            avatarPreview.textContent = makeInitials(fallback);
        }
    }
    async function drawAvatarFromProfile(profile, fallback) {
        const src = await resolveAvatarSrc(profile);
        drawAvatarUrl(src, fallback);
    }
    function broadcastHeader(url, initials) {
        window.dispatchEvent(new CustomEvent('app:avatar-preview', {
            detail: { url: url || null, initials: initials || '?' }
        }));
    }

    function renderCvInfo({ name, size, onClick } = {}) {
        if (!name) { cvInfo.textContent = 'No file.'; return; }
        const sizeMb = size ? ` • ${(size/1024/1024).toFixed(2)} MB` : '';
        cvInfo.innerHTML = `<button type="button" class="btn--ghost" style="padding:0;border:0;background:none;text-decoration:underline;cursor:pointer">${name}</button>${sizeMb}`;
        cvInfo.querySelector('button').addEventListener('click', (e) => { e.preventDefault(); onClick?.(); });
    }

    const PRESETS = Array.from({ length: 5 }, (_, i) => `/assets/component/profile/avatar/avatar${i+1}.svg`);
    let avatarBlob=null, avatarPreset=null, cvBlob=null, cvBlobUrl=null;

    function renderPresets(activeUrl){
        presetGrid.innerHTML = '';
        PRESETS.forEach((src, i) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.setAttribute('role','option');
            btn.setAttribute('aria-selected', String(activeUrl === src));
            btn.title = `Avatar ${i+1}`;
            btn.innerHTML = `<img src="${src}" alt="avatar ${i+1}" width="56" height="56" loading="lazy" />`;
            btn.className = (activeUrl === src) ? 'active' : '';
            btn.addEventListener('click', () => {
                avatarPreset = src; avatarBlob = null;
                if (previewObjectUrl) { URL.revokeObjectURL(previewObjectUrl); previewObjectUrl = null; }
                [...presetGrid.children].forEach(c => { c.classList.remove('active'); c.setAttribute('aria-selected','false'); });
                btn.classList.add('active'); btn.setAttribute('aria-selected','true');
                drawAvatarUrl(src);
                broadcastHeader(src, makeInitials(nameInput.value || emailInput.value));
            });
            presetGrid.appendChild(btn);
        });
    }

    function fillYears(){ const y=new Date().getFullYear(); dobYear.innerHTML=`<option value="" disabled selected>Year</option>`;
        for(let yy=y-14; yy>=y-70; yy--){ const o=document.createElement('option'); o.value=String(yy); o.textContent=String(yy); dobYear.appendChild(o); } }
    function fillMonths(){ dobMonth.innerHTML=`<option value="" disabled selected>Month</option>`;
        MONTHS.forEach((m,i)=>{ const o=document.createElement('option'); o.value=String(i+1); o.textContent=m; dobMonth.appendChild(o); }); }
    const daysInMonth=(Y,M)=>(!Y||!M)?31:new Date(+Y,+M,0).getDate();
    function fillDays(){ const Y=dobYear.value,M=dobMonth.value,c=daysInMonth(Y,M),cur=dobDay.value;
        dobDay.innerHTML=`<option value="" disabled selected>Day</option>`;
        for(let d=1; d<=c; d++){ const o=document.createElement('option'); o.value=String(d); o.textContent=String(d); dobDay.appendChild(o); }
        if(cur && +cur<=c) dobDay.value=cur; }
    dobYear.addEventListener('change', fillDays);
    dobMonth.addEventListener('change', fillDays);

    let profile = {};
    try {
        const res = await fetch(api('/api/profile'), { credentials:'include', headers:{ Accept:'application/json', ...auth() } });
        if (!res.ok) throw new Error(res.status);
        profile = await res.json();
    } catch(e){ console.warn('Profile GET failed', e); }

    const original = JSON.parse(JSON.stringify(profile));

    nameInput.value   = profile?.name ?? (getUser()?.username ?? '');
    emailInput.value  = profile?.email ?? '';
    aboutInput.value  = profile?.about ?? '';

    await drawAvatarFromProfile(profile, nameInput.value || emailInput.value);
    renderPresets(profile?.avatarPreset || profile?.avatarUrl || null);
    broadcastHeader(await resolveAvatarSrc(profile), makeInitials(nameInput.value || emailInput.value));

    fillYears(); fillMonths(); fillDays();
    if (profile?.dob) {
        const [yy, mm, dd] = String(profile.dob).split('-').map(Number);
        if (yy) dobYear.value=String(yy);
        if (mm) dobMonth.value=String(mm);
        fillDays();
        if (dd) dobDay.value=String(dd);
    }

    function hydrateCvFromProfile(p) {
        if (p?.cvFileId) {
            renderCvInfo({
                name: 'CV',
                onClick: () => downloadProtected(`${fileUrl(p.cvFileId)}/download`, 'CV')
            });
        } else {
            renderCvInfo();
        }
    }
    hydrateCvFromProfile(profile);

    avatarFile.addEventListener('change', () => {
        const f = avatarFile.files?.[0]; if(!f) return;
        if (f.size > 2*1024*1024) { alert('File too large (max 2 MB).'); avatarFile.value=''; return; }

        if (previewObjectUrl) { URL.revokeObjectURL(previewObjectUrl); previewObjectUrl = null; }

        avatarBlob = f; avatarPreset = null;
        previewObjectUrl = URL.createObjectURL(f);
        drawAvatarUrl(previewObjectUrl);
        broadcastHeader(previewObjectUrl, makeInitials(nameInput.value || emailInput.value));
    });

    clearAvatar.addEventListener('click', async () => {
        try {
            const res = await fetch(api('/api/profile/avatar'), { method:'DELETE', credentials:'include', headers:auth() });
            if (!res.ok) throw 0;
            profile.avatarFileId=profile.avatarUrl=profile.avatarPreset=null;
        } catch {}
        if (previewObjectUrl) { URL.revokeObjectURL(previewObjectUrl); previewObjectUrl = null; }
        avatarBlob=null; avatarPreset=null; avatarFile.value='';
        const initials = makeInitials(nameInput.value || emailInput.value);
        drawAvatarUrl(null, initials);
        broadcastHeader(null, initials);
        [...presetGrid.children].forEach(c=>c.classList.remove('active'));
    });

    cvFile.addEventListener('change', () => {
        const f=cvFile.files?.[0];
        if(!f){ cvBlob=null; if (cvBlobUrl){URL.revokeObjectURL(cvBlobUrl); cvBlobUrl=null;} renderCvInfo(); return; }
        if (f.size > 5*1024*1024) { alert('CV too large (max 5 MB).'); cvFile.value=''; return; }

        cvBlob=f;
        if (cvBlobUrl) { URL.revokeObjectURL(cvBlobUrl); cvBlobUrl=null; }
        cvBlobUrl = URL.createObjectURL(f);

        renderCvInfo({
            name: f.name,
            size: f.size,
            onClick: () => {
                const a = document.createElement('a');
                a.href = cvBlobUrl;
                a.download = f.name || 'cv';
                document.body.appendChild(a);
                a.click();
                a.remove();
            }
        });
    });

    clearCv.addEventListener('click', async () => {
        try { await fetch(api('/api/profile/cv'), { method:'DELETE', credentials:'include', headers:auth() }); } catch {}
        cvFile.value=''; cvBlob=null;
        if (cvBlobUrl){ URL.revokeObjectURL(cvBlobUrl); cvBlobUrl=null; }
        renderCvInfo();
        profile.cvFileId=null;
    });

    resetBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        nameInput.value   = original?.name ?? (getUser()?.username ?? '');
        emailInput.value  = original?.email ?? '';
        aboutInput.value  = original?.about ?? '';

        if (previewObjectUrl) { URL.revokeObjectURL(previewObjectUrl); previewObjectUrl = null; }
        avatarBlob=null; avatarPreset=null; avatarFile.value='';
        await drawAvatarFromProfile(original, nameInput.value || emailInput.value);
        renderPresets(original?.avatarPreset || original?.avatarUrl || null);
        broadcastHeader(await resolveAvatarSrc(original), makeInitials(nameInput.value || emailInput.value));

        dobYear.value=''; dobMonth.value=''; fillDays(); dobDay.value='';
        if (original?.dob) {
            const [yy,mm,dd]=String(original.dob).split('-').map(Number);
            if (yy) dobYear.value=String(yy); if (mm) dobMonth.value=String(mm); fillDays(); if (dd) dobDay.value=String(dd);
        }

        cvFile.value=''; cvBlob=null;
        if (cvBlobUrl){ URL.revokeObjectURL(cvBlobUrl); cvBlobUrl=null; }
        hydrateCvFromProfile(original);
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const y=dobYear.value, m=dobMonth.value, d=dobDay.value;
        const dob = (y&&m&&d) ? `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}` : '';

        const fd = new FormData();
        fd.append('name', nameInput.value.trim());
        fd.append('email', emailInput.value);
        fd.append('about', aboutInput.value.trim());
        if (dob) fd.append('dob', dob);
        if (avatarBlob) fd.append('avatar', avatarBlob);
        else if (avatarPreset) fd.append('avatarPreset', avatarPreset);
        if (cvBlob) fd.append('cv', cvBlob);

        try {
            const res = await fetch(api('/api/profile'), { method:'PUT', body:fd, credentials:'include', headers:auth() });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const updated = await res.json();

            Object.assign(profile, updated);
            setUser({ ...getUser(), ...updated });

            await drawAvatarFromProfile(updated, nameInput.value || emailInput.value);
            if (previewObjectUrl) { URL.revokeObjectURL(previewObjectUrl); previewObjectUrl = null; }
            broadcastHeader(await resolveAvatarSrc(updated), makeInitials(nameInput.value || emailInput.value));

            if (updated?.cvFileId) {
                renderCvInfo({
                    name: cvBlob?.name || 'CV',
                    size: cvBlob?.size ?? 0,
                    onClick: () => downloadProtected(`${fileUrl(updated.cvFileId)}/download`, cvBlob?.name || 'CV')
                });
            } else {
                renderCvInfo();
            }
            if (cvBlobUrl){ URL.revokeObjectURL(cvBlobUrl); cvBlobUrl=null; }
            cvBlob = null;

            alert('Profile changes saved.');
        } catch (err) {
            console.error(err);
            alert('Could not save profile.');
        }
    });
}
