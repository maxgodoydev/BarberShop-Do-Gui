(() => {
  const {$, api, show} = BG;
  $('#form-login').addEventListener('submit', async e => {
    e.preventDefault(); const f = e.target.elements; const b = $('#btn'); b.disabled = true;
    try {
      await api('/api/csrf'); // garante cookie/token
      const csrf = await (await fetch('/api/csrf', {credentials: 'same-origin'})).json();
      const r = await fetch('/api/auth/login', {method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token},
        body: new URLSearchParams({username: f.username.value, password: f.password.value})});
      if (r.ok) { const me = await api('/api/auth/me').catch(() => ({})); location.href = me.papel === 'RECEPCAO' ? '/admin/agenda.html' : '/admin/painel.html'; return; }
      show($('#msg'), 'Usuário ou senha inválidos, ou conta temporariamente bloqueada. Tente novamente em alguns minutos.', 'err');
    } catch (err) { show($('#msg'), BG.esc(err.message), 'err'); } finally { b.disabled = false; }
  });
})();
