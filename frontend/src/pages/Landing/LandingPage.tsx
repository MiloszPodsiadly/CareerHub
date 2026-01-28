import { useEffect, useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';

import styles from './Landing.module.css';

const styleDelay = (value: string) => ({ ['--delay' as string]: value });

export default function LandingPage() {
    const rootRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        const root = rootRef.current;
        if (!root) return;

        const timers: Array<number> = [];

        const io = new IntersectionObserver(
            (entries) =>
                entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                        entry.target.classList.add(styles.visible);
                        io.unobserve(entry.target);
                    }
                }),
            { threshold: 0.15 },
        );

        root.querySelectorAll(`.${styles.reveal}`).forEach((el) => io.observe(el));

        const jobsEl = root.querySelector(`[data-stat="jobs"] .${styles['stat__num']}`) as HTMLElement | null;
        const citiesEl = root.querySelector(`[data-stat="companies"] .${styles['stat__num']}`) as HTMLElement | null;
        const eventsEl = root.querySelector(`[data-stat="events"] .${styles['stat__num']}`) as HTMLElement | null;

        const fmt = (n: number) => Number(n || 0).toLocaleString(navigator.language || 'pl-PL');

        if (jobsEl) jobsEl.textContent = '0';
        if (citiesEl) citiesEl.textContent = '500+';
        if (eventsEl) eventsEl.textContent = '0';

        const dataCount = (el: HTMLElement | null) => parseInt(el?.dataset?.count || '0', 10);

        const animateCount = (el: HTMLElement | null, target: number) => {
            if (!el) return;
            const finalVal = Math.max(0, Number(target) || 0);
            const durationMs = 900;
            const tickMs = 16;
            const steps = Math.max(18, Math.floor(durationMs / tickMs));
            const step = Math.max(1, Math.ceil(finalVal / steps));
            let cur = 0;

            if ((el as any).__countTimer) clearInterval((el as any).__countTimer);

            el.classList.remove(styles['is-done']);
            el.classList.add(styles['is-animating']);

            const id = window.setInterval(() => {
                cur = Math.min(cur + step, finalVal);
                el.textContent = fmt(cur);
                if (cur === finalVal) {
                    clearInterval(id);
                    (el as any).__countTimer = null;
                    el.classList.remove(styles['is-animating']);
                    el.classList.add(styles['is-done']);
                    window.setTimeout(() => el.classList.remove(styles['is-done']), 650);
                }
            }, tickMs);

            (el as any).__countTimer = id;
            timers.push(id);
        };

        const fetchJson = async (url: string) => {
            const r = await fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store' });
            if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
            const xTotal = r.headers.get('X-Total-Count');
            let json: any = null;
            try {
                json = await r.json();
            } catch {}
            return { json, xTotal: xTotal ? parseInt(xTotal, 10) : null };
        };

        const extractTotal = (json: any) => {
            if (typeof json?.totalElements === 'number') return json.totalElements;
            if (typeof json?.total === 'number') return json.total;
            if (typeof json?.page?.totalElements === 'number') return json.page.totalElements;
            if (typeof json?.meta?.total === 'number') return json.meta.total;
            if (Array.isArray(json?.content)) return json.content.length;
            if (Array.isArray(json?.items)) return json.items.length;
            if (Array.isArray(json)) return json.length;
            return 0;
        };

        const fetchJobsTotal = async (apiUrl: string) => {
            let u = new URL(apiUrl, location.origin);
            u.searchParams.set('page', '1');
            u.searchParams.set('pageSize', '1');
            try {
                const { json, xTotal } = await fetchJson(u.toString());
                if (Number.isFinite(xTotal)) return xTotal!;
                const t = extractTotal(json);
                if (t > 0) return t;
            } catch {}

            u = new URL(apiUrl, location.origin);
            u.searchParams.set('page', '0');
            u.searchParams.set('size', '1');
            try {
                const { json, xTotal } = await fetchJson(u.toString());
                if (Number.isFinite(xTotal)) return xTotal!;
                return extractTotal(json);
            } catch {
                return 0;
            }
        };

        const estimateCitiesFromJobs = async (apiUrl: string, sampleSize = 150) => {
            try {
                const u = new URL(apiUrl, location.origin);
                u.searchParams.set('page', '1');
                u.searchParams.set('pageSize', '1');
                u.searchParams.set('size', '1');
                const { json } = await fetchJson(u.toString());
                const direct =
                    (typeof json?.citiesCount === 'number' && json.citiesCount) ||
                    (typeof json?.meta?.citiesCount === 'number' && json.meta.citiesCount) ||
                    (typeof json?.stats?.cities === 'number' && json.stats.cities);
                if (direct && direct > 0) return direct;
            } catch {}

            const u = new URL(apiUrl, location.origin);
            u.searchParams.set('page', '1');
            u.searchParams.set('pageSize', String(sampleSize));
            u.searchParams.set('size', String(sampleSize));
            u.searchParams.set('sort', 'date');
            try {
                const { json } = await fetchJson(u.toString());
                const list = Array.isArray(json?.content)
                    ? json.content
                    : Array.isArray(json?.items)
                        ? json.items
                        : Array.isArray(json)
                            ? json
                            : [];
                const cities = new Set(
                    list
                        .map((x: any) => x?.cityName || x?.city || '')
                        .map(String)
                        .map((s: string) => s.trim())
                        .filter(Boolean),
                );
                return cities.size;
            } catch {
                return 0;
            }
        };

        const fetchEventsTotal = async (apiUrl: string) => {
            let u = new URL(apiUrl, location.origin);
            u.searchParams.set('from', new Date().toISOString());
            u.searchParams.set('page', '0');
            u.searchParams.set('size', '1');
            u.searchParams.set('sort', 'startAt,asc');
            try {
                const { json, xTotal } = await fetchJson(u.toString());
                if (Number.isFinite(xTotal)) return xTotal!;
                const t = extractTotal(json);
                if (t > 0) return t;
            } catch {}

            u = new URL(apiUrl, location.origin);
            u.searchParams.set('page', '0');
            u.searchParams.set('size', '1');
            u.searchParams.set('sort', 'startAt,asc');
            try {
                const { json, xTotal } = await fetchJson(u.toString());
                if (Number.isFinite(xTotal)) return xTotal!;
                return extractTotal(json);
            } catch {
                return 0;
            }
        };

        const jobsApi = '/api/jobs';
        const eventsApi = '/api/events';

        fetchJobsTotal(jobsApi)
            .then((n) => animateCount(jobsEl, n || dataCount(jobsEl)))
            .catch(() => animateCount(jobsEl, dataCount(jobsEl)));

        if (citiesEl) citiesEl.textContent = '500+';

        fetchEventsTotal(eventsApi)
            .then((n) => animateCount(eventsEl, n || dataCount(eventsEl)))
            .catch(() => animateCount(eventsEl, dataCount(eventsEl)));

        return () => {
            io.disconnect();
            timers.forEach((id) => clearInterval(id));
        };
    }, []);

    const heroCards = useMemo(
        () => [
            {
                title: 'Global spotlight',
                strong: 'Customized IT-related positions with clear job descriptions',
                text: 'Weekly job postings, selected for clarity and impact.',
                wide: true,
                delay: '0.05s',
            },
            {
                title: 'Community',
                strong: 'IT live events',
                text: 'Workshops, conferences, hackathons.',
                delay: '0.12s',
            },
            {
                title: 'Hiring teams',
                strong: 'Free accounts here',
                text: 'Clear expectations and respectful timelines.',
                delay: '0.18s',
            },
        ],
        [],
    );

    return (
        <div className={styles.landing} ref={rootRef}>
            <section className={`${styles.hero} ${styles.reveal} ${styles.container}`} style={styleDelay('0s')}>
                <div className={styles['hero__content']}>
                    <span className={styles.eyebrow}>CareerHub for the world</span>
                    <h1>
                        The global hub for <span className={styles.grad}>IT careers</span> and hiring
                    </h1>
                    <p>
                        CareerHub connects software engineers, designers, and data teams with verified employers and
                        high-signal events worldwide. Precision matching, transparent ranges, and a calm, modern
                        journey.
                    </p>
                    <div className={styles['hero__cta']}>
                        <Link to="/jobs" className={`${styles.btn} ${styles['btn--primary']}`}>
                            Browse jobs
                        </Link>
                        <Link to="/events" className={`${styles.btn} ${styles['btn--ghost']}`}>
                            See events
                        </Link>
                    </div>
                    <div className={styles['hero__note']}>Trusted listings, structured feedback, and a peaceful UX.</div>
                    <div className={styles['hero__badges']} aria-label="Highlights">
                        <span>Worldwide roles, remote-ready</span>
                        <span>Salary ranges on jobs offers</span>
                        <span>Signal-first matching</span>
                    </div>
                </div>
                <div className={styles['hero__visual']} aria-hidden="true">
                    <div className={styles['hero__bento']}>
                        {heroCards.map((card, idx) => (
                            <div
                                key={card.title}
                                className={`${styles['hero__card']} ${card.wide ? styles['hero__card--wide'] : ''}`}
                                style={styleDelay(card.delay)}
                            >
                                <span>{card.title}</span>
                                <strong>{card.strong}</strong>
                                <p>{card.text}</p>
                            </div>
                        ))}
                    </div>
                    <div className={`${styles.blob} ${styles.b1}`}></div>
                    <div className={`${styles.blob} ${styles.b2}`}></div>
                    <div className={`${styles.blob} ${styles.b3}`}></div>
                </div>
            </section>

            <section className={`${styles.stats} ${styles.reveal} ${styles.container}`} aria-label="Key metrics" style={styleDelay('0.1s')}>
                <div className={styles.stat} data-stat="jobs" style={styleDelay('0.04s')}>
                    <div className={styles['stat__num']}>0</div>
                    <div className={styles['stat__label']}>Active jobs</div>
                </div>
                <div className={styles.stat} data-stat="companies" style={styleDelay('0.08s')}>
                    <div className={styles['stat__num']}>0</div>
                    <div className={styles['stat__label']}>Cities with jobs</div>
                </div>
                <div className={styles.stat} data-stat="events" style={styleDelay('0.12s')}>
                    <div className={styles['stat__num']}>0</div>
                    <div className={styles['stat__label']}>Global events</div>
                </div>
            </section>

            <section className={`${styles.bento} ${styles.reveal} ${styles.container}`} aria-label="Platform highlights" style={styleDelay('0.12s')}>
                <div className={`${styles['bento__card']} ${styles['bento__card--wide']} ${styles['bento__card--balanced']}`} style={styleDelay('0.04s')}>
                    <h3>Curated for clarity, not noise</h3>
                    <p>Every role is structured, verified, and ranked for fit, so talent can move fast with confidence.</p>
                    <div className={styles['bento__tags']}>
                        <span>Signal-first search</span>
                        <span>Verified employers</span>
                        <span>Human support</span>
                    </div>
                    <div className={styles['bento__footer']}>Coverage across 80+ countries and remote-first teams.</div>
                </div>
                <div className={styles['bento__card']} style={styleDelay('0.06s')}>
                    <h3>Global reach</h3>
                    <p>One platform for distributed teams, with regional insights and multilingual listings.</p>
                    <div className={styles['bento__mini']}>
                        <span>Remote-first</span>
                        <span>Global time zones</span>
                        <span>Local insights</span>
                    </div>
                </div>
                <div className={styles['bento__card']} style={styleDelay('0.08s')}>
                    <h3>Candidate calm</h3>
                    <p>One clean profile, quick apply, and clear feedback timelines with no surprises.</p>
                    <div className={styles['bento__mini']}>
                        <span>Fast apply</span>
                        <span>Timeline alerts</span>
                        <span>Single profile</span>
                    </div>
                    <div className={styles['bento__footer']}>Consistent updates so candidates never feel lost.</div>
                </div>
                <div className={styles['bento__card']} style={styleDelay('0.12s')}>
                    <h3>Employer focus</h3>
                    <p>Dedicated pipelines, structured interviews, and decision-ready scorecards.</p>
                    <div className={`${styles['bento__mini']} ${styles['bento__mini--center']}`}>
                        <span>Scorecards</span>
                        <span>Interview kits</span>
                        <span>Team alignment</span>
                    </div>
                </div>
                <div className={styles['bento__card']} style={styleDelay('0.16s')}>
                    <h3>Insightful analytics</h3>
                    <p>Track response rates, hiring velocity, and skill demand across markets.</p>
                    <div className={styles['bento__metric']}>
                        <strong>24 hrs</strong>
                        <span>Median time to first reply</span>
                    </div>
                    <div className={styles['bento__footer']}>Weekly insights and quarterly market snapshots.</div>
                </div>
            </section>

            <section className={`${styles.categories} ${styles.reveal} ${styles.container}`} aria-label="Explore" style={styleDelay('0.14s')}>
                <article className={styles.cat} style={styleDelay('0.04s')}>
                    <div className={styles['cat__icon']}>J</div>
                    <h3>Job offers</h3>
                    <p>Fresh listings with salary ranges, stacks, and roles.</p>
                    <Link to="/jobs" className={`${styles.btn} ${styles['btn--ghost']}`}>
                        Go to jobs
                    </Link>
                </article>

                <article className={styles.cat} style={styleDelay('0.08s')}>
                    <div className={styles['cat__icon']}>E</div>
                    <h3>Events</h3>
                    <p>Conferences, meetups, and workshops in one place.</p>
                    <Link to="/events" className={`${styles.btn} ${styles['btn--ghost']}`}>
                        Check events
                    </Link>
                </article>

                <article className={styles.cat} style={styleDelay('0.12s')}>
                    <div className={styles['cat__icon']}>S</div>
                    <h3>Salary calculator</h3>
                    <p>Check ranges by role and seniority fast and clear.</p>
                    <Link to="/salary-calculator" className={`${styles.btn} ${styles['btn--ghost']}`}>
                        Calculate salary
                    </Link>
                </article>
            </section>

            <section className={`${styles.features} ${styles.reveal} ${styles.container}`} style={styleDelay('0.16s')}>
                <div className={styles['section-title']}>
                    <h2>Why CareerHub?</h2>
                    <p>Commercial polish with a calm, focused experience for everyone.</p>
                </div>
                <div className={styles.grid}>
                    <div className={styles.feature} style={styleDelay('0.04s')}>
                        <div className={styles['feature__icon']}>V</div>
                        <h4>Verified listings</h4>
                        <p>Only real offers with clear ranges and requirements.</p>
                    </div>
                    <div className={styles.feature} style={styleDelay('0.08s')}>
                        <div className={styles['feature__icon']}>F</div>
                        <h4>Intuitive filtering</h4>
                        <p>Drill down by tech stack, level, and work mode in seconds.</p>
                    </div>
                    <div className={styles.feature} style={styleDelay('0.12s')}>
                        <div className={styles['feature__icon']}>I</div>
                        <h4>Built for IT</h4>
                        <p>Created by people from the industry with calm UX.</p>
                    </div>
                    <div className={styles.feature} style={styleDelay('0.16s')}>
                        <div className={styles['feature__icon']}>D</div>
                        <h4>Data and insights</h4>
                        <p>Market analytics, salary trends, and tech popularity.</p>
                    </div>
                </div>
            </section>

            <section className={`${styles.process} ${styles.reveal} ${styles.container}`} aria-label="How it works" style={styleDelay('0.18s')}>
                <div className={styles['section-title']}>
                    <h2>Hiring flow that feels effortless</h2>
                    <p>Designed to guide candidates and teams through each decision with clarity.</p>
                </div>
                <div className={styles.steps}>
                    <div className={styles.step} style={styleDelay('0.04s')}>
                        <span className={styles['step__num']}>01</span>
                        <div className={styles['step__content']}>
                            <h3>Curate</h3>
                            <p>Only roles with clear expectations, salary ranges, and timelines.</p>
                        </div>
                    </div>
                    <div className={styles.step} style={styleDelay('0.08s')}>
                        <span className={styles['step__num']}>02</span>
                        <div className={styles['step__content']}>
                            <h3>Connect</h3>
                            <p>Match the right candidates with hiring teams fast and respectfully.</p>
                        </div>
                    </div>
                    <div className={styles.step} style={styleDelay('0.12s')}>
                        <span className={styles['step__num']}>03</span>
                        <div className={styles['step__content']}>
                            <h3>Decide</h3>
                            <p>Structured feedback and shared notes keep decisions calm and fair.</p>
                        </div>
                    </div>
                </div>
            </section>

            <section className={`${styles.testimonials} ${styles.reveal} ${styles.container}`} aria-label="Testimonials" style={styleDelay('0.2s')}>
                <div className={styles['section-title']}>
                    <h2>People choose CareerHub for the calm</h2>
                    <p>From startups to enterprise teams, the experience feels clear and respectful.</p>
                </div>
                <div className={styles.quotes}>
                    <article className={styles.quote} style={styleDelay('0.04s')}>
                        <p>"The UI feels premium and quiet. Candidates actually respond."</p>
                        <span>People Ops Lead, Company XYZ</span>
                    </article>
                    <article className={styles.quote} style={styleDelay('0.08s')}>
                        <p>"We filled three senior roles with the most signal we have ever had."</p>
                        <span>Engineering Manager, Company XYZ</span>
                    </article>
                    <article className={styles.quote} style={styleDelay('0.12s')}>
                        <p>"The landing page explains everything in one glance."</p>
                        <span>Talent Partner, Company XYZ</span>
                    </article>
                </div>
            </section>

            <section className={`${styles.faq} ${styles.reveal} ${styles.container}`} aria-label="Frequently asked questions" style={styleDelay('0.22s')}>
                <div className={styles['section-title']}>
                    <h2>Questions, answered</h2>
                    <p>Clear answers for candidates and employers.</p>
                </div>
                <div className={styles['faq__grid']}>
                    <div className={styles['faq__item']} style={styleDelay('0.04s')}>
                        <h3>Do I need an account to browse?</h3>
                        <p>You can explore jobs and events without signing in. Apply anytime.</p>
                    </div>
                    <div className={styles['faq__item']} style={styleDelay('0.08s')}>
                        <h3>How are listings verified?</h3>
                        <p>We review postings for role clarity, salary transparency, and team fit.</p>
                    </div>
                    <div className={styles['faq__item']} style={styleDelay('0.12s')}>
                        <h3>Is CareerHub only for developers?</h3>
                        <p>No. We cover product, data, design, and operations teams too.</p>
                    </div>
                    <div className={styles['faq__item']} style={styleDelay('0.16s')}>
                        <h3>Can employers host events?</h3>
                        <p>Yes. Share community events that support hiring and learning.</p>
                    </div>
                </div>
            </section>

            <section className={`${styles.trust} ${styles.reveal} ${styles.container}`} aria-label="Powered by" style={styleDelay('0.23s')}>
                <p className={styles['trust__label']}>Powered by</p>
                <div className={styles['trust__logos']}>
                    <span style={styleDelay('0.04s')}>JustJoinIt</span>
                    <span style={styleDelay('0.08s')}>NoFluffJobs</span>
                    <span style={styleDelay('0.12s')}>SolidJobs</span>
                    <span style={styleDelay('0.16s')}>TheProtocolIt</span>
                    <span style={styleDelay('0.20s')}>PracujPl</span>
                </div>
            </section>

            <section className={`${styles['cta-final']} ${styles.reveal} ${styles.container}`} style={styleDelay('0.24s')}>
                <h2>Ready to find your next opportunity?</h2>
                <p>Create a free account and start browsing the best jobs and events.</p>
                <div className={styles['hero__cta']}>
                    <Link to="/auth/login" className={`${styles.btn} ${styles['btn--primary']}`}>
                        Sign up
                    </Link>
                    <Link to="/jobs" className={`${styles.btn} ${styles['btn--ghost']}`}>
                        Browse without an account
                    </Link>
                </div>
            </section>
        </div>
    );
}
