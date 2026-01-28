import { FormEvent, useState } from 'react';

import styles from './AuthPage.module.css';
import { authApi } from '../../shared/api';

export default function ForgotPasswordPage() {
    const [email, setEmail] = useState('');
    const [alert, setAlert] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
    const [loading, setLoading] = useState(false);

    const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setAlert(null);
        setLoading(true);
        try {
            if (!email) {
                setAlert({ type: 'error', text: 'Please enter an email address.' });
                return;
            }
            await authApi.forgotPassword(email.trim());
            setAlert({ type: 'success', text: 'Reset link sent. Check your email.' });
        } catch (err: any) {
            setAlert({ type: 'error', text: err?.message || 'Something went wrong.' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.authWrap}>
            <div className={styles.auth} data-mode="forgot">
                <h2 className={styles.title}>Forgot password</h2>
                <p className={styles.muted}>Enter your e-mail and we'll send you a reset link.</p>

                {alert ? (
                    <div className={`${styles.alert} ${alert.type === 'error' ? styles.alertError : styles.alertSuccess}`}>
                        {alert.text}
                    </div>
                ) : null}

                <form className={styles.form} onSubmit={onSubmit}>
                    <label className={styles.label}>
                        E-mail
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

                    <button className={styles.btn} type="submit" disabled={loading}>
                        Send reset link
                    </button>
                </form>
            </div>
        </div>
    );
}
