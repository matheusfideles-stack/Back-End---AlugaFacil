# AlugaFacil

Backend da **Lar & Negócios** para gestão de aluguel de imóveis: cadastro de imóveis, contratos de locação e cobrança de aluguéis, com autenticação JWT e RBAC.

## Stack

- Java 21 + Spring Boot 3
- Spring Security (JWT via `java-jwt`)
- Spring Data JPA / Hibernate
- MySQL (H2 no perfil `dev`), com o Hibernate criando as tabelas
- Bean Validation
- Lombok
- Swagger/OpenAPI (`/docs`)
- Docker + Docker Compose

## Arquitetura

```
domain/
  usuario/    -> Usuario, Perfil (ADMIN, LOCADOR, LOCATARIO), UsuarioRepository
  imovel/     -> Imovel, TipoImovel, FotoImovel, ImovelRepository
  avaliacao/  -> Avaliacao (nota de 0 a 5 e comentario, uma por locatario e imovel)
  contrato/   -> Contrato, StatusContrato, ContratoRepository
  pagamento/  -> Pagamento, StatusPagamento, MetodoPagamento, PagamentoRepository
  pagamento/gateway/ -> abstração do provedor de pagamento (ver seção abaixo)
service/      -> regras de negócio (validações, orquestração)
controller/   -> endpoints REST
dto/          -> records de entrada/saída (nunca expõe entidade JPA direto)
infra/security -> JWT (TokenService, SecurityFilter)
infra/exception -> exceções de domínio + @RestControllerAdvice
config/       -> SecurityConfigurations (CORS, RBAC, sessão stateless)
```

## Sistema de pagamento (Mercado Pago)

O pagamento é uma **porta/adapter** (`PaymentGateway`) com duas implementações, escolhidas por
configuração (`pagamento.gateway.provider`):

- `mercadopago` (padrão) — integração real com a API `POST /v1/payments`, cobrindo **Pix, boleto,
  cartão de crédito e cartão de débito**, com webhook de confirmação assíncrona e estorno.
- `fake` — aprova tudo localmente, sem rede. É o que os testes automatizados usam.

Dados de cartão nunca passam pelo backend: o frontend tokeniza o cartão com a public key
(`GET /pagamentos/config`) e envia só o token.

> **Configuração:** `MP_ACCESS_TOKEN` e `MP_PUBLIC_KEY` por variável de ambiente, ou
> `src/main/resources/application-local.yml` (fora do git). Credenciais não vão para o repositório.

📄 **Guia completo de uso e teste: [`docs/PAGAMENTOS.md`](docs/PAGAMENTOS.md)** — inclui cartões de
teste, como simular o webhook e os erros mais comuns da API.

## Frontend

As telas (HTML/CSS/JS puros) ficam em `src/main/resources/static` e são servidas pelo próprio Spring
Boot: com a aplicação rodando, abra `http://localhost:8080`. Como front e API compartilham a origem,
não há configuração de CORS nem de URL base.

| Tela | Quem acessa | O que faz |
|---|---|---|
| `index.html` | público | página inicial com imóveis em destaque e busca rápida |
| `imoveis.html` | público | todos os imóveis, com busca por local, tipo, preço e quartos e ordenação (inclusive por melhor avaliação) |
| `login.html` | público | entrar e criar conta (o cadastro do site cria sempre um locatário) |
| `esqueci-senha.html` / `redefinir-senha.html` | público | recuperação de senha: pede o código por e-mail e define a nova senha |
| `confirmar-email.html` | público | digita o código de verificação do e-mail e ativa a conta |
| `imovel.html`, `pagamentos.html`, `cliente.html`, `alugueis.html` | LOCATARIO | galeria de fotos, avaliações, alugar, pagar (Pix, boleto, cartão), contratos e manutenção |
| `proprietario.html`, `meus-imoveis.html`, `novo-imovel.html`, `editar-imovel.html`, `inquilinos.html`, `financeiro.html` | LOCADOR | gestão de imóveis, inquilinos, recebimentos e manutenções |
| `admin.html` | ADMIN | cadastra os proprietários e lista os já cadastrados |

