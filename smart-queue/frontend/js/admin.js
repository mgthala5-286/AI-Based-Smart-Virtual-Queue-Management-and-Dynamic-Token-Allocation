(function () {
    'use strict';

    let services = [];
    let counters = [];
    const api = () => window.SmartQueueAPI;
    const esc = (value) => api().escapeHtml(value);
    const message = () => document.getElementById('admin-message');
    const statusBadge = (active) => '<span class="status-badge ' + (active ? 'status-active' : 'status-inactive') + '">' + (active ? 'ACTIVE' : 'INACTIVE') + '</span>';

    function showSuccess(text) { api().showMessage(message(), text, 'success'); }
    function showError(error) { api().showMessage(message(), error.message, 'error'); }

    async function loadData() {
        [services, counters] = await Promise.all([
            api().get('/api/admin/services'),
            api().get('/api/admin/counters')
        ]);
        renderServiceOptions();
        renderServices();
        renderCounters();
    }

    function renderServiceOptions() {
        const select = document.getElementById('counter-service');
        const current = select.value;
        select.innerHTML = '<option value="">Select a service</option>';
        services.filter((service) => service.active).forEach((service) => {
            const option = document.createElement('option');
            option.value = service.id;
            option.textContent = service.name;
            select.appendChild(option);
        });
        if (current) select.value = current;
    }

    function renderServices() {
        const body = document.getElementById('admin-services-body');
        const empty = document.getElementById('admin-services-empty');
        body.innerHTML = '';
        empty.style.display = services.length ? 'none' : 'block';
        services.forEach((service) => {
            const row = document.createElement('tr');
            row.innerHTML = '<td><strong>' + esc(service.name) + '</strong></td><td>' + esc(service.description || '—') + '</td>'
                + '<td>' + service.averageServiceTime + ' min</td><td>' + statusBadge(service.active) + '</td>'
                + '<td class="row-actions"><button class="button secondary small edit-service" type="button">Edit</button>'
                + '<button class="button ' + (service.active ? 'danger' : 'primary') + ' small toggle-service" type="button">'
                + (service.active ? 'Deactivate' : 'Activate') + '</button></td>';
            row.querySelector('.edit-service').addEventListener('click', () => fillServiceForm(service));
            row.querySelector('.toggle-service').addEventListener('click', () => toggleService(service));
            body.appendChild(row);
        });
    }

    function renderCounters() {
        const body = document.getElementById('admin-counters-body');
        const empty = document.getElementById('admin-counters-empty');
        body.innerHTML = '';
        empty.style.display = counters.length ? 'none' : 'block';
        counters.forEach((counter) => {
            const service = services.find((item) => item.id === counter.serviceId);
            const row = document.createElement('tr');
            row.innerHTML = '<td><strong>#' + counter.counterNumber + '</strong></td><td>' + esc(service ? service.name : counter.serviceId) + '</td>'
                + '<td>' + statusBadge(counter.active) + '</td><td class="row-actions">'
                + '<button class="button secondary small edit-counter" type="button">Edit</button>'
                + '<button class="button ' + (counter.active ? 'danger' : 'primary') + ' small toggle-counter" type="button">'
                + (counter.active ? 'Deactivate' : 'Activate') + '</button></td>';
            row.querySelector('.edit-counter').addEventListener('click', () => fillCounterForm(counter));
            row.querySelector('.toggle-counter').addEventListener('click', () => toggleCounter(counter));
            body.appendChild(row);
        });
    }

    function fillServiceForm(service) {
        document.getElementById('service-id').value = service.id;
        document.getElementById('service-name').value = service.name;
        document.getElementById('service-description').value = service.description || '';
        document.getElementById('service-average-time').value = service.averageServiceTime;
        document.getElementById('service-active').checked = service.active;
        document.getElementById('service-form-title').textContent = 'Edit service';
        document.getElementById('service-cancel-edit').classList.remove('hidden');
        document.getElementById('service-name').focus();
    }

    function resetServiceForm() {
        document.getElementById('service-form').reset();
        document.getElementById('service-id').value = '';
        document.getElementById('service-active').checked = true;
        document.getElementById('service-form-title').textContent = 'Add service';
        document.getElementById('service-cancel-edit').classList.add('hidden');
    }

    function fillCounterForm(counter) {
        document.getElementById('counter-id').value = counter.id;
        document.getElementById('counter-number').value = counter.counterNumber;
        document.getElementById('counter-service').value = counter.serviceId;
        document.getElementById('counter-active').checked = counter.active;
        document.getElementById('counter-form-title').textContent = 'Edit counter';
        document.getElementById('counter-cancel-edit').classList.remove('hidden');
        document.getElementById('counter-number').focus();
    }

    function resetCounterForm() {
        document.getElementById('counter-form').reset();
        document.getElementById('counter-id').value = '';
        document.getElementById('counter-active').checked = true;
        document.getElementById('counter-form-title').textContent = 'Add counter';
        document.getElementById('counter-cancel-edit').classList.add('hidden');
    }

    async function toggleService(service) {
        try {
            await api().put('/api/admin/services/' + encodeURIComponent(service.id), {
                name: service.name,
                description: service.description || '',
                averageServiceTime: service.averageServiceTime,
                active: !service.active
            });
            showSuccess('Service status updated.');
            await loadData();
        } catch (error) { showError(error); }
    }

    async function toggleCounter(counter) {
        try {
            const path = '/api/admin/counters/' + encodeURIComponent(counter.id) + (counter.active ? '/deactivate' : '/activate');
            await api().post(path, {});
            showSuccess('Counter status updated.');
            await loadData();
        } catch (error) { showError(error); }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const session = SmartQueueAuth.requireRole('ADMIN');
        if (!session) return;
        document.getElementById('header-user-name').textContent = session.name;

        document.getElementById('service-form').addEventListener('submit', async (event) => {
            event.preventDefault();
            const id = document.getElementById('service-id').value;
            const payload = {
                name: document.getElementById('service-name').value.trim(),
                description: document.getElementById('service-description').value.trim(),
                averageServiceTime: Number(document.getElementById('service-average-time').value),
                active: document.getElementById('service-active').checked
            };
            try {
                if (id) await api().put('/api/admin/services/' + encodeURIComponent(id), payload);
                else await api().post('/api/admin/services', payload);
                showSuccess(id ? 'Service updated.' : 'Service created.');
                resetServiceForm();
                await loadData();
            } catch (error) { showError(error); }
        });

        document.getElementById('counter-form').addEventListener('submit', async (event) => {
            event.preventDefault();
            const id = document.getElementById('counter-id').value;
            const payload = {
                counterNumber: Number(document.getElementById('counter-number').value),
                serviceId: document.getElementById('counter-service').value,
                active: document.getElementById('counter-active').checked
            };
            try {
                if (id) await api().put('/api/admin/counters/' + encodeURIComponent(id), payload);
                else await api().post('/api/admin/counters', payload);
                showSuccess(id ? 'Counter updated.' : 'Counter created.');
                resetCounterForm();
                await loadData();
            } catch (error) { showError(error); }
        });

        document.getElementById('service-cancel-edit').addEventListener('click', resetServiceForm);
        document.getElementById('counter-cancel-edit').addEventListener('click', resetCounterForm);
        try { await loadData(); } catch (error) { showError(error); }
    });
}());
