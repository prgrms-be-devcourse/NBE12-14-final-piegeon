# GitHub Actions 배포에 필요한 것: 배포 역할(OIDC), 앱 설정값(SSM Parameter Store), 배포 파일 보관 경로(S3 releases/).
# 배포 흐름: Actions가 이미지와 deploy/ 파일을 S3 releases/<커밋>/에 올리고, SSM Run Command로 서버의 deploy.sh를 실행한다.

variable "github_repository" {
  description = "배포를 허용할 GitHub 저장소 (owner/name)"
  type        = string
  default     = "prgrms-be-devcourse/NBE12-14-final-piegeon"
}

variable "github_environment" {
  description = "배포 job이 쓰는 GitHub environment. 이 environment에서 온 요청만 배포 역할을 받는다"
  type        = string
  default     = "production"
}

variable "cors_allowed_origins" {
  description = "쿠키를 주고받을 프론트 주소 (쉼표로 구분)"
  type        = string
  default     = "https://chakchak.world,https://www.chakchak.world"
}

variable "state_bucket" {
  description = "tfstate와 배포 파일(releases/)을 함께 두는 팀 S3 버킷 (부트캠프 규칙상 버킷 1개)"
  type        = string
  default     = "devcos-team09-chakchak-tfstate"
}

locals {
  releases_prefix = "releases"
}

# GitHub OIDC 공급자는 계정에 하나만 둘 수 있고 다른 팀이 이미 만들었다. 새로 만들지 않고 참조만 한다.
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    # 이 저장소의 production environment에서 돈 job만 받는다 (다른 저장소·포크·다른 job은 불가)
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "${var.team_tag}--github-deploy"
  assume_role_policy   = data.aws_iam_policy_document.github_assume.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "UploadRelease"
    actions   = ["s3:PutObject"]
    resources = ["arn:aws:s3:::${var.state_bucket}/${local.releases_prefix}/*"]
  }

  # 우리 서버에만, 셸 실행 문서로만 명령을 보낼 수 있다
  statement {
    sid     = "RunDeployOnOurServer"
    actions = ["ssm:SendCommand"]
    resources = [
      aws_instance.web.arn,
      "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
    ]
  }

  statement {
    sid       = "ReadCommandResult"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${var.team_tag}--github-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# 서버가 배포 파일을 받을 수 있게 releases/ 읽기를 추가한다
data "aws_iam_policy_document" "ec2_releases" {
  statement {
    sid       = "ReadReleases"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${var.state_bucket}/${local.releases_prefix}/*"]
  }
}

resource "aws_iam_role_policy" "ec2_releases" {
  name   = "${var.team_tag}--ec2-releases"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_releases.json
}

# 비밀이 아닌 앱 설정값. 비밀값(Google 키)은 사용자가 SecureString으로 직접 넣는다 (README 참고)
resource "aws_ssm_parameter" "db_url" {
  name  = "${local.app_parameter_path}/DB_URL"
  type  = "String"
  value = "jdbc:mysql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}"
}

resource "aws_ssm_parameter" "db_secret_arn" {
  name  = "${local.app_parameter_path}/DB_SECRET_ARN"
  type  = "String"
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}

resource "aws_ssm_parameter" "cors_allowed_origins" {
  name  = "${local.app_parameter_path}/CORS_ALLOWED_ORIGINS"
  type  = "String"
  value = var.cors_allowed_origins
}

# 배포 파일은 14일 뒤 자동 삭제 (버킷은 버전 관리라 지난 버전도 같이 지운다). tfstate 경로는 건드리지 않는다
resource "aws_s3_bucket_lifecycle_configuration" "releases" {
  bucket = var.state_bucket

  rule {
    id     = "expire-releases"
    status = "Enabled"

    filter {
      prefix = "${local.releases_prefix}/"
    }

    expiration {
      days = 14
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {
      prefix = "prod/"
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}