A sessão é o JWT guardado no navegador; as páginas privadas redirecionam para o login quando ele
falta ou expira, e quem abre a área do outro perfil é levado para a própria.

## Senhas, confirmação de e-mail e recuperação de conta

- **Código de verificação:** toda conta nova (locatário pelo site ou proprietário cadastrado pelo administrador) recebe um
  e-mail com um **código de 6 dígitos** (vale 60 minutos). A pessoa digita o código na própria tela de login (ou em
  `confirmar-email.html`). **Sem confirmar, o login é recusado** (403 com `codigo: EMAIL_NAO_CONFIRMADO`) e a tela oferece
  reenviar um novo código. Contas criadas antes desse recurso e o administrador inicial já nascem confirmados.
  Redefinir a senha com o código do e-mail também confirma a conta.

- A senha é guardada com **BCrypt** (nunca em texto puro) e o login compara o hash.
- `POST /auth/esqueci-senha` sempre responde 200, exista ou não o e-mail (não revela quem tem conta). O e-mail traz um
  código de 6 dígitos, que a pessoa digita em `redefinir-senha.html` junto com a nova senha.
- O código de recuperação vale 30 minutos e só pode ser usado uma vez. Pedir um novo código invalida os anteriores, e
  **5 tentativas erradas bloqueiam o código** (é preciso pedir outro). No banco fica apenas o **SHA-256** do código
  junto com o id do usuário, nunca o código em texto puro.
