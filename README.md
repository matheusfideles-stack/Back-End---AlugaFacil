# AlugaFácil

Sistema de aluguel de imóveis: anúncio, proposta, contrato, pagamento do aluguel e manutenção.

## Como rodar

Com Java 21 e Maven instalados:

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Abra `http://localhost:8080`. O perfil `dev` usa um banco H2 em `./data` e um gateway de pagamento falso, então não precisa de MySQL nem de conta no Mercado Pago.

Contas de teste do perfil `dev`:

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | `admin@alugafacil.local` | `Admin1234` |
| Locador (demo) | `demo.locador329023@alugafacil.local` | `Demo1234` |
| Locatário (demo) | `demo.locatario329023@alugafacil.local` | `Demo1234` |

Com Docker: `docker compose up --build`. Sobe o MySQL e a aplicação.

## Quem faz o quê

- **Locatário:** cadastra a própria conta pelo site, procura imóveis, faz proposta de aluguel, paga o aluguel (Pix, boleto ou cartão), pede manutenção e avalia o imóvel.
- **Locador:** cadastra imóveis com fotos e comodidades, aceita ou recusa propostas, marca visitas, acompanha recebimentos e responde manutenções.
- **Administrador:** cadastra locadores, ativa ou desativa contas e imóveis, modera avaliações e manda lembrete de pagamento.

Quem não está logado vê a vitrine e a página de cada imóvel. Para falar com o proprietário ou pedir visita é preciso entrar como locatário.

## Regras principais

- **Contrato nasce de proposta.** O locatário propõe; o proprietário aceita ou recusa. Ao aceitar, o contrato é criado e o imóvel sai da busca até o contrato ser encerrado.
- **Multa e juros por atraso:** multa de 2% uma vez, mais juros de 1% ao mês, proporcionais aos dias. O valor atualizado aparece na tela de pagamentos.
- **Lembretes por e-mail:** o sistema avisa o locatário antes do vencimento e nos primeiros dias de atraso. Não cobra sozinho.
- **PDFs:** contrato (`/contratos/{id}/contrato.pdf`) e recibo, que só sai depois do pagamento confirmado. O contrato tem linhas para assinar à mão; não há assinatura digital.
- **Imóvel com contrato ativo não pode ser reativado** enquanto o contrato durar. Desativar sempre funciona.

## Configuração

Credenciais e senhas ficam fora do git: use variáveis de ambiente ou o arquivo `.env` (copie de `.env.example`). Nunca suba `.env`, `config/application.yml` nem `application-local.yml`.

| Variável | Para que serve |
|---|---|
| `JWT_SECRET` | chave que assina o login (obrigatória em produção) |
| `PAGAMENTO_PROVIDER` | `fake` para testar, `mercadopago` para cobrar de verdade |
| `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`, `MP_WEBHOOK_SECRET`, `MP_NOTIFICATION_URL` | credenciais do Mercado Pago |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | envio de e-mail (sem isso, os e-mails só aparecem no log) |
| `ADMIN_EMAIL`, `ADMIN_SENHA` | administrador criado na primeira subida |
| `APP_URL_BASE` | endereço público do site, usado nos links dos e-mails |
| `APP_GEOCODIFICACAO_ATIVA` | `false` para não consultar a posição dos imóveis na internet |

## Documentação detalhada

- [`docs/PAGAMENTOS.md`](docs/PAGAMENTOS.md): como cobrar com Mercado Pago, cartões de teste e webhook.
- [`docs/EMAIL.md`](docs/EMAIL.md): como ligar o envio de e-mail e testar com o Mailpit.
- [`docs/DEPLOY.md`](docs/DEPLOY.md): passo a passo para colocar no ar de graça (Oracle Cloud, DuckDNS e HTTPS).
- [`docs/PRODUCAO.md`](docs/PRODUCAO.md): o que configurar antes de abrir para outras pessoas (perfil `prod`, HTTPS, segredos).
- [`docs/BACKUP.md`](docs/BACKUP.md): backup e restauração do banco e das fotos.
- API interativa com a aplicação rodando: `http://localhost:8080/docs`.

## Testes

```bash
mvn test
```

Os testes usam H2 em memória e não precisam de banco rodando.

