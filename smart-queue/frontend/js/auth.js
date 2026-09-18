(function () {
    'use strict';

    const SESSION_KEYS = ['smartQueueJwt', 'smartQueueUserId', 'smartQueueRole', 'smartQueueUserName'];

    function getSession() {
        const token = localStorage.getItem('smartQueueJwt');
        const userId = localStorage.getItem('smartQueueUserId');
        const role = localStorage.getItem('smartQueueRole');
        if (!token || !userId || !role) return null;
        return { token, userId, role, name: localStorage.getItem('smartQueueUserName') || role };
    }

    function clearSession() {
        SESSION_KEYS.forEach((key) => localStorage.removeItem(key));
        if (window.SmartQueueAPI) window.SmartQueueAPI.stopAllPolling();
    }

    function redirectByRole(role) {
        if (role === 'ADMIN') window.location.href = 'admin-dashboard.html';
        else if (role === 'STAFF') window.location.href = 'staff-dashboard.html';
        else window.location.href = 'user-dashboard.html';
    }

    function requireRole(allowedRoles) {
        const session = getSession();
        const roles = Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles];
        if (!session) {
            window.location.href = 'login.html';
            return null;
        }
        if (!roles.includes(session.role)) {
            redirectByRole(session.role);
            return null;
        }
        return session;
    }

    async function login(email, password) {
        const response = await SmartQueueAPI.post('/api/auth/login', { email, password }, { auth: false });
        localStorage.setItem('smartQueueJwt', response.token);
        localStorage.setItem('smartQueueUserId', response.userId);
        localStorage.setItem('smartQueueRole', response.role);
        localStorage.setItem('smartQueueUserName', response.name || response.role);
        return response;
    }

    function logout() {
        clearSession();
        window.location.href = 'login.html';
    }

    document.addEventListener('DOMContentLoaded', () => {
        const loginForm = document.getElementById('login-form');
        if (loginForm) {
            const message = document.getElementById('login-message');
            loginForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                SmartQueueAPI.clearMessage(message);
                const button = loginForm.querySelector('button[type="submit"]');
                button.disabled = true;
                try {
                    const response = await login(
                        loginForm.email.value.trim(),
                        loginForm.password.value
                    );
                    redirectByRole(response.role);
                } catch (error) {
                    SmartQueueAPI.showMessage(message, error.message, 'error');
                    button.disabled = false;
                }
            });
        }

        const registerForm = document.getElementById('register-form');
        if (registerForm) {
            const message = document.getElementById('register-message');
            registerForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                SmartQueueAPI.clearMessage(message);
                const button = registerForm.querySelector('button[type="submit"]');
                button.disabled = true;
                try {
                    await SmartQueueAPI.post('/api/auth/register', {
                        name: registerForm.name.value.trim(),
                        email: registerForm.email.value.trim(),
                        password: registerForm.password.value
                    }, { auth: false });
                    SmartQueueAPI.showMessage(message, 'Registration successful. Please login.', 'success');
                    registerForm.reset();
                } catch (error) {
                    SmartQueueAPI.showMessage(message, error.message, 'error');
                } finally {
                    button.disabled = false;
                }
            });
        }

        const logoutButton = document.getElementById('logout-button');
        if (logoutButton) logoutButton.addEventListener('click', logout);
    });

    window.SmartQueueAuth = { getSession, clearSession, redirectByRole, requireRole, login, logout };
}());
