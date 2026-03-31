// =====================================================================
// State
// =====================================================================
let currentRecipeId = null;
let searchResults = [];
let currentApiReceita = null;

// =====================================================================
// Bootstrap Toast helper
// =====================================================================
function showToast(message, type = 'success') {
    const toastEl = document.getElementById('app-toast');
    const toastBody = document.getElementById('toast-body');
    toastBody.textContent = message;
    toastEl.className = `toast align-items-center border-0 text-bg-${type === 'success' ? 'success' : 'danger'}`;
    bootstrap.Toast.getOrCreateInstance(toastEl, { delay: 3000 }).show();
}

// =====================================================================
// View management
// =====================================================================
const VIEWS = ['welcome-view', 'recipe-view', 'create-form', 'search-results'];

function showView(activeId) {
    VIEWS.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.hidden = id !== activeId;
    });
}

function showHome() {
    currentRecipeId = null;
    document.querySelectorAll('.recipe-list-item').forEach(el => el.classList.remove('active'));
    showView('welcome-view');
}

function showCreateForm() {
    showView('create-form');
    document.getElementById('receita-form').reset();
    document.getElementById('form-error').hidden = true;
    document.getElementById('subtipo-group').hidden = true;
    document.getElementById('ingredientes-list').innerHTML = '';
    document.getElementById('passos-list').innerHTML = '';
    addIngrediente();
    addPasso();
}

// =====================================================================
// Recipe list
// =====================================================================
let allReceitas = [];

async function loadRecipes() {
    try {
        const res = await fetch('/api/receitas');
        if (!res.ok) throw new Error();
        allReceitas = await res.json();
        applyFilter();
    } catch {
        document.getElementById('recipe-list').innerHTML =
            '<p class="text-danger small px-2 py-2">Erro ao carregar receitas.</p>';
    }
}

function applyFilter() {
    const tipo = document.getElementById('tipo-filter').value;
    const filtered = tipo ? allReceitas.filter(r => r.tipo === tipo) : allReceitas;
    renderRecipeList(filtered, tipo);
}

function renderRecipeList(receitas, groupByType) {
    const listEl = document.getElementById('recipe-list');
    if (!receitas.length) {
        listEl.innerHTML = '<p class="text-muted small px-2 py-2">Nenhuma receita nesta categoria.</p>';
        return;
    }

    // Group by type when showing all
    if (!groupByType) {
        const groups = {};
        receitas.forEach(r => {
            if (!groups[r.tipo]) groups[r.tipo] = [];
            groups[r.tipo].push(r);
        });

        listEl.innerHTML = Object.entries(groups).map(([tipo, items]) => `
            <div class="mb-1">
                <div class="sidebar-group-header">
                    <span class="tipo-badge ${tipoBadgeClass(tipo)} me-1">${escapeHtml(tipo)}</span>
                    <span class="text-muted" style="font-size:0.7rem">(${items.length})</span>
                </div>
                ${items.map(r => recipeListItem(r)).join('')}
            </div>
        `).join('');
    } else {
        listEl.innerHTML = receitas.map(r => recipeListItem(r)).join('');
    }
}

function recipeListItem(r) {
    return `
        <div class="recipe-list-item ${r.id === currentRecipeId ? 'active' : ''}"
             id="list-item-${escapeAttr(r.id)}"
             onclick="viewRecipe('${escapeAttr(r.id)}')">
            <div class="fw-medium text-truncate">${escapeHtml(r.titulo)}</div>
        </div>
    `;
}

