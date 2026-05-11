const state = {
	charts: {},
	metrics: [],
	response: null,
	product: null
};

const labels = document.body.dataset;
const locale = document.documentElement.lang || 'en';

document.addEventListener('DOMContentLoaded', () => {
	applyStoredTheme();
	applyTherapyDateDefaults();
	registerChartPlugins();
	document.getElementById('themeToggle').addEventListener('click', toggleTheme);
	document.getElementById('languageSelect').addEventListener('change', changeLanguage);
	document.getElementById('refreshButton').addEventListener('click', loadDashboard);
	document.getElementById('syncHintButton').addEventListener('click', toggleSyncHint);
	document.querySelectorAll('.tab-button').forEach((button) => button.addEventListener('click', activateTab));
	loadDashboard();
});

async function loadDashboard() {
	await Promise.all([loadProduct(), loadMetrics()]);
	renderProduct();
}

async function loadProduct() {
	try {
		const response = await fetch('/api/product');
		if (!response.ok) {
			if (response.status === 401) {
				window.location.href = '/auth/login';
				return;
			}
			throw new Error(`HTTP ${response.status}`);
		}
		state.product = await response.json();
	}
	catch (error) {
		console.error(error);
	}
}

async function loadMetrics() {
	const since = document.getElementById('sinceInput').value;
	const endDate = document.getElementById('endDateInput').value;
	const query = new URLSearchParams({ since, endDate });

	try {
		const response = await fetch(`/api/metrics?${query.toString()}`);
		if (!response.ok) {
			if (response.status === 401) {
				window.location.href = '/auth/login';
				return;
			}
			throw new Error(await errorMessage(response));
		}
		state.response = await response.json();
		state.metrics = state.response.metrics || [];
		renderDashboard();
	}
	catch (error) {
		const sourceBadge = document.getElementById('sourceBadge');
		sourceBadge.textContent = labels.error;
		sourceBadge.title = error.message;
		console.error(error);
	}
}

async function errorMessage(response) {
	try {
		const body = await response.json();
		return body.message || `HTTP ${response.status}`;
	}
	catch (error) {
		return `HTTP ${response.status}`;
	}
}

function renderProduct() {
	const since = document.getElementById('sinceInput').value;
	const product = state.product || {};
	document.getElementById('deviceModel').textContent = product.modelName || 'SleepStyle Auto';
	document.getElementById('therapyStart').textContent = product.startDate ? formatLongDate(product.startDate) : formatLongDate(since);
}

function renderDashboard() {
	const summary = state.response.summary;
	const score = therapyScore(summary);
	document.getElementById('avgAhi').textContent = fixed(summary.averageAhi);
	document.getElementById('avgHours').textContent = `${fixed(summary.averageHours)}h`;
	document.getElementById('avgLeak').textContent = `${fixed(summary.averageLeak)}`;
	document.getElementById('compliance').textContent = `${fixed(summary.complianceRate)}%`;
	document.getElementById('highLeakDays').textContent = summary.highLeakDays;
	document.getElementById('lowUsageDays').textContent = summary.lowUsageDays;
	document.getElementById('scoreValue').textContent = score;
	document.getElementById('scoreRing').style.setProperty('--meter', `${score}%`);
	document.getElementById('ahiRing').style.setProperty('--meter', `${Math.max(0, 100 - Math.min(summary.averageAhi * 10, 100))}%`);
	document.getElementById('leakBar').style.width = `${Math.min(summary.averageLeak * 1.6, 100)}%`;

	document.getElementById('sourceBadge').textContent = `${labels.labelSource}: ${labels.sourceApi}`;

	renderTable();
	renderNhisCompliance();
	renderCharts();
	renderProduct();
	resizeCharts();
}

