# Backup e restauração do AlugaFácil

Para não perder nada, guarde **duas coisas**:

1. O **banco MySQL**: usuários, imóveis, contratos, pagamentos e avaliações.
2. As **fotos dos imóveis**: arquivos que ficam fora do banco. No Docker, no volume `alugafacil-uploads` (dentro do container, em `/data/uploads`). Sem Docker, na pasta `uploads/` (ou onde `UPLOADS_DIR` apontar).

No banco ficam só os registros das fotos. **Sem a pasta das fotos, o banco sozinho não recupera as imagens.**

## Fazer um backup (Docker)

Com o sistema rodando no Docker (`docker compose up -d`), na raiz do projeto:

```powershell
.\scripts\backup.ps1
```

Em Linux, macOS ou Git Bash:

```bash
./scripts/backup.sh
```

Cada backup cria uma pasta `backups/AAAA-MM-DD_HH-mm-ss/` com:

| Arquivo | Conteúdo |
|---|---|
| `banco.sql` | Todas as tabelas e dados do banco |
| `fotos.zip` (ou `fotos.tar.gz` no `.sh`) | Todas as fotos dos imóveis |

Para salvar em outro lugar, informe a pasta: `.\scripts\backup.ps1 -Destino D:\meus-backups` ou `./scripts/backup.sh /var/backups/alugafacil`.

Os scripts usam o usuário e a senha do `docker-compose.yml` (`alugafacil` / `alugafacil`). Se você trocou a senha do banco, informe a nova na variável `BACKUP_DB_SENHA`.

## Restaurar um backup

**Atenção:** a restauração **substitui** o banco atual pelo conteúdo do backup. Por segurança, sem a confirmação os scripts não fazem nada.

```powershell
.\scripts\restaurar.ps1 -Pasta .\backups\2026-11-02_10-30-00 -Confirmar
```

```bash
./scripts/restaurar.sh ./backups/2026-11-02_10-30-00 --confirmar
```

O script para a aplicação, restaura o banco, copia as fotos de volta e liga a aplicação de novo.

## Backup automático

**Windows** (todo dia às 3h da manhã), em um PowerShell como administrador:

```powershell
schtasks /create /tn "Backup AlugaFacil" /sc daily /st 03:00 /tr "powershell -NoProfile -ExecutionPolicy Bypass -File C:\caminho\do\projeto\scripts\backup.ps1"
```

**Linux** (`crontab -e`):

```
0 3 * * * cd /opt/alugafacil && ./scripts/backup.sh /var/backups/alugafacil
```

Os scripts não apagam backups antigos. Remova de tempos em tempos as pastas que você não precisa mais.

## Sem Docker

Se o MySQL e a aplicação rodam direto na máquina:

```bash
mysqldump -u root -p --single-transaction --no-tablespaces alugafacil > banco.sql
```

E copie a pasta `uploads/` inteira. Para restaurar, use `mysql -u root -p alugafacil < banco.sql` e copie a pasta `uploads/` de volta ao mesmo lugar.

## Regras que valem sempre

- **Guarde uma cópia fora do servidor** (outro computador, HD externo ou um serviço de nuvem). Um backup na mesma máquina não protege se ela quebrar ou for perdida.
- **O backup tem dados de pessoas** (nomes, e-mails, CPFs) e as senhas já embaralhadas. Trate como informação sensível e nunca suba para o GitHub. A pasta `backups/` já está no `.gitignore`.
- **Teste a restauração de vez em quando.** Um backup que nunca foi restaurado pode estar quebrado sem ninguém saber.

## O que já foi testado

Em 02/11/2026, no Docker: o sistema foi preenchido com um imóvel, 3 fotos, contrato, pagamentos e avaliação, e foi feito o backup. Em seguida, **todos os volumes foram apagados**, o sistema subiu vazio, e a restauração trouxe de volta o imóvel, o contrato, os 4 pagamentos e as 3 fotos, que voltaram a baixar normalmente. Isso foi testado com o `.ps1` e o `.sh` (no Git Bash do Windows).

## Serviço externo para as fotos

As fotos ainda ficam no próprio servidor. Guardá-las em um serviço de armazenamento (como Amazon S3 ou Cloudflare R2) exigiria uma conta nesse serviço e uma mudança no código. Hoje a solução é o volume do Docker mais os backups acima.
