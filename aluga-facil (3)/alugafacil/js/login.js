const formulario = document.querySelector("form");
 
formulario.addEventListener("submit", function (evento) {
    evento.preventDefault();
    const tipo = formulario.elements.tipo.value;
    if (tipo === "proprietario") {
        window.location.href = "proprietario.html";
    } else {
        window.location.href = "cliente.html";
    }
});
 