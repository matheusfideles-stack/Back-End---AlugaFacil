# AlugaFacil

Backend da **Lar & Negócios** para gestão de aluguel de imóveis: cadastro de imóveis, contratos de locação e cobrança de aluguéis, com autenticação JWT e RBAC.

## Stack

- Java 21 + Spring Boot 3
- Spring Security (JWT via `java-jwt`)
- Spring Data JPA / Hibernate
- PostgreSQL + Flyway (migrations versionadas)
- Bean Validation
- Lombok
- Swagger/OpenAPI (`/docs`)
- Docker + Docker Compose

## Arquitetura

```
domain/
  usuario/    -> Usuario, Perfil (ADMIN, LOCADOR, LOCATARIO), UsuarioRepository
  imovel/     -> Imovel, TipoImovel, ImovelRepository
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

## Endpoints principais

| Método | Rota | Acesso |
|---|---|---|
| POST | `/auth/registro` | público |
| POST | `/auth/login` | público |
| POST | `/imoveis` | LOCADOR, ADMIN |
| GET | `/imoveis` | público |
| GET | `/imoveis/meus` | LOCADOR |
| POST | `/contratos` | LOCATARIO |
| PUT | `/contratos/{id}/encerrar` | participantes do contrato |
| GET | `/pagamentos/config` | autenticado |
| POST | `/pagamentos` | participantes do contrato |
| POST | `/pagamentos/{id}/sincronizar` | participantes do contrato |
| POST | `/pagamentos/{id}/estornar` | participantes do contrato |
| POST | `/webhooks/mercadopago` | público (assinatura HMAC) |

Documentação interativa em `http://localhost:8080/docs` com a aplicação rodando.

## Rodando localmente

```bash
docker compose up -d db
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Ou tudo via Docker:

```bash
docker compose up --build
```

Variáveis de ambiente (com defaults para dev em `application.yaml`): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PAGAMENTO_PROVIDER`, `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`, `MP_NOTIFICATION_URL`, `MP_WEBHOOK_SECRET`. Copie `.env.example` para `.env` e preencha as credenciais do Mercado Pago antes de subir via Docker.

## Testes

```bash
mvn test
```

Usa H2 em memória (perfil `test`) — não precisa de Postgres rodando para os testes.

## Próximos passos sugeridos

- Job agendado para gerar cobranças automaticamente todo mês a partir dos contratos ativos.
- Testes do `MercadoPagoGateway` com servidor HTTP stub (WireMock/MockWebServer).
- Testes unitários de service (JUnit 5 + Mockito) e de integração de controller (MockMvc).
- Notificar locador e locatário quando o pagamento é confirmado.
