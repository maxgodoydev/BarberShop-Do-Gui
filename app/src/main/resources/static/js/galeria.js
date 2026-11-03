/* Galeria: miniaturas padronizadas 4:3; clique amplia (Esc ou clique fora fecha). Sem inline script (CSP). */
(() => {
  const box = document.createElement('div'); box.className = 'lightbox'; box.setAttribute('role', 'dialog'); box.setAttribute('aria-modal', 'true'); box.setAttribute('aria-label', 'Foto ampliada');
  box.innerHTML = '<button type="button" aria-label="Fechar">×</button><img alt="">'; document.body.appendChild(box);
  const img = box.querySelector('img'); let last = null;
  const close = () => { box.classList.remove('on'); if (last) last.focus(); };
  document.addEventListener('click', e => {
    const b = e.target.closest('.gallery button');
    if (b) { last = b; img.src = b.dataset.full; img.alt = b.querySelector('img').alt; box.classList.add('on'); box.querySelector('button').focus(); return; }
    if (e.target === box || e.target.closest('.lightbox button')) close();
  });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && box.classList.contains('on')) close(); });
})();
