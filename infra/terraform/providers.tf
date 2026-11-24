provider "aws" {
  region = var.region

  # 여러 팀이 같이 쓰는 계정이라 모든 리소스에 팀 태그를 붙인다 (부트캠프 규칙)
  default_tags {
    tags = {
      Team      = var.team_tag
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}
