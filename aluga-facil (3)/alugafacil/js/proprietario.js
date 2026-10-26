const imoveisPadrao = [
    { id: 1, nome: "Apto. 202 - Centro", tipo: "Apartamento", status: "Alugado", endereco: "Av. Getúlio Vargas, 202", bairro: "Centro", cidade: "Patos de Minas", quartos: 2, banheiros: 1, area: 65, vagas: 1, valor: 2500, inquilino: 1, descricao: "Apartamento bem localizado, perto do comércio." },
    { id: 2, nome: "Casa - Bairro Novo", tipo: "Casa", status: "Alugado", endereco: "Rua Sete de Setembro, 310", bairro: "Bairro Novo", cidade: "Patos de Minas", quartos: 3, banheiros: 2, area: 140, vagas: 2, valor: 3200, inquilino: 2, descricao: "Casa espaçosa com quintal e área de lazer." },
    { id: 3, nome: "Studio - Centro", tipo: "Studio", status: "Alugado", endereco: "Rua Major Gote, 88", bairro: "Centro", cidade: "Patos de Minas", quartos: 1, banheiros: 1, area: 35, vagas: 0, valor: 1800, inquilino: 3, descricao: "Studio compacto e moderno." },
    { id: 4, nome: "Apto. - Vila Nova", tipo: "Apartamento", status: "Alugado", endereco: "Rua das Palmeiras, 150", bairro: "Vila Nova", cidade: "Patos de Minas", quartos: 3, banheiros: 2, area: 95, vagas: 1, valor: 2800, inquilino: 4, descricao: "Apartamento amplo em bairro tranquilo." },
    { id: 5, nome: "Casa - Bairro Alvorada", tipo: "Casa", status: "Disponível", endereco: "Rua dos Ipês, 75", bairro: "Alvorada", cidade: "Patos de Minas", quartos: 3, banheiros: 2, area: 120, vagas: 2, valor: 2500, inquilino: null, descricao: "Casa com piscina e churrasqueira." },
    { id: 6, nome: "Casa - Jardim Europa", tipo: "Casa", status: "Disponível", endereco: "Rua Lisboa, 230", bairro: "Jardim Europa", cidade: "Belo Horizonte", quartos: 4, banheiros: 3, area: 220, vagas: 3, valor: 2600, inquilino: null, descricao: "Casa de alto padrão com jardim." }
];

const inquilinosPadrao = [
    { id: 1, nome: "João Silva", iniciais: "JS", email: "joao.silva@email.com", telefone: "(31) 99999-0000", cpf: "123.456.789-00", nascimento: "1988-04-12", profissao: "Engenheiro", endereco: "Rua das Flores, 45 - Belo Horizonte/MG", imovel: 1, desde: "Jan/2024", situacao: "Em Dia" },
    { id: 2, nome: "Maria Costa", iniciais: "MC", email: "maria.costa@email.com", telefone: "(31) 99999-0001", cpf: "234.567.890-11", nascimento: "1991-09-03", profissao: "Professora", endereco: "Av. Brasil, 900 - Uberlândia/MG", imovel: 2, desde: "Mar/2023", situacao: "Em Dia" },
    { id: 3, nome: "Pedro Oliveira", iniciais: "PO", email: "pedro.oliveira@email.com", telefone: "(31) 99999-0002", cpf: "345.678.901-22", nascimento: "1995-01-25", profissao: "Designer", endereco: "Rua Goiás, 12 - Patos de Minas/MG", imovel: 3, desde: "Jul/2024", situacao: "Em Dia" },
    { id: 4, nome: "Ana Batista", iniciais: "AB", email: "ana.batista@email.com", telefone: "(31) 99999-0003", cpf: "456.789.012-33", nascimento: "1985-11-30", profissao: "Advogada", endereco: "Rua Bahia, 300 - Araxá/MG", imovel: 4, desde: "Set/2024", situacao: "Em Dia" }
];

function carregar(chave, padrao) {
    try {
        const salvo = localStorage.getItem(chave);
        return salvo ? JSON.parse(salvo) : padrao;
    } catch (erro) {
        return padrao;
    }
}

function guardar(chave, lista) {
    try {
        localStorage.setItem(chave, JSON.stringify(lista));
    } catch (erro) {
        console.error("Não foi possível salvar", erro);
    }
}

