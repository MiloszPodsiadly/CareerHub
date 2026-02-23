import { Navigate, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';

import { getAccess } from '../api';

type Props = {
    children: ReactNode;
};

export default function RequireAuth({ children }: Props) {
    const location = useLocation();
    const token = getAccess();

    if (!token) {
        const next = encodeURIComponent(`${location.pathname}${location.search}${location.hash}`);
        return <Navigate to={`/auth/login?next=${next}`} replace />;
    }

    return children;
}
