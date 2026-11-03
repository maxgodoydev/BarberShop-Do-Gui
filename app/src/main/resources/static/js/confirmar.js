/* Link mágico do e-mail: confirma o horário E abre o espaço do cliente, sem senha.
   O token vem no fragmento (#token=...) e só sai da barra de endereço depois da resposta (assim F5 não perde o link por erro de rede). */
(async () => {
  const {$, esc, api} = BG, msg = $('#msg'), acoes = $('#acoes');
  const m = /#token=([\w-]+)/.exec(location.hash);
  if (!m) { msg.className = 'notice err'; msg.textContent = 'Link incompleto. Abra o link exatamente como veio no e-mail.'; acoes.hidden = false; return; }
  try {
    const r = await api('/api/public/confirm', {method: 'POST', body: {token: m[1]}});
    history.replaceState(null, '', location.pathname);
    msg.className = 'notice ok'; msg.innerHTML = '✅ ' + esc(r.mensagem) + ' Levando você ao seu espaço…';
    setTimeout(() => { location.href = '/consulta.html'; }, 1800);
  } catch (e) {
    history.replaceState(null, '', location.pathname);
    msg.className = 'notice err'; msg.textContent = e.message;
  }
  acoes.hidden = false;
})();
