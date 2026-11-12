/* Telas da recepção e do proprietário: equipe, estoque e agendamento no balcão.
   As permissões reais são aplicadas no servidor; aqui só organizamos a tela. */
(() => {
  const iso = d => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const monday = d => { const x = new Date(d); x.setHours(12, 0, 0, 0); x.setDate(x.getDate() - ((x.getDay() + 6) % 7)); return x; };
  const br = s => s ? s.slice(8, 10) + '/' + s.slice(5, 7) + '/' + s.slice(0, 4) : '';
  window.BGX = {iso, addDays, monday, br};

  /* ---------- Equipe: disponibilidade do dia e produção por profissional ---------- */
  window.renderEquipe = async (BG, me) => {
    const {$, $$, esc, money, api, toast} = BG, dono = me.papel === 'PROPRIETARIO';
    const hoje = new Date();
    $('#dia').value = iso(hoje); $('#de').value = iso(monday(hoje)); $('#ate').value = iso(addDays(monday(hoje), 6));
    async function disp() {
      try {
        const l = await api('/api/admin/equipe/disponibilidade?data=' + $('#dia').value);
        $('#disp').innerHTML = l.map(p => '<article class="card"><h3>' + esc(p.profissional) + '</h3>'
          + (p.janelas.length ? '<p class="small">Expediente: ' + esc(p.janelas.join(' · ')) + '</p><p><strong>' + Number(p.agendamentos) + '</strong> agendamentos · <strong>' + Number(p.horariosLivres30min) + '</strong> horários livres (30 min)</p>'
            : '<p class="muted">Sem expediente neste dia.</p>') + '</article>').join('') || '<p class="empty">Nenhum profissional ativo.</p>';
      } catch (e) { toast(e.message, true); }
    }
    async function resumo() {
      try {
        const l = await api('/api/admin/equipe/resumo?de=' + $('#de').value + '&ate=' + $('#ate').value);
        $('#resumo').innerHTML = l.map(p => '<article class="card"><h3>' + esc(p.profissional) + '</h3>'
          + '<p class="grande"><strong>' + Number(p.concluidos) + '</strong> <span class="muted small">atendimentos concluídos</span></p>'
          + '<p class="small">Agendados: ' + Number(p.agendados) + ' · Cancelados: ' + Number(p.cancelados) + ' · Faltas: ' + Number(p.faltas) + '</p>'
          + (dono ? '<p><strong>' + money(p.valorAtendido) + '</strong> <span class="small muted">atendido (preço de tabela dos concluídos)</span></p>' : '')
          + '<div class="tablewrap"><table><thead><tr><th>Serviço realizado</th><th>Qtd</th>' + (dono ? '<th>Valor</th>' : '') + '</tr></thead><tbody>'
          + (p.servicos.length ? p.servicos.map(s => '<tr><td>' + esc(s.servico) + '</td><td>' + Number(s.quantidade) + '</td>' + (dono ? '<td>' + money(s.valor) + '</td>' : '') + '</tr>').join('')
            : '<tr><td colspan="3" class="empty">Nenhum serviço concluído no período.</td></tr>') + '</tbody></table></div></article>').join('') || '<p class="empty">Sem dados no período.</p>';
      } catch (e) { toast(e.message, true); }
    }
    /* Receita estimada por mês e por profissional (só proprietário: o servidor já omite valores para a recepção).
       Reaproveita /resumo, uma chamada por mês, para não exigir endpoint novo. */
    async function mensal() {
      if (!dono) return;
      $('#mensal-wrap').hidden = false;
      try {
        const base = new Date(); base.setHours(12, 0, 0, 0);
        const meses = [];
        for (let i = 5; i >= 0; i--) {
          const a = new Date(base.getFullYear(), base.getMonth() - i, 1, 12), z = new Date(base.getFullYear(), base.getMonth() - i + 1, 0, 12);
          meses.push({a, z, rotulo: String(a.getMonth() + 1).padStart(2, '0') + '/' + a.getFullYear(), parcial: i === 0});
        }
        const dados = await Promise.all(meses.map(m => api('/api/admin/equipe/resumo?de=' + iso(m.a) + '&ate=' + iso(m.z))));
        const profs = new Map();
        dados.forEach(l => l.forEach(p => { if (!profs.has(p.profissionalId)) profs.set(p.profissionalId, p.profissional); }));
        const ids = [...profs.keys()];
        const cell = dados.map(l => ids.map(id => { const p = l.find(x => x.profissionalId === id); return {v: p ? Number(p.valorAtendido) : 0, n: p ? Number(p.concluidos) : 0}; }));
        const sum = arr => arr.reduce((s, c) => ({v: s.v + c.v, n: s.n + c.n}), {v: 0, n: 0});
        const fmt = c => '<strong>' + money(c.v) + '</strong> <span class="small muted">(' + c.n + ' atend.)</span>';
        let html = '<table><thead><tr><th>Mês</th>' + ids.map(id => '<th>' + esc(profs.get(id)) + '</th>').join('') + '<th>Total</th></tr></thead><tbody>';
        for (let i = meses.length - 1; i >= 0; i--) { // mais recente primeiro
          html += '<tr><td>' + meses[i].rotulo + (meses[i].parcial ? ' <span class="small muted">(em andamento)</span>' : '') + '</td>' + cell[i].map(c => '<td>' + fmt(c) + '</td>').join('') + '<td>' + fmt(sum(cell[i])) + '</td></tr>';
        }
        html += '<tr><td><strong>Total do período</strong></td>' + ids.map((_, j) => '<td>' + fmt(sum(cell.map(r => r[j]))) + '</td>').join('') + '<td>' + fmt(sum(cell.flat())) + '</td></tr></tbody></table>';
        $('#mensal').innerHTML = ids.length ? html : '<p class="empty">Sem dados no período.</p>';
      } catch (e) { toast(e.message, true); }
    }
    $('#dia').addEventListener('change', disp);
    $('#de').addEventListener('change', resumo); $('#ate').addEventListener('change', resumo);
    $$('[data-r]').forEach(b => b.addEventListener('click', () => {
      const t = new Date(); t.setHours(12, 0, 0, 0); let a, z;
      if (b.dataset.r === 'sem') { a = monday(t); z = addDays(a, 6); }
      else if (b.dataset.r === 'sem-ant') { a = addDays(monday(t), -7); z = addDays(a, 6); }
      else if (b.dataset.r === 'mes') { a = new Date(t.getFullYear(), t.getMonth(), 1, 12); z = new Date(t.getFullYear(), t.getMonth() + 1, 0, 12); }
      else { a = new Date(t.getFullYear(), t.getMonth() - 1, 1, 12); z = new Date(t.getFullYear(), t.getMonth(), 0, 12); }
      $('#de').value = iso(a); $('#ate').value = iso(z); resumo();
    }));
    await Promise.all([disp(), resumo(), mensal()]);
  };

  /* ---------- Estoque ---------- */
  window.renderEstoque = async (BG, me) => {
    const {$, esc, money, api, toast} = BG, dono = me.papel === 'PROPRIETARIO';
    const cls = {OK: 'CONFIRMADO', BAIXO: 'SOLICITADO', ZERADO: 'CANCELADO'}, txt = {OK: 'Disponível', BAIXO: 'Acabando', ZERADO: 'Zerado'};
    let list = [];
    function draw() {
      const alerta = list.filter(p => p.ativo && p.situacao !== 'OK');
      $('#alerta').hidden = !alerta.length;
      $('#alerta').textContent = alerta.length ? '⚠ ' + alerta.length + ' produto(s) acabando ou zerado(s): ' + alerta.map(p => p.nome).join(', ') + '.' : '';
      $('#corpo').innerHTML = list.map(p => '<tr' + (p.ativo ? '' : ' class="inativo"') + '><td>' + esc(p.nome) + '<br><small>' + esc(p.marca) + '</small></td><td>' + money(p.preco) + (p.ilustrativo ? '*' : '') + '</td>'
        + '<td><span class="status ' + cls[p.situacao] + '">' + txt[p.situacao] + '</span><br><small>mínimo ' + Number(p.estoqueMinimo) + '</small></td>'
        + '<td><label class="sr" for="q' + Number(p.id) + '">Quantidade de ' + esc(p.nome) + '</label><input class="qtd" id="q' + Number(p.id) + '" type="number" min="0" max="100000" value="' + Number(p.estoque) + '"> '
        + '<button class="btn small" data-s="' + Number(p.id) + '">Salvar</button></td>'
        + (dono ? '<td><button class="btn small ghost" data-e="' + Number(p.id) + '">Editar</button> <button class="btn small ghost" data-t="' + Number(p.id) + '" data-a="' + p.ativo + '">' + (p.ativo ? 'Desativar' : 'Ativar') + '</button></td>' : '') + '</tr>').join('')
        || '<tr><td colspan="5" class="empty">Nenhum produto cadastrado.</td></tr>';
    }
    async function load() { try { list = await api('/api/admin/produtos'); draw(); } catch (e) { toast(e.message, true); } }
    $('#corpo').addEventListener('click', async e => {
      const b = e.target.closest('button'); if (!b) return;
      try {
        if (b.dataset.s) { const v = Number($('#q' + b.dataset.s).value); const r = await api('/api/admin/produtos/' + b.dataset.s + '/estoque', {method: 'PATCH', body: {quantidade: v, motivo: null}}); toast(r.mensagem); await load(); }
        if (b.dataset.t) { const r = await api('/api/admin/produtos/' + b.dataset.t + '/ativo', {method: 'PATCH', body: {ativo: b.dataset.a !== 'true'}}); toast(r.mensagem); await load(); }
        if (b.dataset.e) { const p = list.find(x => x.id === Number(b.dataset.e)); const f = $('#form-prod').elements; f.id.value = p.id; f.nome.value = p.nome; f.marca.value = p.marca; f.preco.value = p.preco; f.estoque.value = p.estoque; f.minimo.value = p.estoqueMinimo; $('#form-prod').scrollIntoView({behavior: 'smooth'}); }
      } catch (err) { toast(err.message, true); }
    });
    $('#form-prod')?.addEventListener('submit', async e => {
      e.preventDefault(); const f = e.target.elements;
      const body = {nome: f.nome.value, marca: f.marca.value, preco: Number(f.preco.value), estoque: Number(f.estoque.value), estoqueMinimo: Number(f.minimo.value)};
      try { const r = await api('/api/admin/produtos' + (f.id.value ? '/' + f.id.value : ''), {method: f.id.value ? 'PUT' : 'POST', body}); toast(r.mensagem); e.target.reset(); f.id.value = ''; await load(); }
      catch (err) { toast(Object.values(err.erros || {})[0] || err.message, true); }
    });
    await load();
  };

  /* ---------- Agendamento no balcão ---------- */
  window.openBalcao = async (BG, {servs, onDone}) => {
    const {esc, money, api, toast} = BG;
    let rules = {maxServicos: 3, maxMinutos: 200, exclusoes: {}};
    try { rules = await api('/api/public/booking-rules'); } catch (_) { /* o servidor valida de novo */ }
    let produtos = []; try { produtos = (await api('/api/admin/produtos')).filter(p => p.ativo && p.estoque > 0); } catch (_) { /* opcional */ }
    const S = {sel: [], day: iso(new Date()), pro: null, time: null, groups: [], prod: [], f: {nome: '', telefone: '', email: '', alergias: '', observacao: ''}, seq: 0, msg: ''};
    const box = document.createElement('div'); box.className = 'wz on'; box.setAttribute('role', 'dialog'); box.setAttribute('aria-modal', 'true'); box.setAttribute('aria-labelledby', 'bc-t');
    document.body.appendChild(box);
    const mins = () => S.sel.reduce((a, s) => a + s.duracaoMinutos, 0);
    const excluded = s => S.sel.some(x => (rules.exclusoes[x.id] || []).includes(s.id));
    const blocked = s => !S.sel.some(x => x.id === s.id) && (excluded(s) || S.sel.length >= rules.maxServicos || mins() + s.duracaoMinutos > rules.maxMinutos);
    const total = () => S.sel.reduce((a, s) => a + Number(s.preco), 0);
    async function slots() {
      const n = ++S.seq; S.pro = null; S.time = null; S.groups = [];
      if (!S.sel.length) return draw();
      try {
        const q = S.sel.map(s => 'servicoIds=' + s.id).join('&');
        const g = await api('/api/admin/balcao/horarios?' + q + '&data=' + S.day);
        if (n === S.seq) { S.groups = g; draw(); }
      } catch (e) { if (n === S.seq) { S.msg = e.message; draw(); } }
    }
    function draw() {
      const ok = S.sel.length && S.pro && S.time && S.f.nome.trim().length > 1;
      box.innerHTML = '<div class="wz-m"><div class="wz-h"><h2 id="bc-t">Agendar no balcão</h2><button type="button" class="wz-x" data-x aria-label="Fechar">×</button></div><div class="wz-b">'
        + (S.msg ? '<div class="notice err" role="alert">' + esc(S.msg) + '</div>' : '')
        + '<h3>1. Serviços</h3><div class="bc-lista">' + servs.map(s => '<label class="bc-op"><input type="checkbox" data-s="' + Number(s.id) + '"' + (S.sel.some(x => x.id === s.id) ? ' checked' : '') + (blocked(s) ? ' disabled' : '') + '> ' + esc(s.nome)
          + ' <span class="muted small">' + money(s.preco) + ' · ' + Number(s.duracaoMinutos) + ' min</span></label>').join('') + '</div>'
        + '<p class="small muted">Até ' + Number(rules.maxServicos) + ' serviços e ' + Number(rules.maxMinutos) + ' min por visita.</p>'
        + '<h3>2. Dia e horário</h3><label for="bc-dia">Dia</label><input id="bc-dia" type="date" value="' + esc(S.day) + '" min="' + iso(new Date()) + '">'
        + (S.sel.length ? (S.groups.length ? S.groups.map(g => '<p class="bc-prof"><strong>' + esc(g.profissional) + '</strong></p><div class="slots">' + (g.horarios.length ? g.horarios.map(h => '<button type="button" class="slot' + (S.pro === g.profissionalId && S.time === h ? ' sel' : '') + '" data-p="' + Number(g.profissionalId) + '" data-h="' + esc(h) + '">' + esc(h) + '</button>').join('') : '<span class="muted small">Sem horários neste dia.</span>') + '</div>').join('')
          : '<p class="muted small">Nenhum profissional faz todos esses serviços neste dia.</p>') : '<p class="muted small">Escolha ao menos um serviço.</p>')
        + '<h3>3. Cliente</h3><label for="bc-nome">Nome *</label><input id="bc-nome" data-f="nome" maxlength="100" value="' + esc(S.f.nome) + '" autocomplete="off">'
        + '<label for="bc-tel">Telefone / WhatsApp</label><input id="bc-tel" data-f="telefone" maxlength="30" inputmode="tel" value="' + esc(S.f.telefone) + '">'
        + '<label for="bc-mail">E-mail (opcional)</label><input id="bc-mail" data-f="email" type="email" maxlength="160" value="' + esc(S.f.email) + '"><p class="small muted">Com e-mail, o cliente pode entrar no espaço dele pelo link mágico, sem senha. Sem e-mail, o atendimento fica só no balcão.</p>'
        + '<label for="bc-al">Alergias (opcional)</label><input id="bc-al" data-f="alergias" maxlength="300" value="' + esc(S.f.alergias) + '">'
        + '<label for="bc-ob">Observação</label><input id="bc-ob" data-f="observacao" maxlength="500" value="' + esc(S.f.observacao) + '">'
        + (produtos.length ? '<h3>4. Produtos para retirar (opcional)</h3><div class="bc-lista">' + produtos.map(p => '<label class="bc-op"><input type="checkbox" data-pr="' + Number(p.id) + '"' + (S.prod.includes(p.id) ? ' checked' : '') + '> ' + esc(p.nome) + ' <span class="muted small">' + money(p.preco) + '</span></label>').join('') + '</div>' : '')
        + '<p class="bc-resumo"><strong>Total dos serviços:</strong> ' + money(total()) + ' · ' + mins() + ' min' + (S.time ? ' · ' + esc(BGX.br(S.day)) + ' às ' + esc(S.time) : '') + '</p>'
        + '<div class="wz-f"><button type="button" class="btn ghost" data-x>Cancelar</button><button type="button" class="btn" data-ok' + (ok ? '' : ' disabled') + '>Confirmar agendamento</button></div></div></div>';
    }
    box.addEventListener('input', e => {
      const f = e.target.dataset.f; if (f) { S.f[f] = e.target.value; const b = box.querySelector('[data-ok]'); if (b) b.disabled = !(S.sel.length && S.pro && S.time && S.f.nome.trim().length > 1); }
    });
    box.addEventListener('change', e => {
      const t = e.target;
      if (t.dataset.s) { const s = servs.find(x => x.id === Number(t.dataset.s)); S.sel = t.checked ? [...S.sel, s] : S.sel.filter(x => x.id !== s.id); S.msg = ''; slots(); }
      else if (t.dataset.pr) { const id = Number(t.dataset.pr); S.prod = t.checked ? [...S.prod, id] : S.prod.filter(x => x !== id); }
      else if (t.id === 'bc-dia') { S.day = t.value || S.day; S.msg = ''; slots(); }
    });
    box.addEventListener('click', async e => {
      if (e.target === box || e.target.closest('[data-x]')) return box.remove();
      const sl = e.target.closest('.slot'); if (sl) { S.pro = Number(sl.dataset.p); S.time = sl.dataset.h; S.msg = ''; return draw(); }
      if (!e.target.closest('[data-ok]')) return;
      const b = e.target.closest('[data-ok]'); b.disabled = true;
      try {
        await api('/api/admin/balcao/agendamentos', {method: 'POST', body: {servicoIds: S.sel.map(s => s.id), profissionalId: S.pro, inicio: S.day + 'T' + S.time + ':00', nome: S.f.nome.trim(),
          telefone: S.f.telefone.trim() || null, email: S.f.email.trim() || null, alergias: S.f.alergias.trim() || null, observacao: S.f.observacao.trim() || null,
          produtos: S.prod.map(id => ({produtoId: id, quantidade: 1}))}});
        box.remove(); toast('Agendamento criado no balcão.'); if (onDone) onDone();
      } catch (err) { S.msg = Object.values(err.erros || {})[0] || err.message; if (err.status === 409) { slots(); } else { draw(); } }
    });
    document.addEventListener('keydown', function esc2(e) { if (!document.body.contains(box)) return document.removeEventListener('keydown', esc2); if (e.key === 'Escape') box.remove(); });
    draw();
  };
})();
