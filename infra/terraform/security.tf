# 서버: 밖에서는 80(HTTP→HTTPS 이동, 인증서 발급)과 443만 들어온다.
# 22(SSH)는 열지 않는다. 서버 접속은 SSM Session Manager로 한다.
# 모니터링 포트(9090)도 열지 않는다.
resource "aws_security_group" "web" {
  name        = "${var.team_tag}--web-sg"
  description = "chakchak backend server: HTTP and HTTPS only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.team_tag}--web-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "web_http" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP (redirect to HTTPS, certificate challenge)"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "web_https" {
  security_group_id = aws_security_group.web.id
  description       = "HTTPS"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "web_all" {
  security_group_id = aws_security_group.web.id
  description       = "outbound (image pull, Google Places, SSM)"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# DB: 서버 보안그룹에서 오는 3306만 받는다. 인터넷에서는 닿지 않는다.
resource "aws_security_group" "db" {
  name        = "${var.team_tag}--db-sg"
  description = "chakchak RDS MySQL: only from web server"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.team_tag}--db-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_web" {
  security_group_id            = aws_security_group.db.id
  description                  = "MySQL from web server"
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = aws_security_group.web.id
}
