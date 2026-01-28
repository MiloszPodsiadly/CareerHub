import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import styles from './PostJobPage.module.css';
import { getAccess } from '../../shared/api';

type FormState = {
    title: string;
    description: string;
    companyName: string;
    cityName: string;
    remote: boolean;
    level: string;
    contract: string;
    contracts: string[];
    salaryMin: string;
    salaryMax: string;
    currency: string;
    techTags: string[];
};

const EMPTY_FORM: FormState = {
    title: '',
    description: '',
    companyName: '',
    cityName: '',
    remote: false,
    level: '',
    contract: '',
    contracts: [],
    salaryMin: '',
    salaryMax: '',
    currency: 'PLN',
    techTags: [],
};

const ALLOWED_TAGS = new Set(['p', 'br', 'strong', 'em', 'u', 'h2', 'h3', 'ul', 'ol', 'li', 'blockquote', 'a']);

function sanitizeHtml(html: string) {
    const tpl = document.createElement('template');
    tpl.innerHTML = html || '';
    (function walk(node: ParentNode) {
        [...node.childNodes].forEach((child) => {
            if (child.nodeType === 1) {
                const el = child as HTMLElement;
                const tag = el.tagName.toLowerCase();
                if (!ALLOWED_TAGS.has(tag)) {
                    const frag = document.createDocumentFragment();
                    while (el.firstChild) frag.appendChild(el.firstChild);
                    el.replaceWith(frag);
                    return;
                }
                if (tag === 'a') {
                    [...el.attributes].forEach((a) => {
                        if (a.name.toLowerCase() !== 'href') el.removeAttribute(a.name);
                    });
                    const href = el.getAttribute('href') || '';
                    if (!/^https?:\/\//i.test(href)) el.removeAttribute('href');
                    else {
                        el.setAttribute('rel', 'noopener noreferrer');
                        el.setAttribute('target', '_blank');
                    }
                } else {
                    [...el.attributes].forEach((a) => el.removeAttribute(a.name));
                }
                walk(el);
            }
        });
    })(tpl.content);
    return tpl.innerHTML
        .replace(/<p>\s*<\/p>/g, '')
        .replace(/\s+<\/(h2|h3|p)>/gi, '</$1>');
}

function htmlToMarkdown(html: string) {
    const clean = sanitizeHtml(html || '');
    return clean
        .replace(/<h2>([\s\S]*?)<\/h2>/gi, '## $1\n\n')
        .replace(/<h3>([\s\S]*?)<\/h3>/gi, '### $1\n\n')
        .replace(/<li>([\s\S]*?)<\/li>/gi, '* $1\n')
        .replace(/<\/ul>|<\/ol>/gi, '\n')
        .replace(/<ul>|<ol>/gi, '\n')
        .replace(/<blockquote>([\s\S]*?)<\/blockquote>/gi, (m, inner) => {
            return inner
                .replace(/<br\s*\/?>/gi, '\n')
                .split('\n')
                .map((l: string) => (l ? `> ${l}` : ''))
                .join('\n') + '\n\n';
        })
        .replace(/<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi, '[$2]($1)')
        .replace(/<strong>([\s\S]*?)<\/strong>/gi, '**$1**')
        .replace(/<b>([\s\S]*?)<\/b>/gi, '**$1**')
        .replace(/<em>([\s\S]*?)<\/em>/gi, '*$1*')
        .replace(/<i>([\s\S]*?)<\/i>/gi, '*$1*')
        .replace(/<u>([\s\S]*?)<\/u>/gi, '__$1__')
        .replace(/<br\s*\/?>/gi, '  \n')
        .replace(/<p>([\s\S]*?)<\/p>/gi, '$1\n\n')
        .replace(/<\/?[^>]+>/g, '')
        .trim();
}

function markdownToHtml(md: string) {
    if (!md) return '';
    let html = md.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    html = html.replace(/^\s*###\s+(.*)$/gm, '<h3>$1</h3>');
    html = html.replace(/^\s*##\s+(.*)$/gm, '<h2>$1</h2>');
    html = html.replace(/(^|\n)>\s?(.*)/g, (m, lead, line) => `${lead}<blockquote>${line}</blockquote>`);
    html = html.replace(/(^|\n)[\*\-]\s+(.*)/g, (m, lead, item) => `${lead}<ul><li>${item}</li></ul>`);
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
    html = html.replace(/__(.*?)__/g, '<u>$1</u>');

    html = html
        .split(/\n{2,}/)
        .map((block) => {
            const t = block.trim();
            if (!t) return '';
            if (/^<h[23]|^<ul>|^<blockquote>/.test(t)) return t;
            return `<p>${t.replace(/\n/g, '<br>')}</p>`;
        })
        .join('');

    html = html.replace(/<\/ul>\s*<ul>/g, '');
    return sanitizeHtml(html);
}

function authHeaders(): Record<string, string> {
    try {
        const t = getAccess?.();
        return t ? { Authorization: `Bearer ${t}` } : {};
    } catch {
        return {};
    }
}

export default function PostJobPage() {
    const navigate = useNavigate();
    const rteRef = useRef<HTMLDivElement | null>(null);
    const draftIdRef = useRef<string | number | null>(null);
    const saveTimerRef = useRef<number | null>(null);
    const formRef = useRef<FormState>(EMPTY_FORM);

    const [form, setForm] = useState<FormState>(EMPTY_FORM);
    const [tagInput, setTagInput] = useState('');
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [message, setMessage] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        formRef.current = form;
    }, [form]);

    const scheduleSave = () => {
        if (!draftIdRef.current) return;
        if (saveTimerRef.current) window.clearTimeout(saveTimerRef.current);
        saveTimerRef.current = window.setTimeout(() => saveDraftNow(), 500);
    };

    const hydrate = (payloadJson: string) => {
        try {
            const d = JSON.parse(payloadJson || '{}');
            const next: FormState = {
                title: d.title || '',
                description: d.description || '',
                companyName: d.companyName || '',
                cityName: d.cityName || '',
                remote: !!d.remote,
                level: d.level || '',
                contract: d.contract || '',
                contracts: d.contracts || [],
                salaryMin: d.salaryMin ?? '',
                salaryMax: d.salaryMax ?? '',
                currency: d.currency || 'PLN',
                techTags: d.techTags || [],
            };
            setForm(next);
            if (rteRef.current) {
                rteRef.current.innerHTML = markdownToHtml(next.description) || '<p></p>';
            }
        } catch {
            setForm(EMPTY_FORM);
        }
    };

    const ensureDraft = async () => {
        try {
            const r = await fetch('/api/job-drafts/latest', {
                headers: { Accept: 'application/json', ...authHeaders() },
                credentials: 'include',
            });
            if (r.ok) {
                const d = await r.json();
                if (d?.id) {
                    draftIdRef.current = d.id;
                    hydrate(d.payloadJson);
                    return;
                }
            } else if (r.status === 401) {
                navigate('/auth/login');
                return;
            }
        } catch {}

        const res = await fetch('/api/job-drafts', {
            method: 'POST',
            headers: { Accept: 'application/json', ...authHeaders() },
            credentials: 'include',
        });
        if (!res.ok) {
            navigate('/auth/login');
            throw new Error('Cannot create draft');
        }
        const created = await res.json();
        draftIdRef.current = created.id;
    };

    const currentModel = () => {
        const cur = formRef.current;
        return {
            title: cur.title.trim(),
            description: cur.description || null,
            companyName: cur.companyName.trim(),
            cityName: cur.cityName.trim() || null,
            remote: !!cur.remote,
            level: cur.level || null,
            contract: cur.contract || null,
            contracts: cur.contracts,
            salaryMin: cur.salaryMin ? Number(cur.salaryMin) : null,
            salaryMax: cur.salaryMax ? Number(cur.salaryMax) : null,
            currency: cur.currency || 'PLN',
            techTags: cur.techTags,
            url: null,
        };
    };

    const saveDraftNow = async () => {
        if (!draftIdRef.current) return;
        if (rteRef.current) {
            const md = htmlToMarkdown(rteRef.current.innerHTML);
            if (md !== formRef.current.description) {
                setForm((prev) => ({ ...prev, description: md }));
                formRef.current = { ...formRef.current, description: md };
            }
        }

        const payload = JSON.stringify(currentModel());
        const body = {
            title: formRef.current.title.trim() || null,
            companyName: formRef.current.companyName.trim() || null,
            cityName: formRef.current.cityName.trim() || null,
            payloadJson: payload,
        };
        await fetch(`/api/job-drafts/${draftIdRef.current}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', ...authHeaders() },
            credentials: 'include',
            body: JSON.stringify(body),
        }).catch(() => {});
    };

    useEffect(() => {
        (async () => {
            try {
                await ensureDraft();
                if (rteRef.current && !rteRef.current.innerHTML.trim()) {
                    rteRef.current.innerHTML = '<p></p>';
                }
            } catch (err) {
                console.error('Draft init failed', err);
            }
        })();
    }, []);

    const validate = () => {
        const nextErrors: Record<string, string> = {};
        if (!form.title.trim()) nextErrors.title = 'Required.';
        if (!form.companyName.trim()) nextErrors.companyName = 'Required.';
        if (!form.level) nextErrors.level = 'Select a level.';
        const min = form.salaryMin ? Number(form.salaryMin) : null;
        const max = form.salaryMax ? Number(form.salaryMax) : null;
        if (min !== null && max !== null && min > max) nextErrors.salaryMax = 'Max cannot be less than Min.';
        setErrors(nextErrors);
        return Object.keys(nextErrors).length === 0;
    };

    const updateField = (field: keyof FormState, value: string | boolean | string[]) => {
        setForm((prev) => ({ ...prev, [field]: value as any }));
        scheduleSave();
    };

    const handleRteInput = () => {
        if (!rteRef.current) return;
        const md = htmlToMarkdown(rteRef.current.innerHTML);
        setForm((prev) => ({ ...prev, description: md }));
        scheduleSave();
    };

    const exec = (cmd: string) => {
        document.execCommand(cmd, false, undefined);
        handleRteInput();
        rteRef.current?.focus();
    };

    const toggleBlock = (tag: string) => {
        document.execCommand('formatBlock', false, tag.toUpperCase());
        handleRteInput();
        rteRef.current?.focus();
    };

    const addLink = () => {
        const selText = (window.getSelection()?.toString() || '').trim();
        const url = window.prompt('Paste URL (https://...)', selText.startsWith('http') ? selText : 'https://');
        if (!url) return;
        try {
            const u = new URL(url);
            if (!/^https?:$/i.test(u.protocol)) throw new Error();
            document.execCommand('createLink', false, u.href);
            handleRteInput();
        } catch {
            window.alert('Invalid URL');
        }
    };

    const removeLink = () => {
        document.execCommand('unlink', false, undefined);
        handleRteInput();
    };

    const handleToolbarClick = (e: React.MouseEvent) => {
        const btn = (e.target as HTMLElement).closest('button');
        if (!btn) return;
        const cmd = btn.getAttribute('data-cmd');
        const block = btn.getAttribute('data-block');
        const link = btn.getAttribute('data-link');
        if (cmd) return exec(cmd);
        if (block) return toggleBlock(block);
        if (link === 'add') return addLink();
        if (link === 'remove') return removeLink();
    };

    const handlePaste = (e: React.ClipboardEvent) => {
        e.preventDefault();
        const text = e.clipboardData.getData('text/plain');
        document.execCommand('insertText', false, text);
    };

    const addTag = () => {
        const v = tagInput.trim();
        if (!v) return;
        if (form.techTags.includes(v)) {
            setTagInput('');
            return;
        }
        updateField('techTags', [...form.techTags, v]);
        setTagInput('');
    };

    const removeTag = (idx: number) => {
        const next = form.techTags.filter((_, i) => i !== idx);
        updateField('techTags', next);
    };

    const onSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setMessage('');
        if (!validate()) {
            setMessage('Please fix the highlighted fields.');
            return;
        }
        setSaving(true);
        try {
            await saveDraftNow();
            const res = await fetch(`/api/job-drafts/${draftIdRef.current}/publish`, {
                method: 'POST',
                headers: { Accept: 'application/json', ...authHeaders() },
                credentials: 'include',
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const created = await res.json();
            setMessage('Job published!');
            navigate(`/jobexaclyoffer?id=${created.id}`);
        } catch (err) {
            console.error(err);
            setMessage('Publishing failed. Please verify the data and try again.');
        } finally {
            setSaving(false);
        }
    };

    const previewSalary = () => {
        const min = form.salaryMin ? Number(form.salaryMin) : null;
        const max = form.salaryMax ? Number(form.salaryMax) : null;
        const cur = form.currency || 'PLN';
        return min || max ? `${min ? min.toLocaleString() : '-'} - ${max ? max.toLocaleString() : '-'} ${cur}/month` : '-';
    };

    const previewCompany = () => {
        const company = form.companyName || 'Company';
        const city = form.cityName || '-';
        return `${company} - ${city}`;
    };

    return (
        <section className={styles.postjob} aria-labelledby="pj-title">
            <header className={styles.postjob__hero}>
                <h1 id="pj-title">
                    Post a <span className={styles.grad}>job</span>
                </h1>
                <p className={styles.muted}>Fill in the details - your listing will be visible immediately after publishing.</p>
            </header>

            <div className={styles.postjob__grid}>
                <form onSubmit={onSubmit} noValidate>
                    <fieldset className={styles.card}>
                        <legend>Listing content</legend>

                        <label className={`${styles.field} ${errors.title ? styles.invalid : ''}`}>
                            <span>Job title *</span>
                            <input
                                name="title"
                                required
                                placeholder="e.g. Senior Java Developer"
                                value={form.title}
                                onChange={(e) => updateField('title', e.target.value)}
                            />
                            {errors.title ? <div className={styles.error}>{errors.title}</div> : null}
                        </label>

                        <label className={styles.field}>
                            <span>Description</span>

                            <div className={styles.rte} aria-label="Job description editor">
                                <div className={styles.rte__toolbar} role="toolbar" aria-label="Formatting" onClick={handleToolbarClick}>
                                    <button type="button" className={styles.rte__btn} data-cmd="bold" title="Bold (Ctrl+B)">
                                        <strong>B</strong>
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-cmd="italic" title="Italic (Ctrl+I)">
                                        <em>I</em>
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-cmd="underline" title="Underline (Ctrl+U)">
                                        <u>U</u>
                                    </button>
                                    <span className={styles.rte__sep}></span>
                                    <button type="button" className={styles.rte__btn} data-block="h2" title="Heading">
                                        H2
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-block="h3" title="Subheading">
                                        H3
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-cmd="insertUnorderedList" title="Bulleted list">
                                        * List
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-cmd="insertOrderedList" title="Numbered list">
                                        1. List
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-block="blockquote" title="Quote">
                                        ""
                                    </button>
                                    <span className={styles.rte__sep}></span>
                                    <button type="button" className={styles.rte__btn} data-link="add" title="Insert link">
                                        Link
                                    </button>
                                    <button type="button" className={styles.rte__btn} data-link="remove" title="Remove link">
                                        Unlink
                                    </button>
                                    <span className={styles.rte__sep}></span>
                                    <button type="button" className={styles.rte__btn} data-cmd="removeFormat" title="Clear formatting">
                                        Clear
                                    </button>
                                </div>

                                <div
                                    ref={rteRef}
                                    className={styles.rte__area}
                                    contentEditable
                                    data-placeholder="Briefly describe responsibilities, requirements, benefits..."
                                    onInput={handleRteInput}
                                    onPaste={handlePaste}
                                    suppressContentEditableWarning
                                ></div>
                            </div>

                            <textarea name="description" value={form.description} readOnly hidden></textarea>

                            <div className={`${styles.muted} ${styles.count}`}>{form.description.length} chars</div>
                        </label>
                    </fieldset>

                    <fieldset className={styles.card}>
                        <legend>Company & location</legend>

                        <div className={`${styles.field} ${styles.two}`}>
                            <label className={errors.companyName ? styles.invalid : undefined}>
                                <span>Company name *</span>
                                <input
                                    name="companyName"
                                    required
                                    placeholder="Employer / software house"
                                    value={form.companyName}
                                    onChange={(e) => updateField('companyName', e.target.value)}
                                />
                                {errors.companyName ? <div className={styles.error}>{errors.companyName}</div> : null}
                            </label>

                            <label>
                                <span>City</span>
                                <input
                                    name="cityName"
                                    placeholder="Warsaw"
                                    value={form.cityName}
                                    onChange={(e) => updateField('cityName', e.target.value)}
                                />
                            </label>
                        </div>

                        <div className={`${styles.field} ${styles.three}`}>
                            <label className={styles.switch}>
                                <input
                                    type="checkbox"
                                    name="remote"
                                    checked={form.remote}
                                    onChange={(e) => updateField('remote', e.target.checked)}
                                />
                                <span>Remote</span>
                            </label>

                            <label className={errors.level ? styles.invalid : undefined}>
                                <span>Level *</span>
                                <select
                                    name="level"
                                    required
                                    value={form.level}
                                    onChange={(e) => updateField('level', e.target.value)}
                                >
                                    <option value="">- select -</option>
                                    <option>INTERNSHIP</option>
                                    <option>JUNIOR</option>
                                    <option>MID</option>
                                    <option>SENIOR</option>
                                    <option>LEAD</option>
                                </select>
                                {errors.level ? <div className={styles.error}>{errors.level}</div> : null}
                            </label>

                            <label>
                                <span>Main contract</span>
                                <select name="contract" value={form.contract} onChange={(e) => updateField('contract', e.target.value)}>
                                    <option value="">- none -</option>
                                    <option>UOP</option>
                                    <option>B2B</option>
                                    <option>UZ</option>
                                    <option>UOD</option>
                                </select>
                            </label>
                        </div>

                        <fieldset className={styles.inline}>
                            <legend>Accepted contract types</legend>
                            {['UOP', 'B2B', 'UZ', 'UOD'].map((value) => (
                                <label key={value} className={styles.check}>
                                    <input
                                        type="checkbox"
                                        value={value}
                                        checked={form.contracts.includes(value)}
                                        onChange={(e) => {
                                            const checked = e.target.checked;
                                            const next = checked
                                                ? [...form.contracts, value]
                                                : form.contracts.filter((v) => v !== value);
                                            updateField('contracts', next);
                                        }}
                                    />{' '}
                                    {value}
                                </label>
                            ))}
                        </fieldset>
                    </fieldset>

                    <fieldset className={styles.card}>
                        <legend>Salary range</legend>
                        <div className={`${styles.field} ${styles.three}`}>
                            <label>
                                <span>Min</span>
                                <input
                                    name="salaryMin"
                                    type="number"
                                    min={0}
                                    step={100}
                                    placeholder="e.g. 15000"
                                    value={form.salaryMin}
                                    onChange={(e) => updateField('salaryMin', e.target.value)}
                                />
                            </label>
                            <label className={errors.salaryMax ? styles.invalid : undefined}>
                                <span>Max</span>
                                <input
                                    name="salaryMax"
                                    type="number"
                                    min={0}
                                    step={100}
                                    placeholder="e.g. 22000"
                                    value={form.salaryMax}
                                    onChange={(e) => updateField('salaryMax', e.target.value)}
                                />
                                {errors.salaryMax ? <div className={styles.error}>{errors.salaryMax}</div> : null}
                            </label>
                            <label>
                                <span>Currency</span>
                                <select name="currency" value={form.currency} onChange={(e) => updateField('currency', e.target.value)}>
                                    <option>PLN</option>
                                    <option>EUR</option>
                                    <option>USD</option>
                                </select>
                            </label>
                        </div>
                        <small className={styles.muted}>Provide gross for UOP or net for B2B (depending on the contract).</small>
                    </fieldset>

                    <fieldset className={styles.card}>
                        <legend>Technology tags</legend>
                        <div className={styles.chips} aria-live="polite">
                            {form.techTags.map((tag, i) => (
                                <span key={`${tag}-${i}`} className={styles.chip}>
                                    {tag}{' '}
                                    <button type="button" title={`Remove ${tag}`} aria-label={`Remove ${tag}`} onClick={() => removeTag(i)}>
                                        x
                                    </button>
                                </span>
                            ))}
                        </div>
                        <div className={styles.field}>
                            <input
                                value={tagInput}
                                onChange={(e) => setTagInput(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') {
                                        e.preventDefault();
                                        addTag();
                                    }
                                }}
                                placeholder="Add tag and press Enter (e.g. Java, Spring, AWS)"
                            />
                        </div>
                    </fieldset>

                    <div className={styles.actions}>
                        <button
                            className={styles.btn}
                            type="button"
                            onClick={async () => {
                                await saveDraftNow();
                                setMessage('Draft saved.');
                            }}
                        >
                            Save draft
                        </button>
                        <button className={`${styles.btn} ${styles['btn--primary']}`} type="submit" disabled={saving}>
                            {saving ? 'Publishing...' : 'Publish job'}
                        </button>
                        <div className={styles['form-msg']} role="status" aria-live="polite">
                            {message}
                        </div>
                    </div>
                </form>

                <aside className={`${styles.preview} ${styles.card}`} aria-label="Preview">
                    <div className={styles.preview__header}>
                        <h3>{form.title || 'Job title'}</h3>
                        <p className={styles.muted}>{previewCompany()}</p>
                    </div>
                    <div className={styles.preview__meta}>
                        <span className={styles.pill}>{form.level || '-'}</span>
                        <span className={styles.pill}>{form.contract || '-'}</span>
                        <span className={styles.pill}>{form.remote ? 'Remote' : 'On-site'}</span>
                    </div>
                    <div className={styles.preview__salary}>{previewSalary()}</div>
                    <div className={`${styles.preview__desc} ${styles.prose}`} dangerouslySetInnerHTML={{ __html: markdownToHtml(form.description) }} />
                    <div className={styles.preview__tags}>
                        {form.techTags.map((tag) => (
                            <span key={`pv-${tag}`} className={styles.pill}>
                                {tag}
                            </span>
                        ))}
                    </div>
                    <p className={`${styles.muted} ${styles.tiny}`}>Preview updates live while you type.</p>
                </aside>
            </div>
        </section>
    );
}
