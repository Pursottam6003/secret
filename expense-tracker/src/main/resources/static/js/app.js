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

document.addEventListener("DOMContentLoaded", () => {

    loadExpenses(GROUP_ID);

    document.getElementById("expDate").value =
        new Date().toISOString().split("T")[0];

    connectWebSocket(GROUP_ID);

    // Initialize split UI
    updateSplitInputs();
});
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


// ==========================================================
// Add Expense
// ==========================================================

async function addExpense(groupId) {

    const errEl = document.getElementById("expError");
    const btn = document.querySelector("#addExpenseModal .modal-footer .btn-primary");

    errEl.classList.add("d-none");
    errEl.textContent = "";

    //--------------------------------------------------
    // Read Form Values
    //--------------------------------------------------

    const description = document.getElementById("expDesc").value.trim();

    const amount =
        parseFloat(document.getElementById("expAmount").value);

    const paidByUserId =
        parseInt(document.getElementById("expPaidBy").value);

    const splitType =
        document.getElementById("expSplitType").value;

    const category =
        document.getElementById("expCategory").value;

    const expenseDate =
        document.getElementById("expDate").value ||
        new Date().toISOString().split("T")[0];

    const notes =
        document.getElementById("expNotes").value;

    //--------------------------------------------------
    // Validation
    //--------------------------------------------------

    if (!description) {

        errEl.textContent = "Description is required.";
        errEl.classList.remove("d-none");
        return;
    }

    if (!amount || amount <= 0) {

        errEl.textContent = "Enter a valid amount.";
        errEl.classList.remove("d-none");
        return;
    }

    //--------------------------------------------------
    // Build Split Payload
    //--------------------------------------------------

    const splitPayload = buildSplitPayload();

    if (!splitPayload) {
        return;
    }

    //--------------------------------------------------
    // Final Payload
    //--------------------------------------------------

    const payload = {

        description: description,

        amount: amount,

        currency: "USD",

        groupId: parseInt(groupId),

        paidByUserId: paidByUserId,

        splitType: splitType,

        category: category,

        expenseDate: expenseDate,

        notes: notes,

        splitDetails: splitPayload.splitDetails,

        participantIds: splitPayload.participantIds
    };

    //--------------------------------------------------
    // DEBUG
    //--------------------------------------------------

    console.log("Expense Payload");
    console.log(payload);

    console.log(
        JSON.stringify(payload, null, 2)
    );

    //--------------------------------------------------
    // Submit
    //--------------------------------------------------

    btn.disabled = true;

    btn.innerHTML =
        '<span class="spinner-border spinner-border-sm me-1"></span> Saving...';

    try {

        const response =
            await apiPost("/api/expenses", payload);

        if (!response.ok) {

            const error =
                await response.json().catch(() => ({}));

            errEl.textContent =
                error.detail ||
                error.message ||
                "Failed to create expense.";

            errEl.classList.remove("d-none");

            return;
        }

        bootstrap.Modal
            .getInstance(document.getElementById("addExpenseModal"))
            ?.hide();

        resetExpenseForm();

        bootstrap.Modal
            .getInstance(document.getElementById("addExpenseModal"))
            ?.hide();

        window.location.reload();

    }
    catch (e) {

        console.error(e);

        errEl.textContent =
            "Network error. Please try again.";

        errEl.classList.remove("d-none");

    }
    finally {

        btn.disabled = false;

        btn.innerHTML =
            '<i class="bi bi-plus-circle"></i> Add Expense';

    }

}
// Add this JavaScript to your group-details.html <script> section
// Add this to your group-details.html <script> section
// This version properly extracts members from your page and shows input fields
// ==========================================================
// Expense Split Helpers
// ==========================================================


function getGroupMembers() {
    return Array.from(document.querySelectorAll("#expPaidBy option")).map(option => ({
        id: parseInt(option.value),
        name: option.textContent.trim()
    }));
}

function showSplitValidation(message) {
    const el = document.getElementById("splitValidationMsg");
    el.textContent = message;
    el.classList.remove("d-none");
}

function hideSplitValidation() {
    document.getElementById("splitValidationMsg").classList.add("d-none");
}

// ==========================================================
// Build Dynamic Split Inputs
// ==========================================================

