# The ECS tasks' security group. Its inbound rule (from the ALB) arrives in Milestone 9.
resource "aws_security_group" "app" {
  name        = "${var.project}-app"
  description = "Ledger API tasks"
  vpc_id      = local.vpc_id
}

# Tasks need outbound HTTPS to pull from ECR and read Secrets Manager.
resource "aws_vpc_security_group_egress_rule" "app_all_out" {
  security_group_id = aws_security_group.app.id
  description       = "Outbound to AWS APIs, ECR and the database"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# The database accepts connections only from the app's security group.
resource "aws_security_group" "db" {
  name        = "${var.project}-db"
  description = "Ledger Postgres"
  vpc_id      = local.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  description                  = "Postgres from the app tasks only"
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}