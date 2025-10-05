import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import './index.css';
import './shared/header/header.css';
import './shared/footer/footer.css';

import './shared/header/loader.header.js';
import './shared/footer/loader.footer.js';

import App from './App.jsx';

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <App />
    </StrictMode>
);

// router odpala bootstrap + pierwszy render
import('./router.js');
