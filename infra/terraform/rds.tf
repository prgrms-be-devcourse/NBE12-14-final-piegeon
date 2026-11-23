# MySQL 8.4. 프라이빗 서브넷에 두고 서버 보안그룹에서만 접근한다.
# 마스터 비밀번호는 AWS가 만들고 Secrets Manager에 보관한다(코드·상태 파일에 비밀번호가 남지 않는다).

resource "aws_db_subnet_group" "main" {
  name       = "${var.team_tag}--db-subnets"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${var.team_tag}--db-subnets" }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.team_tag}-db"
  engine         = "mysql"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name                     = var.db_name
  username                    = var.db_username
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 1
  auto_minor_version_upgrade = true
  apply_immediately          = true

  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.team_tag}-db-final"

  tags = { Name = "${var.team_tag}-db" }
}
