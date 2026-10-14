function meuAluguel(){
  const historico = [
    ['Ago 2026','2.450,00','validado','Validado'],
    ['Jul 2026','2.450,00','validado','Validado'],
    ['Jun 2026','2.450,00','validado','Validado'],
  ];
  return `
    <p class="eyebrow">Meu aluguel</p>
    <h1 class="pagetitle">Apto 302 — Rua das Palmeiras, 88</h1>
    <div class="summary-row">
      <div class="summary-card"><p class="k">Valor do aluguel</p><p class="v">R$ 2.450<small>,00</small></p></div>
      <div class="summary-card"><p class="k">Vencimento</p><p class="v">dia 5</p></div>
      <div class="summary-card"><p class="k">Status deste mês</p><p class="v" style="color:var(--pendente); font-size:18px;">Aguardando comprovante</p></div>
    </div>
    <section class="block">
      <h3>Pagamentos anteriores</h3>
      <div class="ledger">
        ${historico.map(([d,v,cls,label])=>`
          <div class="ledger-row">
            <div class="main-col"><p class="title">${d}</p><p class="sub">Comprovante enviado no dia do vencimento</p></div>
            <span class="tag ${cls}"><span class="dot"></span>${label}</span>
            <div class="amount">R$ ${v}</div>
          </div>
        `).join('')}
      </div>
    </section>
  `;
}

function enviarComprovante(){
  return `
    <p class="eyebrow">Setembro 2026</p>
    <h1 class="pagetitle">Enviar comprovante de pagamento</h1>
    <div class="status-banner" id="uploadBanner">Comprovante enviado — sua imobiliária vai validar em até 1 dia útil.</div>
    <div class="dropzone" id="dropzone">
      <p>Arraste o comprovante aqui</p>
      <span>PDF, JPG ou PNG · até 10MB</span>
      <div style="margin-top:16px;"><button class="btn btn-ghost" id="chooseFileBtn">Escolher arquivo</button></div>
    </div>
    <button class="btn btn-primary" id="sendReceiptBtn">Enviar comprovante</button>
  `;
}

window.AlugaFacil.inquilino = {
  render(page){
    return page === 0 ? meuAluguel() : enviarComprovante();
  },
  wire(page){
    if(page !== 1) return;
    const banner = document.getElementById('uploadBanner');
    const btn = document.getElementById('sendReceiptBtn');
    btn.addEventListener('click', ()=>{
      banner.classList.add('show');
      btn.disabled = true;
      btn.textContent = 'Comprovante enviado';
    });
  }
};
