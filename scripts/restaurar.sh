#!/usr/bin/env bash
# Restaura um backup do AlugaFacil (Docker): substitui o banco e copia as fotos.
#
# Uso:  ./scripts/restaurar.sh ./backups/2026-11-02_10-30-00 --confirmar
# ATENCAO: o banco atual e SUBSTITUIDO. Sem --confirmar o script nao faz nada. Veja docs/BACKUP.md.
set -euo pipefail
export MSYS_NO_PATHCONV=1

PASTA="${1:-}"
CONFIRMA="${2:-}"
BANCO="${BACKUP_CONTAINER_BANCO:-alugafacil-db}"
APLICACAO="${BACKUP_CONTAINER_APP:-alugafacil-api}"
USUARIO="${BACKUP_DB_USUARIO:-alugafacil}"
SENHA="${BACKUP_DB_SENHA:-alugafacil}"

if [ -z "$PASTA" ] || [ ! -f "$PASTA/banco.sql" ]; then
  echo "Informe a pasta de um backup (com banco.sql). Exemplo: ./scripts/restaurar.sh ./backups/2026-11-02_10-30-00 --confirmar"
  exit 1
fi
if [ "$CONFIRMA" != "--confirmar" ]; then
  echo "Isto vai SUBSTITUIR o banco atual pelo backup de '$PASTA'."
  echo "Para continuar, rode de novo com --confirmar no final."
  exit 1
fi

docker stop "$APLICACAO" > /dev/null
trap 'docker start "$APLICACAO" > /dev/null' EXIT

docker cp "$PASTA/banco.sql" "$BANCO:/tmp/restaurar.sql" > /dev/null
docker exec -e "MYSQL_PWD=$SENHA" "$BANCO" sh -c "mysql -u$USUARIO alugafacil < /tmp/restaurar.sql"
docker exec "$BANCO" rm -f /tmp/restaurar.sql

if [ -f "$PASTA/fotos.tar.gz" ]; then
  # Pasta temporaria dentro do backup (e nao em /tmp), para o caminho valer tambem no Docker do Windows.
  TEMP="$PASTA/uploads-restaurar"
  rm -rf "$TEMP"
  mkdir -p "$TEMP"
  tar -xzf "$PASTA/fotos.tar.gz" -C "$TEMP"
  docker cp "$TEMP/." "$APLICACAO:/data/uploads/" > /dev/null
  rm -rf "$TEMP"
fi

echo "Restauracao concluida. A aplicacao foi iniciada de novo."
