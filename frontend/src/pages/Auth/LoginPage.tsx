import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import styles from './AuthPage.module.css';
import { authApi } from '../../shared/api';

export default function LoginPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
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
            await authApi.login(email.trim(), password.trim());
            await new Promise(requestAnimationFrame);
            const next = searchParams.get('next') || '/';
            const safeNext = next.startsWith('/') ? next : '/';
            navigate(safeNext);
        } catch (err: any) {
            setAlert({ type: 'error', text: err?.message || 'Something went wrong.' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.authWrap}>
            <div className={styles.auth} data-mode="login">
                <div className={styles.tabs}>
                    <Link className={`${styles.tab} ${styles.tabActive}`} to="/auth/login">
                        Sign in
                    </Link>
                    <Link className={styles.tab} to="/auth/register">
                        Create account
                    </Link>
                </div>

                <h2 className={styles.title}>Sign in</h2>
                <p className={styles.muted}>Enter your details to continue.</p>

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
                            autoComplete="current-password"
                            required
                            className={styles.input}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />
                    </label>

                    <button className={styles.btn} type="submit" disabled={loading}>
                        Sign in
                    </button>

                    <div className={styles.forgotWrap}>
                        <Link to="/auth/forgot" className={styles.link}>
                            Forgot password?
                        </Link>
                    </div>
                    <div className={styles.resendWrap}>
                        <Link to="/auth/resend-verification" className={styles.link}>
                            Resend verification email
                        </Link>
                    </div>
                </form>
            </div>
        </div>
    );
}
