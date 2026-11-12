/* Painel de BI: gráficos em SVG puro (sem bibliotecas, compatível com a CSP). Dados vêm de /api/admin/bi. */
window.renderBI = async BG => {
  const {$, esc, api, toast} = BG;
  const R = v => 'R$ ' + Number(v).toLocaleString('pt-BR', {minimumFractionDigits: 2, maximumFractionDigits: 2});
  const N = v => Number(v).toLocaleString('pt-BR');
  const P = v => Number(v).toLocaleString('pt-BR', {maximumFractionDigits: 1}) + '%';
  const DIA = ['', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'], DIAL = ['', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado', 'domingo'];
  const ST = {CONCLUIDO: ['Concluído', '#157347'], CONFIRMADO: ['Confirmado', '#1f5fbf'], SOLICITADO: ['Aguardando', '#f0b429'], CANCELADO: ['Cancelado', '#d62839'], FALTOU: ['Faltou', '#7a1420'], EXPIRADO: ['Expirado', '#9ca3af']};
  const RED = '#d62839', BLUE = '#1f5fbf', GRID = '#e5e7eb';
  const cut = (t, n) => String(t).length > n ? String(t).slice(0, n - 1) + '…' : String(t);
  const nice = m => { const p = Math.pow(10, Math.floor(Math.log10(Math.max(m, 1)))); return Math.ceil(m / p) * p; };
  const fig = (title, summary, svg, cls = '') => '<figure class="card ' + cls + '"><figcaption>' + esc(title) + '</figcaption><div role="img" aria-label="' + esc(title + ': ' + summary) + '">' + svg + '</div></figure>';
  const empty = '<p class="muted">Sem dados no período.</p>';

  function line(items) { // [{l, v}]
    if (!items.length) return empty;
    const W = 640, H = 230, L = 58, B = 30, T = 12, max = nice(Math.max(...items.map(i => i.v))), n = items.length;
    const x = i => L + (n === 1 ? (W - L - 10) / 2 : i * (W - L - 10) / (n - 1)), y = v => T + (H - T - B) * (1 - v / max);
    let s = '<svg viewBox="0 0 ' + W + ' ' + H + '" aria-hidden="true">';
    for (let g = 0; g <= 4; g++) { const v = max * g / 4; s += '<line x1="' + L + '" x2="' + (W - 10) + '" y1="' + y(v) + '" y2="' + y(v) + '" stroke="' + GRID + '"/><text x="' + (L - 6) + '" y="' + (y(v) + 4) + '" text-anchor="end">' + N(Math.round(v)) + '</text>'; }
    const pts = items.map((it, i) => x(i) + ',' + y(it.v)).join(' ');
    s += '<polygon points="' + L + ',' + y(0) + ' ' + pts + ' ' + x(n - 1) + ',' + y(0) + '" fill="' + RED + '" opacity=".12"/><polyline points="' + pts + '" fill="none" stroke="' + RED + '" stroke-width="3" stroke-linejoin="round"/>';
    const step = Math.ceil(n / 8);
    items.forEach((it, i) => { s += '<circle cx="' + x(i) + '" cy="' + y(it.v) + '" r="4" fill="#fff" stroke="' + RED + '" stroke-width="2"><title>' + esc(it.l + ': ' + R(it.v)) + '</title></circle>'; if (i % step === 0) s += '<text x="' + x(i) + '" y="' + (H - 8) + '" text-anchor="middle">' + esc(it.l) + '</text>'; });
    return s + '</svg>';
  }
  function vbars(items, fmt) {
    if (!items.length) return empty;
    const W = 640, H = 230, L = 40, B = 28, T = 20, max = nice(Math.max(...items.map(i => i.v))), bw = (W - L - 10) / items.length;
    let s = '<svg viewBox="0 0 ' + W + ' ' + H + '" aria-hidden="true">';
    for (let g = 0; g <= 4; g++) { const v = max * g / 4, yy = T + (H - T - B) * (1 - g / 4); s += '<line x1="' + L + '" x2="' + (W - 10) + '" y1="' + yy + '" y2="' + yy + '" stroke="' + GRID + '"/><text x="' + (L - 6) + '" y="' + (yy + 4) + '" text-anchor="end">' + N(Math.round(v)) + '</text>'; }
    const top = Math.max(...items.map(i => i.v));
    items.forEach((it, i) => { const h = (H - T - B) * it.v / max, xx = L + i * bw + bw * .18; s += '<rect x="' + xx + '" y="' + (H - B - h) + '" width="' + bw * .64 + '" height="' + h + '" rx="5" fill="' + (it.v === top ? RED : BLUE) + '"><title>' + esc(it.l + ': ' + fmt(it.v)) + '</title></rect><text x="' + (xx + bw * .32) + '" y="' + (H - B - h - 5) + '" text-anchor="middle">' + fmt(it.v) + '</text><text x="' + (xx + bw * .32) + '" y="' + (H - 8) + '" text-anchor="middle">' + esc(it.l) + '</text>'; });
    return s + '</svg>';
  }
  function hbars(items, fmt) {
    if (!items.length) return empty;
    const W = 640, row = 30, H = items.length * row + 6, L = 210, max = Math.max(...items.map(i => i.v), 1);
    let s = '<svg viewBox="0 0 ' + W + ' ' + H + '" aria-hidden="true">';
    items.forEach((it, i) => { const w = (W - L - 90) * it.v / max, yy = i * row + 4; s += '<text x="' + (L - 8) + '" y="' + (yy + 17) + '" text-anchor="end">' + esc(cut(it.l, 30)) + '</text><rect x="' + L + '" y="' + yy + '" width="' + Math.max(w, 2) + '" height="20" rx="5" fill="' + (i === 0 ? RED : BLUE) + '"><title>' + esc(it.l + ': ' + fmt(it.v)) + '</title></rect><text x="' + (L + w + 8) + '" y="' + (yy + 15) + '">' + fmt(it.v) + '</text>'; });
    return s + '</svg>';
  }
  function donut(items) { // [{l, v, c}]
    const tot = items.reduce((a, i) => a + i.v, 0); if (!tot) return empty;
    const r = 62, C = 2 * Math.PI * r; let off = 0, s = '<svg viewBox="0 0 640 190" aria-hidden="true"><g transform="rotate(-90 100 95)">';
    items.forEach(i => { const len = C * i.v / tot; s += '<circle cx="100" cy="95" r="' + r + '" fill="none" stroke="' + i.c + '" stroke-width="30" stroke-dasharray="' + len + ' ' + (C - len) + '" stroke-dashoffset="' + (-off) + '"><title>' + esc(i.l + ': ' + N(i.v)) + '</title></circle>'; off += len; });
    s += '</g><text x="100" y="92" text-anchor="middle" font-size="22" font-weight="700">' + N(tot) + '</text><text x="100" y="110" text-anchor="middle">pedidos</text>';
    items.forEach((i, k) => { s += '<rect x="240" y="' + (14 + k * 28) + '" width="14" height="14" rx="3" fill="' + i.c + '"/><text x="262" y="' + (26 + k * 28) + '">' + esc(i.l) + ': ' + N(i.v) + ' (' + Math.round(100 * i.v / tot) + '%)</text>'; });
    return s + '</svg>';
  }
  function heat(cells) {
    if (!cells.length) return empty;
    const hs = cells.map(c => c.hora), h0 = Math.min(...hs), h1 = Math.max(...hs), nh = h1 - h0 + 1, W = 640, L = 44, cw = (W - L - 6) / nh, ch = 26, H = 7 * ch + 28, max = Math.max(...cells.map(c => c.quantidade)), m = {};
    cells.forEach(c => { m[c.dia + '-' + c.hora] = c.quantidade; });
    let s = '<svg viewBox="0 0 ' + W + ' ' + H + '" aria-hidden="true">';
    for (let d = 1; d <= 7; d++) { s += '<text x="' + (L - 6) + '" y="' + ((d - 1) * ch + 18) + '" text-anchor="end">' + DIA[d] + '</text>'; for (let h = h0; h <= h1; h++) { const v = m[d + '-' + h] || 0; s += '<rect x="' + (L + (h - h0) * cw) + '" y="' + ((d - 1) * ch + 2) + '" width="' + (cw - 2) + '" height="' + (ch - 3) + '" rx="4" fill="' + (v ? RED : GRID) + '" opacity="' + (v ? (0.18 + 0.82 * v / max).toFixed(2) : 0.5) + '"><title>' + esc(DIAL[d] + ', ' + h + 'h: ' + v + ' pedidos') + '</title></rect>'; } }
    for (let h = h0; h <= h1; h += Math.ceil(nh / 12)) s += '<text x="' + (L + (h - h0) * cw + cw / 2) + '" y="' + (H - 6) + '" text-anchor="middle">' + h + 'h</text>';
    return s + '</svg>';
  }
  const MIN_BASE = 20; // atendimentos mínimos no período anterior para exibir variação %
  const delta = (cur, prev, prevN) => {
    if (!prev) return cur ? '<em class="flat">sem período anterior</em>' : '<em class="flat">sem base de comparação</em>';
    if (prevN !== undefined && prevN < MIN_BASE) return '<em class="flat">base anterior pequena (' + N(prevN) + ' atend.)</em>';
    const d = (cur - prev) / prev * 100, extremo = Math.abs(d) > 200;
    return '<em class="' + (d > 0.5 ? 'up' : d < -0.5 ? 'down' : 'flat') + '">' + (d > 0.5 ? '▲ ' : d < -0.5 ? '▼ ' : '● ') + P(Math.abs(d)) + ' vs. período anterior' + (extremo ? ' (confira se o período anterior está completo)' : '') + '</em>';
  };

  let data;
  async function load() {
    try { data = await api('/api/admin/bi?dias=' + $('#per').value); } catch (e) { toast(e.message, true); return; }
    const k = data.kpis;
    $('#gerado').textContent = 'Atualizado em ' + new Date(data.geradoEm).toLocaleString('pt-BR') + '.';
    $('#bi-vazio').hidden = k.pedidos > 0;
    const card = (t, v, extra) => '<div class="bi-k"><small>' + t + '</small><strong>' + v + '</strong>' + (extra || '') + '</div>';
    $('#kpis').innerHTML = card('Receita estimada (concluídos)', R(k.receita), delta(k.receita, k.receitaAnterior, k.atendimentosAnterior)) + card('Atendimentos', N(k.atendimentos), delta(k.atendimentos, k.atendimentosAnterior, k.atendimentosAnterior)) + card('Ticket médio', R(k.ticketMedio))
      + card('Cancelamentos', P(k.taxaCancelamento), '<em class="flat">dos pedidos (sem reservas expiradas)</em>') + card('Faltas', P(k.taxaFalta), '<em class="flat">dos agendados</em>') + card('Clientes novos', N(k.clientesNovos)) + card('Clientes recorrentes', P(k.clientesRecorrentesPct), '<em class="flat">2+ visitas no período</em>') + card('Produtos vendidos', R(k.receitaProdutos));
    const dias = {}, horas = {}; data.diaSemana.forEach(d => { dias[d.dia] = d.quantidade; }); data.mapaCalor.forEach(c => { horas[c.hora] = (horas[c.hora] || 0) + c.quantidade; });
    const topD = data.diaSemana.slice().sort((a, b) => b.quantidade - a.quantidade)[0], topH = Object.entries(horas).sort((a, b) => b[1] - a[1])[0], sv = data.servicos[0], pf = data.profissionais[0];
    const tip = [];
    if (topD) tip.push('Dia mais cheio: <strong>' + DIAL[topD.dia] + '</strong> (' + N(topD.quantidade) + ' pedidos).');
    if (topH) tip.push('Horário de pico: <strong>' + topH[0] + 'h</strong>.');
    if (sv) tip.push('Serviço que mais fatura: <strong>' + esc(sv.nome) + '</strong> (' + Math.round(100 * sv.receita / Math.max(k.receita, 1)) + '% da receita).');
    if (pf) tip.push('Profissional com maior receita: <strong>' + esc(pf.nome) + '</strong>.');
    if (k.taxaCancelamento > 15) tip.push('<span class="down">Cancelamentos acima de 15%</span>: avalie lembrete por e-mail um dia antes.');
    $('#ins').innerHTML = '<h2>Destaques</h2>' + (tip.length ? '<ul>' + tip.map(t => '<li>' + t + '</li>').join('') + '</ul>' : '<p class="muted">Sem dados suficientes.</p>');
    const sem = data.semanal.map(s => ({l: s.semana.slice(8, 10) + '/' + s.semana.slice(5, 7), v: Number(s.receita)}));
    $('#graf').innerHTML = fig('Receita por semana', 'evolução semanal em reais', line(sem), 'wide')
      + fig('Pedidos por dia da semana', 'quantidade por dia', vbars([1, 2, 3, 4, 5, 6, 7].map(d => ({l: DIA[d], v: dias[d] || 0})), N))
      + fig('Situação dos pedidos', 'divisão por status', donut(data.status.map(s => ({l: (ST[s.status] || [s.status])[0], v: s.quantidade, c: (ST[s.status] || [0, '#6b7280'])[1]}))))
      + fig('Receita por serviço', 'ranking de serviços', hbars(data.servicos.map(s => ({l: s.nome, v: Number(s.receita)})), R))
      + fig('Receita por profissional', 'ranking de profissionais', hbars(data.profissionais.map(s => ({l: s.nome, v: Number(s.receita)})), R))
      + fig('Mapa de calor: dia × hora', 'quanto mais escuro, mais pedidos', heat(data.mapaCalor), 'wide');
  }
  $('#per').addEventListener('change', load);
  $('#print').addEventListener('click', () => window.print());
  $('#csv').addEventListener('click', () => {
    if (!data) return; const k = data.kpis, f = v => String(v).replace('.', ','), rows = [['Relatório BarberShop do Gui — últimos ' + data.periodoDias + ' dias'], [], ['Indicador', 'Valor']];
    [['Receita', k.receita], ['Atendimentos', k.atendimentos], ['Ticket médio', k.ticketMedio], ['Cancelamento %', k.taxaCancelamento], ['Faltas %', k.taxaFalta], ['Clientes novos', k.clientesNovos], ['Recorrentes %', k.clientesRecorrentesPct], ['Produtos (receita)', k.receitaProdutos]].forEach(r => rows.push([r[0], f(r[1])]));
    rows.push([], ['Semana', 'Receita', 'Atendimentos']); data.semanal.forEach(s => rows.push([s.semana, f(s.receita), s.atendimentos]));
    rows.push([], ['Serviço', 'Quantidade', 'Receita']); data.servicos.forEach(s => rows.push([s.nome, s.quantidade, f(s.receita)]));
    rows.push([], ['Profissional', 'Quantidade', 'Receita']); data.profissionais.forEach(s => rows.push([s.nome, s.quantidade, f(s.receita)]));
    rows.push([], ['Dia da semana', 'Pedidos']); data.diaSemana.forEach(d => rows.push([DIAL[d.dia], d.quantidade]));
    const csv = '\ufeff' + rows.map(r => r.map(c => '"' + String(c).replace(/"/g, '""') + '"').join(';')).join('\r\n');
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([csv], {type: 'text/csv;charset=utf-8'})); a.download = 'relatorio-bi-' + data.periodoDias + 'd.csv'; document.body.appendChild(a); a.click(); a.remove();
  });
  await load();
};
