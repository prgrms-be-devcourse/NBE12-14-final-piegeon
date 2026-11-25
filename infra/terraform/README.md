# 착착 AWS 인프라 (Terraform)

chakchak.world의 백엔드 서버와 DB를 만든다. 프론트는 Vercel에 따로 올린다.

```
인터넷 ── 80/443 ──▶ EC2 t4g.small (퍼블릭 서브넷, 탄력적 IP)
                         │ 3306 (서버 보안그룹에서만)
                         ▼
                    RDS MySQL 8.4 db.t4g.micro (프라이빗 서브넷)
```

| 파일 | 내용 |
| --- | --- |
| `network.tf` | VPC `10.9.0.0/16`, 퍼블릭·프라이빗 서브넷 2개씩, 인터넷 게이트웨이 (NAT 없음) |
| `security.tf` | 서버는 80·443만 공개, DB는 서버에서만 3306. 22(SSH)는 열지 않음 |
| `iam.tf` | 서버 역할: SSM 접속, DB 비밀번호와 `/chakchak/prod/*` 설정값 읽기 |
| `ec2.tf`, `user_data.sh` | 서버 한 대 + 고정 IP. 처음 켤 때 Docker, compose, 스왑 2GB 설치 |
| `rds.tf` | MySQL. 비밀번호는 AWS가 만들어 Secrets Manager에 보관 |

## 알아 둘 것

- **여러 팀이 같이 쓰는 계정이다.** 이 코드는 여기서 만든 리소스만 관리한다. 콘솔에서 다른 팀 리소스를 건드리지 않는다.
- 모든 리소스에 `Team=devcos-team09` 태그가 붙는다 (부트캠프 규칙).
- 부트캠프 지원은 기간 전체 약 75,000원이다. 24시간 켜 두면 월 약 $42(EC2 $15, RDS $18, 고정 IP $4, 디스크 $5). 실제로는 9~18시에만 자동으로 켜진다.
- 18시 이후에 쓰려면 콘솔에서 EC2와 RDS를 직접 켠다.
- 비밀번호·키를 코드나 `terraform.tfvars`에 적지 않는다. 앱 설정값은 SSM Parameter Store `/chakchak/prod/`에 SecureString으로 넣는다.

## 처음 한 번 (이미 했음)

상태 파일을 둘 S3 버킷을 만든다.

```bash
B=devcos-team09-chakchak-tfstate
aws s3api create-bucket --bucket $B --create-bucket-configuration LocationConstraint=ap-northeast-2
aws s3api put-bucket-versioning --bucket $B --versioning-configuration Status=Enabled
aws s3api put-public-access-block --bucket $B --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

## 바꾸고 적용하기

```bash
cd infra/terraform
export AWS_PROFILE=<팀 키를 넣은 프로필> AWS_REGION=ap-northeast-2
terraform init
terraform plan -out=tfplan     # 무엇이 바뀌는지 먼저 본다
terraform apply tfplan
```

상태 파일은 S3에 있고 잠금이 걸려서, 두 사람이 동시에 apply하면 늦은 쪽이 기다린다.

## 배포 (GitHub Actions)

`main`에 머지되면 `.github/workflows/deploy.yml`이 자동으로 배포한다. 운영에 배포하는 브랜치는 `main` 하나다. Actions 탭 → deploy → **Run workflow**로 다시 배포할 수 있지만 `main`에서만 된다. 서버와 DB가 하나라 여러 브랜치를 번갈아 배포하면 브랜치마다 다른 Flyway 마이그레이션이 운영 DB에 섞여 이후 배포가 막힌다.

```
Actions: ./gradlew build → arm64 이미지 → OIDC로 AWS 역할 → S3 releases/<커밋>/ 업로드
       → SSM Run Command로 서버의 deploy.sh 실행