const imoveis = carregar("af_imoveis", imoveisPadrao);
const inquilinos = carregar("af_inquilinos", inquilinosPadrao);
const idDaUrl = Number(new URLSearchParams(window.location.search).get("id"));

const formatos = {
    dinheiro: function (v) { return "R$ " + Number(v).toLocaleString("pt-BR"); },
    data: function (v) { return v.split("-").reverse().join("/"); }
};

function esc(texto) {
    return String(texto).replace(/[&<>"']/g, function (c) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
}

function proximoId(lista) {
    return Math.max(0, ...lista.map(function (x) { return x.id; })) + 1;
}

function gerarIniciais(nome) {
    const partes = nome.split(" ").filter(Boolean);
    const ultima = partes.length > 1 ? partes[partes.length - 1][0] : "";
    return (partes[0][0] + ultima).toUpperCase();
}

function naoEncontrado(destino) {
    document.querySelector("main").innerHTML = '<h1 class="titulo">Registro não encontrado</h1><a class="btn" href="' + destino + '">Voltar</a>';
}

function preencherTela(dados) {
    document.querySelectorAll("[data-campo]").forEach(function (el) {
        let valor = dados[el.dataset.campo];
        if (valor === undefined || valor === null || valor === "") {
            valor = "Não informado";
        } else if (el.dataset.formato) {
            valor = formatos[el.dataset.formato](valor);
        }
        el.textContent = valor;
        if (el.dataset.selo) {
            el.classList.toggle("alerta", valor === "Disponível" || valor === "Pendente");
        }
    });
    document.querySelectorAll("[data-link]").forEach(function (el) {
        el.href = el.dataset.link.replace(/\{(\w+)\}/g, function (trecho, campo) { return dados[campo]; });
        if (el.dataset.exige && !dados[el.dataset.exige]) {
            el.hidden = true;
        }
    });
}

function lerFormulario(form) {
    const dados = {};
    Array.from(form.elements).forEach(function (campo) {
        if (!campo.name) return;
        if (campo.type === "number" || campo.name === "imovel") {
            dados[campo.name] = campo.value === "" ? null : Number(campo.value);
        } else {
            dados[campo.name] = campo.value.trim();
        }
    });
    return dados;
}

function preencherFormulario(form, dados) {
    Array.from(form.elements).forEach(function (campo) {
        if (campo.name && dados[campo.name] !== undefined && dados[campo.name] !== null) {
            campo.value = dados[campo.name];
        }
    });
}

function montarListaImoveis() {
    const status = document.getElementById("st").value;
    const cidade = document.getElementById("ci").value;
    const busca = document.getElementById("bu").value.toLowerCase();
    const filtrados = imoveis.filter(function (i) {
        const texto = (i.nome + " " + i.endereco + " " + i.bairro).toLowerCase();
        return (status === "Todos" || i.status === status) && (cidade === "Todas" || i.cidade === cidade) && texto.includes(busca);
    });
    document.getElementById("lista").innerHTML = filtrados.length ? filtrados.map(function (i) {
        const alerta = i.status === "Disponível" ? " alerta" : "";
        const dono = inquilinos.find(function (t) { return t.id === i.inquilino; });
        return `<article class="cartao imovel"><div class="foto"></div><div class="corpo"><div class="stat"><strong>${esc(i.nome)}</strong><span class="selo${alerta}" style="margin:0">${esc(i.status)}</span></div><p class="info">${i.quartos} quartos · ${i.banheiros} banheiros · ${i.area} m²</p><p class="preco">${formatos.dinheiro(i.valor)}/mês</p><p class="info">${dono ? "Inquilino: " + esc(dono.nome) : "Sem inquilino no momento"}</p><div class="acoes"><button type="button" class="btn" data-ver="${i.id}">Ver</button><a class="btn btn-ouro" href="editar-imovel.html?id=${i.id}">Editar</a></div></div></article>`;
    }).join("") : '<p class="info">Nenhum imóvel encontrado.</p>';
}

function telaListaImoveis() {
    montarListaImoveis();
    ["st", "ci", "bu"].forEach(function (id) {
        document.getElementById(id).addEventListener("input", montarListaImoveis);
    });
    document.querySelector("form").addEventListener("submit", function (evento) {
        evento.preventDefault();
    });
}

function telaListaInquilinos() {
    document.getElementById("lista").innerHTML = inquilinos.map(function (t) {
        const im = imoveis.find(function (i) { return i.id === t.imovel; });
        const pendente = t.situacao === "Pendente";
        return `<div class="tab-linha inq"><div class="avatar">${esc(t.iniciais)}</div><div><strong>${esc(t.nome)}</strong><p class="info">${esc(t.email)} · ${esc(t.telefone)}</p></div><div><strong>${im ? esc(im.nome) : "Sem imóvel"}</strong><p class="info">Desde ${esc(t.desde)}</p></div><div><p class="info">CPF: ${esc(t.cpf)}</p><p class="info">Profissão: ${esc(t.profissao)}</p></div><span class="selo${pendente ? " alerta" : ""}">${pendente ? "⚠ Pendente" : "✓ Em Dia"}</span><div class="acoes" style="margin:0"><a class="btn" href="inquilino.html?id=${t.id}">Ver</a><a class="btn btn-ouro" href="editar-inquilino.html?id=${t.id}">Editar</a></div></div>`;
    }).join("");
}

function telaImovel() {
    const imovel = imoveis.find(function (i) { return i.id === idDaUrl; });
    if (!imovel) return naoEncontrado("meus-imoveis.html");
    const dono = inquilinos.find(function (t) { return t.id === imovel.inquilino; });
    preencherTela({ ...imovel, inquilinoNome: dono ? dono.nome : "Sem inquilino no momento" });
}

function telaInquilino() {
    const inquilino = inquilinos.find(function (t) { return t.id === idDaUrl; });
    if (!inquilino) return naoEncontrado("inquilinos.html");
    const im = imoveis.find(function (i) { return i.id === inquilino.imovel; });
    preencherTela({
        ...inquilino,
        imovelNome: im ? im.nome : "Nenhum imóvel vinculado",
        imovelEndereco: im ? im.endereco + ", " + im.bairro + " - " + im.cidade : null,
        imovelValor: im ? im.valor : null
    });
}

function formularioImovel() {
    const form = document.querySelector("form");
    const existente = imoveis.find(function (i) { return i.id === idDaUrl; });
    if (idDaUrl && !existente) return naoEncontrado("meus-imoveis.html");
    if (existente) preencherFormulario(form, existente);
    form.addEventListener("submit", function (evento) {
        evento.preventDefault();
        const dados = lerFormulario(form);
        if (existente) {
            Object.assign(existente, dados);
        } else {
            imoveis.push({ id: proximoId(imoveis), inquilino: null, ...dados });
        }
        guardar("af_imoveis", imoveis);
        window.location.href = existente ? "imovel.html?id=" + existente.id : "meus-imoveis.html";
    });
}

function vincularImovel(inquilino) {
    imoveis.forEach(function (i) {
        if (i.inquilino === inquilino.id && i.id !== inquilino.imovel) {
            i.inquilino = null;
            i.status = "Disponível";
        }
    });
    inquilinos.forEach(function (outro) {
        if (outro.id !== inquilino.id && outro.imovel === inquilino.imovel) outro.imovel = null;
    });
    const im = imoveis.find(function (i) { return i.id === inquilino.imovel; });
    if (im) {
        im.inquilino = inquilino.id;
        im.status = "Alugado";
    }
}

function formularioInquilino() {
    const form = document.querySelector("form");
    const existente = inquilinos.find(function (t) { return t.id === idDaUrl; });
    if (!existente) return naoEncontrado("inquilinos.html");
    form.elements.imovel.innerHTML = '<option value="">Nenhum imóvel</option>' + imoveis.map(function (i) {
        return `<option value="${i.id}">${esc(i.nome)}</option>`;
    }).join("");
    preencherFormulario(form, existente);
    form.addEventListener("submit", function (evento) {
        evento.preventDefault();
        const dados = lerFormulario(form);
        dados.iniciais = gerarIniciais(dados.nome);
        Object.assign(existente, dados);
        vincularImovel(existente);
        guardar("af_imoveis", imoveis);
        guardar("af_inquilinos", inquilinos);
        window.location.href = "inquilino.html?id=" + existente.id;
    });
}

const telas = {
    imoveis: telaListaImoveis,
    inquilinos: telaListaInquilinos,
    imovel: telaImovel,
    inquilino: telaInquilino,
    "imovel-form": formularioImovel,
    "inquilino-form": formularioInquilino
};

const pagina = document.body.dataset.pagina;
if (telas[pagina]) telas[pagina]();