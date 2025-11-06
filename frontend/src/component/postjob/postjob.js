import { getAccess } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initPostJob() {
    const $  = (sel, ctx = document) => ctx.querySelector(sel);
    const $$ = (sel, ctx = document) => [...ctx.querySelectorAll(sel)];
    const api = (p) => p;
    const auth = () => {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    };

    const form = $('#postjob-form');
    if (!form) return;

    const submitBtn = $('#submit-btn');
    const msg = $('#form-msg');

    const desc = form.description;
    const descCount = $('#desc-count');

    const pv = {
        title: $('#pv-title'),
        company: $('#pv-company'),
        salary: $('#pv-salary'),
        level:  $('#pv-level'),
        contract: $('#pv-contract'),
        remote: $('#pv-remote'),
        tags: $('#pv-tags')
    };

    const state = { tags: [], skills: [] };

    const updatePreview = () => {
        pv.title.textContent = form.title.value || 'Job title';
        const company = form.companyName.value || 'Company';
        const city = form.cityName.value || '—';
        pv.company.textContent = `${company} • ${city}`;
        pv.level.textContent = form.level.value || '—';
        pv.contract.textContent = form.contract.value || '—';
        pv.remote.textContent = form.remote.checked ? 'Remote' : 'On-site';

        const min = form.salaryMin.value ? Number(form.salaryMin.value) : null;
        const max = form.salaryMax.value ? Number(form.salaryMax.value) : null;
        const cur = form.currency.value || '';
        pv.salary.textContent = (min || max)
            ? `${min ? min.toLocaleString() : '—'} – ${max ? max.toLocaleString() : '—'} ${cur}`
            : '—';

        pv.tags.innerHTML = '';
        state.tags.forEach(t => {
            const el = document.createElement('span');
            el.className = 'pill';
            el.textContent = t;
            pv.tags.appendChild(el);
        });
    };

    const tagsBox  = $('#tags');
    const tagInput = $('#tag-input');

    function renderTags() {
        tagsBox.innerHTML = '';
        state.tags.forEach((tag, i) => {
            const chip = document.createElement('span');
            chip.className = 'chip';
            chip.innerHTML = `${tag} <button title="Remove" aria-label="Remove ${tag}" data-i="${i}">×</button>`;
            tagsBox.appendChild(chip);
        });
        updatePreview();
    }

    tagsBox.addEventListener('click', (e) => {
        if (e.target.tagName === 'BUTTON') {
            const i = Number(e.target.dataset.i);
            state.tags.splice(i, 1);
            renderTags();
            scheduleSave();
        }
    });

    tagInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const val = tagInput.value.trim();
            if (!val) return;
            if (!state.tags.includes(val)) {
                state.tags.push(val);
                renderTags();
                scheduleSave();
            }
            tagInput.value = '';
        }
    });

    const skillsList = $('#skills-list');
    const addSkillBtn = $('#add-skill');

    const SKILL_SOURCES = ['REQUIRED','NICE_TO_HAVE','STACK','LD'];

    function newSkillRow(skill = {name:'', levelLabel:'', levelValue:'', source:'REQUIRED'}) {
        const row = document.createElement('div');
        row.className = 'skill-row';
        row.innerHTML = `
      <input placeholder="Name (e.g. Java, Spring)" value="${skill.name || ''}" />
      <input placeholder="Level label (e.g. senior)" value="${skill.levelLabel || ''}" />
      <input type="number" min="0" max="10" step="1" placeholder="Level (0-10)" value="${skill.levelValue ?? ''}" />
      <select>${SKILL_SOURCES.map(s=>`<option ${skill.source===s?'selected':''}>${s}</option>`).join('')}</select>
      <button type="button" class="skill-remove" title="Remove">×</button>
    `;
        skillsList.appendChild(row);
    }

    addSkillBtn.addEventListener('click', () => {
        newSkillRow();
        scheduleSave();
    });

    skillsList.addEventListener('click', (e) => {
        if (e.target.classList.contains('skill-remove')) {
            e.target.parentElement.remove();
            scheduleSave();
        }
    });

    const bindPreviewFields = ['title','companyName','cityName','salaryMin','salaryMax','currency','level','contract','remote'];
    bindPreviewFields.forEach(name =>
        form[name].addEventListener('input', () => { updatePreview(); scheduleSave(); })
    );
    desc.addEventListener('input', () => { if (descCount) descCount.textContent = desc.value.length; scheduleSave(); });

    function setError(el, message) {
        const field = el.closest('.field') || el.closest('.card') || el;
        removeError(field);

        const input = field.querySelector('input,select,textarea') || el;
        input.setAttribute('aria-invalid', 'true');

        const errId = `${input.name || input.id || 'fld'}-err`;
        const small = document.createElement('div');
        small.className = 'error';
        small.id = errId;
        small.textContent = message;
        field.appendChild(small);
        input.setAttribute('aria-describedby', errId);

        field.classList.add('invalid');
        field.classList.remove('valid');

        const card = field.closest('.card');
        if (card) card.classList.add('card--invalid');
    }

    function removeError(field) {
        const input = field.querySelector?.('input,select,textarea');
        const prev = field.querySelector?.('.error');
        if (prev) prev.remove();
        field.classList.remove('invalid');
        if (input) {
            input.removeAttribute('aria-invalid');
            input.removeAttribute('aria-describedby');
        }
        const card = field.closest?.('.card');
        if (card && !card.querySelector('.invalid')) {
            card.classList.remove('card--invalid');
        }
    }

    function clearErrors() {
        [...form.querySelectorAll('.invalid')].forEach(removeError);
        [...form.querySelectorAll('.card--invalid')].forEach(c => c.classList.remove('card--invalid'));
    }

    function validateField(input) {
        const field = (input.closest && input.closest('.field')) || input;
        if (field.classList.contains('invalid')) removeError(field);

        if (input === form.title && !form.title.value.trim()) {
            setError(field, 'Required.');
            return false;
        }
        if (input === form.companyName && !form.companyName.value.trim()) {
            setError(field, 'Required.');
            return false;
        }
        if (input === form.level && !form.level.value) {
            setError(field, 'Select a level.');
            return false;
        }
        if (input === form.salaryMin || input === form.salaryMax) {
            const min = form.salaryMin.value ? Number(form.salaryMin.value) : null;
            const max = form.salaryMax.value ? Number(form.salaryMax.value) : null;
            if (min !== null && max !== null && min > max) {
                setError(form.salaryMax.closest('.field'), 'Max cannot be less than Min.');
                return false;
            }
        }

        field.classList.add('valid');
        return true;
    }

    function scrollToFirstError() {
        const firstInvalid = form.querySelector('.invalid input, .invalid select, .invalid textarea');
        if (firstInvalid) {
            firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
            firstInvalid.focus({ preventScroll: true });
        }
    }

    function validate() {
        clearErrors();
        let ok = true;
        ok &= validateField(form.title);
        ok &= validateField(form.companyName);
        ok &= validateField(form.level);
        ok &= validateField(form.salaryMin);
        ok &= validateField(form.salaryMax);
        if (!ok) scrollToFirstError();
        return !!ok;
    }

    const liveValidate = [form.title, form.companyName, form.level, form.salaryMin, form.salaryMax];
    liveValidate.forEach(inp => {
        const markDirty = () => inp.closest('.field')?.classList.add('dirty');
        inp.addEventListener('input', () => { markDirty(); validateField(inp); });
        inp.addEventListener('blur',  () => { markDirty(); validateField(inp); });
    });

    function serialize() {
        const contracts = $$('input[name="contracts"]:checked', form).map(c => c.value);
        const skills = $$('.skill-row', skillsList).map(row => {
            const [name, levelLabel, levelValue, source] = $$('input, select', row);
            const val = levelValue.value ? Number(levelValue.value) : null;
            return {
                name: name.value.trim(),
                levelLabel: levelLabel.value.trim() || null,
                levelValue: Number.isFinite(val) ? val : null,
                source: source.value
            };
        }).filter(s => s.name);

        return {
            source: form.source.value.trim() || 'platform',
            externalId: form.externalId.value.trim() || null,
            url: null,
            title: form.title.value.trim(),
            description: form.description.value.trim() || null,
            companyName: form.companyName.value.trim(),
            cityName: form.cityName.value.trim() || null,
            remote: form.remote.checked,
            level: form.level.value || null,
            contract: form.contract.value || null,
            contracts,
            salaryMin: form.salaryMin.value ? Number(form.salaryMin.value) : null,
            salaryMax: form.salaryMax.value ? Number(form.salaryMax.value) : null,
            currency: form.currency.value || null,
            techTags: [...state.tags],
            techStack: skills,
            publishedAt: form.publishedAt.value ? new Date(form.publishedAt.value).toISOString() : null,
            active: form.active.checked
        };
    }

    let DRAFT_ID = null;
    let saveTimer = null;

    async function ensureDraft() {
        try {
            const r = await fetch(api('/api/job-drafts/latest'), {
                headers: { Accept:'application/json', ...auth() },
                credentials:'include'
            });
            if (r.ok) {
                const d = await r.json();
                if (d?.id) {
                    DRAFT_ID = d.id;
                    hydrateFromPayload(d.payloadJson);
                    return;
                }
            } else if (r.status === 401) {
                await navigate('/auth/login');
                return;
            }
        } catch {}

        const res = await fetch(api('/api/job-drafts'), {
            method:'POST',
            headers: { Accept:'application/json', ...auth() },
            credentials:'include'
        });
        if (!res.ok) {
            await navigate('/auth/login');
            throw new Error('Cannot create draft');
        }
        const created = await res.json();
        DRAFT_ID = created.id;
    }

    function hydrateFromPayload(payloadJson) {
        try {
            const d = JSON.parse(payloadJson || '{}');
            form.source.value = d.source || 'platform';
            form.externalId.value = d.externalId || '';
            form.title.value = d.title || '';
            form.description.value = d.description || '';
            form.companyName.value = d.companyName || '';
            form.cityName.value = d.cityName || '';
            form.remote.checked = !!d.remote;
            form.level.value = d.level || '';
            form.contract.value = d.contract || '';
            (d.contracts || []).forEach(v => {
                const cb = $$('input[name="contracts"]').find(x => x.value === v);
                if (cb) cb.checked = true;
            });
            form.salaryMin.value = d.salaryMin ?? '';
            form.salaryMax.value = d.salaryMax ?? '';
            form.currency.value = d.currency || 'PLN';
            state.tags = d.techTags || [];
            renderTags();
            skillsList.innerHTML = '';
            (d.techStack || []).forEach(s => newSkillRow(s));
            if (d.publishedAt) form.publishedAt.value = d.publishedAt.slice(0,16);
            form.active.checked = d.active ?? true;

            updatePreview();
            if (descCount) descCount.textContent = form.description.value.length;
        } catch {}
    }

    async function saveDraftNow() {
        if (!DRAFT_ID) return;
        const payload = JSON.stringify(serialize());
        const body = {
            title: form.title.value.trim() || null,
            companyName: form.companyName.value.trim() || null,
            cityName: form.cityName.value.trim() || null,
            payloadJson: payload
        };
        await fetch(api(`/api/job-drafts/${DRAFT_ID}`), {
            method:'PATCH',
            headers: { 'Content-Type':'application/json', ...auth() },
            credentials:'include',
            body: JSON.stringify(body)
        }).catch(() => {});
    }

    function scheduleSave() {
        clearTimeout(saveTimer);
        saveTimer = setTimeout(saveDraftNow, 500);
    }

    $('#save-draft').addEventListener('click', async () => {
        await saveDraftNow();
        msg.textContent = 'Draft saved.';
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        msg.textContent = '';
        if (!validate()) {
            msg.textContent = 'Please fix the highlighted fields.';
            scrollToFirstError();
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = 'Publishing…';

        try {
            await saveDraftNow();
            const res = await fetch(api(`/api/job-drafts/${DRAFT_ID}/publish`), {
                method:'POST',
                headers: { Accept:'application/json', ...auth() },
                credentials:'include'
            });
            if (!res.ok) {
                const errText = await res.text();
                throw new Error(errText || `HTTP ${res.status}`);
            }
            msg.textContent = 'Job published 🎉';
            await navigate('/jobs');
        } catch (err) {
            console.error(err);
            msg.textContent = 'Publishing failed. Please verify the data and try again.';
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Publish job';
        }
    });

    (async () => {
        try {
            await ensureDraft();
        } catch (e) {
            console.error('Draft init failed', e);
            return;
        }
        if ($$('.skill-row').length === 0) newSkillRow();
        updatePreview();
    })();
}