function updateSplitInputs() {

    const splitType = document.getElementById("expSplitType").value;

    const section = document.getElementById("splitInputSection");
    const container = document.getElementById("splitInputs");
    const label = document.getElementById("splitLabel");

    const members = getGroupMembers();

    hideSplitValidation();

    container.innerHTML = "";

    if (splitType === "EQUAL") {

        section.style.display = "block";
        label.textContent = "Select members sharing this expense";

        members.forEach(member => {

            container.innerHTML += `
                <div class="form-check mb-2">
                    <input class="form-check-input member-checkbox"
                           type="checkbox"
                           value="${member.id}"
                           id="member-${member.id}"
                           checked>

                    <label class="form-check-label"
                           for="member-${member.id}">
                        ${member.name}
                    </label>
                </div>
            `;
        });

    }

    else if (splitType === "EXACT") {

        section.style.display = "block";
        label.textContent = "Enter exact amount for each member";

        members.forEach(member => {

            container.innerHTML += `
                <div class="row mb-2 align-items-center">
                    <label class="col-5 col-form-label">
                        ${member.name}
                    </label>

                    <div class="col-7">
                        <input
                            type="number"
                            class="form-control exact-amount"
                            data-user-id="${member.id}"
                            value="0"
                            min="0"
                            step="0.01">
                    </div>
                </div>
            `;
        });

    }

    else if (splitType === "PERCENTAGE") {

        section.style.display = "block";
        label.textContent = "Enter percentage for each member";

        members.forEach(member => {

            container.innerHTML += `
                <div class="row mb-2 align-items-center">
                    <label class="col-5 col-form-label">
                        ${member.name}
                    </label>

                    <div class="col-7">
                        <div class="input-group">

                            <input
                                type="number"
                                class="form-control percentage-input"
                                data-user-id="${member.id}"
                                value="0"
                                min="0"
                                max="100"
                                step="0.01">

                            <span class="input-group-text">%</span>

                        </div>
                    </div>
                </div>
            `;
        });

    }
}

// ==========================================================
// Build Payload
// ==========================================================

function buildSplitPayload() {

    hideSplitValidation();

    const splitType = document.getElementById("expSplitType").value;

    const totalAmount =
        parseFloat(document.getElementById("expAmount").value) || 0;

    let splitDetails = {};
    let participantIds = [];

    //-------------------------------------------------------
    // Equal
    //-------------------------------------------------------

    if (splitType === "EQUAL") {

        document.querySelectorAll(".member-checkbox:checked")
            .forEach(cb => participantIds.push(parseInt(cb.value)));

        if (participantIds.length === 0) {

            showSplitValidation(
                "Please select at least one participant."
            );

            return null;
        }
    }

    //-------------------------------------------------------
    // Exact
    //-------------------------------------------------------

    if (splitType === "EXACT") {

        let total = 0;

        document.querySelectorAll(".exact-amount")
            .forEach(input => {

                const amount =
                    parseFloat(input.value) || 0;

                const userId =
                    parseInt(input.dataset.userId);

                if (amount > 0) {

                    splitDetails[userId] = amount;

                    total += amount;
                }

            });

        if (Object.keys(splitDetails).length === 0) {

            showSplitValidation(
                "Enter at least one amount."
            );

            return null;
        }

        if (Math.abs(total - totalAmount) > 0.01) {

            showSplitValidation(
                `Exact amounts must total ${totalAmount.toFixed(2)}`
            );

            return null;
        }

    }

    //-------------------------------------------------------
    // Percentage
    //-------------------------------------------------------

    if (splitType === "PERCENTAGE") {

        let total = 0;

        document.querySelectorAll(".percentage-input")
            .forEach(input => {

                const pct =
                    parseFloat(input.value) || 0;

                const userId =
                    parseInt(input.dataset.userId);

                if (pct > 0) {

                    splitDetails[userId] = pct;

                    total += pct;
                }

            });

        if (Object.keys(splitDetails).length === 0) {

            showSplitValidation(
                "Enter at least one percentage."
            );

            return null;
        }

        if (Math.abs(total - 100) > 0.01) {

            showSplitValidation(
                "Percentages must total 100%"
            );

            return null;
        }

    }

    return {
        splitDetails,
        participantIds
    };
}
function todayISO() {
    return new Date().toISOString().split('T')[0];
}

// ==========================================================
// Reset Expense Form
// ==========================================================

function resetExpenseForm() {

    document.getElementById("expDesc").value = "";

    document.getElementById("expAmount").value = "";

    document.getElementById("expCategory").value = "OTHER";

    document.getElementById("expSplitType").value = "EQUAL";

    document.getElementById("expNotes").value = "";

    document.getElementById("expDate").value =
        new Date().toISOString().split("T")[0];

    hideSplitValidation();

    document.getElementById("expError").classList.add("d-none");

    updateSplitInputs();
}

/* ─── Auto-refresh token every 20 minutes ─────────────────────────────────── */
setInterval(tryRefreshToken, 20 * 60 * 1000);