function renderCharts() {
	if (typeof Chart === 'undefined') {
		throw new Error('Chart.js is not loaded.');
	}
	Object.values(state.charts).forEach((chart) => chart.destroy());
	state.charts = {};

	const metrics = state.metrics;
	const dates = metrics.map((item) => formatDate(metricDate(item)));
	const colors = getChartColors();
	const compliant = metrics.filter((item) => metricHours(item) >= 4 && !metricLowUsage(item)).length;
	const notCompliant = Math.max(metrics.length - compliant, 0);
	const scores = metrics.map((item) => nightlyScore(item));

	state.charts.hours = new Chart(document.getElementById('hoursChart'), {
		type: 'bar',
		data: {
			labels: dates,
			datasets: [
				{
					label: labels.labelHours,
					data: metrics.map((item) => metricHours(item)),
					backgroundColor: metrics.map((item) => metricHours(item) >= 4 ? colors.blue : colors.gold),
					borderRadius: 4,
					maxBarThickness: 26
				}
			]
		},
		options: chartOptions({ scales: { y: axisOptions(colors, true) } })
	});

	state.charts.trend = new Chart(document.getElementById('trendChart'), {
		type: 'bar',
		data: {
			labels: dates,
			datasets: [
				{
					label: labels.labelAhi,
					data: metrics.map((item) => metricAhi(item)),
					backgroundColor: colors.mint,
					borderRadius: 4,
					maxBarThickness: 22
				},
				{
					label: labels.labelLeak,
					data: metrics.map((item) => metricLeak(item)),
					type: 'line',
					borderColor: colors.coral,
					backgroundColor: colors.coralSoft,
					tension: 0.38,
					pointRadius: 2,
					yAxisID: 'y1'
				}
			]
		},
		options: chartOptions({
			scales: {
				y: axisOptions(colors, true),
				y1: { ...axisOptions(colors, true), position: 'right', grid: { drawOnChartArea: false } }
			}
		})
	});

	state.charts.leak = new Chart(document.getElementById('leakChart'), {
		type: 'line',
		data: {
			labels: dates,
			datasets: [
				{
					label: labels.labelLeak,
					data: metrics.map((item) => metricLeak(item)),
					borderColor: colors.coral,
					backgroundColor: gradient('leakChart', colors.coral),
					fill: true,
					tension: 0.42,
					pointRadius: 2
				}
			]
		},
		options: chartOptions({ scales: { y: axisOptions(colors, true) } })
	});

	state.charts.score = new Chart(document.getElementById('scoreChart'), {
		type: 'bar',
		data: {
			labels: dates,
			datasets: [
				{
					label: labels.labelScore,
					data: scores,
					backgroundColor: scores.map((score) => score >= 70 ? colors.mint : score >= 50 ? colors.gold : colors.coral),
					borderRadius: 4,
					maxBarThickness: 24
				}
			]
		},
		options: chartOptions({ scales: { y: { ...axisOptions(colors, true), max: 100 } } })
	});

	state.charts.humidity = new Chart(document.getElementById('humidityChart'), {
		type: 'line',
		data: {
			labels: dates,
			datasets: [
				{
					label: labels.labelHumidity,
					data: metrics.map((item) => metricHumidity(item)),
					borderColor: colors.blue,
					backgroundColor: gradient('humidityChart', colors.blue),
					fill: true,
					tension: 0.34,
					pointRadius: 3
				}
			]
		},
		options: chartOptions({ scales: { y: axisOptions(colors, true) } })
	});

	state.charts.compliance = new Chart(document.getElementById('complianceChart'), {
		type: 'doughnut',
		data: {
			labels: [labels.labelCompliant, labels.labelNotCompliant],
			datasets: [
				{
					data: [compliant, notCompliant],
					backgroundColor: [colors.blue, colors.coral],
					borderColor: colors.panel,
					borderWidth: 5,
					hoverOffset: 4
				}
			]
		},
		options: chartOptions({ cutout: '66%' })
	});
}

function renderTable() {
	const body = document.getElementById('metricsBody');
	body.replaceChildren();

	if (state.metrics.length === 0) {
		const row = document.createElement('tr');
		const cell = document.createElement('td');
		cell.colSpan = 5;
		cell.textContent = labels.empty;
		row.append(cell);
		body.append(row);
		return;
	}

	for (const metric of state.metrics.slice(-8).reverse()) {
		const row = document.createElement('tr');
		row.append(
			cell(formatDate(metricDate(metric))),
			cell(`${fixed(metricHours(metric))}h`),
			cell(fixed(metricAhi(metric))),
			cell(fixed(metricLeak(metric))),
			statusCell(metric)
		);
		body.append(row);
	}
}

