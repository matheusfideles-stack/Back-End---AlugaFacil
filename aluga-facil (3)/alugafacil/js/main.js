const appState = window.AlugaFacil.state;
const appRoleConfig = window.AlugaFacil.roleConfig;
const modules = {
  proprietario: window.AlugaFacil.proprietario,
  inquilino: window.AlugaFacil.inquilino,
  imobiliaria: window.AlugaFacil.imobiliaria,
};

function goToPage(page){
  appState.page = page;
  render();
}

function renderSidebar(){
  const cfg = appRoleConfig[appState.role];
  const sb = document.getElementById('sidebar');
  sb.innerHTML = `
    <div class="who"><strong>${cfg.who.name}</strong>${cfg.who.sub}</div>
    ${cfg.pages.map((p,i)=>`<a class="navlink ${i===appState.page?'active':''}" data-page="${i}">${p}</a>`).join('')}
  `;
  sb.querySelectorAll('.navlink').forEach(el=>{
    el.addEventListener('click', ()=> goToPage(parseInt(el.dataset.page)));
  });
}

function renderMain(){
  const mod = modules[appState.role];
  const main = document.getElementById('main');
  main.innerHTML = mod.render(appState.page);
  mod.wire(appState.page, goToPage);
}

function render(){
  renderSidebar();
  renderMain();
}

document.getElementById('roleSwitch').addEventListener('click', (e)=>{
  const btn = e.target.closest('button');
  if(!btn) return;
  document.querySelectorAll('.role-switch button').forEach(b=>b.classList.remove('active'));
  btn.classList.add('active');
  appState.role = btn.dataset.role;
  appState.page = 0;
  render();
});

render();
