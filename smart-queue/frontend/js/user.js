(function () {
    'use strict';

    let session;
    let services = [];
    let tokens = [];
    let refreshing = false;

    const api = () => window.SmartQueueAPI;
    const esc = (value) => api().escapeHtml(value);
    const statusBadge = (status) => '<span class="status-badge status-' + String(status || '').toLowerCase() + '">' + esc(status) + '</span>';

    function serviceName(serviceId) {
        const service = services.find((item) => item.id === serviceId);
        return service ? service.name : serviceId;
    }

    async function loadServices() {
        services = await api().get('/api/services');
        const select = document.getElementById('queue-service-select');
        const counter = document.getElementById('services-list');
        select.innerHTML = '';
        counter.innerHTML = '';

        if (!services.length) {
            counter.innerHTML = '<div class="empty-table">No active services are available right now.</div>';
            return;
        }

        services.forEach((service) => {
            const option = document.createElement('option');
            option.value = service.id;
            option.textContent = service.name;
            select.appendChild(option);

            const card = document.createElement('article');
            card.className = 'service-card';
            card.innerHTML = '<div><p class="eyebrow">Available service</p><h3>' + esc(service.name) + '</h3>'
                + '<p class="service-time">Average service time: <strong>' + esc(service.averageServiceTime) + ' minutes</strong></p>'
                + (service.description ? '<p class="muted">' + esc(service.description) + '</p>' : '')
                + '</div><button class="button primary get-token-button" type="button">Get token</button>';
            card.querySelector('button').addEventListener('click', () => createToken(service.id));
            counter.appendChild(card);
        });

        if (!select.value) select.value = services[0].id;
        select.onchange = () => refreshQueue();
    }

    async function createToken(serviceId) {
        const message = document.getElementById('user-message');
        try {
            await api().post('/api/tokens', { serviceId });
            document.getElementById('queue-service-select').value = serviceId;
            api().showMessage(message, 'Token created. Your queue position is being updated.', 'success');
            await refreshAll();
        } catch (error) {
            api().showMessage(message, error.message, 'error');
        }
    }

    async function loadTokens() {
        tokens = await api().get('/api/users/' + encodeURIComponent(session.userId) + '/tokens');
        tokens.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
        renderTokens();
        renderFeaturedToken();
    }

    function renderTokens() {
        const body = document.getElementById('tokens-table-body');
        const empty = document.getElementById('tokens-empty');
        body.innerHTML = '';
        empty.style.display = tokens.length ? 'none' : 'block';
        tokens.forEach((token) => {
            const row = document.createElement('tr');
            const canCancel = token.status === 'WAITING';
            row.innerHTML = '<td><strong>' + esc(token.tokenNumber) + '</strong></td>'
                + '<td>' + esc(serviceName(token.serviceId)) + '</td>'
                + '<td>' + statusBadge(token.status) + '</td>'
                + '<td>' + (token.queuePosition || '—') + '</td>'
                + '<td>' + api().formatMinutes(token.estimatedWaitTime) + '</td>'
                + '<td>' + esc(api().formatDate(token.createdAt)) + '</td>'
                + '<td>' + (canCancel ? '<button class="button danger small cancel-token-button" type="button">Cancel</button>' : '') + '</td>';
            if (canCancel) row.querySelector('button').addEventListener('click', () => cancelToken(token));
            body.appendChild(row);
        });
    }

    function activeTokenForService(serviceId) {
        return tokens
            .filter((token) => token.serviceId === serviceId && ['WAITING', 'CALLED', 'SERVING'].includes(token.status))
            .sort((a, b) => (a.queuePosition || 999999) - (b.queuePosition || 999999))[0];
    }

    function renderFeaturedToken() {
        const panel = document.getElementById('my-token-panel');
        const token = tokens.find((item) => ['WAITING', 'CALLED', 'SERVING'].includes(item.status));
        if (!token) {
            panel.className = 'featured-card empty-state';
            panel.innerHTML = '<p class="eyebrow">My token</p><h2>No active token selected</h2><p class="muted">Get a token from an available service to see your position here.</p>';
            return;
        }
        panel.className = 'featured-card';
        panel.innerHTML = '<p class="eyebrow">My token · ' + esc(serviceName(token.serviceId)) + '</p>'
            + '<h2>' + esc(token.tokenNumber) + '</h2>'
            + '<div class="token-meta"><div><strong>' + esc(token.queuePosition || '—') + '</strong><span>Queue position</span></div>'
            + '<div><strong>' + esc(api().formatMinutes(token.estimatedWaitTime)) + '</strong><span>Estimated wait</span></div>'
            + '<div><strong>' + statusBadge(token.status) + '</strong><span>Status</span></div></div>';
    }

    async function refreshQueue() {
        const serviceId = document.getElementById('queue-service-select').value;
        const body = document.getElementById('queue-table-body');
        const empty = document.getElementById('queue-empty');
        if (!serviceId) {
            body.innerHTML = '';
            empty.style.display = 'block';
            return;
        }
        try {
            const queue = await api().get('/api/queue/' + encodeURIComponent(serviceId));
            body.innerHTML = '';
            empty.style.display = queue.length ? 'none' : 'block';
            if (!queue.length) empty.textContent = 'This queue is currently empty.';
            queue.forEach((item) => {
                const row = document.createElement('tr');
                row.innerHTML = '<td><strong>' + esc(item.tokenNumber) + '</strong></td><td>' + item.position + '</td><td>'
                    + statusBadge(item.status) + '</td><td>' + api().formatMinutes(item.estimatedWaitTime) + '</td>';
                body.appendChild(row);
            });
            await refreshOwnPosition(serviceId);
        } catch (error) {
            api().showMessage(document.getElementById('user-message'), error.message, 'error');
        }
    }

    async function refreshOwnPosition(serviceId) {
        const panel = document.getElementById('my-position-panel');
        const token = activeTokenForService(serviceId);
        if (!token) {
            panel.innerHTML = '<p class="eyebrow">My token position</p><h2>Choose a token</h2><p class="muted">Your detailed position will appear here when you have an active token for this service.</p>';
            return;
        }
        try {
            const position = await api().get('/api/queue/' + encodeURIComponent(serviceId) + '/position/' + encodeURIComponent(token.id));
            panel.innerHTML = '<p class="eyebrow">My token position</p><h2>' + esc(position.tokenNumber) + '</h2>'
                + '<div class="position-highlight">' + position.position + '</div><p class="muted">Your current queue position</p>'
                + '<div class="position-details"><div><span>People ahead</span><strong>' + position.peopleAhead + '</strong></div>'
                + '<div><span>Estimated wait</span><strong>' + api().formatMinutes(position.estimatedWaitTime) + '</strong></div></div>';
        } catch (error) {
            api().showMessage(document.getElementById('user-message'), error.message, 'error');
        }
    }

    async function cancelToken(token) {
        if (!window.confirm('Cancel token ' + token.tokenNumber + '?')) return;
        try {
            await api().delete('/api/tokens/' + encodeURIComponent(token.id));
            api().showMessage(document.getElementById('user-message'), 'Token cancelled. Queue positions have been refreshed.', 'success');
            await refreshAll();
        } catch (error) {
            api().showMessage(document.getElementById('user-message'), error.message, 'error');
        }
    }

    async function refreshAll() {
        if (refreshing) return;
        refreshing = true;
        try {
            await loadTokens();
            await refreshQueue();
        } finally {
            refreshing = false;
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        session = SmartQueueAuth.requireRole('USER');
        if (!session) return;
        document.getElementById('header-user-name').textContent = session.name;
        try {
            await loadServices();
            await refreshAll();
            api().startPolling('user-dashboard', refreshAll, 10000);
        } catch (error) {
            api().showMessage(document.getElementById('user-message'), error.message, 'error');
        }
    });

    window.addEventListener('beforeunload', () => api().stopPolling('user-dashboard'));
}());
