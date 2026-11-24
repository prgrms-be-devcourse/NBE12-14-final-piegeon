# EC2가 쓰는 역할. 키 파일 없이 이 역할로 SSM 접속, DB 비밀번호와 앱 설정값 읽기를 한다.

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${var.team_tag}--ec2-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# SSM Session Manager 접속, Run Command(배포)용
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

locals {
  app_parameter_path = "/${var.project}/prod"
}

data "aws_iam_policy_document" "ec2_app" {
  # RDS 마스터 비밀번호 (AWS가 Secrets Manager에 만들어 둔 것)
  statement {
    sid       = "ReadDbSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_db_instance.main.master_user_secret[0].secret_arn]
  }

  # 앱 설정값 (Google 키 등). SSM Parameter Store의 /chakchak/prod/* 만 읽는다
  statement {
    sid     = "ReadAppParameters"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.app_parameter_path}",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.app_parameter_path}/*",
    ]
  }

  # SecureString을 풀 때 쓰는 KMS 기본 키. SSM을 거칠 때만 허용한다
  statement {
    sid       = "DecryptViaSsm"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com", "secretsmanager.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "ec2_app" {
  name   = "${var.team_tag}--ec2-app"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_app.json
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.team_tag}--ec2-profile"
  role = aws_iam_role.ec2.name
}