- **E-mail:** copie `config/application.yml.exemplo` para `config/application.yml` (ignorado pelo git),
  preencha o SMTP (no Gmail, use uma *senha de app*) e reinicie. Também dá para usar as variáveis
  `SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e `APP_URL_BASE`.
- **Sem SMTP** o e-mail não é enviado e o código vai para o log do servidor. No perfil `dev` ele também
  aparece na própria tela ("modo de teste", tanto na confirmação quanto na recuperação); essa opção (`app.exibir-links-de-teste`)
  fica desligada fora do `dev`. Não deixe produção sem SMTP, porque o código vazaria para os logs.
- **Quem cria cada perfil:** o cadastro do site (`POST /auth/registro`) cria **somente locatários**; qualquer outro perfil é recusado.
  Os proprietários (LOCADOR) são cadastrados por um **administrador** em `admin.html` (`POST /admin/locadores`).
  O primeiro administrador é criado na subida a partir de `ADMIN_EMAIL` e `ADMIN_SENHA` (se ainda não existir).
  Nos perfis `dev` e `local` o padrão é `admin@alugafacil.local` / `Admin1234`: **troque em qualquer ambiente real**.

## Endpoints principais

| Método | Rota | Acesso |
|---|---|---|
| POST | `/auth/registro` | público (cria somente locatário) |
| POST | `/auth/login` | público (devolve `token`, `nome`, `perfil`) |
| POST | `/auth/esqueci-senha` | público |
| POST | `/auth/redefinir-senha` | público (`email`, `codigo` de 6 dígitos e `novaSenha`) |
| POST | `/auth/confirmar-email` | público (`email` e `codigo` de 6 dígitos) |
| POST | `/auth/reenviar-confirmacao` | público |
| POST | `/admin/locadores` | ADMIN (cadastra proprietário) |
| GET | `/admin/locadores` | ADMIN (lista proprietários) |
| POST | `/contato` | público (formulário da Ajuda; vai para `MAIL_SUPORTE`) |
| POST | `/imoveis` | LOCADOR, ADMIN |
| GET | `/imoveis`, `/imoveis/{id}` | público |
| GET | `/imoveis/meus` | LOCADOR, ADMIN |
| PUT | `/imoveis/{id}` | dono do imóvel, ADMIN |
| DELETE | `/imoveis/{id}` | dono do imóvel, ADMIN (tira do ar) |
| PUT | `/imoveis/{id}/reativar` | dono do imóvel, ADMIN |
| POST | `/imoveis/{id}/fotos` | dono do imóvel, ADMIN (multipart, campo `fotos`) |
| GET | `/imoveis/{id}/fotos`, `/imoveis/{id}/fotos/{fotoId}/arquivo`, `/imoveis/{id}/capa` | público |
| PUT | `/imoveis/{id}/fotos/{fotoId}/capa` | dono do imóvel, ADMIN |
| DELETE | `/imoveis/{id}/fotos/{fotoId}` | dono do imóvel, ADMIN |
| GET | `/imoveis/{id}/avaliacoes`, `/imoveis/{id}/avaliacoes/resumo` | público |
| GET, PUT, DELETE | `/imoveis/{id}/avaliacoes/minha` | LOCATARIO (PUT cria ou atualiza a própria avaliação) |
| DELETE | `/avaliacoes/{id}` | ADMIN (moderação) |
| POST | `/contratos` | LOCATARIO |
| GET | `/contratos/meus?papel=locatario\|locador` | autenticado |
| PUT | `/contratos/{id}/encerrar` | participantes do contrato |
| GET | `/pagamentos/config` | autenticado |
| POST | `/pagamentos` | participantes do contrato |
| GET | `/pagamentos/meus`, `/pagamentos/recebidos` | locatário / locador |
| GET | `/pagamentos/{id}` | participantes do contrato |
| POST | `/pagamentos/{id}/sincronizar` | participantes do contrato |
| POST | `/pagamentos/{id}/estornar` | participantes do contrato |
| POST | `/manutencoes` | LOCATARIO do contrato |
| GET | `/manutencoes/meus`, `/manutencoes/recebidas` | locatário / locador |
| PUT | `/manutencoes/{id}/status` | LOCADOR do imóvel |
| POST | `/webhooks/mercadopago` | público (assinatura HMAC) |

Limite de requisições por IP (`ratelimit.*` no `application.yaml`): login/cadastro/recuperação e
`/pagamentos` têm limites próprios e mais rígidos; as telas estáticas não contam. Passou do limite,
a API responde `429` com `Retry-After`.

## Fotos e avaliações dos imóveis

- **Fotos:** o proprietário (ou o admin) envia até **10 fotos por imóvel** em `novo-imovel.html` e `editar-imovel.html`
  (JPG, PNG ou WebP, até **5 MB** cada). A primeira é a **capa**, e dá para trocar a capa ou remover fotos na edição.
  O servidor confere o tipo pelo **conteúdo** do arquivo (não pelo nome nem pelo `Content-Type`), recusa SVG e qualquer
  coisa que não seja imagem, e grava com um nome gerado, nunca com o nome enviado.
  Os arquivos ficam em `./uploads` (mude com `UPLOADS_DIR`); a pasta é ignorada pelo git, então faça backup dela
  junto com o banco. Imóvel sem fotos usa uma foto de exemplo.
- **Avaliações:** só **locatários** avaliam, com **nota de 0 a 5** e comentário opcional (até 1000 caracteres), **uma
  avaliação por imóvel**: avaliar de novo atualiza a anterior, e a própria avaliação pode ser excluída. O admin pode
  excluir qualquer uma (moderação). A lista mostra quem avaliou como `Ana T.` (primeiro nome e inicial do último).
  A média e o total aparecem nos cards, no detalhe do imóvel e na ordenação "Melhor avaliados".

Documentação interativa em `http://localhost:8080/docs` com a aplicação rodando.

## Rodando localmente

**Sem instalar nada (H2 + gateway de pagamento falso), com os dados salvos em `./data`:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Abra `http://localhost:8080`. Crie a conta de locatário pela tela, e ela continua lá depois de reiniciar.
Para cadastrar proprietários, entre com o administrador (`admin@alugafacil.local` / `Admin1234`).

**Com MySQL** (perfil `local`, padrão): crie o banco `alugafacil`, ponha a senha em
`src/main/resources/application-local.yml` (fora do git) ou em `DB_PASSWORD`, e rode:

```bash
mvn spring-boot:run
```

O perfil `local` usa o gateway falso por padrão (`PAGAMENTO_PROVIDER=mercadopago` ativa o real). As
tabelas são criadas e atualizadas pelo Hibernate (`ddl-auto: update`), sem apagar dados ao reiniciar.