// =====================================================================
// Recipe view
// =====================================================================
async function viewRecipe(id) {
    currentRecipeId = id;
    document.querySelectorAll('.recipe-list-item').forEach(el => el.classList.remove('active'));
    const item = document.getElementById(`list-item-${id}`);
    if (item) item.classList.add('active');

    showView('recipe-view');
    document.getElementById('recipe-view').innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary" role="status"></div>
        </div>`;

    try {
        const res = await fetch(`/api/receitas/${encodeURIComponent(id)}`);
        if (!res.ok) throw new Error();
        const receita = await res.json();
        renderRecipeCard(receita, false);
    } catch {
        document.getElementById('recipe-view').innerHTML =
            '<div class="alert alert-danger m-4">Erro ao carregar a receita.</div>';
    }
}

function renderRecipeCard(receita, isApiResult) {
    currentApiReceita = isApiResult ? receita : null;

    const fotoHtml = receita.fotoUrl
        ? `<img src="${escapeAttr(receita.fotoUrl)}" alt="${escapeAttr(receita.titulo)}"
               class="recipe-hero-img" onerror="this.style.display='none'">`
        : '';

    const subtipoHtml = receita.subtipo
        ? `<span class="badge bg-secondary ms-1">${escapeHtml(receita.subtipo)}</span>` : '';

    const diffClass = difficultyClass(receita.dificuldade);

    const actionsHtml = isApiResult
        ? `<button class="btn btn-success btn-sm" onclick="saveFromApi()">
               <i class="bi bi-bookmark-plus me-1"></i>Guardar na Coleção
           </button>`
        : `<button class="btn btn-outline-danger btn-sm" onclick="confirmDelete('${escapeAttr(receita.id)}')">
               <i class="bi bi-trash me-1"></i>Eliminar
           </button>`;

    const ingredientesHtml = (receita.ingredientes || []).map(ing => `
        <li class="list-group-item d-flex justify-content-between align-items-center">
            <span class="badge bg-light text-dark border">${escapeHtml(ing.quantidade)}</span>
            <span>${escapeHtml(ing.nome)}</span>
        </li>`).join('');

    const passosHtml = (receita.passosPreparacao || []).map((p, i) => `
        <div class="step-row">
            <div class="step-circle">${i + 1}</div>
            <div class="step-text">${escapeHtml(p)}</div>
        </div>`).join('');

    document.getElementById('recipe-view').innerHTML = `
        <div class="recipe-card-container">
            ${fotoHtml}
            <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
                <div>
                    <h2 class="fw-bold mb-1">${escapeHtml(receita.titulo)}</h2>
                    <span class="text-muted"><i class="bi bi-person me-1"></i>${escapeHtml(receita.origem)}</span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                    ${actionsHtml}
                </div>
            </div>

            <div class="recipe-meta-grid mb-4">
                <div class="recipe-meta-item">
                    <div class="meta-icon"><i class="bi bi-tag-fill"></i></div>
                    <div class="meta-label">Tipo</div>
                    <div class="meta-value">
                        <span class="badge ${tipoBadgeClass(receita.tipo)}">${escapeHtml(receita.tipo)}</span>
                        ${subtipoHtml}
                    </div>
                </div>
                <div class="recipe-meta-item">
                    <div class="meta-icon"><i class="bi bi-people-fill"></i></div>
                    <div class="meta-label">Pessoas</div>
                    <div class="meta-value">${receita.numeroPessoas}</div>
                </div>
                <div class="recipe-meta-item">
                    <div class="meta-icon"><i class="bi bi-bar-chart-fill"></i></div>
                    <div class="meta-label">Dificuldade</div>
                    <div class="meta-value">
                        <span class="badge px-2 py-1 ${diffClass}">${escapeHtml(receita.dificuldade)}</span>
                    </div>
                </div>
                <div class="recipe-meta-item">
                    <div class="meta-icon"><i class="bi bi-basket2-fill"></i></div>
                    <div class="meta-label">Ingredientes</div>
                    <div class="meta-value">${(receita.ingredientes || []).length}</div>
                </div>
            </div>

            <h5 class="mb-3"><i class="bi bi-basket me-2 text-primary"></i>Ingredientes</h5>
            <ul class="list-group ingredients-list mb-4">${ingredientesHtml}</ul>

            <h5 class="mb-3"><i class="bi bi-list-check me-2 text-primary"></i>Preparação</h5>
            <div class="mb-4">${passosHtml}</div>
        </div>`;
}

// =====================================================================
// Create recipe form
// =====================================================================
function toggleSubtipo() {
    const tipo = document.getElementById('f-tipo').value;
    const group = document.getElementById('subtipo-group');
    group.hidden = tipo !== 'bebidas';
    if (tipo !== 'bebidas') document.getElementById('f-subtipo').value = '';
}

function addIngrediente() {
    const div = document.createElement('div');
    div.className = 'ingrediente-row row g-2 align-items-center';
    div.innerHTML = `
        <div class="col-3">
            <input type="text" class="form-control form-control-sm ing-qtd"
                   placeholder="Quantidade" required maxlength="100">
        </div>
        <div class="col-7">
            <input type="text" class="form-control form-control-sm ing-nome"
                   placeholder="Ingrediente" required maxlength="200">
        </div>
        <div class="col-2 text-end">
            <button type="button" class="btn btn-outline-danger btn-sm" onclick="removeIngrediente(this)">
                <i class="bi bi-trash"></i>
            </button>
        </div>`;
    document.getElementById('ingredientes-list').appendChild(div);
}

function removeIngrediente(btn) {
    const rows = document.querySelectorAll('.ingrediente-row');
    if (rows.length > 1) btn.closest('.ingrediente-row').remove();
}

function addPasso() {
    const list = document.getElementById('passos-list');
    const num = list.querySelectorAll('.passo-row').length + 1;
    const div = document.createElement('div');
    div.className = 'passo-row d-flex gap-2 align-items-start';
    div.innerHTML = `
        <span class="passo-num text-muted mt-2 fw-semibold" style="min-width:1.4rem">${num}.</span>
        <textarea class="form-control form-control-sm passo-texto"
                  rows="2" placeholder="Descreva o passo..." required maxlength="1000"></textarea>
        <button type="button" class="btn btn-outline-danger btn-sm mt-1" onclick="removePasso(this)">
            <i class="bi bi-trash"></i>
        </button>`;
    list.appendChild(div);
}

function removePasso(btn) {
    const rows = document.querySelectorAll('.passo-row');
    if (rows.length > 1) {
        btn.closest('.passo-row').remove();
        document.querySelectorAll('.passo-num').forEach((s, i) => { s.textContent = `${i + 1}.`; });
    }
}

async function submitForm(e) {
    e.preventDefault();
    const errorDiv = document.getElementById('form-error');
    errorDiv.hidden = true;

    const tipo = document.getElementById('f-tipo').value;
    const subtipo = document.getElementById('f-subtipo').value;

    if (tipo === 'bebidas' && !subtipo) {
        errorDiv.textContent = 'Por favor selecione o subtipo da bebida.';
        errorDiv.hidden = false;
        return;
    }

    const ingredientes = [];
    document.querySelectorAll('.ingrediente-row').forEach(row => {
        const nome = row.querySelector('.ing-nome').value.trim();
        const qtd = row.querySelector('.ing-qtd').value.trim();
        if (nome) ingredientes.push({ nome, quantidade: qtd });
    });

    const passosPreparacao = [];
    document.querySelectorAll('.passo-texto').forEach(ta => {
        const v = ta.value.trim();
        if (v) passosPreparacao.push(v);
    });

    const receita = {
        titulo: document.getElementById('f-titulo').value.trim(),
        origem: document.getElementById('f-origem').value.trim(),
        tipo,
        subtipo: tipo === 'bebidas' ? subtipo : null,
        numeroPessoas: parseInt(document.getElementById('f-pessoas').value, 10),
        dificuldade: document.getElementById('f-dificuldade').value,
        ingredientes,
        passosPreparacao,
        fotoUrl: document.getElementById('f-foto').value.trim() || null
    };

    try {
        const res = await fetch('/api/receitas', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(receita)
        });
        if (!res.ok) {
            const msg = await res.text();
            throw new Error(msg || 'Erro ao criar receita.');
        }
        const created = await res.json();
        showToast('Receita criada com sucesso!');
        await loadRecipes();
        viewRecipe(created.id);
    } catch (err) {
        errorDiv.textContent = err.message;
        errorDiv.hidden = false;
    }
}

// =====================================================================
// Delete recipe
// =====================================================================
function confirmDelete(id) {
    if (confirm('Tem a certeza que deseja eliminar esta receita?')) {
        deleteRecipe(id);
    }
}

async function deleteRecipe(id) {
    try {
        const res = await fetch(`/api/receitas/${encodeURIComponent(id)}`, { method: 'DELETE' });
        if (!res.ok) throw new Error();
        showToast('Receita eliminada.');
        currentRecipeId = null;
        await loadRecipes();
        showHome();
    } catch {
        showToast('Erro ao eliminar a receita.', 'danger');
    }
}

// =====================================================================
// MealDB Search (Bonus)
// =====================================================================
async function performSearch() {
    const query = document.getElementById('search-input').value.trim();
    const errorEl = document.getElementById('search-error');

    if (!query) {
        errorEl.textContent = 'Insira um termo de pesquisa.';
        errorEl.hidden = false;
        return;
    }
    errorEl.hidden = true;

    showView('search-results');
    document.getElementById('search-results-content').innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border text-primary"></div>
            <div class="mt-2 text-muted">A pesquisar em MealDB...</div>
        </div>`;

    try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
        if (!res.ok) throw new Error();
        searchResults = await res.json();
        renderSearchResults(query);
    } catch {
        document.getElementById('search-results-content').innerHTML =
            '<div class="alert alert-danger">Erro ao pesquisar. Verifique a sua ligação à internet.</div>';
    }
}