function renderNhisCompliance() {
	const result = nhisCompliance();
	const status = document.getElementById('nhisStatus');
	const progressBar = document.getElementById('nhisProgressBar');
	status.className = result.passed ? 'pill pill-good' : 'pill pill-warn';
	status.textContent = result.passed ? labels.labelNhisPass : (result.finished ? labels.labelNhisFail : labels.labelNhisProgress);
	document.getElementById('nhisPeriod').textContent = `${formatDate(result.periodStart)} - ${formatDate(result.periodEnd)}`;
	document.getElementById('nhisWindow').textContent = `${formatDate(result.windowStart)} - ${formatDate(result.windowEnd)}`;
	document.getElementById('nhisQualified').textContent = `${result.qualifiedDays} / 21`;
	progressBar.style.width = `${Math.min(result.qualifiedDays / 21 * 100, 100)}%`;

}

function nhisCompliance() {
	const periodStartValue = state.product?.startDate || metricDate(state.metrics[0] || {}) || document.getElementById('sinceInput').value;
	const periodStart = parseLocalDate(periodStartValue);
	const periodEnd = addDays(periodStart, 89);
	const today = currentTherapyDate();
	const finished = today > periodEnd;
	const usageByDate = new Map();
	for (const metric of state.metrics) {
		const date = metricDate(metric);
		if (!date) {
			continue;
		}
		const key = toDateInputValue(parseLocalDate(date));
		usageByDate.set(key, Math.max(usageByDate.get(key) || 0, metricHours(metric)));
	}

	let best = {
		qualifiedDays: 0,
		windowStart: periodStart,
		windowEnd: addDays(periodStart, 29)
	};
	for (let offset = 0; offset <= 60; offset++) {
		const windowStart = addDays(periodStart, offset);
		const windowEnd = addDays(windowStart, 29);
		const qualifiedDays = qualifiedDaysInWindow(windowStart, windowEnd, usageByDate);
		if (qualifiedDays > best.qualifiedDays) {
			best = { qualifiedDays, windowStart, windowEnd };
		}
	}

	return {
		periodStart,
		periodEnd,
		windowStart: best.windowStart,
		windowEnd: best.windowEnd,
		qualifiedDays: best.qualifiedDays,
		passed: best.qualifiedDays >= 21,
		finished
	};
}

function qualifiedDaysInWindow(start, end, usageByDate) {
	let count = 0;
	for (let cursor = new Date(start); cursor <= end; cursor = addDays(cursor, 1)) {
		const key = toDateInputValue(cursor);
		if ((usageByDate.get(key) || 0) >= 4) {
			count++;
		}
	}
	return count;
}

function statusCell(metric) {
	const wrapper = document.createElement('td');
	const pill = document.createElement('span');
	const risky = metricHighLeak(metric) || metricLowUsage(metric) || metricAhi(metric) >= 5;
	pill.className = risky ? 'pill pill-warn' : 'pill pill-good';
	pill.textContent = risky ? labels.labelNotCompliant : labels.labelCompliant;
	wrapper.append(pill);
	return wrapper;
}

function cell(value) {
	const element = document.createElement('td');
	element.textContent = value;
	return element;
}

function therapyScore(summary) {
	const ahiScore = Math.max(0, 35 - summary.averageAhi * 2.5);
	const usageScore = Math.min(35, summary.averageHours / 7 * 35);
	const leakScore = Math.max(0, 20 - Math.max(0, summary.averageLeak - 24) * 0.7);
	const complianceScore = summary.complianceRate / 100 * 10;
	return Math.round(Math.max(0, Math.min(100, ahiScore + usageScore + leakScore + complianceScore)));
}

function nightlyScore(metric) {
	const summary = {
		averageAhi: metricAhi(metric),
		averageHours: metricHours(metric),
		averageLeak: metricLeak(metric),
		complianceRate: metricHours(metric) >= 4 && !metricLowUsage(metric) ? 100 : 0
	};
	return therapyScore(summary);
}

function metricDate(metric) {
	return metric.date ?? metric.Date;
}

function metricAhi(metric) {
	return Number(metric.aveAhi ?? metric.AveAhi ?? 0);
}

function metricLeak(metric) {
	return Number(metric.averageLeak ?? metric.AverageLeak ?? 0);
}

function metricHours(metric) {
	return Number(metric.totalHours ?? metric.TotalHours ?? 0);
}

