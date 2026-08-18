(() => {
    'use strict';

    const accountId = 'local-account';
    const numberFormat = new Intl.NumberFormat('ko-KR');
    const timeFormat = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });
    const state = {
        orders: [],
        executions: [],
        portfolio: null,
        selectedInstrument: null,
        latestPrice: null,
        searchResults: [],
        activeSearchIndex: -1,
        searchTimer: null,
        searchController: null,
        orderSource: null,
        quoteSource: null,
        submitting: false
    };

    const byId = id => document.getElementById(id);
    const formatPrice = value => value == null ? '--' : `${numberFormat.format(Number(value))}원`;
    const statusLabels = { PENDING: '체결 대기', FILLED: '체결 완료', CANCELED: '취소', REJECTED: '거절' };
    const sideLabels = { BUY: '매수', SELL: '매도' };
    const typeLabels = { MARKET: '시장가', LIMIT: '지정가' };

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
        const node = byId('order-message');
        node.textContent = message;
        node.classList.toggle('is-error', error);
    }

    function updateAccountSummary() {
        if (!state.portfolio) return;
        byId('order-cash-balance').textContent = formatPrice(state.portfolio.totals.cashBalance);
        const position = state.selectedInstrument
            ? state.portfolio.positions.find(item => item.market === state.selectedInstrument.market
                && item.symbol === state.selectedInstrument.symbol)
            : null;
        byId('order-position-quantity').textContent = numberFormat.format(position?.quantity ?? 0);
    }

    async function fetchPortfolio() {
        try {
            const response = await fetch(`/api/v1/portfolio/accounts/${accountId}/summary`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) throw new Error(`계좌 조회 실패 (${response.status})`);
            state.portfolio = await response.json();
            updateAccountSummary();
        } catch (error) {
            console.error('Unable to load paper account', error);
            byId('order-cash-balance').textContent = '조회 실패';
        }
    }

    function appendCell(row, value, className = '') {
        const cell = document.createElement('td');
        cell.textContent = value;
        if (className) cell.className = className;
        row.appendChild(cell);
        return cell;
    }

    function renderOrders() {
        const body = byId('orders-body');
        body.replaceChildren();
        byId('filled-order-count').textContent = numberFormat.format(
            state.orders.filter(order => order.status === 'FILLED').length
        );
        if (!state.orders.length) {
            const row = document.createElement('tr');
            row.className = 'empty-row';
            const cell = document.createElement('td');
            cell.colSpan = 8;
            cell.textContent = '아직 접수된 주문이 없습니다.';
            row.appendChild(cell);
            body.appendChild(row);
            return;
        }
        state.orders.forEach(order => {
            const row = document.createElement('tr');
            row.dataset.orderId = order.orderId;
            appendCell(row, timeFormat.format(new Date(order.createdAt)));
            appendCell(row, `${order.name} · ${order.symbol}`, 'order-symbol-cell');
            appendCell(row, sideLabels[order.side], order.side === 'BUY' ? 'order-buy' : 'order-sell');
            appendCell(row, typeLabels[order.orderType]);
            appendCell(row, `${numberFormat.format(order.quantity)}주`);
            appendCell(row, order.orderType === 'MARKET' ? '시장가' : formatPrice(order.limitPrice));
            appendCell(row, statusLabels[order.status] ?? order.status, `order-status order-status--${order.status.toLowerCase()}`);
            const actionCell = document.createElement('td');
            if (order.status === 'PENDING') {
                const cancel = document.createElement('button');
                cancel.type = 'button';
                cancel.className = 'order-cancel';
                cancel.dataset.orderCancel = order.orderId;
                cancel.textContent = '취소';
                cancel.setAttribute('aria-label', `${order.name} 주문 취소`);
                actionCell.appendChild(cancel);
            }
            row.appendChild(actionCell);
            body.appendChild(row);
        });
    }

    function renderExecutions() {
        const body = byId('executions-body');
        body.replaceChildren();
        if (!state.executions.length) {
            const row = document.createElement('tr');
            row.className = 'empty-row';
            const cell = document.createElement('td');
            cell.colSpan = 6;
            cell.textContent = '아직 체결 내역이 없습니다.';
            row.appendChild(cell);
            body.appendChild(row);
            return;
        }
        state.executions.forEach(execution => {
            const order = state.orders.find(candidate => candidate.orderId === execution.orderId);
            const row = document.createElement('tr');
            appendCell(row, timeFormat.format(new Date(execution.executedAt)));
            appendCell(row, `${order?.name ?? execution.symbol} · ${execution.symbol}`);
            appendCell(row, sideLabels[execution.side], execution.side === 'BUY' ? 'order-buy' : 'order-sell');
            appendCell(row, `${numberFormat.format(execution.quantity)}주`);
            appendCell(row, formatPrice(execution.price));
            appendCell(row, formatPrice(Number(execution.price) * execution.quantity));
            body.appendChild(row);
        });
    }

    async function fetchOrders() {
        const response = await fetch(`/api/v1/accounts/${accountId}/orders`, { headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(await readApiError(response, `주문 조회 실패 (${response.status})`));
        state.orders = await response.json();
        renderOrders();
    }

    async function fetchExecutions() {
        const response = await fetch(`/api/v1/accounts/${accountId}/orders/executions`, { headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(await readApiError(response, `체결 조회 실패 (${response.status})`));
        state.executions = await response.json();
        renderExecutions();
    }

    async function refreshTradingData() {
        try {
            await Promise.all([fetchOrders(), fetchExecutions(), fetchPortfolio()]);
        } catch (error) {
            console.error('Unable to refresh paper trading data', error);
            setMessage(error.message, true);
        }
    }

    function renderSearchResults() {
        const container = byId('order-instrument-results');
        const input = byId('order-instrument-search');
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
                button.id = `order-instrument-result-${index}`;
                button.className = `instrument-result${index === state.activeSearchIndex ? ' is-active' : ''}`;
                button.dataset.orderInstrumentIndex = String(index);
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
            input.setAttribute('aria-activedescendant', `order-instrument-result-${state.activeSearchIndex}`);
        } else {
            input.removeAttribute('aria-activedescendant');
        }
    }

    function closeSearchResults() {
        byId('order-instrument-results').hidden = true;
        byId('order-instrument-search').setAttribute('aria-expanded', 'false');
        byId('order-instrument-search').removeAttribute('aria-activedescendant');
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
        byId('order-symbol').value = instrument.symbol;
        byId('order-name').value = instrument.name;
        byId('order-market').value = instrument.market;
        byId('order-instrument-search').value = `${instrument.name} · ${instrument.symbol}`;
        byId('order-instrument-search').closest('.order-search-field').classList.add('has-selection');
        byId('order-current-symbol').textContent = `${instrument.market} · ${instrument.symbol}`;
        closeSearchResults();
        setMessage(`${instrument.name}(${instrument.symbol})을 선택했습니다.`);
        await fetchLatestQuote();
        connectQuoteStream();
        updateAccountSummary();
        updateOrderForm();
    }

    async function fetchLatestQuote() {
        if (!state.selectedInstrument) return;
        const response = await fetch(
            `/api/v1/market-data/quotes/${state.selectedInstrument.symbol}?market=${state.selectedInstrument.market}`,
            { headers: { Accept: 'application/json' } }
        );
        if (!response.ok) {
            state.latestPrice = null;
            byId('order-current-price').textContent = '--';
            updateOrderForm();
            return;
        }
        applyQuote(await response.json());
    }

    function applyQuote(tick) {
        if (!state.selectedInstrument || tick.symbol !== state.selectedInstrument.symbol) return;
        state.latestPrice = Number(tick.price);
        byId('order-current-price').textContent = formatPrice(state.latestPrice);
        updateOrderForm();
    }

    function connectQuoteStream() {
        state.quoteSource?.close();
        if (!state.selectedInstrument) return;
        const source = new EventSource(`/api/v1/market-data/stream?symbols=${state.selectedInstrument.symbol}`);
        state.quoteSource = source;
        source.onopen = () => setConnection('live', '실시간');
        source.addEventListener('market-tick', event => applyQuote(JSON.parse(event.data)));
        source.onerror = () => setConnection('offline', '시세 재연결');
    }

    function updateOrderForm() {
        const type = byId('order-type').value;
        const side = document.querySelector('input[name="side"]:checked').value;
        const quantity = Number(byId('order-quantity').value);
        const limitInput = byId('order-limit-price');
        limitInput.disabled = type === 'MARKET';
        limitInput.required = type === 'LIMIT';
        limitInput.placeholder = type === 'MARKET' ? '시장가 주문' : '가격 입력';
        const price = type === 'MARKET' ? state.latestPrice : Number(limitInput.value);
        byId('order-estimated-amount').textContent = price > 0 && quantity > 0 ? formatPrice(price * quantity) : '--';
        const submit = byId('order-submit');
        submit.textContent = `${sideLabels[side]} 주문 접수`;
        submit.dataset.side = side;
        submit.disabled = state.submitting || !state.selectedInstrument || quantity <= 0
            || (type === 'MARKET' ? !state.latestPrice : !(Number(limitInput.value) > 0));
    }

    async function submitOrder(form) {
        if (state.submitting || !state.selectedInstrument) return;
        const data = new FormData(form);
        const orderType = data.get('orderType');
        const payload = {
            market: data.get('market'),
            symbol: data.get('symbol'),
            side: data.get('side'),
            orderType,
            quantity: Number(data.get('quantity')),
            limitPrice: orderType === 'LIMIT' ? Number(data.get('limitPrice')) : null,
            idempotencyKey: crypto.randomUUID()
        };
        state.submitting = true;
        updateOrderForm();
        setMessage('모의 주문을 접수하고 있습니다.');
        try {
            const response = await fetch(`/api/v1/accounts/${accountId}/orders`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error(await readApiError(response, `주문 접수 실패 (${response.status})`));
            const order = await response.json();
            setMessage(order.status === 'FILLED'
                ? `${order.name} ${numberFormat.format(order.quantity)}주가 모의 체결됐습니다.`
                : `${order.name} 지정가 주문이 접수됐습니다.`);
            await refreshTradingData();
        } catch (error) {
            console.error('Unable to place paper order', error);
            setMessage(error.message, true);
        } finally {
            state.submitting = false;
            updateOrderForm();
        }
    }

    async function cancelOrder(orderId) {
        try {
            const response = await fetch(`/api/v1/accounts/${accountId}/orders/${orderId}`, { method: 'DELETE' });
            if (!response.ok) throw new Error(await readApiError(response, `주문 취소 실패 (${response.status})`));
            setMessage('대기 중인 주문을 취소했습니다.');
            await refreshTradingData();
        } catch (error) {
            setMessage(error.message, true);
        }
    }

    function connectOrderStream() {
        state.orderSource?.close();
        const source = new EventSource(`/api/v1/accounts/${accountId}/orders/stream`);
        state.orderSource = source;
        source.onopen = () => setConnection('live', '주문 실시간');
        source.addEventListener('order-updated', async event => {
            const order = JSON.parse(event.data);
            setMessage(`${order.name} 주문 상태가 ${statusLabels[order.status] ?? order.status}(으)로 변경됐습니다.`);
            await refreshTradingData();
        });
        source.onerror = () => setConnection('offline', '주문 재연결');
    }

    function bindInteractions() {
        const search = byId('order-instrument-search');
        search.addEventListener('input', event => {
            state.selectedInstrument = null;
            state.latestPrice = null;
            state.quoteSource?.close();
            state.quoteSource = null;
            byId('order-symbol').value = '';
            byId('order-name').value = '';
            search.closest('.order-search-field').classList.remove('has-selection');
            byId('order-current-price').textContent = '--';
            byId('order-current-symbol').textContent = '종목을 선택해 주세요';
            setMessage('검색 결과에서 주문할 종목을 선택해 주세요.');
            scheduleSearch(event.currentTarget.value);
            updateAccountSummary();
            updateOrderForm();
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
        byId('order-instrument-results').addEventListener('click', event => {
            const result = event.target.closest('[data-order-instrument-index]');
            if (result) selectInstrument(state.searchResults[Number(result.dataset.orderInstrumentIndex)]);
        });
        document.addEventListener('click', event => {
            if (!byId('order-form').contains(event.target)) closeSearchResults();
        });
        document.querySelectorAll('input[name="side"]').forEach(input => input.addEventListener('change', updateOrderForm));
        byId('order-type').addEventListener('change', updateOrderForm);
        byId('order-quantity').addEventListener('input', updateOrderForm);
        byId('order-limit-price').addEventListener('input', updateOrderForm);
        byId('order-form').addEventListener('submit', event => {
            event.preventDefault();
            submitOrder(event.currentTarget);
        });
        byId('orders-refresh').addEventListener('click', refreshTradingData);
        byId('orders-body').addEventListener('click', event => {
            const button = event.target.closest('[data-order-cancel]');
            if (!button) return;
            const order = state.orders.find(candidate => candidate.orderId === button.dataset.orderCancel);
            if (order && window.confirm(`${order.name} 주문을 취소할까요?`)) cancelOrder(order.orderId);
        });
    }

    async function initialize() {
        if (document.body.dataset.page !== 'orders') return;
        bindInteractions();
        updateOrderForm();
        await refreshTradingData();
        connectOrderStream();
    }

    window.addEventListener('beforeunload', () => {
        state.orderSource?.close();
        state.quoteSource?.close();
        state.searchController?.abort();
        clearTimeout(state.searchTimer);
    });
    document.addEventListener('DOMContentLoaded', initialize);
})();
