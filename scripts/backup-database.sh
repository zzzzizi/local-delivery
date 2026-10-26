#!/bin/sh
# Uses the same COMPOSE_FILE/COMPOSE_PROJECT_NAME as `docker compose`.
# Example: docker compose --env-file deploy/.env.trial -f deploy/compose.trial.yml ...
# For this script, export COMPOSE_FILE and COMPOSE_ENV_FILES instead (see deployment guide).
set -eu
umask 077
output=${1:-"backups/local-delivery-$(date -u +%Y%m%dT%H%M%SZ).dump"}
if [ -e "$output" ]; then
  echo "Refusing to overwrite an existing backup." >&2
  exit 1
fi
mkdir -p "$(dirname "$output")"
temporary=$(mktemp "${output}.tmp.XXXXXX")
trap 'rm -f "$temporary"' EXIT HUP INT TERM
# The password remains inside the container; it is never printed or passed as a command argument.
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "$temporary"
test -s "$temporary"
# Hard-link creation fails if a concurrent backup already claimed this destination.
ln "$temporary" "$output"
echo "Database backup saved to $output"
