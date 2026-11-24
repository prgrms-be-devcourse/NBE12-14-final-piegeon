output "web_public_ip" {
  description = "서버 고정 IP. 가비아 DNS에서 api.chakchak.world A 레코드로 넣는다"
  value       = aws_eip.web.public_ip
}

output "web_instance_id" {
  description = "서버 ID. SSM 접속: aws ssm start-session --target <이 값>"
  value       = aws_instance.web.id
}

output "db_endpoint" {
  description = "RDS 주소. 서버 안에서만 닿는다"
  value       = aws_db_instance.main.address
}

output "db_secret_arn" {
  description = "RDS 마스터 비밀번호가 들어 있는 Secrets Manager 비밀"
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "app_parameter_path" {
  description = "앱 설정값(SSM Parameter Store)을 넣을 경로"
  value       = local.app_parameter_path
}

output "github_deploy_role_arn" {
  description = "GitHub Actions 배포 역할. 저장소 변수 AWS_DEPLOY_ROLE_ARN에 넣는다"
  value       = aws_iam_role.github_deploy.arn
}

output "state_bucket" {
  description = "배포 파일(releases/)을 올리는 버킷. 저장소 변수 DEPLOY_BUCKET에 넣는다"
  value       = var.state_bucket
}
