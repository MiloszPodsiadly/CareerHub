import html from './salary-calculator.html?raw';
import './salary-calculator.css';
import { initSalaryCalculator } from './salary-calculator.js';

export function mountSalaryCalculator() {
    const root = document.getElementById('root');
    if (!root) return;

    root.innerHTML = `<main class="salary">${html}</main>`;
    initSalaryCalculator();
}
