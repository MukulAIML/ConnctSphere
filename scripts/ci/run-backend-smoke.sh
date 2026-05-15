#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${ROOT_DIR}/.ci-logs"
mkdir -p "${LOG_DIR}"

PIDS=()

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

dump_logs() {
  log "----- Service Log Tail -----"
  shopt -s nullglob
  for file in "${LOG_DIR}"/*.log; do
    log "tail: $(basename "${file}")"
    tail -n 120 "${file}" || true
  done
  shopt -u nullglob

  log "----- Docker Compose State -----"
  docker compose ps || true
  docker compose logs --no-color --tail 100 || true
}

cleanup() {
  local exit_code=$?

  for pid in "${PIDS[@]:-}"; do
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
    fi
  done

  sleep 2

  for pid in "${PIDS[@]:-}"; do
    if kill -0 "${pid}" >/dev/null 2>&1; then
      kill -9 "${pid}" >/dev/null 2>&1 || true
    fi
  done

  docker compose down -v >/dev/null 2>&1 || true

  if [[ ${exit_code} -ne 0 ]]; then
    dump_logs
  fi

  exit "${exit_code}"
}
trap cleanup EXIT

jar_path() {
  local module_dir=$1
  local jar
  jar="$(find "${ROOT_DIR}/${module_dir}/target" -maxdepth 1 -type f -name '*.jar' \
    ! -name '*.jar.original' ! -name '*sources.jar' ! -name '*javadoc.jar' | head -n 1 || true)"

  if [[ -z "${jar}" ]]; then
    log "Jar not found for module '${module_dir}'. Run: mvn -B -DskipTests clean package"
    return 1
  fi

  printf '%s' "${jar}"
}

start_service() {
  local name=$1
  local jar=$2
  shift 2

  log "Starting ${name}"
  (
    cd "${ROOT_DIR}"
    env "$@" java -jar "${jar}" > "${LOG_DIR}/${name}.log" 2>&1
  ) &

  local pid=$!
  PIDS+=("${pid}")
  log "${name} started (PID ${pid})"
}

wait_for_http_response() {
  local url=$1
  local name=$2
  local retries=${3:-90}
  local code="000"

  for ((i=1; i<=retries; i++)); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "${url}" || true)"
    if [[ "${code}" != "000" ]]; then
      log "${name} responded with HTTP ${code} at ${url}"
      return 0
    fi
    sleep 2
  done

  log "Timed out waiting for ${name} at ${url}"
  return 1
}

wait_for_docker_container_healthy() {
  local container=$1
  local retries=${2:-90}
  local status=""

  for ((i=1; i<=retries; i++)); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" || "${status}" == "running" ]]; then
      log "${container} is ${status}"
      return 0
    fi
    sleep 2
  done

  log "Timed out waiting for ${container}, last status=${status:-unknown}"
  return 1
}

wait_for_eureka_registrations() {
  local retries=${1:-120}
  local expected=(
    "API-GATEWAY"
    "AUTH-SERVICE"
    "POST-SERVICE"
    "COMMENT-SERVICE"
    "LIKE-SERVICE"
    "FOLLOW-SERVICE"
    "NOTIFICATION-SERVICE"
    "MEDIA-SERVICE"
    "SEARCH-SERVICE"
  )

  local apps_xml
  local missing=()

  for ((i=1; i<=retries; i++)); do
    apps_xml="$(curl -fsS http://localhost:8761/eureka/apps || true)"
    missing=()

    for svc in "${expected[@]}"; do
      if ! grep -q "<name>${svc}</name>" <<< "${apps_xml}"; then
        missing+=("${svc}")
      fi
    done

    if [[ ${#missing[@]} -eq 0 ]]; then
      log "All services are registered in Eureka"
      return 0
    fi

    log "Waiting for Eureka registrations: ${missing[*]}"
    sleep 2
  done

  log "Timed out waiting for service registration in Eureka"
  return 1
}

assert_status() {
  local method=$1
  local url=$2
  local expected_csv=$3
  local body="${4:-}"
  local retries="${5:-20}"
  local response_file
  local code=""
  local response_body=""

  for ((attempt=1; attempt<=retries; attempt++)); do
    response_file="$(mktemp)"

    if [[ -n "${body}" ]]; then
      code="$(curl -sS -o "${response_file}" -w '%{http_code}' \
        -X "${method}" -H 'Content-Type: application/json' \
        --data "${body}" "${url}" || true)"
    else
      code="$(curl -sS -o "${response_file}" -w '%{http_code}' \
        -X "${method}" "${url}" || true)"
    fi
    response_body="$(cat "${response_file}")"
    rm -f "${response_file}"

    IFS=',' read -r -a expected_codes <<< "${expected_csv}"
    for expected_code in "${expected_codes[@]}"; do
      if [[ "${code}" == "${expected_code}" ]]; then
        log "OK ${method} ${url} -> ${code}"
        return 0
      fi
    done

    log "Retry ${attempt}/${retries}: ${method} ${url} -> ${code}, expected [${expected_csv}]"
    sleep 2
  done

  log "FAILED ${method} ${url} -> ${code}, expected [${expected_csv}]"
  log "Response body: ${response_body}"
  return 1
}

log "Preparing smoke test logs at ${LOG_DIR}"
rm -f "${LOG_DIR}"/*.log 2>/dev/null || true

log "Starting infrastructure dependencies"
docker compose up -d connectsphere-db rabbitmq redis elasticsearch

wait_for_docker_container_healthy "connectsphere-db" 120
wait_for_docker_container_healthy "rabbitmq" 120
wait_for_docker_container_healthy "redis" 120
wait_for_docker_container_healthy "elasticsearch" 180

COMMON_ENV=(
  "DB_USERNAME=mukul"
  "DB_PASSWORD=password"
  "JWT_SECRET=ConnectSphereSecretKey2026ConnectSphereSecretKey2026ConnectSphereSecretKey"
  "EUREKA_SERVER=http://localhost:8761/eureka/"
  "RABBITMQ_HOST=localhost"
  "RABBITMQ_PORT=5672"
  "RABBITMQ_USERNAME=guest"
  "RABBITMQ_PASSWORD=guest"
  "REDIS_HOST=localhost"
  "REDIS_PORT=6379"
  "ELASTICSEARCH_URI=http://localhost:9200"
  "AUTH_SERVICE_URL=http://localhost:8080"
  "POST_SERVICE_URL=http://localhost:8081"
  "COMMENT_SERVICE_URL=http://localhost:8082"
  "FOLLOW_SERVICE_URL=http://localhost:8084"
  "NOTIFICATION_SERVICE_URL=http://localhost:8085"
  "MEDIA_SERVICE_URL=http://localhost:8086"
  "SEARCH_SERVICE_URL=http://localhost:8087"
)

DB_SUFFIX="?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"

start_service "service-registry" "$(jar_path "service-registry")"
wait_for_http_response "http://localhost:8761/" "service-registry" 120

start_service "auth-service" "$(jar_path "auth-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/connectsphere_auth${DB_SUFFIX}"
wait_for_http_response "http://localhost:8080/auth/oauth2/google" "auth-service"

start_service "post-service" "$(jar_path "post-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/post_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8081/posts/test" "post-service"

start_service "comment-service" "$(jar_path "comment-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/comment_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8082/comments/post/1/count" "comment-service"

start_service "like-service" "$(jar_path "like-service/LikeService")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/like_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8083/likes/target/post/1/count" "like-service"

start_service "follow-service" "$(jar_path "follow-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/follow_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8084/follows/following/1" "follow-service"

start_service "notification-service" "$(jar_path "notification-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/notification_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8085/swagger-ui.html" "notification-service"

start_service "media-service" "$(jar_path "media-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/media_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8086/swagger-ui.html" "media-service"

start_service "search-service" "$(jar_path "search-service")" \
  "${COMMON_ENV[@]}" \
  "DB_URL=jdbc:mysql://localhost:3307/search_db${DB_SUFFIX}"
wait_for_http_response "http://localhost:8087/search/posts?keyword=ci" "search-service"

start_service "api-gateway" "$(jar_path "api-gateway")" \
  "${COMMON_ENV[@]}"
wait_for_http_response "http://localhost:9000/api/v1/posts/test" "api-gateway"

wait_for_eureka_registrations 150

log "Running gateway smoke checks"
assert_status "GET"  "http://localhost:9000/api/v1/auth/oauth2/google" "302,303"
assert_status "GET"  "http://localhost:9000/api/v1/posts/test" "200"
assert_status "GET"  "http://localhost:9000/api/v1/comments/post/1/count" "200"
assert_status "GET"  "http://localhost:9000/api/v1/likes/target/post/1/count" "200"
assert_status "GET"  "http://localhost:9000/api/v1/follows/following/1" "200"
assert_status "POST" "http://localhost:9000/api/v1/notifications" "201" \
  '{"recipientId":2,"actorId":1,"type":"FOLLOW","message":"ci-smoke","targetId":2,"targetType":"USER"}'
assert_status "PUT"  "http://localhost:9000/api/v1/media/soft-delete" "200" \
  '{"mediaUrls":[]}'
assert_status "GET"  "http://localhost:9000/api/v1/search/posts?keyword=ci" "200"
assert_status "GET"  "http://localhost:9000/api/v1/hashtags/trending" "200"

log "Backend smoke test passed: services build, run together, and communicate via gateway + Eureka."