function renderSearchResults(query) {
    const el = document.getElementById('search-results-content');
    if (!searchResults.length) {
        el.innerHTML = `
            <div class="alert alert-warning">
                <i class="bi bi-exclamation-triangle me-2"></i>
                Nenhuma receita encontrada para "<strong>${escapeHtml(query)}</strong>".
            </div>`;
        return;
    }
    el.innerHTML = `
        <p class="text-muted mb-3">${searchResults.length} receita(s) encontrada(s) para "<strong>${escapeHtml(query)}</strong>"</p>
        <div class="row g-3">
            ${searchResults.map(r => `
                <div class="col-sm-6 col-lg-4">
                    <div class="card search-card h-100" onclick="showApiRecipe('${escapeAttr(r.id)}')">
                        ${r.fotoUrl ? `<img src="${escapeAttr(r.fotoUrl)}" class="card-img-top" alt="${escapeAttr(r.titulo)}">` : ''}
                        <div class="card-body">
                            <h6 class="card-title fw-bold">${escapeHtml(r.titulo)}</h6>
                            <p class="card-text small text-muted mb-1">
                                <i class="bi bi-geo-alt me-1"></i>${escapeHtml(r.origem)}
                            </p>
                            <span class="tipo-badge ${tipoBadgeClass(r.tipo)}">${escapeHtml(r.tipo)}</span>
                        </div>
                    </div>
                </div>`).join('')}
        </div>`;
}