function metricHumidity(metric) {
	return Number(metric.humidity ?? metric.Humidity ?? 0);
}

function metricHighLeak(metric) {
	return Boolean(metric.highLeak ?? metric.IsHighLeak);
}

function metricLowUsage(metric) {
	return Boolean(metric.lowCompliedTime ?? metric.IsLowCompliedTime);
}

function chartOptions(overrides = {}) {
	const colors = getChartColors();
	return {
		responsive: true,
		maintainAspectRatio: false,
		plugins: {
			legend: {
				position: 'bottom',
				labels: {
					color: colors.muted,
					usePointStyle: true,
					boxWidth: 8,
					padding: 12
				}
			},
			tooltip: {
				backgroundColor: colors.tooltip,
				titleColor: colors.text,
				bodyColor: colors.text,
				borderColor: colors.line,
				borderWidth: 1,
				padding: 10
			},
			valueLabels: {
				color: colors.text,
				backgroundColor: colors.valueLabelBackground,
				borderColor: colors.line
			}
		},
		scales: {
			x: axisOptions(colors, false)
		},
		...overrides
	};
}

function axisOptions(colors, beginAtZero) {
	return {
		beginAtZero,
		ticks: { color: colors.muted, maxTicksLimit: 6 },
		grid: { color: colors.line, borderDash: [2, 4] }
	};
}

function getChartColors() {
	const styles = getComputedStyle(document.documentElement);
	return {
		blue: styles.getPropertyValue('--blue').trim(),
		mint: styles.getPropertyValue('--mint').trim(),
		coral: styles.getPropertyValue('--coral').trim(),
		gold: styles.getPropertyValue('--gold').trim(),
		text: styles.getPropertyValue('--text').trim(),
		muted: styles.getPropertyValue('--muted').trim(),
		line: styles.getPropertyValue('--line').trim(),
		panel: styles.getPropertyValue('--panel-strong').trim(),
		tooltip: styles.getPropertyValue('--panel-strong').trim(),
		valueLabelBackground: styles.getPropertyValue('--value-label-bg').trim(),
		coralSoft: 'rgba(239, 107, 93, 0.18)'
	};
}

function registerChartPlugins() {
	if (typeof Chart === 'undefined' || Chart.sleepStyleValueLabelsRegistered) {
		return;
	}
	Chart.register({
		id: 'valueLabels',
		afterDatasetsDraw(chart, args, options) {
			const { ctx } = chart;
			ctx.save();
			ctx.font = '700 11px Inter, system-ui, sans-serif';
			ctx.textAlign = 'center';
			ctx.textBaseline = 'middle';

			chart.data.datasets.forEach((dataset, datasetIndex) => {
				const type = dataset.type || chart.config.type;
				if (!['bar', 'line'].includes(type)) {
					return;
				}
				const meta = chart.getDatasetMeta(datasetIndex);
				if (meta.hidden) {
					return;
				}
				meta.data.forEach((element, index) => {
					const value = Number(dataset.data[index]);
					if (!Number.isFinite(value)) {
						return;
					}
					const position = element.tooltipPosition();
					const label = compactValue(value);
					const width = ctx.measureText(label).width + 10;
					const height = 18;
					const y = Math.max(10, position.y - (type === 'bar' ? 14 : 18));
					const x = Math.min(Math.max(position.x, width / 2), chart.width - width / 2);
					roundedRect(ctx, x - width / 2, y - height / 2, width, height, 7);
					ctx.fillStyle = options.backgroundColor || 'rgba(0, 0, 0, 0.58)';
					ctx.fill();
					ctx.strokeStyle = options.borderColor || 'rgba(255, 255, 255, 0.14)';
					ctx.stroke();
					ctx.fillStyle = options.color || '#ffffff';
					ctx.fillText(label, x, y + 0.5);
				});
			});
			ctx.restore();
		}
	});
	Chart.sleepStyleValueLabelsRegistered = true;
}

