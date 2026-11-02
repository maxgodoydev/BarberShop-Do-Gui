/* Utilitários compartilhados: API com CSRF, escape de HTML, formatação e avisos. Sem estilos/handlers inline (CSP). */
window.BG = (() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = v => Number(v).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
  const dt = v => v ? new Date(v).toLocaleString('pt-BR', {dateStyle: 'short', timeStyle: 'short'}) : '—';
  const hm = v => v ? new Date(v).toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'}) : '';
  const DIAS = ['', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
  let csrf = null;

  async function loadCsrf() { csrf = await (await fetch('/api/csrf', {credentials: 'same-origin'})).json(); }

  async function api(url, {method = 'GET', body, retry = true} = {}) {
    const headers = {Accept: 'application/json'};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET') { if (!csrf) await loadCsrf(); headers[csrf.headerName] = csrf.token; }
    let r;
    try { r = await fetch(url, {method, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body)}); }
    catch (_) { throw new ApiError('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.', 0); }
    if (r.status === 403 && method !== 'GET' && retry) { csrf = null; return api(url, {method, body, retry: false}); }
    if (r.status === 401 && url.startsWith('/api/admin')) { location.href = '/admin-login.html'; throw new ApiError('Sessão expirada.', 401); }
    const text = await r.text(); let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { /* resposta não JSON */ }
    if (!r.ok) throw new ApiError((data && data.mensagem) || (r.status === 403 ? 'Você não tem permissão para esta ação.' : 'Falha na requisição (' + r.status + ').'), r.status, data && data.erros);
    return data;
  }
  class ApiError extends Error { constructor(m, status, erros) { super(m); this.status = status; this.erros = erros || {}; } }

  function toast(msg, bad = false) {
    let box = $('.toast'); if (!box) { box = document.createElement('div'); box.className = 'toast'; box.setAttribute('role', 'status'); box.setAttribute('aria-live', 'polite'); document.body.appendChild(box); }
    const d = document.createElement('div'); if (bad) d.className = 'err'; d.textContent = msg; box.appendChild(d); setTimeout(() => d.remove(), 5000);
  }
  function show(el, html, kind = 'info') { el.hidden = false; el.className = 'notice ' + kind; el.innerHTML = html; }
  function state(el, kind, msg) { // carregando | vazio | erro
    if (kind === 'loading') el.innerHTML = '<div class="skeleton" aria-label="Carregando"></div>';
    else el.innerHTML = '<p class="empty' + (kind === 'error' ? ' err' : '') + '">' + esc(msg) + '</p>';
  }
  function bars(el, items, label, value, cls = '') {
    el.textContent = '';
    if (!items || !items.length) return state(el, 'empty', 'Sem dados no período.');
    const max = Math.max(1, ...items.map(i => Number(i[value]) || 0));
    for (const i of items) {
      const row = document.createElement('div'); row.className = 'bar';
      const l = document.createElement('span'); l.textContent = label(i);
      const t = document.createElement('div'); t.className = 'track'; const f = document.createElement('div'); f.className = 'fill ' + cls; f.style.width = ((Number(i[value]) || 0) / max * 100) + '%'; t.appendChild(f);
      const v = document.createElement('strong'); v.textContent = i[value]; row.append(l, t, v); el.appendChild(row);
    }
  }
  function initNav() {
    const b = $('.menu-btn'), n = $('nav.main');
    if (b) b.addEventListener('click', () => { const o = n.classList.toggle('open'); b.setAttribute('aria-expanded', o); });
    const y = $('#ano'); if (y) y.textContent = new Date().getFullYear();
    $$('nav.main a').forEach(a => { if (a.getAttribute('href') === location.pathname) a.setAttribute('aria-current', 'page'); });
  }
  async function shopInfo() { try { return await api('/api/public/config'); } catch (_) { return {}; } }
  const iso = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');

  /* Rodapé "Contato & Horários" em todas as páginas públicas (dados do Facebook/Google Maps da barbearia). */
  const MAPS = 'https://www.google.com/maps/place/Av.+Pres.+João+Goulart,+940+-+Jardim+Malia+II,+São+Paulo+-+SP,+04821-000/data=!4m2!3m1!1s0x94ce4f0ad299531b:0xf0c5710d26ab3ae?sa=X&ved=1t:242&ictx=111';
  const FB = 'https://www.facebook.com/p/BarberShop-Do-Gui-100050410702068/?locale=pt_BR', IG = 'https://www.instagram.com/barbershop_dogui/';
  const ICO = {
    pin: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"/></svg>',
    clock: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm.5 5v5.25l4.5 2.67-.75 1.23L11 13V7h1.5z"/></svg>',
    ig: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zM12 0C8.741 0 8.333.014 7.053.072 2.695.272.273 2.69.073 7.052.014 8.333 0 8.741 0 12c0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98C8.333 23.986 8.741 24 12 24c3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98C15.668.014 15.259 0 12 0zm0 5.838a6.162 6.162 0 1 0 0 12.324 6.162 6.162 0 0 0 0-12.324zM12 16a4 4 0 1 1 0-8 4 4 0 0 1 0 8zm6.406-11.845a1.44 1.44 0 1 0 0 2.881 1.44 1.44 0 0 0 0-2.881z"/></svg>',
    fb: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/></svg>'
  };
  function initFooter() {
    const w = $('footer.site .wrap'); if (!w || document.body.classList.contains('admin') || $('.rodape-info', w)) return;
    const d = document.createElement('div'); d.className = 'rodape-info';
    d.innerHTML = '<div><h3>Contato &amp; Horários</h3><ul>'
      + '<li>' + ICO.pin + '<a href="' + MAPS + '" target="_blank" rel="noopener">Av. Pres. João Goulart, 940 · Jardim Malia II<br>São Paulo - SP · 04821-000</a></li>'
      + '<li>' + ICO.clock + '<span>Funcionamento semanal (domingo fechado)</span></li></ul></div>'
      + '<div><h3>Dias de funcionamento</h3><ul><li><span><b>Segunda:</b> 11h às 20h30</span></li><li><span><b>Terça a sábado:</b> 10h às 20h30</span></li><li><span class="fechado"><b>Domingo:</b> fechado</span></li></ul></div>'
      + '<div><h3>Redes</h3><div class="rodape-redes"><a href="' + IG + '" target="_blank" rel="noopener" aria-label="Instagram da BarberShop do Gui">' + ICO.ig + '</a>'
      + '<a href="' + FB + '" target="_blank" rel="noopener" aria-label="Facebook da BarberShop do Gui">' + ICO.fb + '</a></div></div>';
    w.prepend(d);
  }
  document.addEventListener('DOMContentLoaded', () => { initNav(); initFooter(); });
  return {$, $$, esc, money, dt, hm, DIAS, api, toast, show, state, bars, shopInfo, iso, ApiError};
})();
