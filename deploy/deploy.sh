#!/bin/bash
# 서버에서 실행하는 배포 스크립트. GitHub Actions가 SSM Run Command로 부른다.
#   사용: deploy.sh <커밋 SHA> <S3 버킷>
# 하는 일: S3 releases/<SHA>/에서 이미지와 설정 파일 받기 → 이미지 load → 설정값·DB 비밀 읽기
#         → compose up → 헬스 확인 → 실패하면 직전 버전으로 되돌리기
# 비밀값은 파일로 남기지 않고 이 스크립트가 도는 동안 환경변수로만 compose에 넘긴다.
set -euo pipefail

SHA="$1"
BUCKET="$2"
REGION="${AWS_REGION:-ap-northeast-2}"
APP_DIR=/opt/chakchak
PARAM_PATH=/chakchak/prod
RELEASE_DIR="$APP_DIR/releases/$SHA"
CURRENT_FILE="$APP_DIR/CURRENT"

log() { echo "[deploy] $(date '+%H:%M:%S') $*"; }

log "버전 $SHA 받는 중"
mkdir -p "$RELEASE_DIR"
aws s3 cp --only-show-errors "s3://$BUCKET/releases/$SHA/" "$RELEASE_DIR/" --recursive --region "$REGION"

log "이미지 불러오는 중"
gunzip -c "$RELEASE_DIR/backend-image.tar.gz" | docker load
cp "$RELEASE_DIR/compose.prod.yml" "$RELEASE_DIR/Caddyfile" "$APP_DIR/"

# 설정값: SSM Parameter Store /chakchak/prod/* (SecureString은 복호화해서 읽음)
param() {
  aws ssm get-parameter --region "$REGION" --name "$PARAM_PATH/$1" --with-decryption \
    --query Parameter.Value --output text
}
DB_URL="$(param DB_URL)"
CORS_ALLOWED_ORIGINS="$(param CORS_ALLOWED_ORIGINS)"
GOOGLE_PLACES_API_KEY="$(param GOOGLE_PLACES_API_KEY)"
DB_SECRET_ARN="$(param DB_SECRET_ARN)"
API_DOMAIN="$(param API_DOMAIN 2>/dev/null || echo api.chakchak.world)"

# DB 계정: RDS가 Secrets Manager에 둔 마스터 비밀 ({"username":..., "password":...})
DB_SECRET="$(aws secretsmanager get-secret-value --region "$REGION" --secret-id "$DB_SECRET_ARN" \
  --query SecretString --output text)"
DB_USERNAME="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["username"])' <<<"$DB_SECRET")"
DB_PASSWORD="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["password"])' <<<"$DB_SECRET")"
unset DB_SECRET
export DB_URL DB_USERNAME DB_PASSWORD CORS_ALLOWED_ORIGINS GOOGLE_PLACES_API_KEY API_DOMAIN

up() {
  IMAGE_TAG="$1" docker compose -f "$APP_DIR/compose.prod.yml" --project-directory "$APP_DIR" up -d --remove-orphans
}

healthy() {
  # 기동 + Flyway 마이그레이션까지 최대 3분 기다린다
  for _ in $(seq 1 36); do
    if curl -fsS http://127.0.0.1:9090/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

PREVIOUS="$(cat "$CURRENT_FILE" 2>/dev/null || true)"

log "실행 중 (이전 버전: ${PREVIOUS:-없음})"
up "$SHA"

if healthy; then
  echo "$SHA" > "$CURRENT_FILE"
  log "성공: $SHA 가 떠 있음"
  # 지난 버전 파일과 이미지는 최근 3개만 남긴다
  ls -1t "$APP_DIR/releases" | tail -n +4 | while read -r old; do
    rm -rf "${APP_DIR:?}/releases/$old"
    docker image rm "chakchak-backend:$old" >/dev/null 2>&1 || true
  done
  exit 0
fi

log "실패: 헬스 확인이 3분 안에 UP이 되지 않음. 최근 로그:"
docker compose -f "$APP_DIR/compose.prod.yml" --project-directory "$APP_DIR" logs --tail 80 backend || true

if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$SHA" ]; then
  log "직전 버전 $PREVIOUS 로 되돌림"
  cp "$APP_DIR/releases/$PREVIOUS/compose.prod.yml" "$APP_DIR/releases/$PREVIOUS/Caddyfile" "$APP_DIR/" 2>/dev/null || true
  up "$PREVIOUS"
  healthy && log "되돌리기 성공" || log "되돌리기도 실패. 서버에서 직접 확인 필요"
fi
exit 1
