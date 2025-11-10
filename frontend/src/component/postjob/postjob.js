import { getAccess } from '../../shared/api.js';
import { navigate } from '../../router.js';

export function initPostJob() {
    const $  = (s, ctx=document) => ctx.querySelector(s);
    const $$ = (s, ctx=document) => [...ctx.querySelectorAll(s)];
    const api = (p) => p;
    const auth = () => {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    };

    const PREVIEW_EVENT = 'jobDraft:changed';

    const form = $('#postjob-form');
    if (!form) return;

    const submitBtn = $('#submit-btn');
    const msg = $('#form-msg');

    const rteArea   = $('#rte-desc');
    const descRaw   = $('#desc-raw');
    const descCount = $('#desc-count');

    const pv = {
        title:    $('#pv-title'),
        company:  $('#pv-company'),
        salary:   $('#pv-salary'),
        level:    $('#pv-level'),
        contract: $('#pv-contract'),
        remote:   $('#pv-remote'),
        tags:     $('#pv-tags'),
        desc:     $('#pv-desc')
    };

    const state = { tags: [] };

    const ALLOWED_TAGS = new Set(['p','br','strong','em','u','h2','h3','ul','ol','li','blockquote','a']);

    function sanitizeHtml(html) {
        const tpl = document.createElement('template');
        tpl.innerHTML = html || '';
        (function walk(node) {
            [...node.childNodes].forEach(child => {
                if (child.nodeType === 1) {
                    const tag = child.tagName.toLowerCase();
                    if (!ALLOWED_TAGS.has(tag)) {
                        const frag = document.createDocumentFragment();
                        while (child.firstChild) frag.appendChild(child.firstChild);
                        child.replaceWith(frag);
                        return;
                    }
                    if (tag === 'a') {
                        [...child.attributes].forEach(a => { if (a.name.toLowerCase() !== 'href') child.removeAttribute(a.name); });
                        const href = child.getAttribute('href') || '';
                        if (!/^https?:\/\//i.test(href)) child.removeAttribute('href');
                        else {
                            child.setAttribute('rel','noopener noreferrer');
                            child.setAttribute('target','_blank');
                        }
                    } else {
                        [...child.attributes].forEach(a => child.removeAttribute(a.name));
                    }
                    walk(child);
                }
            });
        })(tpl.content);
        return tpl.innerHTML
            .replace(/<p>\s*<\/p>/g,'')
            .replace(/\s+<\/(h2|h3|p)>/gi,'</$1>');
    }

    function htmlToMarkdown(html) {
        const clean = sanitizeHtml(html || '');
        return clean
            .replace(/<h2>([\s\S]*?)<\/h2>/gi, '## $1\n\n')
            .replace(/<h3>([\s\S]*?)<\/h3>/gi, '### $1\n\n')
            .replace(/<li>([\s\S]*?)<\/li>/gi, '* $1\n')
            .replace(/<\/ul>|<\/ol>/gi, '\n')
            .replace(/<ul>|<ol>/gi, '\n')
            .replace(/<blockquote>([\s\S]*?)<\/blockquote>/gi, (m, inner) => {
                return inner.replace(/<br\s*\/?>/gi, '\n')
                    .split('\n').map(l => l ? `> ${l}` : '').join('\n') + '\n\n';
            })
            .replace(/<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi, '[$2]($1)')
            .replace(/<strong>([\s\S]*?)<\/strong>/gi, '**$1**')
            .replace(/<b>([\s\S]*?)<\/b>/gi,       '**$1**')
            .replace(/<em>([\s\S]*?)<\/em>/gi,     '*$1*')
            .replace(/<i>([\s\S]*?)<\/i>/gi,       '*$1*')
            .replace(/<u>([\s\S]*?)<\/u>/gi,       '__$1__')
            .replace(/<br\s*\/?>/gi, '  \n')
            .replace(/<p>([\s\S]*?)<\/p>/gi, '$1\n\n')
            .replace(/<\/?[^>]+>/g,'')
            .trim();
    }

    function markdownToHtml(md) {
        if (!md) return '';
        let html = md.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
        html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
            '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
        html = html.replace(/^\s*###\s+(.*)$/gm,'<h3>$1</h3>');
        html = html.replace(/^\s*##\s+(.*)$/gm, '<h2>$1</h2>');
        html = html.replace(/(^|\n)>\s?(.*)/g, (m, lead, line) => `${lead}<blockquote>${line}</blockquote>`);
        html = html.replace(/(^|\n)[\*\-]\s+(.*)/g, (m, lead, item) => `${lead}<ul><li>${item}</li></ul>`);
        html = html.replace(/\*\*(.*?)\*\*/g,'<strong>$1</strong>');
        html = html.replace(/\*(.*?)\*/g,   '<em>$1</em>');
        html = html.replace(/__(.*?)__/g,   '<u>$1</u>');

        html = html.split(/\n{2,}/).map(block => {
            const t = block.trim();
            if (!t) return '';
            if (/^<h[23]|^<ul>|^<blockquote>/.test(t)) return t;
            return `<p>${t.replace(/\n/g,'<br>')}</p>`;
        }).join('');

        html = html.replace(/<\/ul>\s*<ul>/g,'');
        return sanitizeHtml(html);
    }

    function syncDescriptionFields() {
        const md = htmlToMarkdown(rteArea.innerHTML);
        descRaw.value = md;
        if (descCount) descCount.textContent = String(md.length);
    }

    function exec(cmd) {
        document.execCommand(cmd, false, null);
        syncDescriptionFields();
        updatePreview();
        rteArea.focus();
    }
    function toggleBlock(tag) {
        document.execCommand('formatBlock', false, tag.toUpperCase());
        syncDescriptionFields();
        updatePreview();
        rteArea.focus();
    }
    function addLink() {
        const selText = (window.getSelection()?.toString() || '').trim();
        const url = prompt('Paste URL (https://…)', selText.startsWith('http') ? selText : 'https://');
        if (!url) return;
        try {
            const u = new URL(url);
            if (!/^https?:$/i.test(u.protocol)) throw new Error();
            document.execCommand('createLink', false, u.href);
            syncDescriptionFields();
            updatePreview();
        } catch { alert('Invalid URL'); }
    }
    function removeLink() {
        document.execCommand('unlink', false, null);
        syncDescriptionFields();
        updatePreview();
    }

    $('.rte__toolbar')?.addEventListener('click', (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;
        const { cmd, block, link } = btn.dataset;
        if (cmd)   return exec(cmd);
        if (block) return toggleBlock(block);
        if (link === 'add')    return addLink();
        if (link === 'remove') return removeLink();
    });

    rteArea.addEventListener('paste', (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData).getData('text/plain');
        document.execCommand('insertText', false, text);
    });

    rteArea.addEventListener('input', () => {
        syncDescriptionFields();
        updatePreview();
        scheduleSave();
    });

    function currentModel() {
        const contracts = $$('input[name="contracts"]:checked', form).map(c => c.value);
        return {
            title:       form.title.value.trim(),
            description: descRaw.value || null,
            companyName: form.companyName.value.trim(),
            cityName:    form.cityName.value.trim() || null,
            remote:      !!form.remote.checked,
            level:       form.level.value || null,
            contract:    form.contract.value || null,
            contracts,
            salaryMin:   form.salaryMin.value ? Number(form.salaryMin.value) : null,
            salaryMax:   form.salaryMax.value ? Number(form.salaryMax.value) : null,
            currency:    form.currency.value || 'PLN',
            techTags:    [...state.tags],
            url:         form.applyUrl?.value?.trim() || null
        };
    }

    function broadcast() {
        window.dispatchEvent(new CustomEvent(PREVIEW_EVENT, { detail: currentModel() }));
    }

    function updatePreview() {
        const company = form.companyName.value || 'Company';
        const city    = form.cityName.value || '—';

        pv.title.textContent    = form.title.value || 'Job title';
        pv.company.textContent  = `${company} • ${city}`;
        pv.level.textContent    = form.level.value || '—';
        pv.contract.textContent = form.contract.value || '—';
        pv.remote.textContent   = form.remote.checked ? 'Remote' : 'On-site';

        const min = form.salaryMin.value ? Number(form.salaryMin.value) : null;
        const max = form.salaryMax.value ? Number(form.salaryMax.value) : null;
        const cur = form.currency.value  || 'PLN';
        pv.salary.textContent = (min || max)
            ? `${min ? min.toLocaleString() : '—'} – ${max ? max.toLocaleString() : '—'} ${cur}/month`
            : '—';

        pv.tags.innerHTML = '';
        state.tags.forEach(t => {
            const el = document.createElement('span');
            el.className = 'pill';
            el.textContent = t;
            pv.tags.appendChild(el);
        });

        if (pv.desc) {
            pv.desc.innerHTML = markdownToHtml(descRaw.value);
        }

        broadcast();
    }

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
            state.tags.splice(Number(e.target.dataset.i), 1);
            renderTags(); scheduleSave();
        }
    });

    tagInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const v = tagInput.value.trim();
            if (v && !state.tags.includes(v)) { state.tags.push(v); renderTags(); scheduleSave(); }
            tagInput.value = '';
        }
    });

    ['title','companyName','cityName','salaryMin','salaryMax','currency','level','contract','remote','applyUrl']
        .forEach(n => form[n]?.addEventListener('input', () => { updatePreview(); scheduleSave(); }));

    function setError(el, message) {
        const field = el.closest('.field') || el;
        field.classList.add('invalid');
        field.querySelector('.error')?.remove();
        const small = document.createElement('div');
        small.className = 'error';
        small.textContent = message;
        field.appendChild(small);
    }
    function clearErrors() {
        [...form.querySelectorAll('.invalid')].forEach(f => {
            f.classList.remove('invalid'); f.querySelector('.error')?.remove();
        });
    }
    function validate() {
        clearErrors();
        let ok = true;
        if (!form.title.value.trim())       { setError(form.title, 'Required.'); ok = false; }
        if (!form.companyName.value.trim()) { setError(form.companyName, 'Required.'); ok = false; }
        if (!form.level.value)              { setError(form.level, 'Select a level.'); ok = false; }
        const min = form.salaryMin.value ? Number(form.salaryMin.value) : null;
        const max = form.salaryMax.value ? Number(form.salaryMax.value) : null;
        if (min !== null && max !== null && min > max) { setError(form.salaryMax, 'Max cannot be less than Min.'); ok = false; }
        if (!ok) (form.querySelector('.invalid input, .invalid select, .invalid textarea')?.focus());
        return ok;
    }

    let DRAFT_ID = null, saveTimer = null;

    async function ensureDraft(){
        try {
            const r = await fetch(api('/api/job-drafts/latest'), {
                headers:{Accept:'application/json', ...auth()}, credentials:'include'
            });
            if (r.ok) {
                const d = await r.json();
                if (d?.id){ DRAFT_ID = d.id; hydrate(d.payloadJson); return; }
            } else if (r.status === 401) {
                await navigate('/auth/login'); return;
            }
        } catch {}
        const res = await fetch(api('/api/job-drafts'), {
            method:'POST', headers:{Accept:'application/json', ...auth()}, credentials:'include'
        });
        if (!res.ok){ await navigate('/auth/login'); throw new Error('Cannot create draft'); }
        const created = await res.json();
        DRAFT_ID = created.id;
    }

    function hydrate(payloadJson) {
        try {
            const d = JSON.parse(payloadJson || '{}');

            form.title.value       = d.title || '';
            form.companyName.value = d.companyName || '';
            form.cityName.value    = d.cityName || '';
            form.remote.checked    = !!d.remote;
            form.level.value       = d.level || '';
            form.contract.value    = d.contract || '';
            (d.contracts||[]).forEach(v => {
                const cb = $$('input[name="contracts"]').find(x => x.value === v);
                if (cb) cb.checked = true;
            });
            form.salaryMin.value = d.salaryMin ?? '';
            form.salaryMax.value = d.salaryMax ?? '';
            form.currency.value  = d.currency || 'PLN';

            if (form.applyUrl) form.applyUrl.value = d.url || '';

            state.tags = d.techTags || [];
            renderTags();

            const md = d.description || '';
            descRaw.value = md;
            rteArea.innerHTML = markdownToHtml(md) || '<p></p>';
            if (descCount) descCount.textContent = String(md.length);

            updatePreview();
        } catch {}
    }

    async function saveDraftNow() {
        if (!DRAFT_ID) return;
        syncDescriptionFields();

        const payload = JSON.stringify(currentModel());
        const body = {
            title:       form.title.value.trim() || null,
            companyName: form.companyName.value.trim() || null,
            cityName:    form.cityName.value.trim() || null,
            payloadJson: payload
        };
        await fetch(api(`/api/job-drafts/${DRAFT_ID}`), {
            method:'PATCH',
            headers:{'Content-Type':'application/json', ...auth()},
            credentials:'include',
            body: JSON.stringify(body)
        }).catch(()=>{});
    }
    function scheduleSave(){ clearTimeout(saveTimer); saveTimer = setTimeout(saveDraftNow, 500); }
    $('#save-draft')?.addEventListener('click', async () => { await saveDraftNow(); msg.textContent = 'Draft saved.'; });

    form.addEventListener('submit', async (e) => {
        e.preventDefault(); msg.textContent = '';
        if (!validate()){ msg.textContent = 'Please fix the highlighted fields.'; return; }

        submitBtn.disabled = true; submitBtn.textContent = 'Publishing…';
        try {
            await saveDraftNow();
            const res = await fetch(api(`/api/job-drafts/${DRAFT_ID}/publish`), {
                method:'POST', headers:{Accept:'application/json', ...auth()}, credentials:'include'
            });
            if (!res.ok) throw new Error(await res.text().catch(()=>null) || `HTTP ${res.status}`);
            const created = await res.json();
            msg.textContent = 'Job published 🎉';
            await navigate(`/jobexaclyoffer?id=${created.id}`);
        } catch (err) {
            console.error(err);
            msg.textContent = 'Publishing failed. Please verify the data and try again.';
        } finally {
            submitBtn.disabled = false; submitBtn.textContent = 'Publish job';
        }
    });

    (async () => {
        try { await ensureDraft(); } catch (e) { console.error('Draft init failed', e); }
        updatePreview();
        if (!rteArea.innerHTML.trim()) { rteArea.innerHTML = '<p></p>'; syncDescriptionFields(); updatePreview(); }
    })();
}
