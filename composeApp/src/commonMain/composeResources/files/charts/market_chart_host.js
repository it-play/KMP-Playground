(() => {
    'use strict';

    const LWC = window.LightweightCharts;
    const container = document.getElementById('chart');
    const emptyState = document.getElementById('empty-state');
    const symbolLabel = document.getElementById('symbol');
    const timeLabel = document.getElementById('time');
    const openLabel = document.getElementById('open');
    const highLabel = document.getElementById('high');
    const lowLabel = document.getElementById('low');
    const closeLabel = document.getElementById('close');
    const changeLabel = document.getElementById('change');
    const volumeLabel = document.getElementById('volume');
    const ma5Label = document.getElementById('ma5');
    const ma20Label = document.getElementById('ma20');

    const colors = {
        paper: '#FCFDFE',
        ink: '#17222D',
        muted: '#687480',
        line: '#DDE3E8',
        rise: '#E34D5B',
        fall: '#2F73D2',
        signal: '#625CF6',
        amber: '#E08719',
    };

    const chart = LWC.createChart(container, {
        autoSize: true,
        layout: {
            background: { type: LWC.ColorType.Solid, color: colors.paper },
            textColor: colors.muted,
            fontFamily: "Inter, Pretendard, 'Noto Sans KR', system-ui, sans-serif",
            fontSize: 11,
            attributionLogo: false,
            panes: {
                enableResize: true,
                separatorColor: colors.line,
                separatorHoverColor: 'rgba(98, 92, 246, 0.18)',
            },
        },
        grid: {
            vertLines: { color: 'rgba(221, 227, 232, 0.58)' },
            horzLines: { color: 'rgba(221, 227, 232, 0.72)' },
        },
        crosshair: {
            mode: LWC.CrosshairMode.Normal,
            vertLine: {
                color: 'rgba(23, 34, 45, 0.42)',
                width: 1,
                style: LWC.LineStyle.Dashed,
                labelBackgroundColor: colors.ink,
            },
            horzLine: {
                color: 'rgba(23, 34, 45, 0.34)',
                width: 1,
                style: LWC.LineStyle.Dashed,
                labelBackgroundColor: colors.ink,
            },
        },
        rightPriceScale: {
            borderColor: colors.line,
            scaleMargins: { top: 0.12, bottom: 0.08 },
        },
        timeScale: {
            borderColor: colors.line,
            timeVisible: true,
            secondsVisible: false,
            rightOffset: 4,
            barSpacing: 8,
            minBarSpacing: 2,
            ticksVisible: true,
            allowBoldLabels: false,
        },
        handleScroll: {
            mouseWheel: true,
            pressedMouseMove: true,
            horzTouchDrag: true,
            vertTouchDrag: false,
        },
        handleScale: {
            axisPressedMouseMove: true,
            mouseWheel: true,
            pinch: true,
        },
    });

    const candleSeries = chart.addSeries(LWC.CandlestickSeries, {
        upColor: colors.rise,
        downColor: colors.fall,
        wickUpColor: colors.rise,
        wickDownColor: colors.fall,
        borderVisible: false,
        priceLineVisible: true,
        priceLineStyle: LWC.LineStyle.Dashed,
        lastValueVisible: true,
    });
    const ma5Series = chart.addSeries(LWC.LineSeries, {
        color: colors.amber,
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
    });
    const ma20Series = chart.addSeries(LWC.LineSeries, {
        color: colors.signal,
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
    });
    const volumeSeries = chart.addSeries(LWC.HistogramSeries, {
        priceFormat: { type: 'volume' },
        priceLineVisible: false,
        lastValueVisible: false,
    }, 1);
    const markerApi = LWC.createSeriesMarkers(candleSeries, [], { autoScale: true });

    const panes = chart.panes();
    if (panes.length >= 2) {
        panes[0].setStretchFactor(4);
        panes[1].setStretchFactor(1);
    }
    volumeSeries.priceScale().applyOptions({
        scaleMargins: { top: 0.08, bottom: 0 },
        borderVisible: true,
        borderColor: colors.line,
    });

    let previousPayload = null;
    let averagePriceLine = null;
    let barByTime = new Map();
    let priceFormatter = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 });
    let volumeFormatter = new Intl.NumberFormat('ko-KR', { notation: 'compact', maximumFractionDigits: 1 });
    let axisTimeFormatter = value => String(value);
    let legendTimeFormatter = value => String(value);

    function decodePayload(encoded) {
        const bytes = Uint8Array.from(atob(encoded), character => character.charCodeAt(0));
        return JSON.parse(new TextDecoder('utf-8').decode(bytes));
    }

    function sameBar(left, right) {
        return left.time === right.time &&
            left.open === right.open &&
            left.high === right.high &&
            left.low === right.low &&
            left.close === right.close &&
            left.volume === right.volume &&
            left.volumeText === right.volumeText;
    }

    function sameBars(left, right, endExclusive) {
        for (let index = 0; index < endExclusive; index += 1) {
            if (!sameBar(left[index], right[index])) return false;
        }
        return true;
    }

    function changeKind(previous, next) {
        if (!previous || previous.instrumentId !== next.instrumentId || previous.resolution !== next.resolution) {
            return 'replace';
        }
        const before = previous.bars;
        const after = next.bars;
        if (before.length === after.length && before.length > 0) {
            if (sameBars(before, after, before.length - 1)) {
                if (before[before.length - 1].time !== after[after.length - 1].time) return 'replace';
                return sameBar(before[before.length - 1], after[after.length - 1]) ? 'none' : 'update';
            }
            return 'replace';
        }
        if (after.length === before.length + 1 && sameBars(before, after, before.length)) {
            return 'update';
        }
        return 'replace';
    }

    function movingAverage(bars, period) {
        const result = [];
        let sum = 0;
        for (let index = 0; index < bars.length; index += 1) {
            sum += bars[index].close;
            if (index >= period) sum -= bars[index - period].close;
            if (index + 1 >= period) result.push({ time: bars[index].time, value: sum / period });
        }
        return result;
    }

    function candleData(bar) {
        return { time: bar.time, open: bar.open, high: bar.high, low: bar.low, close: bar.close };
    }

    function volumeData(bar) {
        return {
            time: bar.time,
            value: bar.volume,
            color: bar.close >= bar.open ? 'rgba(227, 77, 91, 0.38)' : 'rgba(47, 115, 210, 0.38)',
        };
    }

    function updateSeries(payload, kind) {
        const bars = payload.bars;
        const ma5 = movingAverage(bars, 5);
        const ma20 = movingAverage(bars, 20);
        if (kind === 'replace') {
            candleSeries.setData(bars.map(candleData));
            volumeSeries.setData(bars.map(volumeData));
            ma5Series.setData(ma5);
            ma20Series.setData(ma20);
            return;
        }
        if (kind !== 'update' || bars.length === 0) return;
        const last = bars[bars.length - 1];
        candleSeries.update(candleData(last));
        volumeSeries.update(volumeData(last));
        if (ma5.length > 0) ma5Series.update(ma5[ma5.length - 1]);
        if (ma20.length > 0) ma20Series.update(ma20[ma20.length - 1]);
    }

    function configureFormatters(payload) {
        priceFormatter = new Intl.NumberFormat('ko-KR', {
            minimumFractionDigits: payload.pricePrecision,
            maximumFractionDigits: payload.pricePrecision,
        });
        volumeFormatter = new Intl.NumberFormat('ko-KR', {
            notation: 'compact',
            maximumFractionDigits: 1,
        });
        const isIntraday = payload.resolution === '1H';
        const axisOptions = isIntraday
            ? { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }
            : { year: '2-digit', month: '2-digit', day: '2-digit' };
        const legendOptions = isIntraday
            ? { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }
            : { year: 'numeric', month: '2-digit', day: '2-digit' };
        const axisIntl = new Intl.DateTimeFormat('ko-KR', { ...axisOptions, timeZone: payload.timeZone });
        const legendIntl = new Intl.DateTimeFormat('ko-KR', { ...legendOptions, timeZone: payload.timeZone });
        axisTimeFormatter = time => axisIntl.format(new Date(Number(time) * 1000));
        legendTimeFormatter = time => legendIntl.format(new Date(Number(time) * 1000));
        chart.applyOptions({
            localization: {
                locale: 'ko-KR',
                priceFormatter: price => priceFormatter.format(price),
                timeFormatter: legendTimeFormatter,
            },
            timeScale: {
                timeVisible: isIntraday,
                tickMarkFormatter: axisTimeFormatter,
            },
        });
        const minMove = payload.priceMinMove;
        candleSeries.applyOptions({
            priceFormat: { type: 'price', precision: payload.pricePrecision, minMove },
        });
        ma5Series.applyOptions({ priceFormat: { type: 'price', precision: payload.pricePrecision, minMove } });
        ma20Series.applyOptions({ priceFormat: { type: 'price', precision: payload.pricePrecision, minMove } });
    }

    function updateAveragePrice(payload) {
        if (averagePriceLine !== null) {
            candleSeries.removePriceLine(averagePriceLine);
            averagePriceLine = null;
        }
        if (payload.averagePrice === null || payload.averagePrice === undefined) return;
        averagePriceLine = candleSeries.createPriceLine({
            price: payload.averagePrice,
            color: colors.signal,
            lineWidth: 1,
            lineStyle: LWC.LineStyle.Dashed,
            axisLabelVisible: true,
            title: '평균단가',
        });
    }

    function setLegend(bar) {
        if (!bar) {
            timeLabel.textContent = '데이터 없음';
            openLabel.textContent = '-';
            highLabel.textContent = '-';
            lowLabel.textContent = '-';
            closeLabel.textContent = '-';
            changeLabel.textContent = '-';
            volumeLabel.textContent = '-';
            ma5Label.textContent = '-';
            ma20Label.textContent = '-';
            return;
        }
        const change = bar.close - bar.open;
        const rate = bar.open === 0 ? 0 : change / bar.open * 100;
        const sign = change > 0 ? '+' : '';
        timeLabel.textContent = legendTimeFormatter(bar.time);
        openLabel.textContent = priceFormatter.format(bar.open);
        highLabel.textContent = priceFormatter.format(bar.high);
        lowLabel.textContent = priceFormatter.format(bar.low);
        closeLabel.textContent = priceFormatter.format(bar.close);
        changeLabel.textContent = `${sign}${priceFormatter.format(change)} (${sign}${rate.toFixed(2)}%)`;
        changeLabel.dataset.tone = change > 0 ? 'rise' : change < 0 ? 'fall' : 'flat';
        volumeLabel.textContent = bar.volumeText || volumeFormatter.format(bar.volume);

        const bars = previousPayload?.bars || [];
        const index = bars.findIndex(candidate => candidate.time === bar.time);
        const ma5Start = Math.max(0, index - 4);
        const ma20Start = Math.max(0, index - 19);
        ma5Label.textContent = index >= 4
            ? priceFormatter.format(bars.slice(ma5Start, index + 1).reduce((sum, item) => sum + item.close, 0) / 5)
            : '-';
        ma20Label.textContent = index >= 19
            ? priceFormatter.format(bars.slice(ma20Start, index + 1).reduce((sum, item) => sum + item.close, 0) / 20)
            : '-';
    }

    function applyViewport(previous, payload) {
        const identityChanged = !previous ||
            previous.instrumentId !== payload.instrumentId ||
            previous.resolution !== payload.resolution;
        const rangeChanged = !previous || previous.rangeKey !== payload.rangeKey;
        if (!identityChanged && !rangeChanged) return;
        if (payload.bars.length === 0) return;
        if (payload.visibleFrom === null || payload.visibleFrom === undefined) {
            chart.timeScale().fitContent();
            return;
        }
        chart.timeScale().setVisibleRange({
            from: payload.visibleFrom,
            to: payload.bars[payload.bars.length - 1].time,
        });
    }

    function receiveBase64(encoded) {
        try {
            const payload = decodePayload(encoded);
            const before = previousPayload;
            const kind = changeKind(before, payload);
            configureFormatters(payload);
            updateSeries(payload, kind);
            updateAveragePrice(payload);
            markerApi.setMarkers(payload.markers || []);
            previousPayload = payload;
            barByTime = new Map(payload.bars.map(bar => [bar.time, bar]));
            symbolLabel.textContent = `${payload.symbol} · ${payload.resolution}`;
            emptyState.hidden = payload.bars.length > 0;
            setLegend(payload.bars[payload.bars.length - 1]);
            applyViewport(before, payload);
            return true;
        } catch (error) {
            emptyState.hidden = false;
            emptyState.textContent = '차트 데이터를 표시하지 못했습니다.';
            window.__MARKET_LEDGER_CHART_ERROR__ = String(error);
            return false;
        }
    }

    chart.subscribeCrosshairMove(parameter => {
        if (!parameter.time || !parameter.point) {
            setLegend(previousPayload?.bars?.[previousPayload.bars.length - 1]);
            return;
        }
        setLegend(barByTime.get(Number(parameter.time)));
    });

    chart.subscribeClick(parameter => {
        const objectId = parameter.hoveredInfo?.objectId ?? parameter.hoveredObjectId;
        if (typeof objectId !== 'string' || !objectId.startsWith('event:')) return;
        const eventId = objectId.slice('event:'.length);
        if (!eventId || !window.kmpJsBridge) return;
        window.kmpJsBridge.callNative('openChartEvent', { id: eventId }, () => {});
    });

    container.addEventListener('mouseleave', () => {
        setLegend(previousPayload?.bars?.[previousPayload.bars.length - 1]);
    });
    container.addEventListener('dblclick', () => {
        chart.timeScale().fitContent();
        if (window.kmpJsBridge) {
            window.kmpJsBridge.callNative('showAllChartData', {}, () => {});
        }
    });

    window.marketLedgerChart = {
        receiveBase64,
        fitContent: () => chart.timeScale().fitContent(),
    };
    const pending = window.__MARKET_LEDGER_PENDING_PAYLOAD__;
    if (typeof pending === 'string' && pending.length > 0) receiveBase64(pending);
    document.title = 'market-ledger-chart-ready';
})();
