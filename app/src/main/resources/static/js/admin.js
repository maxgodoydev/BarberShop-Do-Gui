/* Área administrativa. Cada página declara data-page no <body>. As permissões reais são aplicadas no servidor;
   aqui só escondemos o que o papel não pode usar. */
(async () => {
  const {$, $$, esc, money, dt, DIAS, api, toast, show, state, bars} = BG;
  let me;
  try { me = await api('/api/auth/me'); } catch (_) { location.href = '/admin-login.html'; return; }
  const ROTULO = {PROPRIETARIO: 'proprietário', RECEPCAO: 'recepção', TECNICO: 'equipe técnica'};
  const NAV = {
    PROPRIETARIO: [['painel', 'Painel'], ['bi', 'BI'], ['agenda', 'Agenda'], ['equipe', 'Equipe'], ['estoque', 'Estoque'], ['servicos', 'Serviços'], ['profissionais', 'Profissionais'], ['clientes', 'Clientes'], ['horarios', 'Horários'], ['usuarios', 'Usuários']],
    RECEPCAO: [['agenda', 'Agenda'], ['equipe', 'Equipe'], ['estoque', 'Estoque'], ['clientes', 'Clientes']],
    TECNICO: [['painel', 'Painel'], ['bi', 'BI'], ['servicos', 'Serviços'], ['profissionais', 'Profissionais'], ['clientes', 'Clientes']]};
  const menu = NAV[me.papel] || [];
  const atual = document.body.dataset.page;
  if (menu.length && !menu.some(m => m[0] === atual)) { location.href = '/admin/' + menu[0][0] + '.html'; return; }
  $('#menu').innerHTML = menu.map(m => '<a href="/admin/' + m[0] + '.html"' + (m[0] === atual ? ' aria-current="page"' : '') + '>' + m[1] + '</a>').join('') + '<a href="/">Site</a><a href="#" id="sair">Sair</a>';
  $('#quem').textContent = me.usuario + ' (' + (ROTULO[me.papel] || me.papel) + ')';
  if (me.papel !== 'PROPRIETARIO') $$('.so-dono').forEach(e => e.remove());
  if (me.papel === 'RECEPCAO') $$('.so-gestao').forEach(e => e.remove());
  $('#sair').addEventListener('click', async () => { await api('/api/auth/logout', {method: 'POST'}).catch(() => {}); location.href = '/admin-login.html'; });
  $('#form-senha')?.addEventListener('submit', async e => { e.preventDefault(); const f = e.target.elements; try { await api('/api/auth/senha', {method: 'POST', body: {senhaAtual: f.atual.value, novaSenha: f.nova.value}}); toast('Senha alterada.'); e.target.reset(); } catch (err) { toast(Object.values(err.erros || {})[0] || err.message, true); } });
  const page = document.body.dataset.page;
  const pages = {painel, agenda, servicos, profissionais, horarios, usuarios, clientes, bi: () => window.renderBI(BG), equipe: () => window.renderEquipe(BG, me), estoque: () => window.renderEstoque(BG, me)};
  if (pages[page]) try { await pages[page](); } catch (e) { toast(e.message, true); }

  async function painel() {
    const d = await api('/api/admin/dashboard'), k = d.kpis, pct = v => v == null ? '—' : v.toLocaleString('pt-BR') + '%';
    const cards = [['Pedidos enviados', k.pedidos, 'Solicitado + confirmado + concluído + cancelado'], ['Concluídos', k.concluidos, 'Atendimentos com baixa'], ['Cancelados', k.cancelados, 'Pelo cliente ou pela equipe'],
      ['Taxa de cancelamento', pct(k.taxaCancelamentoPct), 'Cancelados ÷ pedidos'], ['Taxa de conclusão', pct(k.taxaConclusaoPct), 'Concluídos ÷ pedidos já vencidos'], ['Aguardando confirmação', k.aguardandoConfirmacao, 'Solicitações futuras sem resposta'],
      ['Vencidos sem desfecho', k.vencidosSemDesfecho, 'Horário passou e segue solicitado/confirmado'], ['Abandono de reserva', pct(k.taxaAbandonoReservaPct), 'Reservas expiradas ÷ (expiradas + pedidos)']];
    $('#kpis').innerHTML = cards.map(c => '<div class="card kpi"><span>' + esc(c[0]) + '</span><strong>' + esc(c[1]) + '</strong><small class="muted">' + esc(c[2]) + '</small></div>').join('');
    bars($('#servico'), d.porServico, i => i.servico, 'quantidade'); bars($('#prof'), d.porProfissional, i => i.profissional, 'quantidade', 'b');
    bars($('#status'), d.porStatus, i => i.status, 'quantidade'); bars($('#dia'), d.porDiaSemana, i => DIAS[i.dia_semana], 'quantidade', 'b');
    bars($('#volume'), d.volumeDiario.slice(-14), i => new Date(i.dia + 'T00:00').toLocaleDateString('pt-BR', {day: '2-digit', month: '2-digit'}), 'quantidade');
    $('#aviso').textContent = d.aviso;
  }

  async function agenda() {
    const X = window.BGX, dono = me.papel === 'PROPRIETARIO';
    const [profs, servs, publicos] = await Promise.all([api('/api/admin/profissionais'), api('/api/admin/servicos'), api('/api/public/services')]);
    $('#f-prof').innerHTML += profs.filter(p => p.ativo).map(p => '<option value="' + p.id + '">' + esc(p.nome) + '</option>').join('');
    $('#f-serv').innerHTML += servs.map(s => '<option value="' + s.id + '">' + esc(s.nome) + '</option>').join('');
    $('#f-data').value = X.iso(new Date());
    const tb = $('#corpo');
    const faixa = () => {
      const d = new Date($('#f-data').value + 'T12:00:00'), m = $('#f-modo').value;
      if (m === 'dia') return [d, d];
      if (m === 'semana') { const i = X.monday(d); return [i, X.addDays(i, 6)]; }
      return [d, new Date(d.getFullYear(), d.getMonth() + 1, 0, 12)];
    };
    async function disponibilidade() {
      const card = $('#disp-card'); card.hidden = $('#f-modo').value !== 'dia'; if (card.hidden) return;
      try {
        const l = await api('/api/admin/equipe/disponibilidade?data=' + $('#f-data').value);
        $('#disp').innerHTML = l.map(p => '<span class="chip"><strong>' + esc(p.profissional) + '</strong> · ' + (p.janelas.length ? Number(p.agendamentos) + ' agend. · ' + Number(p.horariosLivres30min) + ' livres' : 'sem expediente') + '</span>').join('');
      } catch (_) { card.hidden = true; }
    }
    async function load() {
      const [de, ate] = faixa(); const q = new URLSearchParams({data: X.iso(de)}); if (X.iso(ate) !== X.iso(de)) q.set('ate', X.iso(ate));
      for (const [id, k] of [['f-prof', 'profissionalId'], ['f-serv', 'servicoId'], ['f-status', 'status']]) if ($('#' + id).value) q.set(k, $('#' + id).value);
      $('#periodo').textContent = X.iso(ate) === X.iso(de) ? 'Dia ' + X.br(X.iso(de)) : 'De ' + X.br(X.iso(de)) + ' até ' + X.br(X.iso(ate));
      tb.innerHTML = '<tr><td colspan="5"><div class="skeleton"></div></td></tr>'; disponibilidade();
      try {
        const rows = await api('/api/admin/agendamentos?' + q);
        if (!rows.length) { tb.innerHTML = '<tr><td colspan="5" class="empty">Nenhum agendamento neste período.</td></tr>'; return; }
        let dia = '';
        tb.innerHTML = rows.map(b => {
          const d = String(b.inicio).slice(0, 10), cab = d !== dia && faixa()[0] !== faixa()[1] ? '<tr class="dia"><td colspan="5">' + esc(DIAS[new Date(d + 'T12:00:00').getDay() || 7] || '') + ' · ' + X.br(d) + '</td></tr>' : ''; dia = d;
          return cab + '<tr><td>' + dt(b.inicio) + '</td><td>' + esc(b.servico) + '<br><small>' + esc(b.profissional) + '</small></td><td>' + esc(b.clienteNome || '') + '<br><small>' + esc(b.clienteEmail || 'sem e-mail') + (b.clienteTelefone ? ' · ' + esc(b.clienteTelefone) : '') + '</small>'
            + (b.observacao ? '<br><small>Obs.: ' + esc(b.observacao) + '</small>' : '') + '</td><td><span class="status ' + esc(b.status) + '">' + esc(b.status) + '</span>' + (b.origem === 'BALCAO' ? ' <span class="tag">balcão</span>' : '') + (b.canceladoPor ? '<br><small>por ' + esc(b.canceladoPor) + '</small>' : '') + '</td><td>' + acoes(b) + '</td></tr>';
        }).join('');
      } catch (e) { tb.innerHTML = '<tr><td colspan="5" class="empty">' + esc(e.message) + '</td></tr>'; }
    }
    const passou30 = b => new Date(b.inicio).getTime() + 30 * 60000 < Date.now();
    const acoes = b => ({SOLICITADO: [['CONFIRMADO', 'Confirmar', ''], ['CANCELADO', 'Recusar/cancelar', 'danger']], CONFIRMADO: [['CONCLUIDO', 'Dar baixa', 'blue'], ...(passou30(b) ? [['FALTOU', 'Faltou', 'danger']] : []), ['CANCELADO', 'Cancelar', 'danger']]}[b.status] || [])
      .map(a => '<button class="btn small ' + a[2] + '" data-id="' + b.id + '" data-s="' + a[0] + '">' + a[1] + '</button>').join(' ') || '—';
    tb.addEventListener('click', async e => {
      const b = e.target.closest('button[data-id]'); if (!b) return;
      if (b.dataset.s === 'CANCELADO' && !confirm('Cancelar este agendamento? O cliente será avisado por e-mail (se tiver e-mail e o envio estiver configurado).')) return;
      if (b.dataset.s === 'FALTOU' && !confirm('Registrar falta/atraso? O cadastro do cliente será BLOQUEADO até o proprietário liberar.')) return;
      b.disabled = true; try { await api('/api/admin/agendamentos/' + b.dataset.id + '/status', {method: 'PATCH', body: {status: b.dataset.s}}); toast('Status atualizado.'); load(); } catch (err) { toast(err.message, true); b.disabled = false; }
    });
    const mover = n => {
      const d = new Date($('#f-data').value + 'T12:00:00'), m = $('#f-modo').value;
      const r = m === 'dia' ? X.addDays(d, n) : m === 'semana' ? X.addDays(d, 7 * n) : new Date(d.getFullYear(), d.getMonth() + n, 1, 12);
      $('#f-data').value = X.iso(r); load();
    };
    $('#ant').addEventListener('click', () => mover(-1)); $('#prox').addEventListener('click', () => mover(1));
    $('#hoje').addEventListener('click', () => { $('#f-data').value = X.iso(new Date()); load(); });
    $$('.filtro').forEach(f => f.addEventListener('change', load)); $('#filtrar').addEventListener('click', load);
    $('#novo').addEventListener('click', () => window.openBalcao(BG, {servs: publicos, onDone: load}));
    load();
  }

  async function servicos() {
    const tb = $('#corpo'), form = $('#form'); let list = [];
    async function load() { list = await api('/api/admin/servicos'); tb.innerHTML = list.map(s => '<tr><td>' + esc(s.nome) + '<br><small>' + esc(s.descricao) + '</small></td><td>' + money(s.preco) + (s.ilustrativo ? ' <span class="tag">demonstrativo</span>' : '') + '</td><td>' + s.duracaoMinutos + ' min' + (s.requerAvaliacao ? '<br><span class="tag warn">avaliação prévia</span>' : '') + '</td><td>' + (s.ativo ? 'Ativo' : 'Inativo')
      + '</td><td class="so-dono"><button class="btn small ghost" data-e="' + s.id + '">Editar</button> <button class="btn small ghost" data-t="' + s.id + '">' + (s.ativo ? 'Desativar' : 'Ativar') + '</button></td></tr>').join('') || '<tr><td colspan="5" class="empty">Nenhum serviço.</td></tr>';
      if (me.papel !== 'PROPRIETARIO') $$('.so-dono').forEach(e => e.remove()); }
    tb.addEventListener('click', async e => {
      const ed = e.target.closest('[data-e]'), tg = e.target.closest('[data-t]');
      if (ed) { const s = list.find(x => x.id == ed.dataset.e), f = form.elements; f.id.value = s.id; f.nome.value = s.nome; f.descricao.value = s.descricao; f.preco.value = s.preco; f.duracao.value = s.duracaoMinutos; f.avaliacao.checked = s.requerAvaliacao; f.ilustrativo.checked = s.ilustrativo; form.scrollIntoView(); }
      if (tg) { const s = list.find(x => x.id == tg.dataset.t); try { await api('/api/admin/servicos/' + s.id + '/ativo', {method: 'PATCH', body: {ativo: !s.ativo}}); load(); } catch (err) { toast(err.message, true); } }
    });
    form?.addEventListener('submit', async e => {
      e.preventDefault(); const f = form.elements; const body = {nome: f.nome.value, descricao: f.descricao.value, preco: Number(f.preco.value), duracaoMinutos: Number(f.duracao.value), requerAvaliacao: f.avaliacao.checked, ilustrativo: f.ilustrativo.checked, ordem: null};
      try { await api(f.id.value ? '/api/admin/servicos/' + f.id.value : '/api/admin/servicos', {method: f.id.value ? 'PUT' : 'POST', body}); toast('Serviço salvo.'); form.reset(); f.id.value = ''; load(); }
      catch (err) { toast(Object.values(err.erros || {})[0] || err.message, true); }
    });
    await load();
  }

  async function profissionais() {
    const [servs] = await Promise.all([api('/api/admin/servicos')]); const tb = $('#corpo'), form = $('#form'); let list = [];
    $('#lista-serv').innerHTML = servs.map(s => '<label><input type="checkbox" value="' + s.id + '"> ' + esc(s.nome) + '</label>').join('');
    async function load() { list = await api('/api/admin/profissionais'); tb.innerHTML = list.map(p => '<tr><td>' + esc(p.nome) + '</td><td>' + p.servicoIds.map(id => esc((servs.find(s => s.id === id) || {}).nome)).join(', ') + '</td><td>' + (p.ativo ? 'Ativo' : 'Inativo') + '</td><td class="so-dono"><button class="btn small ghost" data-e="' + p.id + '">Editar</button> <button class="btn small ghost" data-t="' + p.id + '">' + (p.ativo ? 'Desativar' : 'Ativar') + '</button></td></tr>').join('') || '<tr><td colspan="4" class="empty">Nenhum profissional.</td></tr>';
      if (me.papel !== 'PROPRIETARIO') $$('.so-dono').forEach(e => e.remove()); }
    tb.addEventListener('click', async e => {
      const ed = e.target.closest('[data-e]'), tg = e.target.closest('[data-t]');
      if (ed) { const p = list.find(x => x.id == ed.dataset.e); form.elements.id.value = p.id; form.elements.nome.value = p.nome; $$('#lista-serv input').forEach(c => c.checked = p.servicoIds.includes(Number(c.value))); form.scrollIntoView(); }
      if (tg) { const p = list.find(x => x.id == tg.dataset.t); try { await api('/api/admin/profissionais/' + p.id + '/ativo', {method: 'PATCH', body: {ativo: !p.ativo}}); load(); } catch (err) { toast(err.message, true); } }
    });
    form?.addEventListener('submit', async e => {
      e.preventDefault(); const f = form.elements, ids = $$('#lista-serv input:checked').map(c => Number(c.value)); const body = {nome: f.nome.value, servicoIds: ids, ordem: null};
      try { await api(f.id.value ? '/api/admin/profissionais/' + f.id.value : '/api/admin/profissionais', {method: f.id.value ? 'PUT' : 'POST', body}); toast('Profissional salvo.'); form.reset(); f.id.value = ''; load(); } catch (err) { toast(err.message, true); }
    });
    await load();
  }

  async function horarios() {
    const profs = await api('/api/admin/profissionais'), sel = $('#prof'); sel.innerHTML = profs.map(p => '<option value="' + p.id + '">' + esc(p.nome) + '</option>').join('');
    $('#b-prof').innerHTML = '<option value="">Barbearia inteira</option>' + sel.innerHTML; const grade = $('#grade');
    grade.innerHTML = [1, 2, 3, 4, 5, 6, 7].map(d => '<tr data-d="' + d + '"><th>' + DIAS[d] + '</th><td><input type="time" class="a1"></td><td><input type="time" class="f1"></td><td><input type="time" class="a2"></td><td><input type="time" class="f2"></td></tr>').join('');
    async function loadWin() {
      $$('#grade input').forEach(i => i.value = ''); if (!sel.value) return;
      const ws = await api('/api/admin/profissionais/' + sel.value + '/horarios'), seen = {};
      for (const w of ws) { const r = $('#grade tr[data-d="' + w.diaSemana + '"]'), n = seen[w.diaSemana] = (seen[w.diaSemana] || 0) + 1; if (n > 2) continue; r.querySelector('.a' + n).value = w.inicio.slice(0, 5); r.querySelector('.f' + n).value = w.fim.slice(0, 5); }
    }
    sel.addEventListener('change', loadWin);
    $('#salvar').addEventListener('click', async () => {
      const body = []; for (const r of $$('#grade tr')) for (const n of [1, 2]) { const a = r.querySelector('.a' + n).value, f = r.querySelector('.f' + n).value; if (a && f) body.push({diaSemana: Number(r.dataset.d), inicio: a + ':00', fim: f + ':00'}); }
      try { await api('/api/admin/profissionais/' + sel.value + '/horarios', {method: 'PUT', body}); toast('Horários salvos.'); } catch (e) { toast(e.message, true); }
    });
    async function loadBlocks() { const bs = await api('/api/admin/bloqueios'); $('#bloqueios').innerHTML = bs.map(b => '<tr><td>' + esc(b.profissional || 'Barbearia inteira') + '</td><td>' + dt(b.inicio) + ' → ' + dt(b.fim) + '</td><td>' + esc(b.motivo || '') + '</td><td><button class="btn small ghost" data-x="' + b.id + '">Remover</button></td></tr>').join('') || '<tr><td colspan="4" class="empty">Nenhum bloqueio futuro.</td></tr>'; }
    $('#bloqueios').addEventListener('click', async e => { const b = e.target.closest('[data-x]'); if (!b) return; try { await api('/api/admin/bloqueios/' + b.dataset.x, {method: 'DELETE'}); loadBlocks(); } catch (err) { toast(err.message, true); } });
    $('#form-bloqueio').addEventListener('submit', async e => {
      e.preventDefault(); const f = e.target.elements;
      try { const r = await api('/api/admin/bloqueios', {method: 'POST', body: {profissionalId: f.prof.value ? Number(f.prof.value) : null, inicio: f.inicio.value + ':00', fim: f.fim.value + ':00', motivo: f.motivo.value}});
        show($('#aviso'), r.agendamentosAfetados ? 'Bloqueio criado. Atenção: já existem ' + r.agendamentosAfetados + ' agendamento(s) ativo(s) nesse período. Eles NÃO foram cancelados automaticamente; trate-os na Agenda.' : 'Bloqueio criado.', r.agendamentosAfetados ? 'warn' : 'ok'); e.target.reset(); loadBlocks(); }
      catch (err) { toast(err.message, true); }
    });
    await loadWin(); await loadBlocks();
  }

  async function usuarios() {
    async function load() { const us = await api('/api/admin/usuarios'); $('#corpo').innerHTML = us.map(u => '<tr><td>' + esc(u.username) + '</td><td>' + esc(u.nome) + '</td><td>' + esc(u.papel) + '</td><td>' + (u.ativo ? 'Ativo' : 'Inativo') + '</td><td>' + dt(u.ultimoLoginEm) + '</td><td><button class="btn small ghost" data-t="' + u.id + '" data-a="' + u.ativo + '">' + (u.ativo ? 'Desativar' : 'Ativar') + '</button></td></tr>').join(''); }
    $('#corpo').addEventListener('click', async e => { const b = e.target.closest('[data-t]'); if (!b) return; try { await api('/api/admin/usuarios/' + b.dataset.t + '/ativo', {method: 'PATCH', body: {ativo: b.dataset.a !== 'true'}}); load(); } catch (err) { toast(err.message, true); } });
    $('#form').addEventListener('submit', async e => { e.preventDefault(); const f = e.target.elements; try { const r = await api('/api/admin/usuarios', {method: 'POST', body: {username: f.username.value, nome: f.nome.value, senhaTemporaria: f.senha.value, papel: f.papel.value}}); toast(r.mensagem); e.target.reset(); load(); } catch (err) { toast(Object.values(err.erros || {})[0] || err.message, true); } });
    try { const au = await api('/api/admin/auditoria'); $('#auditoria').innerHTML = au.length ? au.map(a => '<tr><td>' + dt(a.quando) + '</td><td>' + esc(a.usuario) + '</td><td>' + esc(a.acao) + '</td><td>' + esc(a.detalhe || '') + '</td></tr>').join('') : '<tr><td colspan="4" class="empty">Nenhuma ação registrada ainda.</td></tr>'; } catch (_) { /* tabela opcional */ }
    await load();
  }

  async function clientes() {
    const corpo = $('#corpo'), q = $('#q'); let t;
    async function load() {
      try {
        const l = await api('/api/admin/clientes?q=' + encodeURIComponent(q.value.trim()) + '&bloqueados=' + ($('#so-bloq')?.checked ? 'true' : 'false'));
        corpo.innerHTML = l.length ? l.map(c => '<tr><td>' + esc(c.nome) + '</td><td>' + esc(c.email || 'sem e-mail') + '</td><td>' + esc(c.telefone || '—') + '</td><td>' + (c.bloqueado ? '<span class="status CANCELADO">Bloqueado</span>' : '<span class="status CONFIRMADO">Ativo</span>') + '</td><td>' + Number(c.agendamentosAtivos) + '</td><td>' + (me.papel === 'RECEPCAO' ? '—' : '<button class="btn ghost small" data-b="' + Number(c.id) + '" data-a="' + c.bloqueado + '">' + (c.bloqueado ? 'Desbloquear' : 'Bloquear') + '</button> <button class="btn danger small" data-x="' + Number(c.id) + '">Excluir</button>') + '</td></tr>').join('') : '<tr><td colspan="6">Nenhum cliente encontrado.</td></tr>';
      } catch (e) { toast(e.message, true); }
    }
    async function resumo() {
      const box = $('#resumo'); if (!box) return;
      try { const r = await api('/api/admin/clientes/resumo'); box.innerHTML = [['Clientes ativos', r.ativos], ['Bloqueados', r.bloqueados], ['Contas excluídas', r.excluidos], ['Excluídas nos últimos 30 dias', r.excluidos30dias]].map(k => '<div class="card kpi"><strong>' + Number(k[1]) + '</strong><span>' + k[0] + '</span></div>').join(''); } catch (_) { box.remove(); }
    }
    q.addEventListener('input', () => { clearTimeout(t); t = setTimeout(load, 300); });
    $('#so-bloq')?.addEventListener('change', load);
    corpo.addEventListener('click', async e => {
      const b = e.target.closest('button'); if (!b) return;
      try {
        if (b.dataset.b) { const r = await api('/api/admin/clientes/' + b.dataset.b + (b.dataset.a === 'true' ? '/desbloquear' : '/bloquear'), {method: 'POST'}); toast(r.mensagem); }
        if (b.dataset.x) { if (!confirm('Excluir este cadastro? Os dados pessoais serão apagados e isso não pode ser desfeito.')) return; const r = await api('/api/admin/clientes/' + b.dataset.x, {method: 'DELETE'}); toast(r.mensagem); }
        load(); resumo();
      } catch (err) { toast(err.message, true); }
    });
    await resumo(); await load();
  }
})();
