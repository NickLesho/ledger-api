resource "aws_db_subnet_group" "this" {
  name       = var.project
  subnet_ids = local.private_subnet_ids
}

resource "aws_db_instance" "this" {
  identifier     = var.project
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "ledger"
  username = "ledger_admin"

  # RDS generates the password, stores it in Secrets Manager and can rotate it.
  # It never appears in Terraform code, state, or the console.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 1
  auto_minor_version_upgrade = true
  apply_immediately          = true

  # Disposable dev database: destroyed every session, so no final snapshot.
  skip_final_snapshot = true
  deletion_protection = false
}