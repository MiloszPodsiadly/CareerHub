import { useEffect, useMemo, useRef, useState } from 'react';

import styles from './SalaryCalculatorPage.module.css';

type AmountMode = 'gross' | 'net';
type ContractType = 'employment' | 'mandate' | 'specific' | 'b2b';

type SalaryParts = {
    gross: number;
    net: number;
    pension: number;
    disability: number;
    sickness: number;
    health: number;
    pit: number;
};

const PLN = (n: number) =>
    (Number.isFinite(n) ? n : 0).toLocaleString('pl-PL', {
        style: 'currency',
        currency: 'PLN',
        maximumFractionDigits: 2,
    });

const TYPE_ENUM: Record<ContractType, string> = {
    employment: 'EMPLOYMENT',
    mandate: 'MANDATE',
    specific: 'SPECIFIC_TASK',
    b2b: 'B2B',
};

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

function buildPayload(amount: number, mode: AmountMode, type: ContractType, pit0: boolean) {
    return {
        amount,
        amountMode: mode === 'net' ? 'NET' : 'GROSS',
        contractType: TYPE_ENUM[type] || 'EMPLOYMENT',
        year: 2025,
        pit0,
    };
}

async function callCalculate(payload: ReturnType<typeof buildPayload>) {
    const apiBase = (window as any).__API_BASE__ || '';
    const res = await fetch(`${apiBase}/api/salary/calculate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(`API ${res.status}`);
    return res.json();
}

export default function SalaryCalculatorPage() {
    const rootRef = useRef<HTMLDivElement | null>(null);
    const [amount, setAmount] = useState(5000);
    const [mode, setMode] = useState<AmountMode>('gross');
    const [type, setType] = useState<ContractType>('employment');
    const [pit0, setPit0] = useState(false);
    const [annualNet, setAnnualNet] = useState<number | null>(null);

    const payload = useMemo(() => buildPayload(amount, mode, type, pit0), [amount, mode, type, pit0]);

    const renderList = (parts: SalaryParts, grossForBars: number) => {
        const root = rootRef.current;
        if (!root) return;
        const box = root.querySelector<HTMLElement>('#sc-list');
        if (!box) return;

        const gross = Math.max(1, Number(grossForBars || 0));
        const C = {
            net: getComputedStyle(root).getPropertyValue('--netto').trim() || '#5b7fff',
            pit: '#ff6b8b',
            health: '#f9b54c',
            sickness: '#7cc4ff',
            disability: '#b7d1ff',
            pension: '#9aaeff',
        };

        const rows = [
            { key: 'net', label: 'Net amount', val: parts.net, color: C.net },
            { key: 'pension', label: 'Pension insurance', val: parts.pension, color: C.pension },
            { key: 'disability', label: 'Disability insurance', val: parts.disability, color: C.disability },
            { key: 'sickness', label: 'Sickness insurance', val: parts.sickness, color: C.sickness },
            { key: 'health', label: 'Health insurance', val: parts.health, color: C.health },
            { key: 'pit', label: 'PIT prepayment', val: parts.pit, color: C.pit },
        ];

        box.innerHTML = rows
            .map((r) => {
                const pct = Math.max(0, Math.min(100, (Number(r.val || 0) / gross) * 100));
                return `
      <div class="${styles['sc__li']}" style="--p:${pct}; --c:${r.color}">
        <div class="${styles['sc-li__bar']}"></div>
        <div class="${styles['sc-li__content']}">
          <span class="${styles['sc__li__label']}"><i class="${styles['sc-li__dot']}"></i>${r.label}</span>
          <b>${PLN(r.val)}</b>
        </div>
      </div>`;
            })
            .join('');
    };

    const renderDonut = (parts: SalaryParts) => {
        const root = rootRef.current;
        if (!root) return;
        const svg = root.querySelector<SVGSVGElement>('#sc-donut');
        if (!svg) return;
        const r = 52;
        const C = 2 * Math.PI * r;
        const STROKE = 16;
        const EPS = 0.001;

        const segs = [
            { key: 'pit', color: '#ff6b8b' },
            { key: 'health', color: '#f9b54c' },
            { key: 'sickness', color: '#7cc4ff' },
            { key: 'disability', color: '#b7d1ff' },
            { key: 'pension', color: '#9aaeff' },
        ];

        const total = parts.gross > 0 ? parts.gross : 1;
        let acc = 0;

        svg.innerHTML = `
    <defs>
      <linearGradient id="donutBg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%"  stop-color="var(--grad1)" />
        <stop offset="100%" stop-color="var(--grad2)" />
      </linearGradient>
    </defs>
    <g transform="translate(60,60)">
      <circle r="${r}" fill="none" stroke="url(#donutBg)" stroke-width="${STROKE}" opacity=".15"></circle>
    </g>
  `;

        const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        g.setAttribute('transform', 'translate(60,60) rotate(-90)');

        segs.forEach((s) => {
            const rawFrac = Math.max(0, Math.min(1, (parts as any)[s.key] / total));
            if (rawFrac <= 0) return;
            const frac = Math.min(1 - acc, rawFrac + EPS);
            const dash = frac * C;

            const path = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            path.setAttribute('r', String(r));
            path.setAttribute('fill', 'none');
            path.setAttribute('stroke', s.color);
            path.setAttribute('stroke-width', String(STROKE));
            path.setAttribute('stroke-linecap', 'butt');
            path.setAttribute('stroke-dasharray', `${dash} ${C}`);
            path.setAttribute('stroke-dashoffset', `${-acc * C}`);

            acc += rawFrac;
            g.appendChild(path);
        });

        svg.appendChild(g);

        const center = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        center.setAttribute('transform', 'translate(60,60)');
        center.innerHTML = `
    <text x="0" y="-4" text-anchor="middle" font-size="12" fill="var(--muted)">Net</text>
    <text x="0" y="14" text-anchor="middle" font-size="14" font-weight="800">${PLN(parts.net)}</text>`;
        svg.appendChild(center);
    };

    const layoutBarLabels = (colEl: HTMLElement, insetPx = 26, segPadPx = 8, vGapPx = 3) => {
        const H = colEl.clientHeight || 480;
        const spans = Array.from(colEl.querySelectorAll(`.${styles['sc__bar__seg']} > span`)) as HTMLSpanElement[];
        if (!spans.length) return;

        const items = spans
            .map((sp) => {
                sp.style.transform = 'translateY(-50%)';

                const cp = parseFloat(sp.dataset.cp || '0');
                const hSeg = parseFloat(sp.dataset.h || '0');

                sp.style.setProperty('--lane', '0');

                const desired = (cp / 100) * H;
                const tpx = ((cp - hSeg / 2) / 100) * H;
                const bpx = ((cp + hSeg / 2) / 100) * H;

                const half = (sp.offsetHeight || 18) / 2;
                const lo = Math.max(insetPx, tpx + segPadPx + half);
                const hi = Math.min(H - insetPx, bpx - segPadPx - half);

                const segPx = (hSeg / 100) * H;
                const bias = Math.min(4, Math.max(0, half - segPx / 2));

                const y = Math.min(Math.max(desired + bias, lo), hi);
                return { sp, desired, y, half, tpx, bpx, lo, hi, hSeg };
            })
            .sort((a, b) => a.y - b.y);

        let changed = true;
        let iter = 0;
        while (changed && iter++ < 40) {
            changed = false;

            for (let i = 1; i < items.length; i++) {
                const prev = items[i - 1];
                const cur = items[i];

                const need = prev.half + cur.half + vGapPx - (cur.y - prev.y);
                if (need > 0) {
                    const curDown = cur.hi - cur.y;
                    const prevUp = prev.y - prev.lo;

                    if (curDown >= prevUp) {
                        const mv = Math.min(need, curDown);
                        if (mv > 0) {
                            cur.y += mv;
                            changed = true;
                        }
                    } else {
                        const mv = Math.min(need, prevUp);
                        if (mv > 0) {
                            prev.y -= mv;
                            changed = true;
                        }
                    }
                }

                prev.y = Math.min(Math.max(prev.y, prev.lo), prev.hi);
                cur.y = Math.min(Math.max(cur.y, cur.lo), cur.hi);
            }
        }

        for (let i = 1; i < items.length; i++) {
            const a = items[i - 1];
            const b = items[i];

            const aMicro = a.hSeg < 2;
            const bMicro = b.hSeg < 2;
            const aThin = !aMicro && a.hSeg < 3.2;
            const bThin = !bMicro && b.hSeg < 3.2;

            const close = b.y - a.y < a.half + b.half + vGapPx + 2;
            if (!close) continue;

            if ((aMicro && bThin) || (aThin && bMicro)) {
                const micro = aMicro ? a : b;
                const thin = aThin ? a : b;
                micro.sp.style.setProperty('--lane', '0');
                thin.sp.style.setProperty('--lane', '1');
                continue;
            }

            if (aThin && bThin) {
                const smaller = a.hSeg <= b.hSeg ? a : b;
                const larger = smaller === a ? b : a;
                smaller.sp.style.setProperty('--lane', '0');
                larger.sp.style.setProperty('--lane', '1');
                continue;
            }

            if (aMicro && bMicro) {
                a.sp.style.setProperty('--lane', '0');
                b.sp.style.setProperty('--lane', '1');
            }
        }

        items.forEach((it) => {
            it.sp.style.transform = `translateY(-50%) translateY(${it.y - it.desired}px)`;
            it.sp.style.willChange = 'transform';
        });
    };

    const renderBarsFromApi = async (ui: { amount: number; mode: AmountMode }) => {
        const root = rootRef.current;
        if (!root) return;
        const box = root.querySelector<HTMLElement>('#sc-bars');
        if (!box) return;

        const scenarios: Array<{ name: string; type: ContractType }> = [
            { name: 'Employment', type: 'employment' },
            { name: 'Mandate', type: 'mandate' },
            { name: 'Specific-task', type: 'specific' },
            { name: 'B2B', type: 'b2b' },
        ];

        const results = await Promise.all(
            scenarios.map((s) => callCalculate(buildPayload(ui.amount, ui.mode, s.type, pit0))),
        );

        const html = results
            .map((r: any, i) => {
                const grossR = Number(r.gross || 0);
                const netR = Number(r.net || 0);
                const it = r.items || {};
                const parts = {
                    pension: Number(it.pension || 0),
                    disability: Number(it.disability || 0),
                    sickness: Number(it.sickness || 0),
                    health: Number(it.health || 0),
                    pit: Number(it.pit || 0),
                };

                const stack = [
                    { cls: 'pit', val: parts.pit },
                    { cls: 'zdrowotne', val: parts.health },
                    { cls: 'chorobowe', val: parts.sickness },
                    { cls: 'rentowe', val: parts.disability },
                    { cls: 'emerytalne', val: parts.pension },
                ];

                let top = 0;
                const taxSegs = stack
                    .map((p) => {
                        const h = grossR ? (p.val / grossR) * 100 : 0;
                        if (h <= 0) return '';
                        const centerPct = top + h / 2;

                        const seg = `
            <div class="${styles['sc__bar__seg']} ${styles[`sc__bar__seg--${p.cls}`]}" style="top:${top}%;height:${h}%">
              <span data-cp="${centerPct}" data-h="${h}" title="${h.toFixed(1)}%" style="transform:translateY(-50%); --lane:0">${h.toFixed(1)}%</span>
            </div>`;
                        top += h;
                        return seg;
                    })
                    .join('');

                const netPct = Math.max(0, 100 - top);
                const enteredLine = ui.mode === 'gross' ? `gross: ${PLN(ui.amount)}` : '';
                const derivedLine = ui.mode === 'net' ? `gross: ${PLN(grossR)}` : '';

                return `
        <div class="${styles.sc__bar}" title="${scenarios[i].name}">
          <div class="${styles['sc__bar__col']}">
            ${taxSegs}
            <div class="${styles['sc__bar__seg']} ${styles['sc__bar__seg--netto']}" style="bottom:0;height:${netPct}%">
              <span data-cp="${100 - netPct / 2}" data-h="${netPct}" title="${netPct.toFixed(1)}%">${netPct.toFixed(1)}%</span>
            </div>
          </div>
          <div class="${styles['sc__bar__label']}">
            <span>${scenarios[i].name}</span>
            <strong class="${styles['sc__net__val']}">${PLN(netR)}</strong>
            <small>${enteredLine}</small>
            ${derivedLine ? `<small>${derivedLine}</small>` : ''}
          </div>
        </div>`;
            })
            .join('');

        box.innerHTML = html;

        const relayout = () => {
            const cssInset =
                parseFloat(getComputedStyle(root).getPropertyValue('--label-vert-inset')) || 26;
            root.querySelectorAll<HTMLElement>(`.${styles['sc__bar__col']}`).forEach((col) =>
                layoutBarLabels(col, cssInset, -3.1, 3),
            );
        };
        relayout();

        if (!(window as any).__scBarsResizeBound) {
            window.addEventListener('resize', relayout, { passive: true });
            (window as any).__scBarsResizeBound = true;
        }
    };

    const renderMonths = (parts: SalaryParts) => {
        const root = rootRef.current;
        if (!root) return;
        const tbody = root.querySelector<HTMLElement>('#sc-months');
        if (!tbody) return;
        tbody.innerHTML = MONTHS.map(
            (m) => `
        <tr>
          <td>${m}</td>
          <td>${PLN(parts.gross)}</td>
          <td>${PLN(parts.pension + parts.disability + parts.sickness)}</td>
          <td>${PLN(parts.health)}</td>
          <td>${PLN(parts.pit)}</td>
          <td>${PLN(parts.net)}</td>
        </tr>`,
        ).join('');
    };

    useEffect(() => {
        const t = setTimeout(async () => {
            try {
                const main = await callCalculate(payload);
                const gross = Number(main.gross || 0);
                const net = Number(main.net || 0);
                const it = main.items || {};
                const parts: SalaryParts = {
                    gross,
                    net,
                    pension: Number(it.pension || 0),
                    disability: Number(it.disability || 0),
                    sickness: Number(it.sickness || 0),
                    health: Number(it.health || 0),
                    pit: Number(it.pit || 0),
                };
                setAnnualNet(net * 12);

                renderList(parts, gross);
                renderDonut({ ...parts });
                await renderBarsFromApi({ amount, mode });
                renderMonths(parts);
            } catch (err) {
                console.warn('Salary API error', err);
            }
        }, 120);
        return () => clearTimeout(t);
    }, [payload, amount, mode, pit0]);

    return (
        <section className={styles.sc} ref={rootRef}>
            <header className={styles.sc__hero}>
                <h1 className={styles.grad}>Salary Calculator</h1>
                <p>Enter the amount and contract type to see an estimated take-home pay and deduction structure.</p>
            </header>

            <section className={styles.sc__panel} aria-labelledby="sc-form-title">
                <h2 id="sc-form-title" style={{ textAlign: 'center', margin: '0 0 14px', fontWeight: 900, letterSpacing: '.2px' }}>
                    Calculator form
                </h2>

                <div className={styles.sc__grid}>
                    <label className={styles.sc__field}>
                        <span>Monthly amount</span>
                        <div className={styles.sc__amount}>
                            <input
                                id="sc-amount"
                                type="number"
                                min={0}
                                step={50}
                                value={amount}
                                inputMode="numeric"
                                onChange={(e) => setAmount(Math.max(0, Number(e.target.value || 0)))}
                            />
                            <span className={styles.sc__currency}>PLN</span>
                        </div>
                    </label>

                    <fieldset className={styles.sc__field} role="group" aria-label="Amount mode">
                        <span>Amount mode</span>
                        <div className={styles.sc__seg}>
                            <input
                                type="radio"
                                name="sc-mode"
                                id="sc-mode-gross"
                                value="gross"
                                checked={mode === 'gross'}
                                onChange={() => setMode('gross')}
                            />
                            <label htmlFor="sc-mode-gross">Gross</label>
                            <input
                                type="radio"
                                name="sc-mode"
                                id="sc-mode-net"
                                value="net"
                                checked={mode === 'net'}
                                onChange={() => setMode('net')}
                            />
                            <label htmlFor="sc-mode-net">Net (take-home)</label>
                        </div>
                    </fieldset>

                    <fieldset className={`${styles.sc__field} ${styles.sc__full}`} role="group" aria-label="Contract type">
                        <span>Contract type</span>
                        <div className={`${styles.sc__seg} ${styles['sc__seg--wide']}`}>
                            <input
                                type="radio"
                                name="sc-type"
                                id="sc-type-employment"
                                value="employment"
                                checked={type === 'employment'}
                                onChange={() => setType('employment')}
                            />
                            <label htmlFor="sc-type-employment">Employment</label>

                            <input
                                type="radio"
                                name="sc-type"
                                id="sc-type-mandate"
                                value="mandate"
                                checked={type === 'mandate'}
                                onChange={() => setType('mandate')}
                            />
                            <label htmlFor="sc-type-mandate">Mandate</label>

                            <input
                                type="radio"
                                name="sc-type"
                                id="sc-type-specific"
                                value="specific"
                                checked={type === 'specific'}
                                onChange={() => setType('specific')}
                            />
                            <label htmlFor="sc-type-specific">Specific-task</label>

                            <input
                                type="radio"
                                name="sc-type"
                                id="sc-type-b2b"
                                value="b2b"
                                checked={type === 'b2b'}
                                onChange={() => setType('b2b')}
                            />
                            <label htmlFor="sc-type-b2b">B2B</label>
                        </div>
                    </fieldset>

                    <details className={`${styles.sc__details} ${styles.sc__full}`}>
                        <summary>Details (optional)</summary>
                        <div className={styles['sc-details__grid']}>
                            <label className={styles['sc-inline']}>
                                <input id="sc-pit0" type="checkbox" checked={pit0} onChange={(e) => setPit0(e.target.checked)} />
                                <span>Tax-exempt &lt; 26 (PIT-0)</span>
                            </label>
                        </div>
                    </details>
                </div>
            </section>

            <section className={styles.sc__summary} aria-labelledby="sc-summary-title">
                <h2 id="sc-summary-title" className={styles['visually-hidden']}>
                    Summary
                </h2>

                <div className={`${styles.sc__card} ${styles.sc__breakdown}`}>
                    <div className={styles.sc__breakdown__chart}>
                        <svg id="sc-donut" className={styles.sc__donut} viewBox="0 0 120 120" aria-label="Deductions structure"></svg>
                    </div>
                    <div className={styles.sc__breakdown__list} id="sc-list"></div>
                </div>
            </section>

            <section className={`${styles.sc__card} ${styles.sc__compare}`} aria-labelledby="sc-compare-title">
                <div className={styles.sc__compare__head}>
                    <h3 id="sc-compare-title">Comparison (net/tax proportions - detailed)</h3>
                    <div className={styles.sc__legend}>
                        <span>
                            <i className={`${styles.sw} ${styles.netto}`}></i>Net
                        </span>
                        <span>
                            <i className={`${styles.sw} ${styles.pit}`}></i>PIT
                        </span>
                        <span>
                            <i className={`${styles.sw} ${styles.zdrow}`}></i>Health
                        </span>
                        <span>
                            <i className={`${styles.sw} ${styles.chor}`}></i>Sickness
                        </span>
                        <span>
                            <i className={`${styles.sw} ${styles.rent}`}></i>Disability
                        </span>
                        <span>
                            <i className={`${styles.sw} ${styles.emer}`}></i>Pension
                        </span>
                    </div>
                </div>
                <div className={styles.sc__bars} id="sc-bars"></div>
            </section>

            <section className={`${styles.sc__card} ${styles.sc__table}`} aria-labelledby="sc-table-title">
                <h3 id="sc-table-title" style={{ textAlign: 'center', margin: '0 0 14px' }}>
                    Monthly breakdown
                </h3>
                <div className={styles.sc__table__wrap}>
                    <table>
                        <thead>
                            <tr>
                                <th>Month</th>
                                <th>Gross</th>
                                <th>Social insurance</th>
                                <th>Health</th>
                                <th>PIT prepayment</th>
                                <th>Net</th>
                            </tr>
                        </thead>
                        <tbody id="sc-months"></tbody>
                    </table>
                </div>
                {annualNet != null ? <p className={styles.sc__annual}>Year total (net): {PLN(annualNet)}</p> : null}
            </section>

            <p className={styles.sc__note}>
                This is a prototype with simplified rules - educational only. Real results depend on full regulations and your situation.
            </p>
        </section>
    );
}
