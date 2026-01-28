import { useEffect } from 'react';
import { Outlet } from 'react-router-dom';

import './App.css';
import { bootstrapAuth } from './shared/api';
import Header from './shared/header/Header';
import Footer from './shared/footer/Footer';

export default function App() {
    useEffect(() => {
        bootstrapAuth().catch((err) => {
            console.error('Failed to bootstrap auth:', err);
        });
    }, []);

    return (
        <div className="page" id="app">
            <Header />
            <main className="page__content">
                <Outlet />
            </main>
            <Footer />
        </div>
    );
}
