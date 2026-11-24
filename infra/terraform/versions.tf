terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.70"
    }
  }

  # 상태 파일은 팀 S3 버킷에 둔다. 버킷은 README의 "처음 한 번" 명령으로 미리 만든다.
  # use_lockfile: DynamoDB 없이 S3 파일로 잠가서 두 사람이 동시에 apply하지 못하게 한다.
  backend "s3" {
    bucket       = "devcos-team09-chakchak-tfstate"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
