document.addEventListener('DOMContentLoaded', () => {
	applyStoredTheme();
	applyRememberedEmail();
	document.getElementById('themeToggle').addEventListener('click', toggleTheme);
	document.getElementById('languageSelect').addEventListener('change', changeLanguage);
	document.querySelector('.login-form').addEventListener('submit', handleLoginSubmit);
});

function applyStoredTheme() {
	const stored = localStorage.getItem('sleepstyle-theme');
	const preferred = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
	document.documentElement.dataset.theme = stored || preferred;
}

function toggleTheme() {
	const current = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
	document.documentElement.dataset.theme = current;
	localStorage.setItem('sleepstyle-theme', current);
}

function changeLanguage(event) {
	const url = new URL(window.location.href);
	url.searchParams.set('lang', event.target.value);
	window.location.href = url.toString();
}

function applyRememberedEmail() {
	const savedEmail = localStorage.getItem('sleepstyle-email') || '';
	const remember = localStorage.getItem('sleepstyle-remember-email') === 'true';
	const emailInput = document.getElementById('email');
	const checkbox = document.getElementById('rememberEmail');
	checkbox.checked = remember;
	if (remember && savedEmail && !emailInput.value) {
		emailInput.value = savedEmail;
	}
}

function saveEmailPreference() {
	const emailInput = document.getElementById('email');
	const checkbox = document.getElementById('rememberEmail');
	if (checkbox.checked) {
		localStorage.setItem('sleepstyle-remember-email', 'true');
		localStorage.setItem('sleepstyle-email', emailInput.value || '');
	}
	else {
		localStorage.removeItem('sleepstyle-remember-email');
		localStorage.removeItem('sleepstyle-email');
	}
}

function handleLoginSubmit() {
	saveEmailPreference();
	const button = document.getElementById('loginSubmitButton');
	const label = button.querySelector('.button-label');
	button.classList.add('is-loading');
	button.disabled = true;
	label.textContent = document.body.dataset.loginLoading || label.textContent;
}
