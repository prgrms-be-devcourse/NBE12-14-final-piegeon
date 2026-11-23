#!/bin/bash
# EC2를 처음 켤 때 한 번 실행된다. Docker와 compose를 설치하고 앱 폴더를 만든다.
set -euxo pipefail

# 메모리 2GB라 빌드나 동시 실행 때 모자랄 수 있어서 스왑 2GB를 둔다
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
fi

dnf install -y docker mariadb105
systemctl enable --now docker
usermod -aG docker ec2-user

# docker compose 플러그인 (Amazon Linux 2023 저장소에 없어서 공식 배포본을 받는다)
COMPOSE_VERSION=v2.39.4
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 서버 시간대는 UTC 그대로 둔다. 앱은 Clock(Asia/Seoul)으로 시각을 구한다
mkdir -p /opt/chakchak
chown ec2-user:ec2-user /opt/chakchak
