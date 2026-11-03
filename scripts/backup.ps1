<#
Backup do AlugaFacil rodando no Docker: banco MySQL e fotos dos imoveis.

Uso:
  .\scripts\backup.ps1
  .\scripts\backup.ps1 -Destino D:\backups-alugafacil

Cria uma pasta com data e hora contendo banco.sql e fotos.zip. Veja docs/BACKUP.md.
#>
param(
    [string]$Destino = (Join-Path $PSScriptRoot '..\backups'),
    [string]$Banco = 'alugafacil-db',
    [string]$Aplicacao = 'alugafacil-api',
    [string]$Usuario = 'alugafacil',
    [string]$Senha = $(if ($env:BACKUP_DB_SENHA) { $env:BACKUP_DB_SENHA } else { 'alugafacil' })
)

$ErrorActionPreference = 'Stop'

$carimbo = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$pasta = Join-Path $Destino $carimbo
New-Item -ItemType Directory -Path $pasta -Force | Out-Null

# O dump e gravado dentro do container e copiado depois, para o texto nao passar pelo encoding do PowerShell.
docker exec -e "MYSQL_PWD=$Senha" $Banco sh -c "mysqldump -u$Usuario --single-transaction --no-tablespaces --routines alugafacil > /tmp/backup.sql"
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o dump do banco. O container do banco esta rodando?' }
docker cp "${Banco}:/tmp/backup.sql" (Join-Path $pasta 'banco.sql') | Out-Null
docker exec $Banco rm -f /tmp/backup.sql | Out-Null

$temporaria = Join-Path $pasta 'uploads-temp'
docker cp "${Aplicacao}:/data/uploads" $temporaria | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar as fotos. O container da aplicacao existe?' }

$arquivos = @(Get-ChildItem -Path $temporaria -Force)
if ($arquivos.Count -gt 0) {
    Compress-Archive -Path (Join-Path $temporaria '*') -DestinationPath (Join-Path $pasta 'fotos.zip') -Force
} else {
    'Nenhuma foto enviada ate este backup.' | Set-Content -Path (Join-Path $pasta 'fotos-vazio.txt') -Encoding UTF8
}
Remove-Item $temporaria -Recurse -Force

$tamanhoBanco = [math]::Round((Get-Item (Join-Path $pasta 'banco.sql')).Length / 1KB, 1)
Write-Host "Backup criado em: $pasta"
Write-Host "  banco.sql  : $tamanhoBanco KB"
Write-Host ("  fotos      : {0} arquivo(s)" -f $arquivos.Count)
