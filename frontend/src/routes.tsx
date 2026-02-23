import { Navigate, createBrowserRouter } from 'react-router-dom';

import App from './App';
import LandingPage from './pages/Landing/LandingPage';
import JobsPage from './pages/Jobs/JobsPage';
import EventsPage from './pages/Events/EventsPage';
import SalaryCalculatorPage from './pages/SalaryCalculator/SalaryCalculatorPage';
import PostJobPage from './pages/PostJob/PostJobPage';
import LoginPage from './pages/Auth/LoginPage';
import RegisterPage from './pages/Auth/RegisterPage';
import ForgotPasswordPage from './pages/Auth/ForgotPasswordPage';
import ResetPasswordPage from './pages/Auth/ResetPasswordPage';
import ResendVerificationPage from './pages/Auth/ResendVerificationPage';
import VerifyEmailPage from './pages/Auth/VerifyEmailPage';
import ProfilePage from './pages/Profile/ProfilePage';
import FavoritesPage from './pages/Favorites/FavoritesPage';
import MyApplicationsPage from './pages/MyApplications/MyApplicationsPage';
import MyOffersPage from './pages/MyOffers/MyOffersPage';
import JobExactlyOfferPage from './pages/JobExactlyOffer/JobExactlyOfferPage';
import RequireAuth from './shared/auth/RequireAuth';

export const router = createBrowserRouter([
    {
        path: '/',
        element: <App />,
        children: [
            { index: true, element: <LandingPage /> },
            { path: 'jobs', element: <JobsPage /> },
            { path: 'events', element: <EventsPage /> },
            {
                path: 'salary-calculator',
                element: <SalaryCalculatorPage />,
            },
            { path: 'auth/login', element: <LoginPage /> },
            { path: 'auth/register', element: <RegisterPage /> },
            { path: 'auth/forgot', element: <ForgotPasswordPage /> },
            { path: 'auth/reset-password', element: <ResetPasswordPage /> },
            {
                path: 'profile',
                element: (
                    <RequireAuth>
                        <ProfilePage />
                    </RequireAuth>
                ),
            },
            {
                path: 'favorite',
                element: (
                    <RequireAuth>
                        <FavoritesPage />
                    </RequireAuth>
                ),
            },
            {
                path: 'post-job',
                element: (
                    <RequireAuth>
                        <PostJobPage />
                    </RequireAuth>
                ),
            },
            {
                path: 'my-offers',
                element: (
                    <RequireAuth>
                        <MyOffersPage />
                    </RequireAuth>
                ),
            },
            {
                path: 'jobexaclyoffer',
                element: <JobExactlyOfferPage />,
            },
            {
                path: 'my-applications',
                element: (
                    <RequireAuth>
                        <MyApplicationsPage />
                    </RequireAuth>
                ),
            },
            { path: 'auth/verify', element: <VerifyEmailPage /> },
            { path: 'auth/resend-verification', element: <ResendVerificationPage /> },
            { path: '*', element: <Navigate to="/" replace /> },
        ],
    },
]);
