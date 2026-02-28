/* ─── SplitEase Client Utilities ─────────────────────────────────────────── */

/**
 * GET request with JWT auth header.
 */
async function apiGet(url) {
    const token = localStorage.getItem('jwt');
    const res = await fetch(url, {
        headers: { 'Authorization': token ? `Bearer ${token}` : '' }
    });
    if (res.status === 401) { redirectToLogin(); return; }
    if (!res.ok) throw new Error(`GET ${url} failed: ${res.status}`);
    return res.json();
}

/**
 * POST request with JWT auth header.
 */
async function apiPost(url, body) {
    const token = localStorage.getItem('jwt');
    return fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify(body)
    });
}

/**
 * DELETE request with JWT auth header.
 */
async function apiDelete(url) {
    const token = localStorage.getItem('jwt');
    return fetch(url, {
        method: 'DELETE',
        headers: { 'Authorization': token ? `Bearer ${token}` : '' }
    });
}

/**
 * PATCH request with JWT auth header.
 */
async function apiPatch(url, body = {}) {
    const token = localStorage.getItem('jwt');
    return fetch(url, {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify(body)
    });
}

function redirectToLogin() {
    localStorage.clear();
    document.cookie = 'jwt=; Max-Age=0; path=/';
    window.location.href = '/login';
}

/* ─── Token refresh ───────────────────────────────────────────────────────── */
async function tryRefreshToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) { redirectToLogin(); return; }

    try {
        const res = await fetch(`/api/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`, {
            method: 'POST'
        });
        if (!res.ok) { redirectToLogin(); return; }
        const data = await res.json();
        localStorage.setItem('jwt', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        document.cookie = `jwt=${data.accessToken}; path=/; SameSite=Strict`;
    } catch {
        redirectToLogin();
    }
}

/* ─── Toast notifications ─────────────────────────────────────────────────── */
function showToast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = 'toast-message';
    toast.style.borderLeft = `4px solid ${type === 'success' ? '#2dc653' : type === 'error' ? '#ef233c' : '#4361ee'}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
}

/* ─── Format currency ─────────────────────────────────────────────────────── */
function formatCurrency(amount, currency = 'USD') {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currency,
        minimumFractionDigits: 2
    }).format(amount);
}

/* ─── Date utilities ──────────────────────────────────────────────────────── */
function formatDate(dateStr) {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric'
    });
}

function todayISO() {
    return new Date().toISOString().split('T')[0];
}

/* ─── Auto-refresh token every 20 minutes ─────────────────────────────────── */
setInterval(tryRefreshToken, 20 * 60 * 1000);