function compactValue(value) {
	return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function roundedRect(context, x, y, width, height, radius) {
	const safeRadius = Math.min(radius, width / 2, height / 2);
	context.beginPath();
	context.moveTo(x + safeRadius, y);
	context.lineTo(x + width - safeRadius, y);
	context.quadraticCurveTo(x + width, y, x + width, y + safeRadius);
	context.lineTo(x + width, y + height - safeRadius);
	context.quadraticCurveTo(x + width, y + height, x + width - safeRadius, y + height);
	context.lineTo(x + safeRadius, y + height);
	context.quadraticCurveTo(x, y + height, x, y + height - safeRadius);
	context.lineTo(x, y + safeRadius);
	context.quadraticCurveTo(x, y, x + safeRadius, y);
	context.closePath();
}

function gradient(canvasId, color) {
	const canvas = document.getElementById(canvasId);
	const context = canvas.getContext('2d');
	const gradientFill = context.createLinearGradient(0, 0, 0, canvas.offsetHeight || 220);
	gradientFill.addColorStop(0, hexToRgba(color, 0.26));
	gradientFill.addColorStop(1, hexToRgba(color, 0));
	return gradientFill;
}

function hexToRgba(color, alpha) {
	if (!color.startsWith('#')) {
		return color;
	}
	const value = color.replace('#', '');
	const bigint = parseInt(value.length === 3 ? value.split('').map((x) => x + x).join('') : value, 16);
	const red = (bigint >> 16) & 255;
	const green = (bigint >> 8) & 255;
	const blue = bigint & 255;
	return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

function applyStoredTheme() {
	const stored = localStorage.getItem('sleepstyle-theme');
	const preferred = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
	document.documentElement.dataset.theme = stored || preferred;
}

function toggleTheme() {
	const current = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
	document.documentElement.dataset.theme = current;
	localStorage.setItem('sleepstyle-theme', current);
	if (state.metrics.length > 0) {
		renderCharts();
	}
}

function changeLanguage(event) {
	const url = new URL(window.location.href);
	url.searchParams.set('lang', event.target.value);
	window.location.href = url.toString();
}

function activateTab(event) {
	const target = event.currentTarget.dataset.tabTarget;
	document.querySelectorAll('.tab-button').forEach((button) => {
		button.classList.toggle('is-active', button.dataset.tabTarget === target);
	});
	const dashboard = document.getElementById('dashboardConsole');
	dashboard.className = `dashboard-console view-${target}`;
	document.querySelectorAll('[data-dashboard-section]').forEach((element) => {
		const sections = element.dataset.dashboardSection.split(' ');
		element.classList.toggle('is-hidden', target !== 'all' && !sections.includes(target));
	});
	resizeCharts();
}

function toggleSyncHint() {
	const message = document.getElementById('syncHintMessage');
	message.hidden = !message.hidden;
}

function resizeCharts() {
	window.requestAnimationFrame(() => {
		Object.values(state.charts).forEach((chart) => chart.resize());
	});
}

function applyTherapyDateDefaults() {
	const endDateInput = document.getElementById('endDateInput');
	const sinceInput = document.getElementById('sinceInput');
	const endDate = currentTherapyDate();
	const since = addDays(endDate, -14);
	endDateInput.value = toDateInputValue(endDate);
	sinceInput.value = toDateInputValue(since);
}

function currentTherapyDate() {
	const now = new Date();
	const therapyDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
	if (now.getHours() < 12) {
		therapyDate.setDate(therapyDate.getDate() - 1);
	}
	return therapyDate;
}

function addDays(value, days) {
	const date = new Date(value);
	date.setDate(date.getDate() + days);
	return date;
}

function parseLocalDate(value) {
	if (!value) {
		return currentTherapyDate();
	}
	const date = value instanceof Date ? new Date(value) : new Date(`${value}T00:00:00`);
	return Number.isNaN(date.getTime()) ? currentTherapyDate() : date;
}

function toDateInputValue(value) {
	if (!(value instanceof Date) || Number.isNaN(value.getTime())) {
		return toDateInputValue(currentTherapyDate());
	}
	const year = value.getFullYear();
	const month = String(value.getMonth() + 1).padStart(2, '0');
	const day = String(value.getDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
}

function formatDate(value) {
	if (!value) {
		return '-';
	}
	return new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' }).format(parseLocalDate(value));
}

function formatLongDate(value) {
	if (!value) {
		return '-';
	}
	return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' }).format(parseLocalDate(value));
}

function fixed(value) {
	return Number(value || 0).toFixed(1);
}
