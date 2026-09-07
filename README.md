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

## Sistema de pagamento (sem API integrada)

O pagamento foi construído como uma **porta/adapter** (`PaymentGateway`), então nenhuma API de pagamento real está integrada — só o ponto de extensão pronto:

- `PaymentGateway` (interface): `criarCobranca(...)` e `estornarCobranca(...)`.
- `SolicitacaoCobranca` / `RespostaCobranca`: contratos de dados neutros, que servem para qualquer provedor.
- `FakePaymentGateway`: implementação padrão ativa (`pagamento.gateway.provider=fake` no `application.yml`), simula uma cobrança aprovada na hora, sem chamar nada externo. É o que permite rodar e testar o fluxo completo de cobrança agora.

**Para plugar um provedor real (Mercado Pago, Stripe, PagSeguro etc.):**
1. Crie uma classe nova implementando `PaymentGateway`, anotada com `@Service` e `@Primary` (ou removendo o `@Service` do `FakePaymentGateway`), chamando o SDK/API do provedor dentro de `criarCobranca`/`estornarCobranca`.
2. Nenhuma outra camada muda — `PagamentoService`, controllers e o resto do domínio dependem só da interface.

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
| POST | `/pagamentos` | participantes do contrato |
| POST | `/pagamentos/{id}/estornar` | participantes do contrato |

Documentação interativa em `http://localhost:8080/docs` com a aplicação rodando.

## Rodando localmente

```bash
docker compose up -d db
./mvnw spring-boot:run
```

Ou tudo via Docker:

```bash
docker compose up --build
```

Variáveis de ambiente (com defaults para dev em `application.yml`): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.

## Testes

```bash
./mvnw test
```

Usa H2 em memória (perfil `test`) — não precisa de Postgres rodando para os testes.

## Próximos passos sugeridos

- Implementação real de `PaymentGateway` (ex.: Mercado Pago).
- Job agendado para gerar cobranças automaticamente todo mês a partir dos contratos ativos.
- Webhook para receber confirmação assíncrona de pagamento do gateway real.
- Testes unitários de service (JUnit 5 + Mockito) e de integração de controller (MockMvc).
