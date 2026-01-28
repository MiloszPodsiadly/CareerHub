import { FormEvent, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import styles from './Footer.module.css';

export default function Footer() {
    const year = useMemo(() => new Date().getFullYear(), []);
    const [message, setMessage] = useState('');
    const [error, setError] = useState(false);

    const onSubmit = (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        const form = e.currentTarget;
        const fd = new FormData(form);
        const email = String(fd.get('email') || '').trim();
        const valid = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email);

        if (!valid) {
            setError(true);
            setMessage('Please enter a valid email address.');
            return;
        }

        setError(false);
        setMessage('Thanks! Please confirm your subscription via email.');

        try {
            document.dispatchEvent(new CustomEvent('footer:newsletter', { detail: { email } }));
        } catch (err) {
            const ev = document.createEvent('CustomEvent');
            ev.initCustomEvent('footer:newsletter', true, true, { email });
            document.dispatchEvent(ev);
        }

        form.reset();
    };

    return (
        <footer className={styles.ftr} role="contentinfo">
            <div className={styles.ftr__inner}>
                <div className={styles.ftr__brand}>
                    <Link className={styles.brand} to="/" aria-label="Home">
                        <span className={styles.brand__badge} aria-hidden="true">
                            ⚡
                        </span>
                        <span className={styles.brand__text}>Discovery</span>
                    </Link>
                    <p>A friendly platform for IT professionals: job offers, events, and hackathons in one place.</p>

                    <div className={styles.ftr__social}>
                        <a href="#" aria-label="LinkedIn" title="LinkedIn">
                            in
                        </a>
                        <a href="#" aria-label="GitHub" title="GitHub">
                            gh
                        </a>
                        <a href="#" aria-label="YouTube" title="YouTube">
                            yt
                        </a>
                        <a href="#" aria-label="X" title="X">
                            x
                        </a>
                    </div>
                </div>

                <nav className={styles.ftr__nav} aria-label="Footer navigation">
                    <div>
                        <h4>Explore</h4>
                        <ul>
                            <li>
                                <Link to="/jobs">Jobs</Link>
                            </li>
                            <li>
                                <Link to="/events">Events</Link>
                            </li>
                            <li>
                                <a href="#hackathons">Hackathons</a>
                            </li>
                            <li>
                                <Link to="/salary-calculator">Salary calculator</Link>
                            </li>
                        </ul>
                    </div>
                    <div>
                        <h4>For recruiters</h4>
                        <ul>
                            <li>
                                <Link to="/post-job">Post a job</Link>
                            </li>
                            <li>
                                <a href="#" title="Pricing (coming soon)">
                                    Pricing
                                </a>
                            </li>
                            <li>
                                <a href="#" title="Employer branding (coming soon)">
                                    Employer branding
                                </a>
                            </li>
                        </ul>
                    </div>
                    <div>
                        <h4>Support</h4>
                        <ul>
                            <li>
                                <a href="#" title="FAQ">
                                    FAQ
                                </a>
                            </li>
                            <li>
                                <a href="#" title="Contact">
                                    Contact
                                </a>
                            </li>
                            <li>
                                <a href="#" title="Terms of service">
                                    Terms
                                </a>
                            </li>
                            <li>
                                <a href="#" title="Privacy policy">
                                    Privacy
                                </a>
                            </li>
                        </ul>
                    </div>
                </nav>

                <div className={styles.ftr__cta}>
                    <h4>Stay in the loop</h4>
                    <p>Fresh jobs and events — once a week, zero spam.</p>
                    <form className={styles.nl} onSubmit={onSubmit}>
                        <input type="email" name="email" placeholder="Your email" aria-label="Email address" required />
                        <button className={[styles.btn, styles['btn--primary']].join(' ')} type="submit">
                            Subscribe
                        </button>
                    </form>
                    <p className={styles.nl__msg} aria-live="polite" style={error ? { color: '#e11d48' } : undefined}>
                        {message}
                    </p>
                </div>
            </div>

            <div className={styles.ftr__bar}>© {year} CareerHub</div>
        </footer>
    );
}
