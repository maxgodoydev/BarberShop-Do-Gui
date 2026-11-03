/* Páginas públicas: início e serviços. */
(async () => {
  const {$, esc, money, api, state, shopInfo} = BG;
  const info = await shopInfo(); const box = $('#contato');
  if (box) {
    const parts = [];
    if (info.endereco) parts.push('<p><strong>Endereço:</strong> ' + esc(info.endereco) + '</p>');
    if (info.horarios) parts.push('<p><strong>Horários:</strong> ' + esc(info.horarios) + '</p>');
    if (info.telefone) parts.push('<p><strong>Telefone:</strong> ' + esc(info.telefone) + '</p>');
    const links = [];
    if (info.whatsappUrl) links.push('<a class="btn blue small" rel="noopener" target="_blank" href="' + esc(info.whatsappUrl) + '">WhatsApp</a>');
    if (info.mapsUrl) links.push('<a class="btn ghost small" rel="noopener" target="_blank" href="' + esc(info.mapsUrl) + '">Abrir no Google Maps</a>');
    if (info.instagramUrl) links.push('<a class="btn ghost small" rel="noopener" target="_blank" href="' + esc(info.instagramUrl) + '">Instagram</a>');
    const mapa = info.endereco ? '<div class="mapa"><iframe title="Mapa com a localização da BarberShop do Gui" loading="lazy" referrerpolicy="no-referrer-when-downgrade" allowfullscreen src="'
      + esc('https://www.google.com/maps?q=' + encodeURIComponent(info.endereco) + '&hl=pt-BR&z=16&output=embed') + '"></iframe></div>' : '';
    box.classList.add('contato-card');
    box.innerHTML = '<div class="contato-grid"><div class="contato-info">' + (parts.join('') || '<p class="muted">Endereço, telefone e horários ainda não foram informados pelo proprietário.</p>')
      + '<div class="row">' + links.join('') + '</div></div>' + mapa + '</div>';
  }
  const grid = $('#servicos'); if (!grid) return;
  state(grid, 'loading');
  try {
    const list = await api('/api/public/services'); const limit = Number(grid.dataset.limit || 999);
    if (!list.length) return state(grid, 'empty', 'Nenhum serviço disponível no momento.');
    grid.innerHTML = list.slice(0, limit).map(s => '<article class="card"><h3>' + esc(s.nome) + '</h3><p class="muted small">' + esc(s.descricao) + '</p>'
      + '<p><span class="price">' + money(s.preco) + (s.ilustrativo ? '*' : '') + '</span> <span class="tag">' + s.duracaoMinutos + ' min' + (s.requerAvaliacao ? ' (estimado)' : '') + '</span></p>'
      + (s.requerAvaliacao ? '<p class="tag warn">Exige avaliação prévia: tempo e valor podem mudar</p>' : '')
      + '<a class="btn small" href="/agendar.html?servico=' + Number(s.id) + '">Agendar</a></article>').join('');
  } catch (e) { state(grid, 'error', e.message); }
})();
