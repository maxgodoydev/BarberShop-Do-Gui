/* Espaço do cliente (sem senha). Entra pelo link do e-mail (#token=...) ou por sessão já aberta pelo link de confirmação. */
(async () => {
  const {$, esc, money, dt, api, state, show} = BG;
  const req = $('#pedir'), prox = $('#prox'), hist = $('#hist'), msg = $('#msg');
  const ROT = {SOLICITADO: 'Aguardando confirmação', CONFIRMADO: 'Confirmado', CONCLUIDO: 'Concluído', CANCELADO: 'Cancelado', FALTOU: 'Faltou'};
  const card = b => '<article class="card"><h3>' + esc(b.servico) + '</h3><p>' + dt(b.inicio) + ' · com ' + esc(b.profissional) + '</p>'
    + '<p><span class="status ' + esc(b.status) + '">' + esc(ROT[b.status] || b.status) + '</span>' + (b.origem === 'BALCAO' ? ' <span class="tag">balcão</span>' : '') + '</p>'
    + (b.produtos ? '<p class="small"><strong>Produtos:</strong> ' + esc(b.produtos) + '</p>' : '')
    + '<p class="muted small">' + esc(b.descricaoStatus) + ' · ' + money(b.precoReferencia) + '</p>'
    + (b.podeCancelar ? '<button class="btn danger small" data-id="' + Number(b.id) + '">Cancelar</button>' : '') + '</article>';
  async function carregar(silencioso) {
    try {
      const [rows, p] = await Promise.all([api('/api/cliente/agendamentos'), api('/api/cliente/perfil')]);
      req.hidden = true; $('#area').hidden = false;
      const desde = String(p.clienteDesde || '').slice(0, 10).split('-').reverse().join('/');
      $('#perfil').innerHTML = '<h3>Meus dados</h3><p><strong>' + esc(p.nome) + '</strong></p><p>' + esc(p.email || '') + (p.telefone ? ' · ' + esc(p.telefone) : '') + '</p>'
        + '<p class="muted small">Cliente desde ' + esc(desde) + '. Seus dados são usados só para o agendamento (veja a <a href="/privacidade.html">política de privacidade</a>).</p>';
      const ativos = rows.filter(b => b.status === 'SOLICITADO' || b.status === 'CONFIRMADO');
      prox.innerHTML = ativos.length ? ativos.map(card).join('') : '<p class="muted">Nenhum agendamento futuro. <a href="/agendar.html">Agendar horário</a></p>';
      const resto = rows.filter(b => !ativos.includes(b));
      hist.innerHTML = resto.length ? resto.map(card).join('') : '<p class="muted">Seu histórico aparece aqui depois dos primeiros atendimentos.</p>';
    } catch (e) { req.hidden = false; $('#area').hidden = true; if (!silencioso) show(msg, esc(e.message), 'err'); }
  }
  const m = /#token=([\w-]+)/.exec(location.hash);
  if (m) {
    try { await api('/api/cliente/sessao', {method: 'POST', body: {token: m[1]}}); history.replaceState(null, '', location.pathname); await carregar(false); }
    catch (e) { history.replaceState(null, '', location.pathname); show(msg, esc(e.message), 'err'); }
  } else { await carregar(true); }
  $('#form-link').addEventListener('submit', async e => {
    e.preventDefault(); const b = $('#btn-link'); b.disabled = true;
    try { const r = await api('/api/public/access-link', {method: 'POST', body: {email: e.target.elements.email.value}}); show(msg, esc(r.mensagem), 'ok'); }
    catch (err) { show(msg, esc(err.message), 'err'); } finally { b.disabled = false; }
  });
  $('#area').addEventListener('click', async e => {
    const b = e.target.closest('button[data-id]'); if (!b || !confirm('Cancelar este agendamento?')) return;
    try { await api('/api/cliente/agendamentos/' + b.dataset.id + '/cancelar', {method: 'POST'}); BG.toast('Agendamento cancelado.'); carregar(false); }
    catch (err) { BG.toast(err.message, true); }
  });
  $('#excluir').addEventListener('click', () => {
    const box = document.createElement('div'); box.className = 'wz on'; box.setAttribute('role', 'alertdialog'); box.setAttribute('aria-modal', 'true'); box.setAttribute('aria-labelledby', 'ex-t');
    box.innerHTML = '<div class="wz-m"><div class="wz-h"><h2 id="ex-t">Excluir minha conta?</h2></div><div class="wz-b"><p>Seu nome, e-mail e telefone serão <strong>apagados</strong> e você sai desta conta. Isso não pode ser desfeito.</p><p class="muted small">Só é possível sem agendamentos futuros ativos.</p><div class="wz-f"><button type="button" class="btn ghost" data-no>Voltar</button><button type="button" class="btn danger" data-yes>Sim, excluir</button></div></div></div>';
    document.body.appendChild(box); box.querySelector('[data-no]').focus();
    box.addEventListener('click', async e => {
      if (e.target === box || e.target.closest('[data-no]')) return box.remove();
      if (!e.target.closest('[data-yes]')) return;
      try { await api('/api/cliente/conta/excluir', {method: 'POST'}); box.remove(); BG.toast('Conta excluída.'); setTimeout(() => { location.href = '/'; }, 1200); }
      catch (err) { box.remove(); BG.toast(err.message, true); }
    });
  });
  $('#sair').addEventListener('click', async () => { await api('/api/cliente/sair', {method: 'POST'}).catch(() => {}); location.href = '/consulta.html'; });
})();
