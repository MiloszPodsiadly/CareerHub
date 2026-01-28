import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import styles from './AuthPage.module.css';
import { authApi } from '../../shared/api';

export default function VerifyEmailPage() {
    const navigate = useNavigate();
    const token = useMemo(() => new URLSearchParams(location.search).get('token') || '', []);
    const [alert, setAlert] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

    useEffect(() => {
        if (!token) {
            setAlert({ type: 'error', text: 'Missing verification token.' });
            return;
        }

        (async () => {
            try {
                await authApi.verifyEmail(token);
                setAlert({ type: 'success', text: 'Email verified! You can now sign in.' });
                setTimeout(() => navigate('/auth/login'), 900);
            } catch (err: any) {
                setAlert({ type: 'error', text: err?.message || 'Invalid or expired verification link.' });
            }
        })();
    }, [token, navigate]);

    return (
        <div className={styles.authWrap}>
            <div className={styles.auth} data-mode="verify">
                <h2 className={styles.title}>Email verification</h2>
                <p className={styles.muted}>We are verifying your account…</p>

                {alert ? (
                    <div className={`${styles.alert} ${alert.type === 'error' ? styles.alertError : styles.alertSuccess}`}>
                        {alert.text}
                    </div>
                ) : null}

                <div className={styles.actions}>
                    <Link className={`${styles.btn} ${styles.btnSecondary}`} to="/auth/login">
                        Back to login
                    </Link>
                </div>
            </div>
        </div>
    );
}
