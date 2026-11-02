output "db_endpoint" {
  value = aws_db_instance.this.address
}

output "db_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "app_security_group_id" {
  value = aws_security_group.app.id
}

output "alb_url" {
  value = "http://${aws_lb.this.dns_name}"
}

output "target_group_arn" {
  value = aws_lb_target_group.app.arn
}

output "ecs_cluster" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service" {
  value = aws_ecs_service.app.name
}