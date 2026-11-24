variable "region" {
  description = "AWS 리전. 부트캠프 계정은 서울·도쿄·오사카·버지니아만 쓸 수 있다"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름 앞에 붙는 프로젝트 이름"
  type        = string
  default     = "chakchak"
}

variable "team_tag" {
  description = "모든 리소스의 Team 태그 값"
  type        = string
  default     = "devcos-team09"
}

variable "vpc_cidr" {
  description = "VPC 주소 범위"
  type        = string
  default     = "10.9.0.0/16"
}

variable "ec2_instance_type" {
  description = "백엔드 서버 크기. ARM(t4g)이라 Docker 이미지도 arm64로 빌드한다"
  type        = string
  default     = "t4g.small"
}

variable "ec2_volume_size" {
  description = "EC2 디스크 크기(GB)"
  type        = number
  default     = 20
}

variable "db_instance_class" {
  description = "RDS 크기"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "MySQL 버전. 메이저만 적으면 그 안의 기본 마이너 버전을 쓴다"
  type        = string
  default     = "8.4"
}

variable "db_allocated_storage" {
  description = "RDS 디스크 크기(GB)"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "처음 만들 데이터베이스 이름"
  type        = string
  default     = "chakchak"
}

variable "db_username" {
  description = "RDS 마스터 사용자 이름. 비밀번호는 AWS가 만들어 Secrets Manager에 보관한다"
  type        = string
  default     = "chakchak_admin"
}

variable "db_deletion_protection" {
  description = "RDS 삭제 보호. 지울 때만 false로 바꿔 apply한 뒤 destroy한다"
  type        = bool
  default     = true
}
