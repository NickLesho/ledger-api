output "db_endpoint" {
  value = aws_db_instance.this.address
}

output "db_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "app_security_group_id" {
  value = aws_security_group.app.id
}