Variáveis de ambiente: `DB_PASSWORD`, `JWT_SECRET`, `PAGAMENTO_PROVIDER`, `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`,
`MP_NOTIFICATION_URL`, `MP_WEBHOOK_SECRET`, `ADMIN_EMAIL`, `ADMIN_SENHA`, `APP_URL_BASE`, `SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`,
`SPRING_MAIL_PASSWORD`, `RATELIMIT_ENABLED`. Copie `.env.example` para `.env` e preencha as credenciais.

> Para subir o sistema com Docker, rode `docker compose up --build` na raiz do projeto. Sobe o MySQL 8.4 e a aplicação.
> Os dados do MySQL ficam no volume `alugafacil-data`. Para criar o administrador, defina `ADMIN_EMAIL` e `ADMIN_SENHA` no `.env`.

## Produção

Antes de abrir o sistema para outras pessoas, leia **[`docs/PRODUCAO.md`](docs/PRODUCAO.md)**. Com `SPRING_PROFILES_ACTIVE=prod`:

- o sistema **não sobe** sem um `JWT_SECRET` próprio (32 caracteres ou mais) e recusa senha de administrador fraca ou conhecida;
- **HTTPS é obrigatório** (atrás de um proxy como Caddy ou nginx), e o navegador recebe o cabeçalho HSTS;
- os códigos de verificação só chegam por e-mail, nunca na tela, e o sistema **não sobe sem `SPRING_MAIL_HOST`**: veja [`docs/EMAIL.md`](docs/EMAIL.md), que também mostra como testar o envio sem usar o seu e-mail (Mailpit).
- para cobrar de verdade (Pix, boleto, débito e crédito parcelado) siga [`docs/PAGAMENTOS.md`](docs/PAGAMENTOS.md); em `prod` o sistema não sobe com o gateway `fake` nem sem o segredo do webhook;
- faça **backup** do banco e das fotos com `scripts/backup.ps1` (ou `backup.sh`) e teste a restauração: veja [`docs/BACKUP.md`](docs/BACKUP.md).

## Mapa, cidades e comodidades

- A vitrine (`imoveis.html`) tem seletor de **cidade e bairro**, filtro por **comodidades** (piscina, estacionamento gratuito, máquina de lavar, ar-condicionado, lareira, TV, aquecedor, churrasqueira) e um **mapa** com o preço de cada imóvel. Mexer no mapa mostra o botão "Buscar nesta região", que filtra a lista pela área visível. A página do imóvel também mostra as comodidades e a localização.
- As cidades atendidas ficam em `CIDADES`, no começo de `static/js/api.js` (Patos de Minas, Lagoa Formosa, Nova Ponte e Tiros). Para incluir outra, acrescente uma linha com o nome, a UF e as coordenadas do centro.
- O mapa usa **Leaflet** (guardado em `static/js/vendor/leaflet`, licença BSD-2) com os mapas do **OpenStreetMap**, sem chave de API. O OpenStreetMap pede uso moderado dos mapas; para um site com muito acesso, troque o endereço dos mapas em `static/js/mapa.js` por um provedor próprio.
- A posição de cada imóvel vem do **Nominatim** (busca de endereços do OpenStreetMap), consultado pelo servidor quando o imóvel é cadastrado ou muda de endereço, no máximo 1 consulta por segundo, como a política deles pede. Imóveis antigos são completados ao subir o servidor. Se o endereço não for encontrado, o imóvel aparece perto do centro da cidade. Para não consultar a internet, use `APP_GEOCODIFICACAO_ATIVA=false`. Em produção, defina `app.geocodificacao.user-agent` com o nome do site e um e-mail de contato, como a política do Nominatim pede.

## Testes

```bash
mvn test
```

Usa H2 em memória (perfil `test`) — não precisa de banco rodando para os testes. Cobrem autenticação,
recuperação de senha, imóveis, fotos, avaliações, contratos, pagamentos, manutenção, permissões por perfil, limite
de requisições e a entrega do frontend. Os testes de `service/` são unitários puros (sem Spring nem banco) e usam
repositórios falsos de `Dobles.java`; os `*IntegrationTest` sobem a aplicação inteira.

## Próximos passos sugeridos

- Gerar a cobrança (Pix/boleto) automaticamente todo mês. Hoje o sistema só **lembra por e-mail** (veja `docs/EMAIL.md`); o locatário ainda abre a tela de pagamentos para pagar.
