(() => {
    'use strict';

    const symbols = {
        '005930': { name: '삼성전자', market: 'KRX' },
        '000660': { name: 'SK하이닉스', market: 'KRX' }
    };

    const state = {
        selectedSymbol: '005930',
        quotes: new Map(),
        histories: new Map(Object.keys(symbols).map(symbol => [symbol, []])),
        sessionOpen: new Map(),
        eventCount: 0,
        reconnectDelay: 1000,
        eventSource: null,
        reconnectTimer: null
    };

    const numberFormat = new Intl.NumberFormat('ko-KR');
    const timeFormat = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });
    const dateTimeFormat = new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });

    const byId = id => document.getElementById(id);
    const priceElement = symbol => document.querySelector(`[data-price="${symbol}"]`);
    const changeElement = symbol => document.querySelector(`[data-change="${symbol}"]`);
    const quoteElement = symbol => document.querySelector(`[data-symbol="${symbol}"]`);
    const sparklineElement = symbol => document.querySelector(`[data-sparkline="${symbol}"]`);

    function formatPrice(value) {
        return value == null ? '--' : `${numberFormat.format(value)}원`;
    }

    function calculateChange(symbol, price) {
        const open = state.sessionOpen.get(symbol);
        if (open == null || open === 0) return { value: 0, percent: 0, direction: 'flat' };
        const value = price - open;
        return {
            value,
            percent: (value / open) * 100,
            direction: value > 0 ? 'up' : value < 0 ? 'down' : 'flat'
        };
    }

    function changeText(change) {
        if (change.direction === 'flat') return '0 (0.00%)';
        const sign = change.value > 0 ? '+' : '';
        return `${sign}${numberFormat.format(change.value)} (${sign}${change.percent.toFixed(2)}%)`;
    }

    function setConnection(status, label) {
        byId('connection-badge').dataset.status = status;
        byId('connection-label').textContent = label;
    }

    function updateClockAndSession() {
        const now = new Date();
        byId('market-clock').textContent = `${timeFormat.format(now)} KST`;

        const parts = new Intl.DateTimeFormat('en-US', {
            timeZone: 'Asia/Seoul', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false
        }).formatToParts(now).reduce((acc, part) => ({ ...acc, [part.type]: part.value }), {});
        const minute = Number(parts.hour) * 60 + Number(parts.minute);
        const weekday = !['Sat', 'Sun'].includes(parts.weekday);
        const isOpen = weekday && minute >= 540 && minute <= 930;

        byId('market-session').textContent = isOpen ? 'KRX 장중' : 'KRX 장 마감';
        byId('market-session-detail').textContent = isOpen ? '정규 시장 · 09:00—15:30' : '다음 정규장 데이터를 준비합니다';
    }

    function normalizeHistory(history, width, height) {
        if (history.length === 0) return `0,${height / 2} ${width},${height / 2}`;
        if (history.length === 1) return `0,${height / 2} ${width},${height / 2}`;
        const values = history.map(point => point.price);
        let min = Math.min(...values);
        let max = Math.max(...values);
        if (min === max) { min -= 1; max += 1; }
        const pad = (max - min) * 0.1;
        min -= pad;
        max += pad;
        return history.map((point, index) => {
            const x = (index / (history.length - 1)) * width;
            const y = height - ((point.price - min) / (max - min)) * height;
            return `${x.toFixed(2)},${y.toFixed(2)}`;
        }).join(' ');
    }

    function renderSparkline(symbol) {
        sparklineElement(symbol).setAttribute('points', normalizeHistory(state.histories.get(symbol), 96, 30));
    }

    function renderSelectedChart() {
        const symbol = state.selectedSymbol;
        const quote = state.quotes.get(symbol);
        const history = state.histories.get(symbol);
        const meta = symbols[symbol];

        byId('selected-market').textContent = `${meta.market} · ${symbol}`;
        byId('chart-title').textContent = meta.name;

        if (!quote) {
            byId('selected-price').textContent = '--';
            byId('selected-change').textContent = '실시간 시세 대기 중';
            byId('selected-change').className = '';
            byId('session-high').textContent = '--';
            byId('session-low').textContent = '--';
            byId('selected-volume').textContent = '--';
            byId('selected-source').textContent = '--';
            byId('empty-chart').classList.remove('hidden');
            return;
        }

        const change = calculateChange(symbol, Number(quote.price));
        byId('selected-price').textContent = formatPrice(Number(quote.price));
        byId('selected-change').textContent = changeText(change);
        byId('selected-change').className = change.direction;
        byId('selected-volume').textContent = numberFormat.format(quote.volume);
        byId('selected-source').textContent = quote.source;

        const prices = history.map(point => point.price);
        byId('session-high').textContent = formatPrice(Math.max(...prices));
        byId('session-low').textContent = formatPrice(Math.min(...prices));

        const points = normalizeHistory(history, 800, 260);
        byId('price-line').setAttribute('points', points);
        byId('price-area').setAttribute('d', `M${points.replaceAll(' ', ' L')} L800 300 L0 300 Z`);
        byId('empty-chart').classList.add('hidden');
    }

    function renderQuote(tick, previousPrice) {
        const symbol = tick.symbol;
        const price = Number(tick.price);
        const change = calculateChange(symbol, price);
        const direction = previousPrice == null ? change.direction : price > previousPrice ? 'up' : price < previousPrice ? 'down' : 'flat';
        const priceNode = priceElement(symbol);
        const changeNode = changeElement(symbol);
        const quoteNode = quoteElement(symbol);

        priceNode.textContent = formatPrice(price);
        changeNode.textContent = changeText(change);
        changeNode.className = change.direction;
        quoteNode.dataset.direction = direction;

        if (direction !== 'flat') {
            priceNode.classList.remove('price-flash-up', 'price-flash-down');
            void priceNode.offsetWidth;
            priceNode.classList.add(direction === 'up' ? 'price-flash-up' : 'price-flash-down');
        }
        renderSparkline(symbol);
    }

    function appendActivity(tick, direction) {
        const body = byId('activity-body');
        body.querySelector('.empty-row')?.remove();
        const row = document.createElement('tr');
        const cells = [
            dateTimeFormat.format(new Date(tick.tradedAt)),
            `${symbols[tick.symbol]?.name ?? tick.symbol} · ${tick.symbol}`,
            tick.market,
            formatPrice(Number(tick.price)),
            numberFormat.format(tick.volume),
            tick.source
        ];
        cells.forEach((value, index) => {
            const cell = document.createElement('td');
            cell.textContent = value;
            if (index === 3 && direction !== 'flat') cell.className = direction === 'up' ? 'price-up' : 'price-down';
            row.appendChild(cell);
        });
        body.prepend(row);
        while (body.children.length > 8) body.lastElementChild.remove();
    }

    function applyTick(tick, { historical = false } = {}) {
        if (!symbols[tick.symbol]) return;
        const previous = state.quotes.get(tick.symbol);
        const previousPrice = previous ? Number(previous.price) : null;
        const price = Number(tick.price);

        if (!state.sessionOpen.has(tick.symbol)) state.sessionOpen.set(tick.symbol, price);
        state.quotes.set(tick.symbol, tick);

        const history = state.histories.get(tick.symbol);
        history.push({ price, tradedAt: tick.tradedAt });
        if (history.length > 60) history.shift();

        renderQuote(tick, previousPrice);
        if (state.selectedSymbol === tick.symbol) renderSelectedChart();

        if (!historical) {
            state.eventCount += 1;
            const latency = Math.max(0, Date.now() - new Date(tick.receivedAt).getTime());
            byId('event-count').textContent = numberFormat.format(state.eventCount);
            byId('feed-latency').textContent = numberFormat.format(latency);
            byId('last-update').textContent = `${timeFormat.format(new Date())} 마지막 갱신`;
            const direction = previousPrice == null ? 'flat' : price > previousPrice ? 'up' : price < previousPrice ? 'down' : 'flat';
            appendActivity(tick, direction);
        }
    }

    async function fetchLatestQuotes() {
        await Promise.all(Object.keys(symbols).map(async symbol => {
            try {
                const response = await fetch(`/api/v1/market-data/quotes/${symbol}?market=KRX`, { headers: { Accept: 'application/json' } });
                if (response.ok) applyTick(await response.json(), { historical: true });
            } catch (error) {
                console.warn(`Unable to load latest quote for ${symbol}`, error);
            }
        }));
    }

    function scheduleReconnect() {
        clearTimeout(state.reconnectTimer);
        const delay = state.reconnectDelay;
        state.reconnectDelay = Math.min(state.reconnectDelay * 2, 15000);
        state.reconnectTimer = setTimeout(connectStream, delay);
    }

    function connectStream() {
        state.eventSource?.close();
        setConnection('connecting', '연결 중');
        const symbolList = Object.keys(symbols).join(',');
        const source = new EventSource(`/api/v1/market-data/stream?symbols=${symbolList}`);
        state.eventSource = source;

        source.onopen = () => {
            state.reconnectDelay = 1000;
            setConnection('live', '실시간');
        };
        source.addEventListener('market-tick', event => {
            try { applyTick(JSON.parse(event.data)); }
            catch (error) { console.error('Invalid market tick payload', error); }
        });
        source.onerror = () => {
            source.close();
            setConnection('offline', '재연결');
            scheduleReconnect();
        };
    }

    function selectSymbol(symbol) {
        state.selectedSymbol = symbol;
        document.querySelectorAll('.quote-item').forEach(item => {
            const selected = item.dataset.symbol === symbol;
            item.classList.toggle('selected', selected);
            item.setAttribute('aria-pressed', String(selected));
        });
        renderSelectedChart();
    }

    function bindInteractions() {
        document.querySelectorAll('.quote-item').forEach(item => {
            item.addEventListener('click', () => selectSymbol(item.dataset.symbol));
        });

        const menuButton = document.querySelector('.menu-button');
        const mobileMenu = byId('mobile-menu');
        menuButton.addEventListener('click', () => {
            const expanded = menuButton.getAttribute('aria-expanded') === 'true';
            menuButton.setAttribute('aria-expanded', String(!expanded));
            mobileMenu.hidden = expanded;
        });
        mobileMenu.querySelectorAll('a').forEach(link => link.addEventListener('click', () => {
            mobileMenu.hidden = true;
            menuButton.setAttribute('aria-expanded', 'false');
        }));
    }

    async function initialize() {
        bindInteractions();
        updateClockAndSession();
        setInterval(updateClockAndSession, 1000);
        await fetchLatestQuotes();
        connectStream();
    }

    window.addEventListener('beforeunload', () => {
        state.eventSource?.close();
        clearTimeout(state.reconnectTimer);
    });
    document.addEventListener('DOMContentLoaded', initialize);
})();
