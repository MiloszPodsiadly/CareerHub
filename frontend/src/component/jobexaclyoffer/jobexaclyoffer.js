import { getAccess as sharedGetAccess } from '../../shared/api.js';
import { navigate } from '../../router.js';

const API = (p) => p;

const DBG = true;
const log  = (...a) => DBG && console.debug('[JOBX]', ...a);
const info = (...a) => DBG && console.info('[JOBX]', ...a);
const err  = (...a) => console.error('[JOBX]', ...a);
const peek = (t) => { try{ const s=String(t||''); return !s?'(empty)':(s.length<=16?s:`${s.slice(0,10)}…${s.slice(-6)}`);}catch{ return '(?)'; } };

function escapeHtml(s){ return String(s??'').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m])); }
const prettyLevel   = (l)=>({INTERNSHIP:'Intern',JUNIOR:'Junior',MID:'Mid',SENIOR:'Senior',LEAD:'Lead'}[String(l||'').toUpperCase()]??l);
function prettyContract(c){ if(!c) return null; const u=String(c).toUpperCase(); return u==='UOP'?'UoP':u==='UOD'?'UoD':u; }
function salaryToText({min,max,currency,period='MONTH'}={}){ if(min==null&&max==null)return''; const a=min!=null?min.toLocaleString('en-GB'):''; const b=max!=null?' – '+max.toLocaleString('en-GB'):''; return `${a}${b} ${currency||'PLN'}/${period.toLowerCase()}`; }
function markdownToHtml(md){
    if(!md) return '';
    let html = md.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,'<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    html = html.replace(/^\s*###\s+(.*)$/gm,'<h3>$1</h3>')
        .replace(/^\s*##\s+(.*)$/gm,'<h2>$1</h2>')
        .replace(/(^|\n)>\s?(.*)/g,(m,lead,line)=>`${lead}<blockquote>${line}</blockquote>`)
        .replace(/(^|\n)[\*\-]\s+(.*)/g,(m,lead,item)=>`${lead}<ul><li>${item}</li></ul>`)
        .replace(/\*\*(.*?)\*\*/g,'<strong>$1</strong>')
        .replace(/\*(.*?)\*/g,'<em>$1</em>')
        .replace(/__(.*?)__/g,'<u>$1</u>');
    html = html.split(/\n{2,}/).map(b=>{
        const t=b.trim(); if(!t) return '';
        if(/^<h[23]|^<ul>|^<blockquote>/.test(t)) return t;
        return `<p>${t.replace(/\n/g,'<br>')}</p>`;
    }).join('').replace(/<\/ul>\s*<ul>/g,'');
    return html;
}

function resolveAccessToken(){
    try{ if(typeof sharedGetAccess==='function'){ const t=sharedGetAccess(); if(t) return t; } }catch{}
    try{
        if(typeof window!=='undefined'){
            if(typeof window.getAccess==='function'){ const t=window.getAccess(); if(t) return t; }
            if(window.appAuth?.getAccess){ const t=window.appAuth.getAccess(); if(t) return t; }
            const ss=window.sessionStorage?.getItem?.('accessToken'); if(ss) return JSON.parse(ss);
            const ls=window.localStorage?.getItem?.('accessToken'); if(ls) return ls;
        }
    }catch{}
    return '';
}
function persistAccessToken(at){ try{ sessionStorage.setItem('accessToken', JSON.stringify(at||'')); }catch{} }
const isAuthed=()=>!!resolveAccessToken();
const authHeaders=()=>{ const t=resolveAccessToken(); const h=t?{Authorization:`Bearer ${t}`}:{}; log('authHeaders -> hasAuth?',!!t,peek(t)); return h; };

async function ensureAuthOrLogin(nextUrl){
    const token=resolveAccessToken();
    if(!token){ await navigate(`/auth/login?next=${encodeURIComponent(nextUrl||location.href)}`); return false; }
    try{
        let r=await fetch('/api/auth/me',{headers:{Accept:'application/json',...authHeaders()},credentials:'include'});
        if(r.status===401){
            try{
                const rr=await fetch('/api/auth/refresh',{method:'POST',credentials:'include'});
                if(rr.ok){ const {accessToken}=await rr.json().catch(()=>({})); if(accessToken) persistAccessToken(accessToken);
                    r=await fetch('/api/auth/me',{headers:{Accept:'application/json',...authHeaders()},credentials:'include'});
                }
            }catch(e){ err('refresh error',e); }
        }
        if(r.status===401){ await navigate(`/auth/login?next=${encodeURIComponent(nextUrl||location.href)}`); return false; }
        return r.ok;
    }catch(e){ err('/auth/me network',e); return true; }
}

function ensureStatusNode(scope){
    let el=scope.querySelector('#jobx-status');
    if(!el){ el=document.createElement('span'); el.id='jobx-status'; el.className='jobx__status';
        el.style.marginLeft='10px'; el.style.fontWeight='700'; el.style.fontSize='12px'; }
    return el;
}
function setStatus(scope,text,kind='info'){
    const el=ensureStatusNode(scope);
    el.textContent=text||''; el.style.display=text?'inline-flex':'none';
    el.style.padding=text?'6px 8px':'0'; el.style.borderRadius='10px'; el.style.border='1px solid transparent';
    const styles={ok:['#e8fff1','#14532d','#bbf7d0'],warn:['#fff7ed','#7c2d12','#fed7aa'],err:['#fee2e2','#7f1d1d','#fecaca'],info:['#eef2ff','#1e3a8a','#c7d2fe']};
    const [bg,fg,b]=styles[kind]||styles.info; el.style.background=bg; el.style.color=fg; el.style.borderColor=b;
    const cta=scope.querySelector('.jobx__cta'); if(cta && !el.isConnected) cta.appendChild(el);
}

async function hasCv(){
    try{
        const r=await fetch('/api/profile',{headers:{Accept:'application/json',...authHeaders()},credentials:'include'});
        if(!r.ok) return 'unknown';
        const p=await r.json();

        if (p?.hasCv === true)  return 'yes';
        if (p?.hasCv === false) return 'no';
        if (typeof p?.cvFileId !== 'undefined') return p.cvFileId ? 'yes' : 'no';

        const cvObj = p?.cv || p?.resume || p?.files?.cv || p?.files?.resume;
        if (cvObj) {
            if (typeof cvObj === 'string' && cvObj.trim()) return 'yes';
            if (typeof cvObj?.id === 'string' && cvObj.id.trim()) return 'yes';
            if (typeof cvObj?.fileId === 'string' && cvObj.fileId.trim()) return 'yes';
            if (typeof cvObj?.url === 'string' && /^https?:\/\//i.test(cvObj.url)) return 'yes';
        }

        return 'unknown';
    }catch{ return 'unknown'; }
}

export async function initJobExactlyOffer(rootEl,{id}){
    info('initJobExactlyOffer start, id=',id);
    const scope=rootEl||document;
    const $=(s)=>scope.querySelector(s);

    const $t=$('#jobx-title'), $m=$('#jobx-meta'), $b=$('#jobx-badges'), $d=$('#jobx-desc');
    const $tags=$('#jobx-tags'), $det=$('#jobx-details'), $apply=$('#jobx-apply'), $crumb=$('#jobx-crumb');

    ensureStatusNode(scope);

    async function fetchOffer(){
        const raw = String(id ?? '').trim();
        const isNumericId = /^\d+$/.test(raw);

        const url = isNumericId
            ? API(`/api/jobs/${encodeURIComponent(raw)}`)
            : API(`/api/jobs/by-external/${encodeURIComponent(raw)}?source=PLATFORM`);

        const r = await fetch(url, { headers: { Accept: 'application/json' } });
        if(!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
    }


    function render(x){
        $t.textContent=x.title||'Job offer';
        $crumb.textContent=x.title||'Offer';
        const company=x.companyName||x.company||'—';
        const city=x.cityName||x.city||'—';
        const lvl=x.level?prettyLevel(x.level):null;
        const salary={min:x.salaryMin,max:x.salaryMax,currency:x.currency,period:'MONTH'};
        $m.innerHTML=`🏢 ${escapeHtml(company)} • 📍 ${escapeHtml(city)} ${x.country?`(${escapeHtml(x.country)})`:''}`;
        const badges=[];
        if(x.contract) badges.push(`<span class="badge">${escapeHtml(prettyContract(x.contract))}</span>`);
        (Array.isArray(x.contracts)?x.contracts:[]).forEach(c=>badges.push(`<span class="badge">${escapeHtml(prettyContract(c))}</span>`));
        if(x.remote) badges.push('<span class="badge">Remote</span>');
        if(lvl) badges.push(`<span class="badge">${escapeHtml(lvl)}</span>`);
        const salTxt=salaryToText(salary); if(salTxt) badges.push(`<span class="badge">${escapeHtml(salTxt)}</span>`);
        $b.innerHTML=badges.join('');
        const tags=(x.techTags||x.keywords||[]).map(k=>`<span class="badge">${escapeHtml(k)}</span>`).join(''); $tags.innerHTML=tags||'—';
        const details=[];
        if (x.publishedAt) {
            details.push(`<li><strong>Published:</strong> ${new Date(x.publishedAt).toLocaleDateString('en-GB')}</li>`);
        }
        $det.innerHTML=details.join('')||'<li class="muted">—</li>';
        $d.innerHTML=markdownToHtml(x.description||'');

        $apply.addEventListener('click', async (e)=>{
            e.preventDefault();
            setStatus(scope,'','info');

            if(!(await ensureAuthOrLogin(location.href))) return;

            const cvState = await hasCv();
            if(cvState === 'no'){
                setStatus(scope,'Add your CV in profile before applying.','warn');
                const link=document.createElement('a'); link.href='/profile#cv'; link.textContent=' Go to profile'; link.style.marginLeft='6px';
                link.addEventListener('click',(ev)=>{ ev.preventDefault(); navigate('/profile#cv'); });
                const chip=ensureStatusNode(scope); if(!chip.querySelector('a')) chip.appendChild(link);
                return;
            }

            const body={ offerId:x.id };
            const headers={'Content-Type':'application/json',Accept:'application/json',...authHeaders()};
            const oldLabel=$apply.textContent; $apply.setAttribute('disabled','disabled'); $apply.style.opacity='.7';

            try{
                const res=await fetch('/api/applications',{method:'POST',headers,credentials:'include',body:JSON.stringify(body)});

                if(res.status===401||res.status===403){
                    setStatus(scope,'Please sign in to apply.','warn');
                    await navigate(`/auth/login?next=${encodeURIComponent(location.href)}`); return;
                }
                if(res.status===409){
                    setStatus(scope,'You have applied for this role.','warn');
                    return;
                }
                if(!res.ok){
                    let msg=''; try{ const j=await res.json(); msg=j?.message||j?.error||''; }catch{ msg=await res.text(); }
                    setStatus(scope, msg || 'Unexpected error. Please try again later.', 'err');
                    return;
                }

                setStatus(scope,'Applied for job.','ok');
                $apply.textContent='APPLIED';
            }catch(e2){
                err('[apply] network error',e2);
                setStatus(scope,'Network error. Please try again.','err');
            }finally{
                if($apply.textContent!=='APPLIED'){ $apply.removeAttribute('disabled'); $apply.style.opacity=''; $apply.textContent=oldLabel; }
            }
        });

        $apply.href='#';

    }

    try{
        if(!id) throw new Error('Missing id');
        const data=await fetchOffer();
        render(data);
    }catch(e){
        err('load failed:',e);
        const host=scope.querySelector('.jobx')||scope.body||scope;
        host.innerHTML=`<div class="jobx"><p class="muted">Offer not found.</p></div>`;
    }
}
