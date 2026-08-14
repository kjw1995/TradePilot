(() => {
    'use strict';

    const symbols = {};
    const portfolioAccountId = 'local-account';

    const state = {
        selectedSymbol: null,
        watchlist: [],
        quotes: new Map(),
        histories: new Map(),
        sessionOpen: new Map(),
        eventCount: 0,
        reconnectDelay: 1000,
        eventSource: null,
        reconnectTimer: null,
        portfolio: null,
        portfolioLoading: false,
        watchlistLoading: false,
        watchlistMutating: false,
        instrumentResults: [],
        instrumentSearchLoading: false,
        instrumentSearchActiveIndex: -1,
        instrumentSearchTimer: null,
        instrumentSearchController: null,
        selectedInstrument: null
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

    function formatSignedPrice(value) {
        if (value == null) return '--';
        const sign = value > 0 ? '+' : '';
        return `${sign}${numberFormat.format(value)}원`;
    }

    function valueDirection(value) {
        return value > 0 ? 'is-positive' : value < 0 ? 'is-negative' : '';
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
        const badge = byId('connection-badge');
        const connectionLabel = byId('connection-label');
        if (badge) badge.dataset.status = status;
        if (connectionLabel) connectionLabel.textContent = label;
    }

    function updateClockAndSession() {
        const now = new Date();
        const clock = byId('market-clock');
        if (clock) clock.textContent = `${timeFormat.format(now)} KST`;

        const parts = new Intl.DateTimeFormat('en-US', {
            timeZone: 'Asia/Seoul', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false
        }).formatToParts(now).reduce((acc, part) => ({ ...acc, [part.type]: part.value }), {});
        const minute = Number(parts.hour) * 60 + Number(parts.minute);
        const weekday = !['Sat', 'Sun'].includes(parts.weekday);
        const isOpen = weekday && minute >= 540 && minute <= 930;

        const session = byId('market-session');
        const sessionDetail = byId('market-session-detail');
        if (session) session.textContent = isOpen ? 'KRX 장중' : 'KRX 장 마감';
        if (sessionDetail) sessionDetail.textContent = isOpen ? '정규 시장 · 09:00—15:30' : '다음 정규장 데이터를 준비합니다';
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
        sparklineElement(symbol)?.setAttribute('points', normalizeHistory(state.histories.get(symbol) ?? [], 96, 30));
    }

    function renderSelectedChart() {
        if (!byId('chart-title')) return;
        const symbol = state.selectedSymbol;
        if (!symbol || !symbols[symbol]) {
            byId('selected-market').textContent = '관심 종목';
            byId('chart-title').textContent = '종목을 추가해 주세요';
            byId('selected-price').textContent = '--';
            byId('selected-change').textContent = '선택된 종목이 없습니다';
            byId('selected-change').className = '';
            byId('session-high').textContent = '--';
            byId('session-low').textContent = '--';
            byId('selected-volume').textContent = '--';
            byId('selected-source').textContent = '--';
            byId('empty-chart').classList.remove('hidden');
            return;
        }
        const quote = state.quotes.get(symbol);
        const history = state.histories.get(symbol) ?? [];
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

        if (!priceNode || !changeNode || !quoteNode) return;
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

    function syncSymbolRegistry() {
        const nextSymbols = {};
        state.watchlist.forEach(item => {
            nextSymbols[item.symbol] = { name: item.name, market: item.market };
        });
        state.portfolio?.positions.forEach(position => {
            if (!nextSymbols[position.symbol]) {
                nextSymbols[position.symbol] = { name: position.name, market: position.market };
            }
        });

        Object.keys(symbols).forEach(symbol => delete symbols[symbol]);
        Object.assign(symbols, nextSymbols);
        Object.keys(symbols).forEach(symbol => {
            if (!state.histories.has(symbol)) state.histories.set(symbol, []);
        });
    }

    function setWatchlistMessage(message, error = false) {
        const node = byId('watchlist-message');
        if (!node) return;
        node.textContent = message;
        node.classList.toggle('is-error', error);
    }

    function createQuoteItem(item, index) {
        const row = document.createElement('div');
        row.className = `quote-item${state.selectedSymbol === item.symbol ? ' selected' : ''}`;
        row.dataset.symbol = item.symbol;
        row.dataset.market = item.market;
        row.setAttribute('role', 'listitem');

        const select = document.createElement('button');
        select.className = 'quote-select';
        select.type = 'button';
        select.dataset.action = 'select';
        select.dataset.testid = `quote-${item.symbol}`;
        select.setAttribute('aria-pressed', String(state.selectedSymbol === item.symbol));
        select.setAttribute('aria-label', `${item.name} ${item.symbol} 차트 보기`);

        const symbol = document.createElement('span');
        symbol.className = 'quote-symbol';
        const code = document.createElement('b');
        code.textContent = item.symbol;
        const name = document.createElement('small');
        name.textContent = item.name;
        symbol.append(code, name);

        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.classList.add('mini-chart');
        svg.setAttribute('viewBox', '0 0 96 34');
        svg.setAttribute('preserveAspectRatio', 'none');
        svg.setAttribute('aria-hidden', 'true');
        const line = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
        line.dataset.sparkline = item.symbol;
        line.setAttribute('points', normalizeHistory(state.histories.get(item.symbol) ?? [], 96, 30));
        svg.appendChild(line);

        const price = document.createElement('span');
        price.className = 'quote-price';
        const priceValue = document.createElement('b');
        priceValue.dataset.price = item.symbol;
        priceValue.textContent = '--';
        const change = document.createElement('small');
        change.dataset.change = item.symbol;
        change.textContent = '대기 중';
        price.append(priceValue, change);
        select.append(symbol, svg, price);

        const actions = document.createElement('div');
        actions.className = 'quote-actions';
        [
            { action: 'up', label: '위로 이동', text: '↑', disabled: index === 0 },
            { action: 'down', label: '아래로 이동', text: '↓', disabled: index === state.watchlist.length - 1 },
            { action: 'delete', label: '관심종목 삭제', text: '×', disabled: false }
        ].forEach(config => {
            const button = document.createElement('button');
            button.className = `quote-action${config.action === 'delete' ? ' quote-action--delete' : ''}`;
            button.type = 'button';
            button.dataset.action = config.action;
            button.textContent = config.text;
            button.setAttribute('aria-label', `${item.name} ${config.label}`);
            button.disabled = config.disabled || state.watchlistMutating;
            actions.appendChild(button);
        });

        row.append(select, actions);
        return row;
    }

    function renderWatchlist() {
        const list = byId('quote-list');
        const count = byId('watch-count');
        if (count) count.textContent = numberFormat.format(state.watchlist.length);
        if (!list) return;
        list.replaceChildren();
        list.setAttribute('aria-busy', String(state.watchlistLoading));

        if (state.watchlistLoading) {
            const loading = document.createElement('div');
            loading.className = 'watchlist-empty';
            loading.textContent = '관심종목을 불러오고 있습니다.';
            list.appendChild(loading);
            return;
        }
        if (!state.watchlist.length) {
            const empty = document.createElement('div');
            empty.className = 'watchlist-empty';
            empty.textContent = '관심종목이 없습니다. 종목 추가 버튼으로 시작해 보세요.';
            list.appendChild(empty);
            return;
        }

        state.watchlist.forEach((item, index) => list.appendChild(createQuoteItem(item, index)));
        state.watchlist.forEach(item => {
            const quote = state.quotes.get(item.symbol);
            if (quote) renderQuote(quote, null);
        });
    }

    function ensureSelectedWatchlistItem() {
        const selectedExists = state.watchlist.some(item => item.symbol === state.selectedSymbol);
        if (!selectedExists) state.selectedSymbol = state.watchlist[0]?.symbol ?? null;
    }

    async function readApiError(response, fallback) {
        try {
            const body = await response.json();
            return body.message || fallback;
        } catch (_) {
            return fallback;
        }
    }

    async function fetchWatchlist() {
        state.watchlistLoading = true;
        renderWatchlist();
        try {
            const response = await fetch(`/api/v1/accounts/${portfolioAccountId}/watchlist`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) throw new Error(await readApiError(response, `관심종목 조회 실패 (${response.status})`));
            state.watchlist = await response.json();
            ensureSelectedWatchlistItem();
            syncSymbolRegistry();
            setWatchlistMessage('');
        } catch (error) {
            console.error('Unable to load watchlist', error);
            state.watchlist = [];
            ensureSelectedWatchlistItem();
            syncSymbolRegistry();
            setWatchlistMessage(error.message, true);
        } finally {
            state.watchlistLoading = false;
            renderWatchlist();
            renderSelectedChart();
        }
    }

    function updateInstrumentSubmitState() {
        const submit = byId('watchlist-submit');
        if (submit) submit.disabled = state.watchlistMutating || !state.selectedInstrument;
    }

    function closeInstrumentResults() {
        const input = byId('instrument-search');
        const results = byId('instrument-results');
        results.hidden = true;
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
        state.instrumentSearchActiveIndex = -1;
    }

    function resetInstrumentSearch(clearInput = true) {
        clearTimeout(state.instrumentSearchTimer);
        state.instrumentSearchController?.abort();
        state.instrumentSearchController = null;
        state.instrumentResults = [];
        state.instrumentSearchLoading = false;
        state.selectedInstrument = null;
        byId('watchlist-symbol').value = '';
        byId('watchlist-name').value = '';
        byId('watchlist-market').value = 'KRX';
        byId('instrument-search').closest('.instrument-search-field').classList.remove('has-selection');
        if (clearInput) byId('instrument-search').value = '';
        closeInstrumentResults();
        updateInstrumentSubmitState();
    }

    function isAlreadyWatching(instrument) {
        return state.watchlist.some(item =>
            item.market === instrument.market && item.symbol === instrument.symbol);
    }

    function renderInstrumentResults() {
        const container = byId('instrument-results');
        const input = byId('instrument-search');
        container.replaceChildren();

        if (state.instrumentSearchLoading) {
            const loading = document.createElement('span');
            loading.className = 'instrument-result-empty';
            loading.textContent = '종목을 검색하고 있습니다.';
            container.appendChild(loading);
        } else if (!state.instrumentResults.length) {
            const empty = document.createElement('span');
            empty.className = 'instrument-result-empty';
            empty.textContent = '일치하는 종목이 없습니다.';
            container.appendChild(empty);
        } else {
            state.instrumentResults.forEach((instrument, index) => {
                const button = document.createElement('button');
                const alreadyWatching = isAlreadyWatching(instrument);
                button.className = `instrument-result${index === state.instrumentSearchActiveIndex ? ' is-active' : ''}`;
                button.type = 'button';
                button.id = `instrument-result-${index}`;
                button.dataset.instrumentIndex = String(index);
                button.setAttribute('role', 'option');
                button.setAttribute('aria-selected', String(index === state.instrumentSearchActiveIndex));
                button.disabled = alreadyWatching;

                const name = document.createElement('span');
                name.className = 'instrument-result__name';
                const title = document.createElement('b');
                title.textContent = instrument.name;
                const symbol = document.createElement('small');
                symbol.textContent = instrument.symbol;
                name.append(title, symbol);

                const meta = document.createElement('span');
                meta.className = 'instrument-result__meta';
                meta.textContent = alreadyWatching ? '추가됨' : `${instrument.exchange} · ${instrument.currency}`;
                button.append(name, meta);
                container.appendChild(button);
            });
        }

        container.hidden = false;
        input.setAttribute('aria-expanded', 'true');
        if (state.instrumentSearchActiveIndex >= 0) {
            input.setAttribute('aria-activedescendant', `instrument-result-${state.instrumentSearchActiveIndex}`);
        } else {
            input.removeAttribute('aria-activedescendant');
        }
    }

    function selectInstrument(instrument) {
        if (!instrument || isAlreadyWatching(instrument)) return;
        state.selectedInstrument = instrument;
        byId('watchlist-market').value = instrument.market;
        byId('watchlist-symbol').value = instrument.symbol;
        byId('watchlist-name').value = instrument.name;
        byId('instrument-search').value = `${instrument.name} · ${instrument.symbol}`;
        byId('instrument-search').closest('.instrument-search-field').classList.add('has-selection');
        closeInstrumentResults();
        setWatchlistMessage(`${instrument.name}(${instrument.symbol})을 선택했습니다.`);
        updateInstrumentSubmitState();
    }

    async function searchInstruments(query) {
        const normalizedQuery = query.trim();
        if (!normalizedQuery) {
            state.instrumentResults = [];
            closeInstrumentResults();
            return;
        }

        state.instrumentSearchController?.abort();
        const controller = new AbortController();
        state.instrumentSearchController = controller;
        state.instrumentSearchLoading = true;
        state.instrumentSearchActiveIndex = -1;
        renderInstrumentResults();
        try {
            const market = byId('watchlist-market').value;
            const response = await fetch(
                `/api/v1/instruments/search?market=${encodeURIComponent(market)}&query=${encodeURIComponent(normalizedQuery)}&limit=8`,
                { headers: { Accept: 'application/json' }, signal: controller.signal }
            );
            if (!response.ok) throw new Error(await readApiError(response, `종목 검색 실패 (${response.status})`));
            state.instrumentResults = await response.json();
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('Unable to search instruments', error);
            state.instrumentResults = [];
            setWatchlistMessage(error.message, true);
        } finally {
            if (state.instrumentSearchController === controller) {
                state.instrumentSearchLoading = false;
                state.instrumentSearchController = null;
                renderInstrumentResults();
            }
        }
    }

    function scheduleInstrumentSearch(query) {
        clearTimeout(state.instrumentSearchTimer);
        state.instrumentSearchTimer = setTimeout(() => searchInstruments(query), 250);
    }

    function moveInstrumentResultSelection(direction) {
        if (!state.instrumentResults.length) return;
        let nextIndex = state.instrumentSearchActiveIndex;
        for (let attempts = 0; attempts < state.instrumentResults.length; attempts += 1) {
            nextIndex = (nextIndex + direction + state.instrumentResults.length) % state.instrumentResults.length;
            if (!isAlreadyWatching(state.instrumentResults[nextIndex])) {
                state.instrumentSearchActiveIndex = nextIndex;
                renderInstrumentResults();
                return;
            }
        }
    }

    async function addWatchlistItem(form) {
        if (state.watchlistMutating) return;
        if (!state.selectedInstrument) {
            setWatchlistMessage('검색 결과에서 추가할 종목을 선택해 주세요.', true);
            return;
        }
        const formData = new FormData(form);
        state.watchlistMutating = true;
        setWatchlistMessage('종목을 추가하고 있습니다.');
        form.querySelectorAll('button, input, select').forEach(control => control.disabled = true);
        try {
            const response = await fetch(`/api/v1/accounts/${portfolioAccountId}/watchlist/items`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({
                    market: formData.get('market'),
                    symbol: String(formData.get('symbol')).trim().toUpperCase(),
                    name: String(formData.get('name')).trim()
                })
            });
            if (!response.ok) throw new Error(await readApiError(response, `종목 추가 실패 (${response.status})`));
            resetInstrumentSearch();
            await fetchWatchlist();
            await fetchLatestQuotes();
            connectStream();
            setWatchlistFormOpen(false);
        } catch (error) {
            console.error('Unable to add watchlist item', error);
            setWatchlistMessage(error.message, true);
        } finally {
            state.watchlistMutating = false;
            form.querySelectorAll('button, input, select').forEach(control => control.disabled = false);
            updateInstrumentSubmitState();
            renderWatchlist();
        }
    }

    async function removeWatchlistItem(item) {
        if (state.watchlistMutating) return;
        state.watchlistMutating = true;
        renderWatchlist();
        try {
            const response = await fetch(
                `/api/v1/accounts/${portfolioAccountId}/watchlist/items/${encodeURIComponent(item.symbol)}?market=${encodeURIComponent(item.market)}`,
                { method: 'DELETE' }
            );
            if (!response.ok) throw new Error(await readApiError(response, `종목 삭제 실패 (${response.status})`));
            await fetchWatchlist();
            connectStream();
        } catch (error) {
            console.error('Unable to remove watchlist item', error);
            setWatchlistMessage(error.message, true);
        } finally {
            state.watchlistMutating = false;
            renderWatchlist();
        }
    }

    async function moveWatchlistItem(item, offset) {
        if (state.watchlistMutating) return;
        const currentIndex = state.watchlist.findIndex(candidate =>
            candidate.symbol === item.symbol && candidate.market === item.market);
        const nextIndex = currentIndex + offset;
        if (currentIndex < 0 || nextIndex < 0 || nextIndex >= state.watchlist.length) return;

        const nextOrder = [...state.watchlist];
        [nextOrder[currentIndex], nextOrder[nextIndex]] = [nextOrder[nextIndex], nextOrder[currentIndex]];
        state.watchlistMutating = true;
        renderWatchlist();
        try {
            const response = await fetch(`/api/v1/accounts/${portfolioAccountId}/watchlist/order`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                body: JSON.stringify({
                    items: nextOrder.map(candidate => ({ market: candidate.market, symbol: candidate.symbol }))
                })
            });
            if (!response.ok) throw new Error(await readApiError(response, `순서 변경 실패 (${response.status})`));
            state.watchlist = await response.json();
            setWatchlistMessage('관심종목 순서를 저장했습니다.');
        } catch (error) {
            console.error('Unable to reorder watchlist', error);
            setWatchlistMessage(error.message, true);
        } finally {
            state.watchlistMutating = false;
            renderWatchlist();
        }
    }

    function setWatchlistFormOpen(open) {
        const form = byId('watchlist-form');
        form.hidden = !open;
        byId('watchlist-add-toggle').setAttribute('aria-expanded', String(open));
        if (open) {
            setWatchlistMessage('');
            byId('instrument-search').focus();
        } else {
            form.reset();
            resetInstrumentSearch();
        }
    }

    function setPortfolioStatus(status, message) {
        const footer = document.querySelector('.portfolio-footer');
        const quoteStatus = byId('portfolio-quote-status');
        if (!footer || !quoteStatus) return;
        footer.dataset.status = status;
        quoteStatus.lastChild.textContent = message;
    }

    function recalculatePortfolioTotals() {
        if (!state.portfolio) return;
        const positions = state.portfolio.positions;
        const valuedPositions = positions.filter(position => position.quoteAvailable);
        const investedAmount = positions.reduce((sum, position) => sum + Number(position.costBasis), 0);
        const valuedCost = valuedPositions.reduce((sum, position) => sum + Number(position.costBasis), 0);
        const evaluationAmount = valuedPositions.reduce((sum, position) => sum + Number(position.marketValue), 0);
        const profitLoss = evaluationAmount - valuedCost;
        const cashBalance = Number(state.portfolio.totals.cashBalance);

        state.portfolio.totals = {
            ...state.portfolio.totals,
            cashBalance,
            investedAmount,
            evaluationAmount,
            totalAssets: cashBalance + evaluationAmount,
            profitLoss,
            returnRate: valuedCost === 0 ? 0 : (profitLoss / valuedCost) * 100,
            valuedPositionCount: valuedPositions.length,
            totalPositionCount: positions.length
        };
    }

    function appendPortfolioCell(row, primary, detail = null, className = '') {
        const cell = document.createElement('td');
        if (className) cell.className = className;
        const main = document.createElement('span');
        main.textContent = primary;
        cell.appendChild(main);
        if (detail) {
            const secondary = document.createElement('small');
            secondary.className = 'portfolio-cell-detail';
            secondary.textContent = detail;
            cell.appendChild(secondary);
        }
        row.appendChild(cell);
        return cell;
    }

    function renderPortfolioPositions() {
        const body = byId('portfolio-body');
        if (!body || !state.portfolio) return;
        body.replaceChildren();

        if (!state.portfolio.positions.length) {
            const row = document.createElement('tr');
            row.className = 'empty-row';
            const cell = document.createElement('td');
            cell.colSpan = 7;
            cell.textContent = '보유 중인 종목이 없습니다.';
            row.appendChild(cell);
            body.appendChild(row);
            return;
        }

        state.portfolio.positions.forEach(position => {
            const row = document.createElement('tr');
            row.dataset.portfolioSymbol = position.symbol;
            appendPortfolioCell(row, position.name, `${position.market} · ${position.symbol}`, 'portfolio-symbol-cell');
            appendPortfolioCell(row, `${numberFormat.format(position.quantity)}주`);
            appendPortfolioCell(row, formatPrice(position.averagePrice));
            appendPortfolioCell(row, position.quoteAvailable ? formatPrice(position.currentPrice) : '시세 없음', position.quoteAvailable ? '실시간' : '확인 필요');
            appendPortfolioCell(row, formatPrice(position.costBasis));
            appendPortfolioCell(row, position.quoteAvailable ? formatPrice(position.marketValue) : '--');

            const profitCell = appendPortfolioCell(
                row,
                position.quoteAvailable ? formatSignedPrice(position.profitLoss) : '--',
                position.quoteAvailable ? `${position.returnRate > 0 ? '+' : ''}${Number(position.returnRate).toFixed(2)}%` : '시세 대기 중',
                'portfolio-profit'
            );
            const directionClass = valueDirection(Number(position.profitLoss));
            if (directionClass) profitCell.classList.add(directionClass);
            body.appendChild(row);
        });
    }

    function renderPortfolio() {
        if (!state.portfolio || !byId('portfolio-body')) return;
        const { account, totals } = state.portfolio;
        const profitNode = byId('portfolio-profit-loss');
        const returnNode = byId('portfolio-return-rate');

        byId('account-name').textContent = account.displayName;
        byId('account-meta').textContent = `${account.broker} · ${account.maskedAccountNumber}`;
        byId('portfolio-synced-at').textContent = `${dateTimeFormat.format(new Date(account.syncedAt))} 계좌 동기화`;
        byId('portfolio-total-assets').textContent = formatPrice(totals.totalAssets);
        byId('portfolio-invested').textContent = formatPrice(totals.investedAmount);
        byId('portfolio-evaluation').textContent = `평가액 ${formatPrice(totals.evaluationAmount)}`;
        profitNode.textContent = formatSignedPrice(totals.profitLoss);
        profitNode.className = valueDirection(Number(totals.profitLoss));
        returnNode.textContent = `수익률 ${Number(totals.returnRate) > 0 ? '+' : ''}${Number(totals.returnRate).toFixed(2)}%`;
        returnNode.className = valueDirection(Number(totals.returnRate));
        byId('portfolio-cash').textContent = formatPrice(totals.cashBalance);
        setPortfolioStatus(
            totals.valuedPositionCount === totals.totalPositionCount ? 'live' : 'waiting',
            ` ${totals.valuedPositionCount}/${totals.totalPositionCount}개 종목 실시간 비교 중`
        );
        renderPortfolioPositions();
    }

    function applyPortfolioTick(tick) {
        if (!state.portfolio) return;
        const position = state.portfolio.positions.find(item => item.symbol === tick.symbol && item.market === tick.market);
        if (!position) return;

        const currentPrice = Number(tick.price);
        position.currentPrice = currentPrice;
        position.marketValue = currentPrice * position.quantity;
        position.profitLoss = position.marketValue - position.costBasis;
        position.returnRate = position.costBasis === 0 ? 0 : (position.profitLoss / position.costBasis) * 100;
        position.quoteAvailable = true;
        position.quotedAt = tick.tradedAt;
        recalculatePortfolioTotals();
        renderPortfolio();
    }

    async function fetchPortfolio() {
        if (state.portfolioLoading) return;
        state.portfolioLoading = true;
        const button = byId('portfolio-refresh');
        if (button) {
            button.disabled = true;
            button.classList.add('is-loading');
        }

        try {
            const response = await fetch(`/api/v1/portfolio/accounts/${portfolioAccountId}/summary`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) throw new Error(`Portfolio request failed: ${response.status}`);
            const portfolio = await response.json();
            portfolio.positions = portfolio.positions.map(position => ({
                ...position,
                quantity: Number(position.quantity),
                averagePrice: Number(position.averagePrice),
                costBasis: Number(position.costBasis),
                currentPrice: position.currentPrice == null ? null : Number(position.currentPrice),
                marketValue: position.marketValue == null ? null : Number(position.marketValue),
                profitLoss: position.profitLoss == null ? null : Number(position.profitLoss),
                returnRate: position.returnRate == null ? null : Number(position.returnRate)
            }));
            state.portfolio = portfolio;
            syncSymbolRegistry();
            recalculatePortfolioTotals();
            renderPortfolio();
        } catch (error) {
            console.error('Unable to load portfolio', error);
            setPortfolioStatus('error', ' 계좌 정보를 불러오지 못했습니다');
            const body = byId('portfolio-body');
            body.innerHTML = '<tr class="empty-row"><td colspan="7">계좌 API 연결을 확인해 주세요.</td></tr>';
            byId('portfolio-synced-at').textContent = '계좌 동기화 실패';
        } finally {
            state.portfolioLoading = false;
            if (button) {
                button.disabled = false;
                button.classList.remove('is-loading');
            }
        }
    }

    function appendActivity(tick, direction) {
        const body = byId('activity-body');
        if (!body) return;
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

        if (priceElement(tick.symbol)) {
            renderQuote(tick, previousPrice);
            if (state.selectedSymbol === tick.symbol) renderSelectedChart();
        }
        applyPortfolioTick(tick);

        if (!historical) {
            state.eventCount += 1;
            const latency = Math.max(0, Date.now() - new Date(tick.receivedAt).getTime());
            const eventCount = byId('event-count');
            const feedLatency = byId('feed-latency');
            const lastUpdate = byId('last-update');
            if (eventCount) eventCount.textContent = numberFormat.format(state.eventCount);
            if (feedLatency) feedLatency.textContent = numberFormat.format(latency);
            if (lastUpdate) lastUpdate.textContent = `${timeFormat.format(new Date())} 마지막 갱신`;
            const direction = previousPrice == null ? 'flat' : price > previousPrice ? 'up' : price < previousPrice ? 'down' : 'flat';
            appendActivity(tick, direction);
        }
    }

    async function fetchLatestQuotes() {
        await Promise.all(Object.keys(symbols).map(async symbol => {
            try {
                const market = symbols[symbol].market;
                const response = await fetch(`/api/v1/market-data/quotes/${symbol}?market=${encodeURIComponent(market)}`, { headers: { Accept: 'application/json' } });
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
        if (!symbolList) {
            state.eventSource = null;
            setConnection('offline', '구독 없음');
            return;
        }
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
            item.querySelector('.quote-select')?.setAttribute('aria-pressed', String(selected));
        });
        renderSelectedChart();
    }

    function bindInteractions() {
        const instrumentSearch = byId('instrument-search');
        if (instrumentSearch) {
            byId('quote-list').addEventListener('click', event => {
                const action = event.target.closest('[data-action]');
                const row = event.target.closest('.quote-item');
                if (!action || !row) return;
                const item = state.watchlist.find(candidate =>
                    candidate.symbol === row.dataset.symbol && candidate.market === row.dataset.market);
                if (!item) return;
                if (action.dataset.action === 'select') selectSymbol(item.symbol);
                if (action.dataset.action === 'up') moveWatchlistItem(item, -1);
                if (action.dataset.action === 'down') moveWatchlistItem(item, 1);
                if (action.dataset.action === 'delete'
                    && window.confirm(`${item.name}(${item.symbol})을 관심종목에서 삭제할까요?`)) {
                    removeWatchlistItem(item);
                }
            });
            byId('watchlist-add-toggle').addEventListener('click', () => {
                setWatchlistFormOpen(byId('watchlist-form').hidden);
            });
            byId('watchlist-cancel').addEventListener('click', () => setWatchlistFormOpen(false));
            byId('instrument-results').addEventListener('click', event => {
                const result = event.target.closest('[data-instrument-index]');
                if (!result) return;
                selectInstrument(state.instrumentResults[Number(result.dataset.instrumentIndex)]);
            });
            instrumentSearch.addEventListener('input', event => {
                state.selectedInstrument = null;
                byId('watchlist-symbol').value = '';
                byId('watchlist-name').value = '';
                instrumentSearch.closest('.instrument-search-field').classList.remove('has-selection');
                updateInstrumentSubmitState();
                setWatchlistMessage('검색 결과에서 종목을 선택해 주세요.');
                scheduleInstrumentSearch(event.currentTarget.value);
            });
            instrumentSearch.addEventListener('keydown', event => {
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    moveInstrumentResultSelection(1);
                }
                if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    moveInstrumentResultSelection(-1);
                }
                if (event.key === 'Enter' && state.instrumentSearchActiveIndex >= 0) {
                    event.preventDefault();
                    selectInstrument(state.instrumentResults[state.instrumentSearchActiveIndex]);
                }
                if (event.key === 'Escape') closeInstrumentResults();
            });
            byId('watchlist-market').addEventListener('change', () => {
                resetInstrumentSearch(false);
                scheduleInstrumentSearch(instrumentSearch.value);
            });
            document.addEventListener('click', event => {
                if (!byId('watchlist-form').contains(event.target)) closeInstrumentResults();
            });
            byId('watchlist-form').addEventListener('submit', event => {
                event.preventDefault();
                addWatchlistItem(event.currentTarget);
            });
        }

        const menuButton = document.querySelector('.menu-button');
        const mobileMenu = byId('mobile-menu');
        if (menuButton && mobileMenu) {
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

        const portfolioRefresh = byId('portfolio-refresh');
        if (portfolioRefresh) {
            portfolioRefresh.addEventListener('click', async () => {
                await fetchPortfolio();
                await fetchLatestQuotes();
                connectStream();
            });
        }
    }

    async function initialize() {
        bindInteractions();
        updateClockAndSession();
        setInterval(updateClockAndSession, 1000);
        const page = document.body.dataset.page ?? 'dashboard';
        const initialRequests = [];
        if (page === 'portfolio') initialRequests.push(fetchPortfolio());
        if (page === 'dashboard' || page === 'watchlist' || page === 'activity') initialRequests.push(fetchWatchlist());
        await Promise.all(initialRequests);
        syncSymbolRegistry();
        await fetchLatestQuotes();
        connectStream();
    }

    window.addEventListener('beforeunload', () => {
        state.eventSource?.close();
        clearTimeout(state.reconnectTimer);
    });
    document.addEventListener('DOMContentLoaded', initialize);
})();
