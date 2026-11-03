<#
Restaura um backup do AlugaFacil (Docker): substitui o banco e copia as fotos.

Uso:
  .\scripts\restaurar.ps1 -Pasta .\backups\2026-11-02_10-30-00 -Confirmar

ATENCAO: o banco atual e SUBSTITUIDO pelo conteudo do backup. Sem -Confirmar o script nao faz nada.
Veja docs/BACKUP.md.
#>
param(
    [Parameter(Mandatory = $true)][string]$Pasta,
    [switch]$Confirmar,
    [string]$Banco = 'alugafacil-db',
    [string]$Aplicacao = 'alugafacil-api',
    [string]$Usuario = 'alugafacil',
    [string]$Senha = $(if ($env:BACKUP_DB_SENHA) { $env:BACKUP_DB_SENHA } else { 'alugafacil' })
)

$ErrorActionPreference = 'Stop'

$dump = Join-Path $Pasta 'banco.sql'
if (-not (Test-Path $dump)) { throw "Nao encontrei banco.sql em $Pasta. Informe a pasta de um backup." }
if (-not $Confirmar) {
    Write-Host "Isto vai SUBSTITUIR o banco atual pelo backup de '$Pasta'."
    Write-Host 'Para continuar, rode de novo com -Confirmar.'
    exit 1
}

# Com a aplicacao parada ninguem grava no banco durante a restauracao.
docker stop $Aplicacao | Out-Null
try {
    docker cp $dump "${Banco}:/tmp/restaurar.sql" | Out-Null
    docker exec -e "MYSQL_PWD=$Senha" $Banco sh -c "mysql -u$Usuario alugafacil < /tmp/restaurar.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao restaurar o banco.' }
    docker exec $Banco rm -f /tmp/restaurar.sql | Out-Null

    $zip = Join-Path $Pasta 'fotos.zip'
    if (Test-Path $zip) {
        $temporaria = Join-Path $Pasta 'uploads-restaurar'
        if (Test-Path $temporaria) { Remove-Item $temporaria -Recurse -Force }
        Expand-Archive -Path $zip -DestinationPath $temporaria -Force
        docker cp (Join-Path $temporaria '.') "${Aplicacao}:/data/uploads/" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar as fotos.' }
        Remove-Item $temporaria -Recurse -Force
    }
}
finally {
    docker start $Aplicacao | Out-Null
}

Write-Host 'Restauracao concluida. A aplicacao foi iniciada de novo.'
