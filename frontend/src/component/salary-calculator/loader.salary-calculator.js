import html from './salary-calculator.html?raw';
import './salary-calculator.css';
import { initSalaryCalculator } from './salary-calculator.js';
import { setView } from '../../shared/mount.js';

export function mountSalaryCalculator() {
    setView(`<section class="salary">${html}</section>`);
    initSalaryCalculator();
}