서버(deploy/deploy.sh): 이미지 load → 설정값·DB 비밀 읽기 → compose up → 헬스 확인(3분) → 실패하면 직전 버전으로
```

- 서버 구성: `deploy/compose.prod.yml` (Caddy: 80·443, HTTPS 자동 / backend: 8080, actuator 9090은 서버 안에서만 / alloy: 수집기, 아래 "모니터링")
- 이미지는 GHCR 대신 S3로 전달한다. org 패키지는 비공개라 서버가 받으려면 토큰이 필요한데, 공용 계정의 SSM 명령 기록에 토큰을 남기지 않으려고
- 배포 역할(`devcos-team09--github-deploy`)은 이 저장소의 `production` environment에서 온 요청만 받는다. GitHub에 AWS 키를 저장하지 않는다
- `production` environment는 GitHub Settings → Environments에서 Deployment branches를 `main`으로 제한한다. 배포 역할은 서버에 root로 셸 명령을 보낼 수 있으므로, 다른 브랜치에서 워크플로를 고쳐 실행해도 역할을 받지 못하게 한다
- 저장소 변수: `AWS_DEPLOY_ROLE_ARN`, `EC2_INSTANCE_ID`, `DEPLOY_BUCKET` (비밀 아님, `terraform output`에서 나온 값)

### 앱 설정값 (SSM Parameter Store `/chakchak/prod/`)

| 이름 | 종류 | 넣는 곳 |
| --- | --- | --- |
| `DB_URL`, `DB_SECRET_ARN`, `CORS_ALLOWED_ORIGINS` | String | Terraform이 만든다 |
| `GOOGLE_PLACES_API_KEY` | SecureString | 사람이 직접 넣는다 (아래) |
| `API_DOMAIN` | String | 없으면 `api.chakchak.world` |
| `GRAFANA_PROM_URL`, `GRAFANA_PROM_USER`, `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_USER` | String | 사람이 직접 넣는다 (아래 "모니터링") |
| `GRAFANA_TOKEN` | SecureString | 사람이 직접 넣는다 (아래 "모니터링") |

```bash
# 값은 셸 기록에 남지 않게 입력받아 넣는다
read -rs KEY && aws ssm put-parameter --name /chakchak/prod/GOOGLE_PLACES_API_KEY --type SecureString --overwrite --value "$KEY"; unset KEY
```
값을 바꾼 뒤에는 다시 배포해야 반영된다.

### 모니터링 (Grafana Cloud)

서버에는 수집기(Grafana Alloy) 컨테이너 하나만 둔다. 저장, 화면, 알림은 Grafana Cloud에 있다. 서버 메모리가 2GB라 Prometheus와 Grafana를 같이 올리면 백엔드가 쓸 여유가 남지 않는다.

```
backend:9090/actuator/prometheus ─┐
서버 CPU·메모리·스왑·부하         ─┼─▶ alloy (60초마다 읽음) ── HTTPS ──▶ Grafana Cloud
/opt/chakchak/logs/backend.log   ─┘
```

- 설정: `deploy/config.alloy`. 모든 데이터에 `env="prod"` 라벨이 붙는다
- 수집기는 포트를 열지 않고, Docker 소켓과 호스트 폴더를 마운트하지 않는다. root가 아닌 사용자로 돌고 메모리는 256MB로 제한된다
- 로그는 백엔드가 `/opt/chakchak/logs/backend.log`에 쓴 것을 읽는다 (3일치, 합계 50MB까지). `docker logs`는 그대로 쓸 수 있다
- Caddy 로그와 컨테이너별 지표는 모으지 않는다

`GRAFANA_*` 값 5개가 모두 있을 때만 수집기가 뜬다. 하나라도 없으면 배포 로그에 "모니터링: 건너뜀"이 찍히고 나머지는 그대로 배포된다.

| 이름 | 값 | Grafana Cloud에서 찾는 곳 |
| --- | --- | --- |
| `GRAFANA_PROM_URL` | 지표 전송 주소 (`.../api/prom/push`로 끝남) | 스택의 Prometheus → Details → Remote Write Endpoint |
| `GRAFANA_PROM_USER` | 숫자로 된 사용자 번호 | 같은 화면의 Username / Instance ID |
| `GRAFANA_LOKI_URL` | 로그 전송 주소 (`.../loki/api/v1/push`로 끝남) | 스택의 Loki → Details |
| `GRAFANA_LOKI_USER` | 숫자로 된 사용자 번호 | 같은 화면의 User |
| `GRAFANA_TOKEN` | 쓰기 전용 토큰 (권한: `metrics:write`, `logs:write`) | Access Policies에서 발급 |

```bash
# 비밀이 아닌 4개
aws ssm put-parameter --name /chakchak/prod/GRAFANA_PROM_URL --type String --overwrite --value "<주소>"
# 토큰은 셸 기록에 남지 않게 입력받아 넣는다
read -rs KEY && aws ssm put-parameter --name /chakchak/prod/GRAFANA_TOKEN --type SecureString --overwrite --value "$KEY"; unset KEY
```

넣은 뒤 다시 배포하면 수집기가 뜬다. 배포 로그 끝의 `수집기 상태: running, 재시작 0회`로 확인한다. 재시작 횟수가 늘면 설정이나 값이 틀린 것이다. 수집기가 죽어도 배포는 실패로 보지 않는다.

```bash
# 서버에서: 수집기 로그와 메모리
cd /opt/chakchak && sudo docker compose -f compose.prod.yml logs --tail 50 alloy
sudo docker stats --no-stream
```

로컬에서 `config.alloy`를 고쳤으면 올리기 전에 검사한다.

```bash
docker run --rm -v "$PWD/deploy/config.alloy:/c/config.alloy:ro" \
  -e GRAFANA_PROM_URL=http://x -e GRAFANA_PROM_USER=1 -e GRAFANA_LOKI_URL=http://x \
  -e GRAFANA_LOKI_USER=2 -e GRAFANA_TOKEN=t -e MONITORING_ENV=local \
  grafana/alloy:v1.22.0 validate /c/config.alloy
```

### 서버에서 직접 보기

```bash
aws ssm start-session --target $(terraform output -raw web_instance_id)
cd /opt/chakchak && sudo docker compose -f compose.prod.yml ps
sudo docker compose -f compose.prod.yml logs -f --tail 100 backend
```

## 서버 접속 (SSH 키 없음)

```bash
aws ssm start-session --target $(terraform output -raw web_instance_id)
```

콘솔에서는 EC2 → 인스턴스 → 연결 → Session Manager.

## DB 비밀번호 보기 (필요할 때만)

```bash
aws secretsmanager get-secret-value --secret-id $(terraform output -raw db_secret_arn) --query SecretString --output text
```

## 지울 때

RDS는 삭제 보호가 켜져 있다. `db_deletion_protection = false`로 apply한 뒤 `terraform destroy`한다. 지울 때 마지막 스냅샷(`devcos-team09-db-final`)이 남는다.
