#!/usr/bin/env bash
# Backup do AlugaFacil rodando no Docker: banco MySQL e fotos dos imoveis.
#
# Uso:  ./scripts/backup.sh [pasta-de-destino]
# Cria uma pasta com data e hora contendo banco.sql e fotos.tar.gz. Veja docs/BACKUP.md.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # evita que o Git Bash no Windows mude os caminhos do container

DESTINO="${1:-$(dirname "$0")/../backups}"
BANCO="${BACKUP_CONTAINER_BANCO:-alugafacil-db}"
APLICACAO="${BACKUP_CONTAINER_APP:-alugafacil-api}"
USUARIO="${BACKUP_DB_USUARIO:-alugafacil}"
SENHA="${BACKUP_DB_SENHA:-alugafacil}"

PASTA="$DESTINO/$(date +%Y-%m-%d_%H-%M-%S)"
mkdir -p "$PASTA"

docker exec -e "MYSQL_PWD=$SENHA" "$BANCO" sh -c \
  "mysqldump -u$USUARIO --single-transaction --no-tablespaces --routines alugafacil > /tmp/backup.sql"
docker cp "$BANCO:/tmp/backup.sql" "$PASTA/banco.sql" > /dev/null
docker exec "$BANCO" rm -f /tmp/backup.sql

docker cp "$APLICACAO:/data/uploads" "$PASTA/uploads-temp" > /dev/null
QUANTIDADE=$(find "$PASTA/uploads-temp" -type f | wc -l | tr -d ' ')
if [ "$QUANTIDADE" -gt 0 ]; then
  tar -czf "$PASTA/fotos.tar.gz" -C "$PASTA/uploads-temp" .
else
  echo "Nenhuma foto enviada ate este backup." > "$PASTA/fotos-vazio.txt"
fi
rm -rf "$PASTA/uploads-temp"

echo "Backup criado em: $PASTA"
echo "  banco.sql : $(du -k "$PASTA/banco.sql" | cut -f1) KB"
echo "  fotos     : $QUANTIDADE arquivo(s)"
