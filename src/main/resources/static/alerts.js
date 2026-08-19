(() => {
    'use strict';

    const accountId = 'local-account';
    const numberFormat = new Intl.NumberFormat('ko-KR');
    const timeFormat = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });
    const conditionLabels = { ABOVE: '목표가 이상', BELOW: '목표가 이하' };
    const state = {
        alerts: [],
        selectedInstrument: null,
        latestPrice: null,
        searchResults: [],
        activeSearchIndex: -1,
        searchTimer: null,
        searchController: null,
        alertSource: null,
        quoteSource: null,
        toastTimer: null,
        submitting: false
    };

    const byId = id => document.getElementById(id);
    const formatPrice = value => value == null ? '--' : `${numberFormat.format(Number(value))}원`;

    function setConnection(status, label) {
        const badge = byId('connection-badge');
        if (badge) badge.dataset.status = status;
        if (byId('connection-label')) byId('connection-label').textContent = label;
    }

    async function readApiError(response, fallback) {
        try {
            const body = await response.json();
            return body.message || fallback;
        } catch (_) {
            return fallback;
        }
    }

    function setMessage(message, error = false) {
        const node = byId('alert-message');
        node.textContent = message;
        node.classList.toggle('is-error', error);
    }

    function showToast(alert) {
        const toast = byId('alert-toast');
        byId('alert-toast-title').textContent = `${alert.name} 목표 가격 도달`;
        byId('alert-toast-message').textContent = `${conditionLabels[alert.condition]} ${formatPrice(alert.targetPrice)} · 체결가 ${formatPrice(alert.lastTriggeredPrice)}`;
        toast.hidden = false;
        requestAnimationFrame(() => toast.classList.add('is-visible'));
        document.title = `● ${alert.name} 가격 도달 · TradePilot`;
        clearTimeout(state.toastTimer);
        state.toastTimer = setTimeout(hideToast, 8000);
    }

    function hideToast() {
        const toast = byId('alert-toast');
        toast.classList.remove('is-visible');
        clearTimeout(state.toastTimer);
        state.toastTimer = setTimeout(() => { toast.hidden = true; }, 180);
        document.title = 'TradePilot · 가격 알림';
    }

    function appendCell(row, value, className = '') {
        const cell = document.createElement('td');
        cell.textContent = value;
        if (className) cell.className = className;
        row.appendChild(cell);
        return cell;
    }

    function updateSummary() {
        const active = state.alerts.filter(alert => alert.status === 'ACTIVE');
        const triggered = state.alerts.filter(alert => alert.status === 'TRIGGERED');
        byId('active-alert-count').textContent = numberFormat.format(active.length);
        byId('triggered-alert-count').textContent = numberFormat.format(triggered.length);
        const latest = [...triggered].sort((left, right) =>
            new Date(right.lastTriggeredAt).getTime() - new Date(left.lastTriggeredAt).getTime()
        )[0];
        byId('latest-alert-name').textContent = latest?.name ?? '아직 없음';
        byId('latest-alert-detail').textContent = latest
            ? `${formatPrice(latest.lastTriggeredPrice)} · ${timeFormat.format(new Date(latest.lastTriggeredAt))}`
            : '가격 조건을 등록해 보세요';
    }

    function renderAlerts() {
        const body = byId('alerts-body');
        body.replaceChildren();
        updateSummary();
        if (!state.alerts.length) {
            const row = document.createElement('tr');
            row.className = 'empty-row';
            const cell = document.createElement('td');
            cell.colSpan = 6;
            cell.textContent = '등록된 가격 알림이 없습니다.';
            row.appendChild(cell);
            body.appendChild(row);
            return;
        }
        state.alerts.forEach(alert => {
            const row = document.createElement('tr');
            row.dataset.alertId = alert.alertId;
            const symbolCell = appendCell(row, '', 'alert-symbol-cell');
            const name = document.createElement('b');
            name.textContent = alert.name;
            const symbol = document.createElement('small');
            symbol.textContent = `${alert.market} · ${alert.symbol}`;
            symbolCell.append(name, symbol);
            appendCell(row, conditionLabels[alert.condition], alert.condition === 'ABOVE' ? 'alert-above' : 'alert-below');
            appendCell(row, formatPrice(alert.targetPrice), 'alert-target-price');
            appendCell(row, alert.status === 'ACTIVE' ? '감시 중' : '도달 완료', `alert-state alert-state--${alert.status.toLowerCase()}`);
            appendCell(
                row,
                alert.status === 'TRIGGERED'
                    ? `${formatPrice(alert.lastTriggeredPrice)} · ${timeFormat.format(new Date(alert.lastTriggeredAt))}`
                    : '실시간 평가 중',
                'alert-trigger-detail'
            );
            const actionCell = document.createElement('td');
            actionCell.className = 'alert-row-actions';
            if (alert.status === 'TRIGGERED') {
                const reactivate = document.createElement('button');
                reactivate.type = 'button';
                reactivate.className = 'alert-reactivate';
                reactivate.dataset.alertAction = 'reactivate';
                reactivate.dataset.alertId = alert.alertId;
                reactivate.textContent = '다시 활성화';
                actionCell.appendChild(reactivate);
            }
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'alert-delete';
            remove.dataset.alertAction = 'delete';
            remove.dataset.alertId = alert.alertId;
            remove.textContent = '삭제';
            actionCell.appendChild(remove);
            row.appendChild(actionCell);
            body.appendChild(row);
        });
    }

    async function fetchAlerts() {
        const response = await fetch(`/api/v1/accounts/${accountId}/price-alerts`, {
            headers: { Accept: 'application/json' }
        });
        if (!response.ok) throw new Error(await readApiError(response, `가격 알림 조회 실패 (${response.status})`));
        state.alerts = await response.json();
        renderAlerts();
    }

    async function refreshAlerts() {
        try {
            await fetchAlerts();
        } catch (error) {
            console.error('Unable to load price alerts', error);
            setMessage(error.message, true);
        }
    }

    function renderSearchResults() {
        const container = byId('alert-instrument-results');
        const input = byId('alert-instrument-search');
        container.replaceChildren();
        if (!state.searchResults.length) {
            const empty = document.createElement('span');
            empty.className = 'instrument-result-empty';
            empty.textContent = '일치하는 종목이 없습니다.';
            container.appendChild(empty);
        } else {
            state.searchResults.forEach((instrument, index) => {
                const button = document.createElement('button');
                button.type = 'button';
                button.id = `alert-instrument-result-${index}`;
                button.className = `instrument-result${index === state.activeSearchIndex ? ' is-active' : ''}`;
                button.dataset.alertInstrumentIndex = String(index);
                button.setAttribute('role', 'option');
                button.setAttribute('aria-selected', String(index === state.activeSearchIndex));
                const name = document.createElement('span');
                name.className = 'instrument-result__name';
                const title = document.createElement('b');
                title.textContent = instrument.name;
                const symbol = document.createElement('small');
                symbol.textContent = instrument.symbol;
                name.append(title, symbol);
                const meta = document.createElement('span');
                meta.className = 'instrument-result__meta';
                meta.textContent = `${instrument.exchange} · ${instrument.currency}`;
                button.append(name, meta);
                container.appendChild(button);
            });
        }
        container.hidden = false;
        input.setAttribute('aria-expanded', 'true');
        if (state.activeSearchIndex >= 0) {
            input.setAttribute('aria-activedescendant', `alert-instrument-result-${state.activeSearchIndex}`);
        } else {
            input.removeAttribute('aria-activedescendant');
        }
    }

    function closeSearchResults() {
        byId('alert-instrument-results').hidden = true;
        byId('alert-instrument-search').setAttribute('aria-expanded', 'false');
        byId('alert-instrument-search').removeAttribute('aria-activedescendant');
        state.activeSearchIndex = -1;
    }

    async function searchInstruments(query) {
        const normalized = query.trim();
        if (!normalized) {
            state.searchResults = [];
            closeSearchResults();
            return;
        }
        state.searchController?.abort();
        const controller = new AbortController();
        state.searchController = controller;
        try {
            const response = await fetch(
                `/api/v1/instruments/search?market=KRX&query=${encodeURIComponent(normalized)}&limit=8`,
                { headers: { Accept: 'application/json' }, signal: controller.signal }
            );
            if (!response.ok) throw new Error(await readApiError(response, `종목 검색 실패 (${response.status})`));
            state.searchResults = await response.json();
            state.activeSearchIndex = -1;
            renderSearchResults();
        } catch (error) {
            if (error.name === 'AbortError') return;
            setMessage(error.message, true);
        }
    }

    function scheduleSearch(query) {
        clearTimeout(state.searchTimer);
        state.searchTimer = setTimeout(() => searchInstruments(query), 250);
    }

    function moveSearchSelection(direction) {
        if (!state.searchResults.length) return;
        state.activeSearchIndex = (state.activeSearchIndex + direction + state.searchResults.length) % state.searchResults.length;
        renderSearchResults();
    }

    async function selectInstrument(instrument) {
        if (!instrument) return;
        state.selectedInstrument = instrument;
        byId('alert-symbol').value = instrument.symbol;
        byId('alert-market').value = instrument.market;
        byId('alert-instrument-search').value = `${instrument.name} · ${instrument.symbol}`;
        byId('alert-instrument-search').closest('.alert-search-field').classList.add('has-selection');
        closeSearchResults();
        setMessage(`${instrument.name}(${instrument.symbol})을 선택했습니다.`);
        await fetchLatestQuote();
        connectQuoteStream();
        updateForm();
    }

    async function fetchLatestQuote() {
        if (!state.selectedInstrument) return;
        const response = await fetch(
            `/api/v1/market-data/quotes/${state.selectedInstrument.symbol}?market=${state.selectedInstrument.market}`,
            { headers: { Accept: 'application/json' } }
        );
        if (!response.ok) {
            state.latestPrice = null;
            byId('alert-current-price').textContent = '--';
            updateForm();
            return;
        }
        applyQuote(await response.json());
    }

    function applyQuote(tick) {
        if (!state.selectedInstrument || tick.symbol !== state.selectedInstrument.symbol) return;
        state.latestPrice = Number(tick.price);
        byId('alert-current-price').textContent = formatPrice(state.latestPrice);
        updateForm();
    }

    function connectQuoteStream() {
        state.quoteSource?.close();
        if (!state.selectedInstrument) return;
        const source = new EventSource(`/api/v1/market-data/stream?symbols=${state.selectedInstrument.symbol}`);
        state.quoteSource = source;
        source.addEventListener('market-tick', event => applyQuote(JSON.parse(event.data)));
    }

    function updateForm() {
        const target = Number(byId('alert-target-price').value);
        const condition = document.querySelector('input[name="condition"]:checked').value;
        const submit = byId('alert-submit');
        submit.disabled = state.submitting || !state.selectedInstrument || !(target > 0);
        if (!(target > 0) || !(state.latestPrice > 0)) {
            byId('alert-price-gap').textContent = state.selectedInstrument ? '목표 가격을 입력해 주세요' : '종목을 선택해 주세요';
            return;
        }
        const gap = condition === 'ABOVE' ? target - state.latestPrice : state.latestPrice - target;
        byId('alert-price-gap').textContent = gap <= 0
            ? '다음 시세에서 즉시 도달할 수 있습니다'
            : `${numberFormat.format(gap)}원 ${condition === 'ABOVE' ? '상승' : '하락'} 시 도달`;
    }

    async function submitAlert(form) {
        if (state.submitting || !state.selectedInstrument) return;
        const data = new FormData(form);
        const payload = {
            market: data.get('market'),
            symbol: data.get('symbol'),
            condition: data.get('condition'),
            targetPrice: Number(data.get('targetPrice'))
        };
        state.submitting = true;
        updateForm();
        setMessage('가격 알림을 등록하고 있습니다.');
        try {
            const response = await fetch(`/api/v1/accounts/${accountId}/price-alerts`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error(await readApiError(response, `가격 알림 등록 실패 (${response.status})`));
            const alert = await response.json();
            setMessage(`${alert.name} ${conditionLabels[alert.condition]} 알림을 등록했습니다.`);
            byId('alert-target-price').value = '';
            await fetchAlerts();
        } catch (error) {
            console.error('Unable to create price alert', error);
            setMessage(error.message, true);
        } finally {
            state.submitting = false;
            updateForm();
        }
    }

    async function reactivateAlert(alertId) {
        const response = await fetch(`/api/v1/accounts/${accountId}/price-alerts/${alertId}/reactivate`, {
            method: 'PATCH', headers: { Accept: 'application/json' }
        });
        if (!response.ok) throw new Error(await readApiError(response, `알림 활성화 실패 (${response.status})`));
        setMessage('가격 알림을 다시 활성화했습니다.');
        await fetchAlerts();
    }

    async function deleteAlert(alertId) {
        const response = await fetch(`/api/v1/accounts/${accountId}/price-alerts/${alertId}`, { method: 'DELETE' });
        if (!response.ok) throw new Error(await readApiError(response, `알림 삭제 실패 (${response.status})`));
        setMessage('가격 알림을 삭제했습니다.');
        await fetchAlerts();
    }

    function connectAlertStream() {
        state.alertSource?.close();
        const source = new EventSource(`/api/v1/accounts/${accountId}/price-alerts/stream`);
        state.alertSource = source;
        source.onopen = () => setConnection('live', '알림 실시간');
        source.addEventListener('price-alert-updated', async event => {
            const alert = JSON.parse(event.data);
            if (alert.status === 'TRIGGERED') {
                showToast(alert);
                setMessage(`${alert.name}이(가) 목표 가격에 도달했습니다.`);
            }
            await fetchAlerts();
        });
        source.onerror = () => setConnection('offline', '알림 재연결');
    }

    function bindInteractions() {
        const search = byId('alert-instrument-search');
        search.addEventListener('input', event => {
            state.selectedInstrument = null;
            state.latestPrice = null;
            state.quoteSource?.close();
            state.quoteSource = null;
            byId('alert-symbol').value = '';
            search.closest('.alert-search-field').classList.remove('has-selection');
            byId('alert-current-price').textContent = '--';
            setMessage('검색 결과에서 알림 종목을 선택해 주세요.');
            scheduleSearch(event.currentTarget.value);
            updateForm();
        });
        search.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown') { event.preventDefault(); moveSearchSelection(1); }
            if (event.key === 'ArrowUp') { event.preventDefault(); moveSearchSelection(-1); }
            if (event.key === 'Enter' && state.activeSearchIndex >= 0) {
                event.preventDefault();
                selectInstrument(state.searchResults[state.activeSearchIndex]);
            }
            if (event.key === 'Escape') closeSearchResults();
        });
        byId('alert-instrument-results').addEventListener('click', event => {
            const result = event.target.closest('[data-alert-instrument-index]');
            if (result) selectInstrument(state.searchResults[Number(result.dataset.alertInstrumentIndex)]);
        });
        document.addEventListener('click', event => {
            if (!byId('alert-form').contains(event.target)) closeSearchResults();
        });
        document.querySelectorAll('input[name="condition"]').forEach(input => input.addEventListener('change', updateForm));
        byId('alert-target-price').addEventListener('input', updateForm);
        byId('alert-form').addEventListener('submit', event => {
            event.preventDefault();
            submitAlert(event.currentTarget);
        });
        byId('alerts-refresh').addEventListener('click', refreshAlerts);
        byId('alerts-body').addEventListener('click', async event => {
            const button = event.target.closest('[data-alert-action]');
            if (!button) return;
            const alert = state.alerts.find(candidate => candidate.alertId === button.dataset.alertId);
            if (!alert) return;
            try {
                if (button.dataset.alertAction === 'reactivate') await reactivateAlert(alert.alertId);
                if (button.dataset.alertAction === 'delete'
                    && window.confirm(`${alert.name} 가격 알림을 삭제할까요?`)) await deleteAlert(alert.alertId);
            } catch (error) {
                setMessage(error.message, true);
            }
        });
        byId('alert-toast-close').addEventListener('click', hideToast);
    }

    async function initialize() {
        if (document.body.dataset.page !== 'alerts') return;
        bindInteractions();
        updateForm();
        await refreshAlerts();
        connectAlertStream();
    }

    window.addEventListener('beforeunload', () => {
        state.alertSource?.close();
        state.quoteSource?.close();
        state.searchController?.abort();
        clearTimeout(state.searchTimer);
        clearTimeout(state.toastTimer);
    });
    document.addEventListener('DOMContentLoaded', initialize);
})();
