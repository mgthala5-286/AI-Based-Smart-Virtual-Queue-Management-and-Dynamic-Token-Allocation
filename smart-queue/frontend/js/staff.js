(function () {
    'use strict';

    let services = [];
    let refreshing = false;
    const api = () => window.SmartQueueAPI;
    const esc = (value) => api().escapeHtml(value);
    const statusBadge = (status) => '<span class="status-badge status-' + String(status || '').toLowerCase() + '">' + esc(status) + '</span>';

    async function loadServices() {
        services = await api().get('/api/services');
        const select = document.getElementById('staff-service-select');
        select.innerHTML = '';
        services.forEach((service) => {
            const option = document.createElement('option');
            option.value = service.id;
            option.textContent = service.name;
            select.appendChild(option);
        });
        select.onchange = refreshQueue;
    }

    async function refreshQueue() {
        const serviceId = document.getElementById('staff-service-select').value;
        const body = document.getElementById('staff-queue-body');
        const empty = document.getElementById('staff-queue-empty');
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
        } catch (error) {
            api().showMessage(document.getElementById('staff-message'), error.message, 'error');
        }
    }

    async function refreshAll() {
        if (refreshing) return;
        refreshing = true;
        try {
            await refreshQueue();
        } finally {
            refreshing = false;
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const session = SmartQueueAuth.requireRole(['STAFF', 'ADMIN']);
        if (!session) return;
        document.getElementById('header-user-name').textContent = session.name;
        try {
            await loadServices();
            await refreshAll();
            api().startPolling('staff-dashboard', refreshAll, 10000);
        } catch (error) {
            api().showMessage(document.getElementById('staff-message'), error.message, 'error');
        }
    });

    window.addEventListener('beforeunload', () => api().stopPolling('staff-dashboard'));
}());
