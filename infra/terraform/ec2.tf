# 백엔드 서버 한 대. SSE가 서버 한 대 기준 설계라 한 대로 운영한다.

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "web" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.web.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  user_data              = file("${path.module}/user_data.sh")

  # 메타데이터는 IMDSv2(토큰)만 받는다. 컨테이너 안에서도 닿게 hop 2
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.ec2_volume_size
    encrypted   = true
    tags        = { Name = "${var.team_tag}--web-root", Team = var.team_tag, Project = var.project, ManagedBy = "terraform" }
  }

  tags = { Name = "${var.team_tag}--web" }

  # 새 AMI가 나오거나 시작 스크립트를 고쳐도 서버를 다시 만들지 않는다 (데이터·설정 유지)
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "web" {
  domain   = "vpc"
  instance = aws_instance.web.id

  tags = { Name = "${var.team_tag}--eip" }

  depends_on = [aws_internet_gateway.main]
}
