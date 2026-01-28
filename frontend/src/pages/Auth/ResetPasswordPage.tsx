import { FormEvent, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import styles from './AuthPage.module.css';
import { authApi } from '../../shared/api';

export default function ResetPasswordPage() {
    const navigate = useNavigate();
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [alert, setAlert] = useState<{ type: 'error' | 'success'; text: string } | null>(null);
    const [loading, setLoading] = useState(false);

    const token = useMemo(() => new URLSearchParams(location.search).get('token') || '', []);

    const onSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setAlert(null);
        setLoading(true);
        try {
            if (!token) {
                setAlert({ type: 'error', text: 'Missing reset token.' });
                return;
            }
            if (password.length < 8) {
                setAlert({ type: 'error', text: 'Password must be at least 8 characters.' });
                return;
            }
            if (password !== confirmPassword) {
                setAlert({ type: 'error', text: 'Passwords do not match.' });
                return;
            }
            await authApi.resetPassword(token, password);
            setAlert({ type: 'success', text: 'Password updated. You can sign in now.' });
            setTimeout(() => navigate('/auth/login'), 900);
        } catch (err: any) {
            setAlert({ type: 'error', text: err?.message || 'Something went wrong.' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.authWrap}>
            <div className={styles.auth} data-mode="reset">
                <h2 className={styles.title}>Reset password</h2>
                <p className={styles.muted}>Enter a new password for your account.</p>

                {alert ? (
                    <div className={`${styles.alert} ${alert.type === 'error' ? styles.alertError : styles.alertSuccess}`}>
                        {alert.text}
                    </div>
                ) : null}

                <form className={styles.form} onSubmit={onSubmit}>
                    <label className={styles.label}>
                        New password
                        <input
                            name="password"
                            type="password"
                            autoComplete="new-password"
                            minLength={8}
                            required
                            className={styles.input}
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />
                    </label>

                    <label className={styles.label}>
                        Confirm new password
                        <input
                            name="confirmPassword"
                            type="password"
                            autoComplete="new-password"
                            minLength={8}
                            required
                            className={styles.input}
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                        />
                    </label>

                    <button className={styles.btn} type="submit" disabled={loading}>
                        Set new password
                    </button>
                </form>
            </div>
        </div>
    );
}
