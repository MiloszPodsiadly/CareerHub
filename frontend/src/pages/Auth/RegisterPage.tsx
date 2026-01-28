import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import styles from './AuthPage.module.css';
import { authApi } from '../../shared/api';

function isValidEmail(email: string) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export default function RegisterPage() {
    const navigate = useNavigate();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [alert, setAlert] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
    const [loading, setLoading] = useState(false);

    const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setAlert(null);
        setLoading(true);
        try {
            if (!email || !password) {
                setAlert({ type: 'error', text: 'Please enter email and password.' });
                return;
            }
            if (!isValidEmail(email)) {
                setAlert({ type: 'error', text: 'Please enter a valid e-mail address.' });
                return;
            }
            if (password.length < 8) {
                setAlert({ type: 'error', text: 'Password must be at least 8 characters.' });
                return;
            }

            await authApi.register(email.trim(), password.trim());
            setAlert({ type: 'success', text: 'Account created. Check your email to verify your account, then sign in.' });
            setTimeout(() => navigate('/auth/login'), 900);
        } catch (err: any) {
            setAlert({ type: 'error', text: err?.message || 'Something went wrong.' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.authWrap}>
            <div className={styles.auth} data-mode="register">
                <div className={styles.tabs}>
                    <Link className={styles.tab} to="/auth/login">
                        Sign in
                    </Link>
                    <Link className={`${styles.tab} ${styles.tabActive}`} to="/auth/register">
                        Create account
                    </Link>
                </div>

                <h2 className={styles.title}>Create account</h2>
                <p className={styles.muted}>Create a free account.</p>

                {alert ? (
                    <div className={`${styles.alert} ${alert.type === 'error' ? styles.alertError : styles.alertSuccess}`}>
                        {alert.text}
                    </div>
                ) : null}

                <form className={styles.form} onSubmit={onSubmit}>
                    <label className={styles.label}>
                        Email
                        <input
                            name="email"
                            type="email"
                            autoComplete="email"
                            required
                            className={styles.input}
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                        />
                    </label>

                    <label className={styles.label}>
                        Password
                        <input
                            name="password"
                            type="password"
                            autoComplete="new-password"
                            required
                            className={styles.input}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />
                    </label>

                    <button className={styles.btn} type="submit" disabled={loading}>
                        Sign up
                    </button>
                </form>
            </div>
        </div>
    );
}