function showApiRecipe(id) {
    const receita = searchResults.find(r => r.id === id);
    if (receita) {
        showView('recipe-view');
        renderRecipeCard(receita, true);
    }
}

// =====================================================================
// Save from API (Bonus)
// =====================================================================
async function saveFromApi() {
    if (!currentApiReceita) return;
    try {
        const res = await fetch('/api/receitas/guardar-api', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(currentApiReceita)
        });
        if (!res.ok) throw new Error();
        const saved = await res.json();
        showToast('Receita guardada na sua coleção!');
        await loadRecipes();
        viewRecipe(saved.id);
    } catch {
        showToast('Erro ao guardar a receita.', 'danger');
    }
}

// =====================================================================
// Utilities
// =====================================================================
function escapeHtml(str) {
    if (str == null) return '';
    const d = document.createElement('div');
    d.appendChild(document.createTextNode(String(str)));
    return d.innerHTML;
}

function escapeAttr(str) {
    if (str == null) return '';
    return String(str).replace(/['"<>&]/g, c => ({ "'": '&#39;', '"': '&quot;', '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
}

function tipoBadgeClass(tipo) {
    const map = {
        'doces': 't-doces',
        'carne': 't-carne', 'peixe': 't-peixe', 'marisco': 't-marisco',
        'massas': 't-massas', 'pizzas': 't-pizzas', 'sopas': 't-sopas',
        'saladas': 't-saladas', 'petiscos': 't-petiscos',
        'molhos': 't-molhos', 'temperos': 't-temperos', 'bebidas': 't-bebidas',
        'internacional': 't-internacional'
    };
    return map[tipo] || 'bg-secondary text-white';
}

function difficultyClass(d) {
    const map = {
        'principiante': 'diff-principiante',
        'intermedio': 'diff-intermedio',
        'avançado': 'diff-avancado'
    };
    return map[d] || 'bg-secondary text-white';
}

// =====================================================================
// Init
// =====================================================================
document.addEventListener('DOMContentLoaded', () => {
    loadRecipes();
    document.getElementById('search-input').addEventListener('keydown', e => {
        if (e.key === 'Enter') performSearch();
    });
});
