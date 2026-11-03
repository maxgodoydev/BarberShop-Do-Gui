/* Assistente de agendamento (pop-up em etapas, no formato do protótipo):
   Serviços (até 3, sem combinações proibidas) -> Profissional/dia/hora (reserva temporária) -> Produtos (retirada na loja)
   -> Dados -> Privacidade (LGPD) -> Resumo -> envio do link mágico. Sem inline script/style (CSP). */
(async () => {
  const {$, esc, money, hm, api, iso, DIAS} = BG;
  const box = $('#wz'), msg = $('#msg'), LBL = ['Serviços', 'Horário', 'Produtos', 'Seus dados', 'Privacidade', 'Resumo'];
  const LAST = LBL.length - 1;
  let S, last;
  const fdia = d => ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'][d.getDay()] + ' ' + d.toLocaleDateString('pt-BR', {day: '2-digit', month: '2-digit'});
  const monday = o => { const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - ((d.getDay() + 6) % 7) + o * 7); return d; };
  let svcs = [], products = [], rules = {maxServicos: 3, maxMinutos: 200, exclusoes: {}};
  try { svcs = await api('/api/public/services'); }
  catch (e) { msg.hidden = false; msg.className = 'notice err'; msg.textContent = e.message + ' Tente recarregar a página.'; return; }
  try { products = await api('/api/public/products'); } catch (_) { products = []; }
  try { rules = await api('/api/public/booking-rules'); } catch (_) { /* o servidor valida de novo */ }
  const pre = Number(new URLSearchParams(location.search).get('servico')) || null;

  const mins = () => S.sel.reduce((a, s) => a + s.duracaoMinutos, 0);
  const total = () => S.sel.reduce((a, s) => a + Number(s.preco), 0);
  const ids = () => S.sel.map(s => s.id);
  const excluded = s => S.sel.some(x => (rules.exclusoes[x.id] || []).includes(s.id));
  const blocked = s => !S.sel.some(x => x.id === s.id) && (excluded(s) || S.sel.length >= rules.maxServicos || mins() + s.duracaoMinutos > rules.maxMinutos);

  const open = () => {
    last = document.activeElement;
    const first = svcs.find(x => x.id === pre);
    S = {step: 0, sel: first ? [first] : [], alergia: '', week: {}, pro: null, proName: '', day: null, time: null, hold: null, timer: null, err: '',
      ask: null, prod: [], f: {nome: '', sobrenome: '', email: '', telefone: ''}, aceite: false};
    box.classList.add('on'); draw();
  };
  const releaseHold = () => {
    clearInterval(S && S.timer);
    if (S && S.hold) { api('/api/public/holds/release', {method: 'POST', body: {reservaId: S.hold.reservaId, token: S.hold.token}}).catch(() => {}); S.hold = null; }
  };
  const close = () => { if (S && S.step < 7) releaseHold(); clearInterval(S && S.timer); box.classList.remove('on'); box.innerHTML = ''; if (last) last.focus(); };
  $('#abrir').addEventListener('click', open);
  document.addEventListener('click', e => { if (e.target.closest('[data-open]')) { e.preventDefault(); open(); } });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && box.classList.contains('on')) close(); });

  async function loadWeek() {
    S.loading = true; draw();
    const today = iso(new Date());
    for (const off of [0, 1]) {
      const days = [0, 1, 2, 3, 4, 5].map(i => { const d = monday(off); d.setDate(d.getDate() + i); return d; }).filter(d => iso(d) >= today);
      const res = await Promise.all(days.map(d => api('/api/public/availability?servicoIds=' + ids().join(',') + '&data=' + iso(d)).then(g => [d, g]).catch(() => [d, []])));
      S.week = {}; S.days = days; S.next = off === 1;
      for (const [d, g] of res) for (const x of g) if (x.horarios.length) { (S.week[x.profissionalId] ||= {name: x.profissional, days: {}}).days[iso(d)] = x.horarios; }
      if (Object.keys(S.week).length) break;
    }
    S.loading = false; draw();
  }
  function startTimer() {
    const end = new Date(S.hold.expiraEm).getTime(); clearInterval(S.timer);
    S.timer = setInterval(() => {
      const left = Math.max(0, Math.round((end - Date.now()) / 1000)); const t = $('#wz-t');
      if (t) t.textContent = '⏱ ' + String(Math.floor(left / 60)).padStart(2, '0') + ':' + String(left % 60).padStart(2, '0');
      if (!left) { clearInterval(S.timer); S.hold = null; S.step = 1; S.time = null; S.err = 'O tempo da reserva terminou e o horário foi liberado. Escolha novamente.'; loadWeek(); }
    }, 1000);
  }
  async function reserve() {
    const b = box.querySelector('[data-n]'); b.disabled = true;
    try {
      S.hold = await api('/api/public/holds', {method: 'POST', body: {servicoIds: ids(), profissionalId: Number(S.pro), inicio: S.day + 'T' + S.time + ':00'}});
      S.proName = S.week[S.pro] ? S.week[S.pro].name : ''; startTimer(); S.step = 2; S.err = ''; draw();
    } catch (e) { S.err = e.message; S.time = null; loadWeek(); }
  }
  async function send() {
    const b = box.querySelector('[data-n]'); b.disabled = true; b.textContent = 'Enviando…';
    const f = S.f;
    try {
      S.res = await api('/api/public/bookings', {method: 'POST', body: {reservaId: S.hold.reservaId, token: S.hold.token, nome: f.nome.trim(), sobrenome: f.sobrenome.trim(), email: f.email.trim(),
        telefone: f.telefone.trim() || null, alergias: S.alergia.trim() || null, observacao: null, produtos: S.prod.map(p => ({produtoId: p.id, quantidade: 1})), aceitePrivacidade: S.aceite}});
      clearInterval(S.timer); S.hold = null; S.step = 7; S.err = ''; draw();
    } catch (e) {
      if (e.status === 410 || e.status === 404) { clearInterval(S.timer); S.hold = null; S.step = 1; S.time = null; S.err = e.message; loadWeek(); }
      else { S.err = e.message + Object.values(e.erros || {}).map(v => ' ' + v).join(''); S.step = e.status === 403 ? LAST : 3; draw(); }
    }
  }
  const emailOk = v => /^\S+@\S+\.\S+$/.test(v.trim());
  const dataOk = () => S.f.nome.trim().length > 1 && S.f.sobrenome.trim().length > 1 && emailOk(S.f.email);

  const LGPD = '<div class="wz-lgpd"><p><b>🔒 Seus dados e a LGPD</b></p>'
    + '<p>Para fazer seu agendamento, a BarberShop do Gui usa seus dados <b>somente para esta finalidade</b>: atender o pedido e falar com você sobre ele.</p>'
    + '<ul><li><b>O que guardamos:</b> nome, sobrenome, e-mail, telefone (se informar), serviços, horário, produtos reservados e a <b>alergia</b>, se você informar (dado de saúde, usado só para o atendimento seguro).</li>'
    + '<li><b>Segurança:</b> não existe senha de cliente; o acesso é por link de uso único enviado ao seu e-mail, com validade curta. Guardamos apenas uma versão criptografada (hash) do link. Só a equipe autorizada vê seus dados.</li>'
    + '<li><b>Confirmação:</b> seu cadastro e o horário só valem depois que você abrir o link enviado ao e-mail. Sem confirmar, o pedido expira e o horário volta para a agenda.</li>'
    + '<li><b>Seus direitos:</b> você pode consultar, corrigir e <b>excluir seu cadastro</b> quando quiser, na página “Consultar agendamento” (a exclusão só é possível sem agendamentos ativos). Ao excluir, nome, e-mail, telefone, observações e alergias são apagados.</li>'
    + '<li><b>Não compartilhamos</b> seus dados com terceiros para publicidade.</li></ul>'
    + '<p>Leia a <a href="/privacidade.html" target="_blank" rel="noopener">política de privacidade completa</a>.</p></div>';

  function draw() {
    let b = '', ok = false;
    if (S.step === 0) {
      ok = S.sel.length > 0;
      b = '<h3>✂️ Qual serviço deseja?</h3>' + svcs.map(s => {
        const on = S.sel.some(x => x.id === s.id), off = blocked(s);
        return '<button type="button" class="wz-o ' + (on ? 's' : '') + (off ? ' off' : '') + '" data-s="' + s.id + '" ' + (off ? 'disabled' : '') + '><b>' + esc(s.nome) + '</b><span>' + money(s.preco) + (s.ilustrativo ? '*' : '') + '<br>⏱ ' + s.duracaoMinutos + ' min</span></button>';
      }).join('')
        + '<p class="wz-hint">Até ' + rules.maxServicos + ' serviços e ' + rules.maxMinutos + ' min por visita. Combos e cortes semelhantes não se combinam.</p>'
        + (S.sel.some(x => x.requerAvaliacao) ? '<div class="notice">Um dos serviços exige avaliação prévia: tempo e valor podem mudar.</div>' : '')
        + '<label for="w-al">🩺 Possui alguma alergia? <small class="muted">(opcional)</small></label><input class="wz-in" id="w-al" maxlength="300" placeholder="Ex.: amônia, tintura, látex… ou deixe em branco" value="' + esc(S.alergia) + '">'
        + '<div class="wz-bar"><span>⏱ ' + mins() + ' min</span><b>' + money(total()) + '</b></div>';
    }
    if (S.step === 1) {
      if (S.loading) b = '<h3>💈 Buscando horários…</h3><div class="skeleton"></div>';
      else if (!Object.keys(S.week).length) b = '<h3>💈 Sem horários</h3><p class="muted">Nenhum profissional tem horário livre para essa combinação de serviços nas próximas duas semanas. Volte e tente menos serviços.</p>';
      else {
        const w = S.week[S.pro], ds = w ? S.days.filter(d => w.days[iso(d)]) : [];
        b = '<h3>💈 Escolha o profissional</h3>' + (S.next ? '<div class="notice">⚠️ Semana atual cheia: liberamos a próxima semana.</div>' : '')
          + '<div class="wz-c">' + Object.entries(S.week).map(([id, x]) => '<button type="button" class="' + (S.pro == id ? 's' : '') + '" data-p="' + id + '">' + esc(x.name) + '</button>').join('') + '</div>'
          + (w ? '<div class="wz-c">' + ds.map(d => '<button type="button" class="' + (S.day === iso(d) ? 's' : '') + '" data-d="' + iso(d) + '">' + fdia(d) + '</button>').join('') + '</div>' : '')
          + (w && S.day && w.days[S.day] ? '<div class="wz-c">' + w.days[S.day].map(h => '<button type="button" class="' + (S.time === h ? 's' : '') + '" data-t="' + h + '">' + h + '</button>').join('') + '</div>' : '')
          + '<p class="wz-hint">Mostramos só quem executa todos os serviços escolhidos (' + mins() + ' min). Ao continuar, o horário fica reservado para você.</p>';
      }
      ok = !!S.time;
    }
    if (S.step === 2) {
      b = '<h3>🧴 Quer reservar algum produto?</h3><div class="wz-yn"><button type="button" class="btn ' + (S.ask === true ? '' : 'ghost') + '" data-y="1">Sim</button><button type="button" class="btn ' + (S.ask === false ? '' : 'ghost') + '" data-y="0">Não</button></div>';
      if (S.ask) b += (products.length ? products.map(p => '<button type="button" class="wz-o ' + (S.prod.some(x => x.id === p.id) ? 's' : '') + (p.disponivel ? '' : ' off') + '" data-pr="' + p.id + '" ' + (p.disponivel ? '' : 'disabled') + '><b>' + esc(p.nome) + ' <small class="muted">' + esc(p.marca) + '</small></b><span>' + money(p.preco) + (p.ilustrativo ? '*' : '') + (p.disponivel ? '' : '<br>esgotado') + '</span></button>').join('') : '<p class="muted">Nenhum produto disponível no momento.</p>')
        + '<p class="wz-hint">Você reserva agora, <b>retira e paga na barbearia</b> no dia do atendimento.</p>';
      ok = S.ask === false || (S.ask === true && S.prod.length > 0);
    }
    if (S.step === 3) {
      const f = S.f; ok = dataOk();
      b = '<h3>📧 Seus dados</h3><form id="wz-f" novalidate><label for="w-n">Nome *</label><input class="wz-in" id="w-n" maxlength="50" autocomplete="given-name" value="' + esc(f.nome) + '">'
        + '<label for="w-s">Sobrenome *</label><input class="wz-in" id="w-s" maxlength="50" autocomplete="family-name" value="' + esc(f.sobrenome) + '">'
        + '<label for="w-e">E-mail * <small class="muted">(enviaremos o link mágico de confirmação)</small></label><input class="wz-in" id="w-e" type="email" maxlength="160" autocomplete="email" value="' + esc(f.email) + '">'
        + '<label for="w-t">Telefone <small class="muted">(opcional)</small></label><input class="wz-in" id="w-t" type="tel" maxlength="20" autocomplete="tel" value="' + esc(f.telefone) + '"></form>'
        + '<p class="wz-hint">Sem senha: o cadastro é confirmado quando você abrir o link enviado ao seu e-mail.</p>';
    }
    if (S.step === 4) { ok = S.aceite; b = '<h3>🔒 Privacidade e seus dados</h3>' + LGPD + '<label class="wz-l"><input type="checkbox" id="w-a" ' + (S.aceite ? 'checked' : '') + '><span>Li e concordo com o uso dos meus dados para este agendamento. *</span></label>'; }
    if (S.step === 5) {
      ok = true; const d = new Date(S.hold.inicio), f = S.f, pv = S.prod.reduce((a, p) => a + Number(p.preco), 0);
      b = '<h3>📋 Confira seu pedido</h3><table class="wz-s">' + S.sel.map(s => '<tr><td>✂️ ' + esc(s.nome) + ' <small class="muted">' + s.duracaoMinutos + ' min</small></td><td>' + money(s.preco) + '</td></tr>').join('')
        + S.prod.map(p => '<tr><td>🧴 ' + esc(p.nome) + ' <small class="muted">retirar na loja</small></td><td>' + money(p.preco) + '</td></tr>').join('')
        + '<tr><td><b>Total (pagamento na barbearia)</b></td><td><b>' + money(total() + pv) + '</b></td></tr></table>'
        + '<p>💈 <b>' + esc(S.proName) + '</b> · 📅 ' + DIAS[d.getDay() || 7] + ', ' + d.toLocaleDateString('pt-BR') + ' às <b>' + hm(S.hold.inicio) + '</b> (' + S.hold.duracaoMinutos + ' min, até ' + hm(S.hold.fim) + ')<br>'
        + '👤 ' + esc(f.nome.trim() + ' ' + f.sobrenome.trim()) + '<br>📧 ' + esc(f.email.trim()) + (f.telefone.trim() ? '<br>📞 ' + esc(f.telefone.trim()) : '')
        + '<br>🩺 Alergia: ' + (S.alergia.trim() ? esc(S.alergia.trim()) : 'nenhuma informada') + '<br>🔒 Política de privacidade: <b>aceita</b></p>'
        + '<p class="wz-hint">O cadastro e o horário só são confirmados quando você abrir o link enviado ao e-mail (vale 30 min).<br>Cancelamento online: até 1 dia antes. Falta ou atraso de 30 min pode bloquear o cadastro até a equipe liberar.</p>';
    }
    if (S.step === 7) b = '<div class="wz-ok">📨</div><h3>Falta só confirmar!</h3><p>' + esc(S.res.mensagem) + '</p><p class="wz-hint">Pedido nº ' + Number(S.res.id) + '. Não recebeu? Confira o spam. Se o link vencer, é só agendar de novo.</p><div class="wz-f"><a class="btn ghost" href="/consulta.html">Consultar agendamento</a><button type="button" class="btn" data-x>Fechar</button></div>';
    const nav = S.step < 7 ? '<div class="wz-f"><button type="button" class="btn ghost" data-b ' + (S.step ? '' : 'disabled') + '>← Voltar</button><button type="button" class="btn" data-n ' + (ok ? '' : 'disabled') + '>' + (S.step === LAST ? 'Confirmar ✔' : 'Continuar →') + '</button></div>' : '';
    box.innerHTML = '<div class="wz-m"><div class="wz-h"><h2 id="wz-ti">' + (S.step < 7 ? LBL[S.step] : 'Pedido enviado') + '</h2><span class="wz-t" id="wz-t"></span><button class="wz-x" type="button" data-x aria-label="Fechar">×</button></div><div class="wz-p">'
      + LBL.map((_, i) => '<i class="' + (i < S.step ? 'd' : i === S.step ? 'c' : '') + '"></i>').join('') + '</div><div class="wz-b">' + (S.err ? '<div class="notice err" role="alert">' + esc(S.err) + '</div>' : '') + b + nav + '</div></div>';
    const f = $('#wz-f');
    if (f) { const up = () => { S.f = {nome: $('#w-n').value, sobrenome: $('#w-s').value, email: $('#w-e').value, telefone: $('#w-t').value}; box.querySelector('[data-n]').disabled = !dataOk(); }; f.addEventListener('input', up); f.addEventListener('submit', e => e.preventDefault()); }
    const al = $('#w-al'); if (al) al.addEventListener('input', () => { S.alergia = al.value; });
    const ac = $('#w-a'); if (ac) ac.addEventListener('change', () => { S.aceite = ac.checked; box.querySelector('[data-n]').disabled = !S.aceite; });
    if (S.hold) { const t = $('#wz-t'); if (t) t.textContent = '⏱ ' + Math.max(0, Math.floor((new Date(S.hold.expiraEm) - Date.now()) / 60000)) + ' min'; }
    const fc = box.querySelector('.wz-o.s,.wz-c .s,[data-n]:not(:disabled),[data-x]'); if (fc && !f && !al) fc.focus({preventScroll: true});
  }
  box.addEventListener('click', e => {
    if (e.target === box) return close(); const t = e.target.closest('button'); if (!t) return; const D = t.dataset;
    if ('x' in D) return close();
    if ('b' in D) {
      S.err = '';
      if (S.step === 2) { releaseHold(); S.step = 1; S.time = null; return loadWeek(); }
      if (S.step === 1) { S.step = 0; return draw(); }
      S.step--; return draw();
    }
    if ('n' in D) {
      S.err = '';
      if (S.step === 0) { S.step = 1; S.pro = S.day = S.time = null; return loadWeek(); }
      if (S.step === 1) return reserve();
      if (S.step === LAST) return send();
      S.step++; return draw();
    }
    if (D.s) { const s = svcs.find(x => x.id === Number(D.s)), i = S.sel.findIndex(x => x.id === s.id); if (i < 0) S.sel.push(s); else S.sel.splice(i, 1); S.pro = S.day = S.time = null; }
    if (D.p) { S.pro = D.p; S.day = S.time = null; const ds = Object.keys(S.week[D.p].days); if (ds.length === 1) S.day = ds[0]; }
    if (D.d) { S.day = D.d; S.time = null; } if (D.t) S.time = D.t;
    if (D.y) { S.ask = D.y === '1'; if (!S.ask) S.prod = []; }
    if (D.pr) { const p = products.find(x => x.id === Number(D.pr)), i = S.prod.findIndex(x => x.id === p.id); if (i < 0) S.prod.push(p); else S.prod.splice(i, 1); }
    draw();
  });
  if (pre) open();
})();
