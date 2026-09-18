(function () {
    'use strict';

    const API_BASE_URL = 'http://localhost:8080';
    const pollingTimers = new Map();

    function getToken() {
        return localStorage.getItem('smartQueueJwt');
    }

    function userMessage(status, payload) {
        if (status === 401) return 'Your session has expired. Please log in again.';
        if (status === 403) return 'You do not have permission to perform this action.';
        if (status === 404) return 'The requested item could not be found.';
        if (status === 422) return 'Some information is invalid. Please check your entries.';
        if (status >= 500) return 'The server is temporarily unavailable. Please try again.';
        if (payload && typeof payload.message === 'string') return payload.message;
        if (payload && payload.errors && typeof payload.errors === 'object') {
            return Object.values(payload.errors).join(' ');
        }
        return 'The request could not be completed.';
    }

    async function request(path, options) {
        const settings = Object.assign({ method: 'GET' }, options || {});
        const headers = new Headers(settings.headers || {});
        headers.set('Accept', 'application/json');
        if (settings.body && typeof settings.body !== 'string') {
            headers.set('Content-Type', 'application/json');
            settings.body = JSON.stringify(settings.body);
        }
        if (settings.auth !== false) {
            const token = getToken();
            if (token) headers.set('Authorization', 'Bearer ' + token);
        }
        delete settings.auth;
        settings.headers = headers;

        let response;
        try {
            response = await fetch(API_BASE_URL + path, settings);
        } catch (error) {
            throw new Error('The Java backend could not be reached. Make sure it is running on port 8080.');
        }

        const text = await response.text();
        let payload = null;
        if (text) {
            try { payload = JSON.parse(text); } catch (_) { payload = { message: text }; }
        }

        if (response.status === 401) {
            if (window.SmartQueueAuth) window.SmartQueueAuth.clearSession();
            if (!window.location.pathname.endsWith('/login.html')) window.location.href = 'login.html';
        }
        if (!response.ok) {
            const error = new Error(userMessage(response.status, payload));
            error.status = response.status;
            throw error;
        }
        return payload;
    }

    function startPolling(key, callback, intervalMs) {
        stopPolling(key);
        callback();
        pollingTimers.set(key, window.setInterval(callback, intervalMs || 10000));
    }

    function stopPolling(key) {
        const timer = pollingTimers.get(key);
        if (timer) window.clearInterval(timer);
        pollingTimers.delete(key);
    }

    function stopAllPolling() {
        pollingTimers.forEach((timer) => window.clearInterval(timer));
        pollingTimers.clear();
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatMinutes(value) {
        const number = Number(value || 0);
        if (!Number.isFinite(number)) return '0 min';
        return (Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')) + ' min';
    }

    function formatDate(value) {
        if (!value) return '—';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
    }

    function showMessage(element, message, type) {
        if (!element) return;
        element.textContent = message || '';
        element.className = 'message visible ' + (type || 'error');
    }

    function clearMessage(element) {
        if (!element) return;
        element.textContent = '';
        element.className = 'message';
    }

    window.SmartQueueAPI = {
        get: (path, options) => request(path, Object.assign({}, options || {}, { method: 'GET' })),
        post: (path, body, options) => request(path, Object.assign({}, options || {}, { method: 'POST', body })),
        put: (path, body, options) => request(path, Object.assign({}, options || {}, { method: 'PUT', body })),
        delete: (path, options) => request(path, Object.assign({}, options || {}, { method: 'DELETE' })),
        startPolling,
        stopPolling,
        stopAllPolling,
        escapeHtml,
        formatMinutes,
        formatDate,
        showMessage,
        clearMessage
    };
}());
